package com.ivarna.nativecode.core.termux

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lightweight embedded command executor replacing external Termux intent-based communication.
 *
 * Uses [ProcessBuilder] directly instead of termux-shared's [com.termux.shared.shell.TermuxTask]
 * to avoid tight coupling to Termux-specific paths (`/data/data/com.termux/`). For full
 * PTY-backed interactive sessions the existing [com.ivarna.nativecode.core.runtime.EmbeddedRuntime]
 * + [com.ivarna.nativecode.core.runtime.ShellSession] should be used instead.
 */
object EmbeddedTermuxBridge {

    private const val TAG = "EmbeddedTermux"

    data class ExecResult(
        val stdout: String,
        val stderr: String,
        val exitCode: Int,
    )

    /**
     * Execute a command synchronously (blocks caller thread).
     */
    suspend fun executeSync(
        command: List<String>,
        workingDir: String? = null,
        env: Map<String, String> = emptyMap(),
    ): ExecResult = withContext(Dispatchers.IO) {
        val proc = buildProcess(command, workingDir, env)
        val stdout = proc.inputStream.bufferedReader().readText()
        val stderr = proc.errorStream.bufferedReader().readText()
        val exit = proc.waitFor()
        proc.destroy()
        ExecResult(stdout.trim(), stderr.trim(), exit)
    }

    /**
     * Execute a command asynchronously, streaming output lines via Flow.
     */
    fun executeAsync(
        command: List<String>,
        workingDir: String? = null,
        env: Map<String, String> = emptyMap(),
    ): Flow<String> = callbackFlow {
        val cancelled = AtomicBoolean(false)
        val proc = buildProcess(command, workingDir, env)

        val readerThread = Thread {
            proc.inputStream.bufferedReader().useLines { lines ->
                lines.forEach {
                    if (!cancelled.get()) trySend(it)
                }
            }
        }.also { it.isDaemon = true; it.start() }

        val errThread = Thread {
            proc.errorStream.bufferedReader().useLines { lines ->
                lines.forEach {
                    if (!cancelled.get()) trySend("[stderr] $it")
                }
            }
        }.also { it.isDaemon = true; it.start() }

        readerThread.join()
        errThread.join()
        val exitCode = proc.waitFor()
        if (exitCode != 0) trySend("[exit] $exitCode")
        proc.destroy()
        close()
    }

    /**
     * Simple shell command execution — wraps [executeAsync] with `/system/bin/sh -c`.
     */
    fun runShellCommand(
        script: String,
        workingDir: String? = null,
        env: Map<String, String> = emptyMap(),
    ): Flow<String> = executeAsync(
        command = listOf("/system/bin/sh", "-c", script),
        workingDir = workingDir,
        env = env,
    )

    private fun buildProcess(
        command: List<String>,
        workingDir: String?,
        env: Map<String, String>,
    ): Process {
        val pb = ProcessBuilder(command)
        pb.redirectErrorStream(false)
        if (workingDir != null) pb.directory(File(workingDir))
        for ((k, v) in env) pb.environment()[k] = v
        Log.d(TAG, "exec: ${command.joinToString(" ")}")
        return pb.start()
    }
}
