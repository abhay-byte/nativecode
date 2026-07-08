package com.ivarna.nativecode.core.termux

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.ivarna.nativecode.core.runtime.Distro
import com.termux.shared.models.ExecutionCommand
import com.termux.shared.models.errors.Errno
import com.termux.shared.shell.ShellUtils
import com.termux.shared.shell.TermuxSession
import com.termux.shared.shell.TermuxTask
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import java.io.File
import java.util.ArrayList

class TerminalService : Service(),
    TermuxTask.TermuxTaskClient,
    TermuxSession.TermuxSessionClient {

    private var executionId = 1000
    private val handler by lazy { android.os.Handler(mainLooper) }
    var distro: Distro = Distro.Alpine

    val sessions = ArrayList<TermuxSession>()
    val tasks = ArrayList<TermuxTask>()
    private val pendingCommands = ArrayList<ExecutionCommand>()
    var activeSessionClient: TerminalSessionClient? = null
    var wantsToStop = false; private set
    private val envClient get() = FluxShellEnvironmentClient(this, distro)

    inner class LocalBinder : Binder() { val service: TerminalService get() = this@TerminalService }
    private val binder = LocalBinder()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra(EXTRA_DISTRO)?.let {
            try { distro = Distro.valueOf(it) } catch (_: Exception) {}
        }
        when (intent?.action) {
            ACTION_STOP_SERVICE -> actionStopService()
            ACTION_SERVICE_EXECUTE -> intent?.let { actionServiceExecute(it) }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() { Log.v(TAG, "onDestroy"); if (!wantsToStop) killAll() }
    override fun onBind(intent: Intent?) = binder
    override fun onUnbind(intent: Intent?): Boolean {         activeSessionClient = null; return false }

    private fun actionStopService() { wantsToStop = true; killAll(); stopSelf() }

    private fun killAll() {
        Log.d(TAG, "Kill sessions=${sessions.size} tasks=${tasks.size} pending=${pendingCommands.size}")
        for (s in ArrayList(sessions)) {
            s.killIfExecuting(this, wantsToStop || (s.executionCommand?.isPluginExecutionCommandWithPendingResult == true))
        }
        for (t in ArrayList(tasks)) {
            if (t.executionCommand?.isPluginExecutionCommandWithPendingResult == true) t.killIfExecuting(this, true)
        }
        for (cmd in ArrayList(pendingCommands)) {
            if (!cmd.shouldNotProcessResults() && cmd.isPluginExecutionCommandWithPendingResult) {
                if (cmd.setStateFailed(Errno.ERRNO_CANCELLED.getCode(), "Cancelled")) sendResult(cmd)
            }
        }
    }

    private fun actionServiceExecute(intent: Intent) {
        val cmd = ExecutionCommand(nextExecutionId())
        cmd.executableUri = intent.data
        cmd.inBackground = intent.getBooleanExtra(EXTRA_BACKGROUND, false)
        if (cmd.executableUri != null) {
            cmd.executable = cmd.executableUri.path
            cmd.arguments = intent.getStringArrayExtra(EXTRA_ARGUMENTS)
            cmd.stdin = intent.getStringExtra(EXTRA_STDIN)
        }
        cmd.workingDirectory = intent.getStringExtra(EXTRA_WORKDIR)
        cmd.sessionAction = intent.getStringExtra(EXTRA_SESSION_ACTION)
        cmd.commandLabel = intent.getStringExtra(EXTRA_COMMAND_LABEL) ?: "Execution Command"
        cmd.commandDescription = intent.getStringExtra(EXTRA_COMMAND_DESCRIPTION)
        cmd.commandHelp = intent.getStringExtra(EXTRA_COMMAND_HELP)
        cmd.isPluginExecutionCommand = true
        cmd.resultConfig.resultPendingIntent = intent.getParcelableExtra(EXTRA_PENDING_INTENT)
        cmd.resultConfig.resultDirectoryPath = intent.getStringExtra(EXTRA_RESULT_DIRECTORY)
        pendingCommands.add(cmd)
        if (cmd.inBackground) createTermuxTask(cmd) else createTermuxSession(cmd, null)
    }

    fun createTermuxTask(cmd: ExecutionCommand): TermuxTask? {
        if (cmd == null || !cmd.inBackground) return null
        Log.d(TAG, "Create task: ${cmd.commandIdAndLabelLogString}"); ensureProotLibs()
        val task = TermuxTask.execute(this, cmd, this, envClient, false)
        if (task == null) { Log.e(TAG, "Task failed"); return null }
        tasks.add(task); pendingCommands.remove(cmd); return task
    }

    override fun onTermuxTaskExited(task: TermuxTask) {
        handler.post {
            val cmd = task.executionCommand
            if (cmd?.isPluginExecutionCommand == true) sendResult(cmd)
            tasks.remove(task)
        }
    }

    fun createTermuxSession(cmd: ExecutionCommand, sessionName: String?): TermuxSession? {
        if (cmd == null || cmd.inBackground) return null
        Log.d(TAG, "Create session: ${cmd.commandIdAndLabelLogString}"); ensureProotLibs()
        val sn = sessionName ?: cmd.executable?.let { ShellUtils.getExecutableBasename(it).replace('-', ' ') }
        val session = TermuxSession.execute(this, cmd, activeSessionClient ?: baseClient, this, envClient, sn, cmd.isPluginExecutionCommand)
        if (session == null) { Log.e(TAG, "Session failed"); return null }
        sessions.add(session); pendingCommands.remove(cmd); return session
    }

    override fun onTermuxSessionExited(session: TermuxSession) {
        handler.post {
            val cmd = session.executionCommand
            if (cmd?.isPluginExecutionCommand == true) sendResult(cmd)
            sessions.remove(session)
        }
    }

    fun setSessionClient(client: TerminalSessionClient?) {
        activeSessionClient = client
        for (s in sessions) s.terminalSession.updateTerminalSessionClient(client)
    }

    private fun ensureProotLibs() {
        val fd = applicationContext.filesDir; val nd = applicationContext.applicationInfo.nativeLibraryDir
        for ((s, t) in listOf("libproot_loader.so" to "libproot_loader.so", "libtalloc.so" to "libtalloc.so.2", "libmemfd_shim.so" to "libmemfd_shim.so")) {
            val src = File(nd, s); val dst = File(fd, t)
            if (!dst.exists() && src.exists()) src.copyTo(dst, overwrite = true)
        }
    }

    private fun sendResult(cmd: ExecutionCommand) {
        val pi = cmd.resultConfig?.resultPendingIntent ?: return
        val data = cmd.resultData ?: return
        try {
            val intent = Intent()
            intent.putExtra("stdout", data.stdout.toString() as java.io.Serializable)
            intent.putExtra("stderr", data.stderr.toString() as java.io.Serializable)
            intent.putExtra("exitCode", data.exitCode)
            pi.send(this, 0, intent)
        } catch (e: Exception) { Log.w(TAG, "sendResult failed", e) }
    }

    fun nextExecutionId(): Int = ++executionId
    fun removeTermuxSession(session: TerminalSession): Int {
        val i = sessions.indexOfFirst { it.terminalSession == session }
        if (i >= 0) sessions[i].finish()
        return i
    }

    companion object {
        const val TAG = "FluxTerminalSvc"
        const val ACTION_STOP_SERVICE = "com.ivarna.nativecode.ACTION_STOP_SERVICE"
        const val ACTION_SERVICE_EXECUTE = "com.ivarna.nativecode.ACTION_SERVICE_EXECUTE"
        const val EXTRA_BACKGROUND = "com.ivarna.nativecode.EXTRA_BACKGROUND"; const val EXTRA_ARGUMENTS = "com.ivarna.nativecode.EXTRA_ARGUMENTS"
        const val EXTRA_STDIN = "com.ivarna.nativecode.EXTRA_STDIN"; const val EXTRA_WORKDIR = "com.ivarna.nativecode.EXTRA_WORKDIR"
        const val EXTRA_SESSION_ACTION = "com.ivarna.nativecode.EXTRA_SESSION_ACTION"; const val EXTRA_COMMAND_LABEL = "com.ivarna.nativecode.EXTRA_COMMAND_LABEL"
        const val EXTRA_COMMAND_DESCRIPTION = "com.ivarna.nativecode.EXTRA_COMMAND_DESCRIPTION"; const val EXTRA_COMMAND_HELP = "com.ivarna.nativecode.EXTRA_COMMAND_HELP"
        const val EXTRA_PENDING_INTENT = "com.ivarna.nativecode.EXTRA_PENDING_INTENT"; const val EXTRA_RESULT_DIRECTORY = "com.ivarna.nativecode.EXTRA_RESULT_DIRECTORY"
        const val EXTRA_DISTRO = "com.ivarna.nativecode.EXTRA_DISTRO"
    }

    private val baseClient = object : TerminalSessionClient {
        override fun onTextChanged(s: TerminalSession) {}
        override fun onTitleChanged(s: TerminalSession) {}
        override fun onSessionFinished(s: TerminalSession) {}
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
        override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) { Log.e(tag, message, e) }
        override fun logStackTrace(tag: String, e: Exception) { Log.e(tag, "stacktrace", e) }
    }
}
