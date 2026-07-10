package com.ivarna.nativecode.core.termux

import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * Runs GUI launch scripts (start_gui.sh) in a background proot session and
 * brings the in-app Termux:X11 activity to the foreground.
 *
 * Terminal stays off-screen (no TermuxTerminalScreen) so the X11 surface is primary.
 */
object GuiSessionLauncher {
    private const val TAG = "GuiSessionLauncher"

    private val bgProcess = AtomicReference<Process?>(null)
    private val xServerProcess = AtomicReference<Process?>(null)

    /**
     * Deploy is caller's job. This:
     * 1) starts termux-x11 [CmdEntryPoint] outside proot (real unix socket)
     * 2) starts [command] in background bootstrap proot
     * 3) opens [com.termux.x11.MainActivity]
     */
    fun launchGui(context: Context, command: String) {
        // Prefer Activity context so X11 is pushed on the same task as MainActivity
        val uiContext = context
        val app = context.applicationContext
        Thread({
            try {
                ensureXServer(app)
                Thread.sleep(1500L)
                runBootstrapCommand(app, command)
                // Re-raise X11 after socket/session ready (still same task via Activity if alive)
                Thread.sleep(2500L)
                openX11Activity(uiContext)
            } catch (e: Exception) {
                Log.e(TAG, "launchGui failed: ${e.message}", e)
            }
        }, "gui-session").apply { isDaemon = true }.start()

        // Immediate UI on same task
        openX11Activity(uiContext)
    }

    fun openX11Activity(context: Context) {
        try {
            val intent = Intent(context, com.termux.x11.MainActivity::class.java)
            // Same-task stack: MainActivity → X11 → Back returns to MainActivity.
            // Avoid NEW_TASK when possible (orphan task → Back looked like app closed).
            val act = context as? android.app.Activity
            if (act != null && !act.isFinishing && !act.isDestroyed) {
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                act.startActivity(intent)
            } else {
                // Fallback: package default affinity (manifest taskAffinity="") keeps one app task
                intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                )
                context.applicationContext.startActivity(intent)
            }
            Log.i(TAG, "Opened com.termux.x11.MainActivity (same-task preferred)")
        } catch (e: Exception) {
            Log.e(TAG, "openX11Activity: ${e.message}", e)
        }
    }

    /**
     * Start X server via app_process **outside proot** (real unix sockets).
     *
     * Correct embedding (per termux-x11 design):
     * - CLASSPATH = this app's APK (contains CmdEntryPoint + libXlorie)
     * - Entry = com.termux.x11.CmdEntryPoint (not Loader → standalone com.termux.x11)
     * - TERMUX_X11_OVERRIDE_PACKAGE = this package (ACTION_START hits embedded MainActivity)
     * - TMPDIR = app termux-tmp (shared with proot-distro)
     */
    fun ensureXServer(context: Context) {
        val realTmp = File(context.filesDir, "termux-tmp").also { it.mkdirs() }
        val realPrefix = File(context.filesDir, "termux-prefix")

        val sock = File(realTmp, ".X11-unix/X0")
        if (sock.exists()) {
            Log.i(TAG, "X socket already present")
            return
        }

        // Kill stale X server processes (best-effort)
        try {
            Runtime.getRuntime().exec(arrayOf("sh", "-c",
                "ps -A -o pid= -o args= 2>/dev/null | while read p a; do " +
                    "case \"\$a\" in " +
                    "*com.termux.x11.Loader*|*com.termux.x11.CmdEntryPoint*|*termux-x11\\ com.termux*) " +
                    "kill -9 \$p 2>/dev/null;; esac; done"
            )).waitFor()
        } catch (_: Exception) {}

        File(realTmp, ".X11-unix").mkdirs()
        File(realTmp, "runtime-flux-xfce").mkdirs()
        sock.delete()
        File(realTmp, ".X0-lock").delete()

        val xkbCandidates = listOf(
            File(realPrefix, "var/lib/proot-distro/containers/debian/rootfs/usr/share/X11/xkb"),
            File(realPrefix, "var/lib/proot-distro/installed-rootfs/debian/usr/share/X11/xkb"),
            File(realPrefix, "share/X11/xkb"),
        )
        val xkb = xkbCandidates.firstOrNull { it.isDirectory }?.absolutePath

        // Build CLASSPATH from our own APK(s) — embedded termux-x11 library
        val ai = context.applicationInfo
        val cp = buildString {
            append(ai.sourceDir)
            ai.splitSourceDirs?.forEach { append(':').append(it) }
        }
        val nativeLibDir = ai.nativeLibraryDir

        val log = File(realTmp, "x11-start.log")
        // Official pattern (README chroot section): app_process … CmdEntryPoint :0
        val pb = ProcessBuilder(
            "/system/bin/app_process",
            "-Xnoimage-dex2oat",
            "/",
            "--nice-name=termux-x11 com.termux.x11 :0",
            "com.termux.x11.CmdEntryPoint",
            ":0",
            "-ac",
        )
        val env = pb.environment()
        env["ANDROID_DATA"] = "/data"
        env["ANDROID_ROOT"] = "/system"
        env["ANDROID_STORAGE"] = "/storage"
        env["EXTERNAL_STORAGE"] = "/sdcard"
        env["CLASSPATH"] = cp
        env["TMPDIR"] = realTmp.absolutePath
        env["XDG_RUNTIME_DIR"] = File(realTmp, "runtime-flux-xfce").absolutePath
        // Broadcast ACTION_START to our package (embedded MainActivity receiver)
        env["TERMUX_X11_OVERRIDE_PACKAGE"] = context.packageName
        if (xkb != null) env["XKB_CONFIG_ROOT"] = xkb
        // Explicit path for CmdEntryPoint to System.load (embedded packaging)
        if (!nativeLibDir.isNullOrBlank()) {
            env["TERMUX_X11_NATIVE_LIBDIR"] = nativeLibDir
            val prev = env["LD_LIBRARY_PATH"]
            env["LD_LIBRARY_PATH"] = if (prev.isNullOrBlank()) nativeLibDir else "$nativeLibDir:$prev"
        }
        env.remove("LD_PRELOAD")
        pb.redirectErrorStream(true)
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(log))
        pb.directory(realTmp)

        try {
            xServerProcess.getAndSet(null)?.destroy()
            val p = pb.start()
            xServerProcess.set(p)
            Log.i(TAG, "Started embedded X server (CmdEntryPoint) CLASSPATH=$cp log=${log.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start X server: ${e.message}", e)
        }
    }

    /** Run shell command inside bootstrap proot; keep process until exit. */
    fun runBootstrapCommand(context: Context, command: String) {
        if (!TermuxBootstrapManager.isInstalled(context)) {
            Log.e(TAG, "Bootstrap not installed — cannot run: $command")
            return
        }
        TermuxBootstrapManager.ensureProotDeps(context)

        val launcher = TermuxBootstrapManager.launcherPath()
        val prootArgs = TermuxBootstrapManager.buildProotArgs(context)
        // linker64 + proot argv… + bash -lc command
        val cmd = ArrayList<String>()
        cmd.add(launcher)
        cmd.addAll(prootArgs)
        cmd.add("-lc")
        cmd.add(command)

        val envPairs = TermuxBootstrapManager.buildEnvironment(context)
        val pb = ProcessBuilder(cmd)
        val env = pb.environment()
        for (pair in envPairs) {
            val i = pair.indexOf('=')
            if (i > 0) env[pair.substring(0, i)] = pair.substring(i + 1)
        }
        env["ANDROID_DATA"] = "/data"
        env["ANDROID_ROOT"] = "/system"

        val log = File(context.filesDir, "termux-tmp/gui-session.log")
        log.parentFile?.mkdirs()
        pb.redirectErrorStream(true)
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(log))
        pb.directory(context.filesDir)

        try {
            bgProcess.getAndSet(null)?.destroy()
            val p = pb.start()
            bgProcess.set(p)
            Log.i(TAG, "Background GUI session started cmd=$command log=${log.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "runBootstrapCommand failed: ${e.message}", e)
        }
    }

    fun stopAll() {
        bgProcess.getAndSet(null)?.destroy()
        xServerProcess.getAndSet(null)?.destroy()
    }
}
