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
     * Fast GUI path (bypasses TermuxTerminalScreen / PTY).
     *
     * Real Termux XFCE = 1× proot (debian). Old NativeCode path was
     * outer bootstrap proot + proot-distro = 2× proot (very slow).
     *
     * Now:
     * 1) ensureXServer (host app_process)
     * 2) open X11 ASAP
     * 3) single proot into debian rootfs → startxfce4 (no outer termux shell)
     */
    fun launchGui(context: Context, command: String) {
        val uiContext = context
        val app = context.applicationContext
        openX11Activity(uiContext)
        Thread({
            try {
                val t0 = System.currentTimeMillis()
                ensureXServer(app)
                val sock = File(app.filesDir, "termux-tmp/.X11-unix/X0")
                var n = 0
                while (n < 30 && !sock.exists()) {
                    Thread.sleep(50L)
                    n++
                }
                openX11Activity(uiContext)
                val distro = parseDistroFromCommand(command)
                if (!runDebianDesktop(app, distro)) {
                    Log.w(TAG, "direct debian proot failed — fallback outer bootstrap")
                    runBootstrapCommand(app, command)
                }
                Log.i(TAG, "launchGui pipeline ${System.currentTimeMillis() - t0}ms sock=${sock.exists()}")
            } catch (e: Exception) {
                Log.e(TAG, "launchGui failed: ${e.message}", e)
            }
        }, "gui-session").apply { isDaemon = true }.start()
    }

    private fun parseDistroFromCommand(command: String): String {
        // "bash ~/start_gui.sh debian" or "bash ~/start_gui_kde.sh debian"
        val parts = command.trim().split(Regex("\\s+"))
        return parts.lastOrNull()?.takeIf { it.matches(Regex("[a-zA-Z0-9_-]+")) && it != "start_gui.sh" && !it.contains("start_gui") }
            ?: "debian"
    }

    /**
     * One proot into installed proot-distro rootfs (fluxlinux-speed).
     * Uses apk-native libproot.so (DT_NEEDED resolves).
     */
    fun runDebianDesktop(context: Context, distro: String = "debian"): Boolean {
        val prefix = TermuxBootstrapManager.prefixDir(context)
        val rootfsCandidates = listOf(
            File(prefix, "var/lib/proot-distro/containers/$distro/rootfs"),
            File(prefix, "var/lib/proot-distro/installed-rootfs/$distro"),
        )
        val rootfs = rootfsCandidates.firstOrNull { File(it, "bin/bash").exists() }
        if (rootfs == null) {
            Log.e(TAG, "debian rootfs not found under $prefix for distro=$distro")
            return false
        }

        val nativeLib = context.applicationInfo.nativeLibraryDir
        val proot = File(nativeLib, "libproot.so")
        if (!proot.exists()) {
            Log.e(TAG, "libproot.so missing in nativeLibraryDir=$nativeLib")
            return false
        }
        val loader = File(nativeLib, "libproot_loader.so").absolutePath
        val realTmp = File(context.filesDir, "termux-tmp").also {
            it.mkdirs()
            File(it, ".X11-unix").mkdirs()
            File(it, "runtime-flux-xfce").mkdirs()
        }

        // Guest command: fluxlinux-style startxfce4 as flux (no xdpyinfo waits)
        val guestScript = """
            set -e
            mkdir -p /tmp/runtime-flux-xfce /tmp/.X11-unix
            chown -R flux:flux /tmp/runtime-flux-xfce 2>/dev/null || true
            chmod 700 /tmp/runtime-flux-xfce
            export DISPLAY=:0
            export PULSE_SERVER=tcp:127.0.0.1
            export XDG_RUNTIME_DIR=/tmp/runtime-flux-xfce
            export VTEST_SOCKET_NAME=/tmp/.virgl_test
            exec su -s /bin/bash flux -c '
              export DISPLAY=:0
              export PULSE_SERVER=tcp:127.0.0.1
              export XDG_RUNTIME_DIR=/tmp/runtime-flux-xfce
              export VTEST_SOCKET_NAME=/tmp/.virgl_test
              export HOME=/home/flux
              export USER=flux
              xfconf-query -c xfwm4 -p /general/use_compositing -s false 2>/dev/null || true
              exec dbus-launch --exit-with-session startxfce4
            '
        """.trimIndent()

        // ProcessBuilder: linker64 proot [args...]  (argv0 = linker for shell-style)
        val cmd = mutableListOf(
            TermuxBootstrapManager.linkerPath(),
            proot.absolutePath,
            proot.absolutePath,
            "--rootfs=${rootfs.absolutePath}",
            "--bind=/dev",
            "--bind=/proc",
            "--bind=/sys",
            "--bind=/system",
            // shared tmp so guest /tmp/.X11-unix/X0 is host termux-tmp socket
            "--bind=${realTmp.absolutePath}:/tmp",
            "--bind=/sdcard:/sdcard",
            "--link2symlink",
            "-w", "/home/flux",
            "/bin/bash",
            "-lc",
            guestScript,
        )

        val log = File(realTmp, "gui-session.log")
        try { log.writeText("") } catch (_: Exception) {}

        val pb = ProcessBuilder(cmd)
        val env = pb.environment()
        env["PROOT_LOADER"] = loader
        env["PROOT_TMP_DIR"] = realTmp.absolutePath
        env["PROOT_NO_SECCOMP"] = "1"
        env["ANDROID_DATA"] = "/data"
        env["ANDROID_ROOT"] = "/system"
        env["LD_LIBRARY_PATH"] = nativeLib
        env.remove("LD_PRELOAD")
        pb.redirectErrorStream(true)
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(log))
        pb.directory(context.filesDir)

        return try {
            bgProcess.getAndSet(null)?.destroy()
            val p = pb.start()
            bgProcess.set(p)
            Log.i(TAG, "single-proot desktop rootfs=${rootfs.absolutePath} log=${log.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "runDebianDesktop failed: ${e.message}", e)
            false
        }
    }

    fun openX11Activity(context: Context) {
        try {
            val intent = Intent(context, com.termux.x11.MainActivity::class.java)
            // Stack on host task (manifest taskAffinity=package). NEVER bare NEW_TASK
            // alone — orphan X11 task + finish/ACTION_STOP → Android launcher "home".
            val act = (context as? android.app.Activity)?.takeIf {
                !it.isFinishing && !it.isDestroyed
            }
            if (act != null) {
                intent.addFlags(
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
                act.startActivity(intent)
            } else {
                intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
                context.applicationContext.startActivity(intent)
            }
            Log.i(TAG, "Opened com.termux.x11.MainActivity sameTask=${act != null}")
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
        // Live socket only if an X server process still holds it. Stale sockets
        // make start_gui skip restart → black screen / "didn't work".
        val xAlive = try {
            val p = Runtime.getRuntime().exec(
                arrayOf(
                    "sh", "-c",
                    "ps -A -o args= 2>/dev/null | grep -E 'CmdEntryPoint|com\\.termux\\.x11\\.Loader|termux-x11 com\\.termux' | grep -v grep"
                )
            )
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor()
            out.isNotBlank()
        } catch (_: Exception) {
            false
        }
        if (sock.exists() && xAlive) {
            Log.i(TAG, "X socket already present (server alive)")
            return
        }
        if (sock.exists() && !xAlive) {
            Log.w(TAG, "Stale X socket — restarting server")
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

    /** Run shell command inside bootstrap proot via ProcessBuilder (no PTY). */
    fun runBootstrapCommand(context: Context, command: String) {
        if (!TermuxBootstrapManager.isInstalled(context)) {
            Log.e(TAG, "Bootstrap not installed — cannot run: $command")
            return
        }
        TermuxBootstrapManager.ensureProotDeps(context)

        // ProcessBuilder argv0 = executable. JNI session uses linker as cmd + args[0]=proot.
        // Shell-equivalent: linker64 proot … bash -lc 'cmd'
        val launch = TermuxBootstrapManager.buildSessionLaunch(context, execCommand = command)
        val cmd = ArrayList<String>(1 + launch.args.size)
        cmd.add(launch.executable)
        cmd.addAll(launch.args)

        val pb = ProcessBuilder(cmd)
        val env = pb.environment()
        for (pair in launch.env) {
            val i = pair.indexOf('=')
            if (i > 0) env[pair.substring(0, i)] = pair.substring(i + 1)
        }
        env["ANDROID_DATA"] = "/data"
        env["ANDROID_ROOT"] = "/system"
        env["PROOT_NO_SECCOMP"] = "1"

        val log = File(context.filesDir, "termux-tmp/gui-session.log")
        log.parentFile?.mkdirs()
        // Truncate old log so tail is current run
        try { log.writeText("") } catch (_: Exception) {}
        pb.redirectErrorStream(true)
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(log))
        pb.directory(context.filesDir)

        try {
            bgProcess.getAndSet(null)?.destroy()
            val p = pb.start()
            bgProcess.set(p)
            Log.i(TAG, "Background GUI session cmd=$command log=${log.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "runBootstrapCommand failed: ${e.message}", e)
        }
    }

    fun stopAll() {
        bgProcess.getAndSet(null)?.destroy()
        xServerProcess.getAndSet(null)?.destroy()
    }
}
