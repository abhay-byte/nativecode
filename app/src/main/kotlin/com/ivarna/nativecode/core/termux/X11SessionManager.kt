package com.ivarna.nativecode.core.termux

import android.content.Context
import android.util.Log
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manages the embedded Termux terminal session (by running local /system/bin/sh
 * shell session in the app's files directory using termux terminal emulator library)
 * and detects when an X11 display server becomes active.
 */
object X11SessionManager {

    private const val TAG = "X11SessionManager"
    private const val POLL_INTERVAL_MS = 1000L

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var monitorJob: Job? = null
    private var terminalJob: Job? = null

    private val _isX11Active = MutableStateFlow(false)
    val isX11Active: StateFlow<Boolean> = _isX11Active

    private val _isTerminalRunning = MutableStateFlow(false)
    val isTerminalRunning: StateFlow<Boolean> = _isTerminalRunning

    private val _session = MutableStateFlow<TerminalSession?>(null)
    val session: StateFlow<TerminalSession?> = _session

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
            _isTerminalRunning.value = false
            _session.value = null
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
        override fun logStackTraceWithMessage(tag: String, message: String, e: java.lang.Exception) {
            Log.e(tag, message, e)
        }
        override fun logStackTrace(tag: String, e: java.lang.Exception) { Log.e(tag, "stacktrace", e) }
    }

    /**
     * Starts the local /system/bin/sh terminal session.
     */
    fun startTerminal(context: Context) {
        if (_isTerminalRunning.value) {
            Log.d(TAG, "Terminal already running")
            return
        }

        Log.d(TAG, "Starting local system terminal session")
        terminalJob?.cancel()
        terminalJob = scope.launch {
            val shellPath = "/system/bin/sh"
            val homePath = context.filesDir.absolutePath
            val args = arrayOf("-i")
            val env = arrayOf(
                "TERM=xterm-256color",
                "PATH=/system/bin:/system/xbin",
                "HOME=$homePath",
                "LANG=C.UTF-8"
            )

            val s = withContext(Dispatchers.Main) {
                try {
                    val session = TerminalSession(
                        shellPath,
                        homePath,
                        args,
                        env,
                        2000,
                        sessionClient
                    )
                    session.initializeEmulator(80, 24, 0, 0)
                    session.updateSize(80, 24, 0, 0)
                    session
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create local TerminalSession", e)
                    null
                }
            }

            if (s != null) {
                _session.value = s
                _isTerminalRunning.value = true
                _isX11Active.value = false
                startX11Monitor(context)
                Log.d(TAG, "Local terminal session started successfully")
            } else {
                Log.e(TAG, "Local terminal session failed to start")
            }
        }
    }

    /**
     * Stops the terminal session and cancels monitoring.
     */
    fun stopTerminal() {
        Log.d(TAG, "Stopping terminal session")
        monitorJob?.cancel()
        monitorJob = null
        terminalJob?.cancel()
        terminalJob = null
        
        _session.value?.finishIfRunning()
        _session.value = null
        _isTerminalRunning.value = false
        _isX11Active.value = false
    }

    /**
     * Resets X11 state and resumes monitoring.
     */
    fun resetX11State(context: Context) {
        _isX11Active.value = false
        if (_isTerminalRunning.value && monitorJob?.isActive != true) {
            startX11Monitor(context)
        }
    }

    /**
     * Writes input to the active session.
     */
    fun write(input: String) {
        _session.value?.write(input)
    }

    // ── Private ────────────────────────────────────────────────────────────

    private fun startX11Monitor(context: Context) {
        // Distro X11 socket path: /data/data/com.ivarna.nativecode/files/tmp/.X11-unix/X0
        val internalSocket = File(context.filesDir, "tmp/.X11-unix/X0")
        // External Termux socket path
        val externalSocket = File("/data/data/com.termux/files/usr/tmp/.X11-unix/X0")

        monitorJob?.cancel()
        monitorJob = scope.launch {
            Log.d(TAG, "X11 monitor started. Internal: ${internalSocket.absolutePath}, External: ${externalSocket.absolutePath}")
            while (isActive) {
                val internalExists = internalSocket.exists()
                val externalExists = externalSocket.exists()
                
                if ((internalExists || externalExists) && !_isX11Active.value) {
                    Log.i(TAG, "X11 socket detected (internal=$internalExists, external=$externalExists) — activating display")
                    _isX11Active.value = true
                    break
                }
                delay(POLL_INTERVAL_MS)
            }
            Log.d(TAG, "X11 monitor exited")
        }
    }
}
