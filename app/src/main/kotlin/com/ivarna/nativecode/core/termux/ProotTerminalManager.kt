package com.ivarna.nativecode.core.termux

import android.content.Context
import android.util.Log
import com.ivarna.nativecode.core.runtime.Distro
import com.ivarna.nativecode.core.runtime.EmbeddedRuntime
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import java.io.File

class ProotTerminalManager(
    private val context: Context,
    private val distro: Distro = Distro.Alpine,
) {
    private val TAG = "ProotTerminalManager"
    private val runtime = EmbeddedRuntime(context, distro)
    private var session: TerminalSession? = null

    var onTextChangedListener: (() -> Unit)? = null

    private val sessionClient = object : TerminalSessionClient {
        override fun onTextChanged(s: TerminalSession) {
            val text = s.emulator?.screen?.transcriptText ?: ""
            Log.d("TerminalOutput", "Transcript updated: length=${text.length}")
            if (text.isNotEmpty()) {
                Log.d("TerminalOutput", "Last line: " + text.lines().lastOrNull { it.isNotBlank() })
            }
            onTextChangedListener?.invoke()
        }
        override fun onTitleChanged(s: TerminalSession) {}
        override fun onSessionFinished(s: TerminalSession) {
            Log.d(TAG, "Session finished: exit=${s.exitStatus}")
            session = null
            onFinish?.invoke()
        }
        override fun onCopyTextToClipboard(s: TerminalSession, text: String) {}
        override fun onPasteTextFromClipboard(s: TerminalSession) {}
        override fun onBell(s: TerminalSession) {}
        override fun onColorsChanged(s: TerminalSession) {}
        override fun onTerminalCursorStateChange(state: Boolean) {}
        override fun getTerminalCursorStyle(): Int? = null
        override fun logError(tag: String, message: String) { Log.e(tag, message) }
        override fun logWarn(tag: String, message: String) { Log.w(tag, message) }
        override fun logInfo(tag: String, message: String) { Log.i(tag, message) }
        override fun logDebug(tag: String, message: String) { Log.d(tag, message) }
        override fun logVerbose(tag: String, message: String) { Log.v(tag, message) }
        override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {
            Log.e(tag, message, e)
        }
        override fun logStackTrace(tag: String, e: Exception) { Log.e(tag, "stacktrace", e) }
    }

    var onFinish: (() -> Unit)? = null

    suspend fun ensureReady(): Boolean {
        val result = runtime.ensureRootfs()
        return result.isSuccess
    }

    fun createSession(): TerminalSession? {
        session?.let { return it }
        ensurePaths()

        val proot = runtime.prootPath
        val rootfs = runtime.rootfsDir.absolutePath
        val home = File(context.filesDir, "home").also { it.mkdirs() }.absolutePath
        val tmp = File(context.filesDir, "tmp").also { it.mkdirs() }.absolutePath
        val parent = context.filesDir.parentFile?.absolutePath ?: "/data/data/${context.packageName}"

        val args = mutableListOf(
            "libproot.so",
            "--rootfs=$rootfs",
            "--bind=/dev", "--bind=/proc", "--bind=/sys",
            "--bind=/sdcard/Android/data/${context.packageName}/files:/sdcard",
            "--bind=$home:/root",
            "--bind=$tmp:/tmp",
            "-0", "-w", "/root",
        )

        val x11Socket = "/data/data/com.termux/files/usr/tmp/.X11-unix"
        if (File(x11Socket).exists()) {
            args.add("--bind=$x11Socket:$rootfs/tmp/.X11-unix")
        }
        if (parent.isNotEmpty()) {
            args.add("--bind=$parent:/data/data/${context.packageName}")
        }

        args.add("/bin/sh")
        args.add("-l")

        val env = arrayOf(
            "HOME=/root",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "TERM=xterm-256color",
            "LANG=C.UTF-8",
            "LD_LIBRARY_PATH=${context.filesDir.absolutePath}:${context.applicationInfo.nativeLibraryDir}",
            "PROOT_TMP_DIR=$tmp",
            "PROOT_LOADER=${context.applicationInfo.nativeLibraryDir}/libproot_loader.so",
        )

        return try {
            val s = TerminalSession(proot, "/", args.toTypedArray(), env, 2000, sessionClient)
            s.initializeEmulator(80, 24, 0, 0)
            s.updateSize(80, 24, 0, 0)
            session = s
            s
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create proot session", e)
            null
        }
    }

    fun destroySession() {
        session?.finishIfRunning()
        session = null
    }

    private fun ensurePaths() {
        val loader = File(context.applicationInfo.nativeLibraryDir, "libproot_loader.so")
        val targetLoader = File(context.filesDir, "libproot_loader.so")
        if (!targetLoader.exists() && loader.exists()) {
            loader.copyTo(targetLoader, overwrite = true)
        }
        val talloc = File(context.applicationInfo.nativeLibraryDir, "libtalloc.so")
        val targetTalloc = File(context.filesDir, "libtalloc.so.2")
        if (!targetTalloc.exists() && talloc.exists()) {
            talloc.copyTo(targetTalloc, overwrite = true)
        }
        val memfd = File(context.applicationInfo.nativeLibraryDir, "libmemfd_shim.so")
        val targetMemfd = File(context.filesDir, "libmemfd_shim.so")
        if (!targetMemfd.exists() && memfd.exists()) {
            memfd.copyTo(targetMemfd, overwrite = true)
        }
    }

    companion object {
        private const val TAG = "ProotTerminalManager"
    }
}
