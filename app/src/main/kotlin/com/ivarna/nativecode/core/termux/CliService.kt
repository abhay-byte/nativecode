package com.ivarna.nativecode.core.termux

import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.ivarna.nativecode.core.runtime.Distro
import com.termux.shared.models.ExecutionCommand
import com.termux.shared.models.errors.Errno
import java.io.File

class CliService : Service() {

    private val binder = LocalBinder()
    inner class LocalBinder : Binder() { val service: CliService get() = this@CliService }

    override fun onBind(intent: Intent?) = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")
        if (intent == null || ACTION_RUN_COMMAND != intent.action) return START_NOT_STICKY

        val cmd = ExecutionCommand()
        var errMsg: String? = null

        cmd.executable = intent.getStringExtra(EXTRA_COMMAND_PATH)
        cmd.arguments = intent.getStringArrayExtra(EXTRA_ARGUMENTS)
        cmd.stdin = intent.getStringExtra(EXTRA_STDIN)
        cmd.workingDirectory = intent.getStringExtra(EXTRA_WORKDIR)
        cmd.inBackground = intent.getBooleanExtra(EXTRA_BACKGROUND, false)
        cmd.sessionAction = intent.getStringExtra(EXTRA_SESSION_ACTION)
        cmd.commandLabel = intent.getStringExtra(EXTRA_COMMAND_LABEL) ?: "RUN_COMMAND Execution"
        cmd.commandDescription = intent.getStringExtra(EXTRA_COMMAND_DESCRIPTION)
        cmd.commandHelp = intent.getStringExtra(EXTRA_COMMAND_HELP)
        cmd.isPluginExecutionCommand = true
        cmd.resultConfig.resultPendingIntent = intent.getParcelableExtra(EXTRA_PENDING_INTENT)
        cmd.resultConfig.resultDirectoryPath = intent.getStringExtra(EXTRA_RESULT_DIRECTORY)

        val distroName = intent.getStringExtra(EXTRA_DISTRO) ?: "Alpine"
        val distro = try { Distro.valueOf(distroName) } catch (_: Exception) { Distro.Alpine }

        if (cmd.executable.isNullOrEmpty()) {
            errMsg = "Missing EXTRA_COMMAND_PATH"
            cmd.setStateFailed(Errno.ERRNO_FAILED.getCode(), errMsg)
            sendError(cmd, errMsg)
            Log.e(TAG, errMsg)
            return START_NOT_STICKY
        }

        val exeFile = File(cmd.executable)
        if (!exeFile.canExecute() && !exeFile.exists()) {
            errMsg = "Executable not found: ${cmd.executable}"
            cmd.setStateFailed(Errno.ERRNO_FAILED.getCode(), errMsg)
            sendError(cmd, errMsg)
            Log.e(TAG, errMsg)
            return START_NOT_STICKY
        }

        Log.d(TAG, "Forward to TerminalService: ${cmd.executable}")
        val svcIntent = Intent(this, TerminalService::class.java).apply {
            action = TerminalService.ACTION_SERVICE_EXECUTE
            `package` = packageName
            putExtra(TerminalService.EXTRA_BACKGROUND, cmd.inBackground)
            putExtra(TerminalService.EXTRA_ARGUMENTS, cmd.arguments)
            putExtra(TerminalService.EXTRA_STDIN, cmd.stdin)
            putExtra(TerminalService.EXTRA_WORKDIR, cmd.workingDirectory)
            putExtra(TerminalService.EXTRA_SESSION_ACTION, cmd.sessionAction)
            putExtra(TerminalService.EXTRA_COMMAND_LABEL, cmd.commandLabel)
            putExtra(TerminalService.EXTRA_COMMAND_DESCRIPTION, cmd.commandDescription)
            putExtra(TerminalService.EXTRA_COMMAND_HELP, cmd.commandHelp)
            putExtra(TerminalService.EXTRA_PENDING_INTENT, cmd.resultConfig.resultPendingIntent)
            putExtra(TerminalService.EXTRA_RESULT_DIRECTORY, cmd.resultConfig.resultDirectoryPath)
            putExtra(TerminalService.EXTRA_DISTRO, distro.name)
            data = Uri.fromFile(exeFile)
        }
        startService(svcIntent)
        return START_NOT_STICKY
    }

    private fun sendError(cmd: ExecutionCommand, msg: String) {
        val pi = cmd.resultConfig?.resultPendingIntent ?: return
        try { pi.send(this, 0, Intent().apply { putExtra("error", msg as java.io.Serializable) }) }
        catch (e: Exception) { Log.w(TAG, "sendError failed", e) }
    }

    companion object {
        const val TAG = "FluxCliService"
        const val ACTION_RUN_COMMAND = "com.ivarna.nativecode.RUN_COMMAND"
        const val PERMISSION_RUN_COMMAND = "com.ivarna.nativecode.permission.RUN_COMMAND"
        const val EXTRA_COMMAND_PATH = "com.ivarna.nativecode.RUN_COMMAND_PATH"
        const val EXTRA_ARGUMENTS = "com.ivarna.nativecode.RUN_COMMAND_ARGUMENTS"
        const val EXTRA_STDIN = "com.ivarna.nativecode.RUN_COMMAND_STDIN"
        const val EXTRA_WORKDIR = "com.ivarna.nativecode.RUN_COMMAND_WORKDIR"
        const val EXTRA_BACKGROUND = "com.ivarna.nativecode.RUN_COMMAND_BACKGROUND"
        const val EXTRA_SESSION_ACTION = "com.ivarna.nativecode.RUN_COMMAND_SESSION_ACTION"
        const val EXTRA_COMMAND_LABEL = "com.ivarna.nativecode.RUN_COMMAND_LABEL"
        const val EXTRA_COMMAND_DESCRIPTION = "com.ivarna.nativecode.RUN_COMMAND_DESCRIPTION"
        const val EXTRA_COMMAND_HELP = "com.ivarna.nativecode.RUN_COMMAND_HELP"
        const val EXTRA_PENDING_INTENT = "com.ivarna.nativecode.RUN_COMMAND_PENDING_INTENT"
        const val EXTRA_RESULT_DIRECTORY = "com.ivarna.nativecode.RUN_COMMAND_RESULT_DIRECTORY"
        const val EXTRA_DISTRO = "com.ivarna.nativecode.RUN_COMMAND_DISTRO"
    }
}
