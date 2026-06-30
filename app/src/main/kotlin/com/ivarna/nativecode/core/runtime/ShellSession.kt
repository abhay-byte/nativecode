package com.ivarna.nativecode.core.runtime

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Drives an interactive proot session. Output is streamed **line by line** so the
 * Compose LazyColumn can render incrementally without an ANSI parser buffering the
 * whole transcript. Mirrors Kai's `ProotHandle` model: one process, one read pair
 * (stdout + stderr merged) on background threads, atomic cancel flag.
 */
class ShellSession(private val runtime: EmbeddedRuntime) {

    private val cancelled = AtomicBoolean(false)
    private var process: Process? = null
    private var writerJob: Job? = null
    private var readerJob: Job? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _lines = MutableSharedFlow<TerminalLine>(
        replay = 0,
        extraBufferCapacity = 512,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val lines: SharedFlow<TerminalLine> = _lines.asSharedFlow()

    val isRunning: Boolean get() = process?.isAlive == true && !cancelled.get()

    /** Start a persistent interactive login shell. */
    fun start(): Boolean {
        if (isRunning) return true
        cancelled.set(false)
        return try {
            val proc = runtime.startInteractiveShell()
            process = proc

            // Stream stdout + stderr line-by-line, merging into the same flow.
            val pipe = proc.inputStream
            val errPipe = proc.errorStream
            readerJob = scope.launch {
                val out = BufferedReader(InputStreamReader(pipe, Charsets.UTF_8))
                val err = BufferedReader(InputStreamReader(errPipe, Charsets.UTF_8))
                val a = launch { drain(out) { TerminalLine.Output(it) } }
                val b = launch { drain(err) { TerminalLine.Error(it) } }
                joinAll(a, b)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start shell: ${e.message}")
            false
        }
    }

    private suspend fun drain(
        reader: BufferedReader,
        wrap: (String) -> TerminalLine,
    ) {
        try {
            while (!cancelled.get()) {
                val line = reader.readLine() ?: break
                _lines.emit(wrap(line))
            }
        } catch (_: Exception) {
            // pipe closed
        }
    }

    /** Send one command followed by a newline. */
    fun sendInput(text: String) {
        if (cancelled.get()) return
        runCatching {
            val out = process?.outputStream ?: return
            out.write(text.toByteArray(Charsets.UTF_8))
            out.write("\n".toByteArray(Charsets.UTF_8))
            out.flush()
        }
    }

    fun kill() {
        if (cancelled.getAndSet(true)) return
        runCatching { process?.inputStream?.close() }
        runCatching { process?.errorStream?.close() }
        runCatching { process?.outputStream?.close() }
        runCatching { process?.destroyForcibly() }
        process = null
        scope.coroutineContext.cancelChildren()
    }

    companion object {
        private const val TAG = "ShellSession"
    }
}
