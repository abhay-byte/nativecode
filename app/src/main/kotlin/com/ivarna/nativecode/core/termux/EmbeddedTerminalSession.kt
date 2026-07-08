package com.ivarna.nativecode.core.termux

import android.util.Log
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient

class EmbeddedTerminalSession(
    private val shellPath: String = "/system/bin/sh",
    private val cwd: String = "/",
    private val args: Array<String> = emptyArray(),
    private val env: Array<String> = defaultEnv(),
) {
    private val TAG = "EmbeddedTerminalSession"

    var session: TerminalSession? = null
        private set

    private val _client: TerminalSessionClient = object : TerminalSessionClient {
        override fun onTextChanged(changedSession: TerminalSession) {
            textCallback?.invoke(
                changedSession.getEmulator()?.getScreen()?.getTranscriptText() ?: ""
            )
        }
        override fun onTitleChanged(changedSession: TerminalSession) {}
        override fun onSessionFinished(finishedSession: TerminalSession) {
            Log.d(TAG, "Session finished: exit=${finishedSession.exitStatus}")
        }
        override fun onCopyTextToClipboard(session: TerminalSession, text: String) {}
        override fun onPasteTextFromClipboard(session: TerminalSession) {}
        override fun onBell(session: TerminalSession) {}
        override fun onColorsChanged(session: TerminalSession) {}
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

    var textCallback: ((String) -> Unit)? = null

    fun start(): Boolean {
        if (session != null) return true
        return try {
            val s = TerminalSession(shellPath, cwd, args, env, null, _client)
            s.initializeEmulator(80, 24, 0, 0)
            s.updateSize(80, 24, 0, 0)
            session = s
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start terminal session", e)
            false
        }
    }

    fun write(input: String) {
        session?.write(input)
    }

    fun stop() {
        session?.finishIfRunning()
        session = null
    }

    companion object {
        fun defaultEnv(): Array<String> = arrayOf(
            "TERM=xterm-256color",
            "HOME=/data/data/com.ivarna.nativecode/files/home",
            "PATH=/sbin:/system/sbin:/system/bin:/system/xbin:/odm/bin:/vendor/bin",
        )
    }
}
