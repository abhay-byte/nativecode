package com.ivarna.nativecode.core.runtime

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Manages a persistent interactive proot shell session.
 * Provides raw stdin access and streams raw stdout/stderr bytes
 * so xterm.js in the WebView can render them natively (including
 * all ANSI escape sequences, colors, nerd font glyphs, etc.).
 */
class ShellSession(private val runtime: EmbeddedRuntime) {

    private var process: Process? = null

    private val outputBuffer = StringBuilder()

    private val _output = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 4096,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    /** Raw terminal output (ANSI codes preserved) to feed directly to xterm.js. */
    val output: SharedFlow<String> = _output.asSharedFlow()

    private val _exitCode = MutableStateFlow<Int?>(null)
    val exitCode: StateFlow<Int?> = _exitCode.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val isRunning: Boolean get() = process?.isAlive == true

    fun getBufferedOutput(): String = synchronized(outputBuffer) {
        outputBuffer.toString()
    }

    private fun appendToBuffer(chunk: String) = synchronized(outputBuffer) {
        outputBuffer.append(chunk)
        if (outputBuffer.length > 50000) {
            outputBuffer.delete(0, outputBuffer.length - 20000)
        }
    }

    /**
     * Start the interactive shell. Call once; reuse the session until [kill].
     * Returns false if proot is not available.
     */
    fun start(): Boolean {
        return try {
            val proc = runtime.startInteractiveShell()
            process = proc

            // Drain stdout → xterm
            scope.launch {
                try {
                    val buf = ByteArray(4096)
                    val stream = proc.inputStream
                    while (isActive) {
                        val n = stream.read(buf)
                        if (n < 0) break
                        val chunk = String(buf, 0, n, Charsets.UTF_8)
                        appendToBuffer(chunk)
                        _output.emit(chunk)
                    }
                } catch (_: Exception) {}
            }

            // Drain stderr → xterm (merge into same stream)
            scope.launch {
                try {
                    val buf = ByteArray(4096)
                    val stream = proc.errorStream
                    while (isActive) {
                        val n = stream.read(buf)
                        if (n < 0) break
                        val chunk = String(buf, 0, n, Charsets.UTF_8)
                        appendToBuffer(chunk)
                        _output.emit(chunk)
                    }
                } catch (_: Exception) {}
            }

            // Wait for process exit
            scope.launch {
                try {
                    val code = proc.waitFor()
                    _exitCode.value = code
                    val exitMsg = "\r\n\u001b[1;33m[Process exited with code $code]\u001b[0m\r\n"
                    appendToBuffer(exitMsg)
                    _output.emit(exitMsg)
                } catch (_: Exception) {}
            }

            Log.i(TAG, "Shell session started")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start shell: ${e.message}")
            false
        }
    }

    /**
     * Send raw input bytes to the shell's stdin.
     * This is called from xterm.js onData events (keypresses, pastes, etc.)
     */
    fun sendInput(data: String) {
        try {
            process?.outputStream?.let { out ->
                out.write(data.toByteArray(Charsets.UTF_8))
                out.flush()
            }
        } catch (e: Exception) {
            Log.w(TAG, "sendInput failed: ${e.message}")
        }
    }

    /** Kill the shell process and clean up. */
    fun kill() {
        scope.cancel()
        try { process?.destroyForcibly() } catch (_: Exception) {}
        process = null
    }

    companion object {
        private const val TAG = "ShellSession"
    }
}
