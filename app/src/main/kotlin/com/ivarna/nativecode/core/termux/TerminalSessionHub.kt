package com.ivarna.nativecode.core.termux

import android.content.Context
import android.util.Log
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * Process-wide registry of live terminal sessions so the Terminal screen can
 * show a sidebar of tabs (Termux shell, Debian proot, XFCE start_gui, …)
 * without killing sessions when navigating away.
 */
object TerminalSessionHub {
    private const val TAG = "TerminalSessionHub"
    private val seq = AtomicInteger(0)

    data class Tab(
        val id: String,
        val title: String,
        val subtitle: String,
        val session: TerminalSession,
        val createdAt: Long = System.currentTimeMillis(),
    )

    private val _tabs = MutableStateFlow<List<Tab>>(emptyList())
    val tabs: StateFlow<List<Tab>> = _tabs.asStateFlow()

    private val _activeId = MutableStateFlow<String?>(null)
    val activeId: StateFlow<String?> = _activeId.asStateFlow()

    fun activeTab(): Tab? {
        val id = _activeId.value ?: return _tabs.value.lastOrNull()
        return _tabs.value.find { it.id == id } ?: _tabs.value.lastOrNull()
    }

    fun setActive(id: String) {
        if (_tabs.value.any { it.id == id }) _activeId.value = id
    }

    fun titleForCommand(command: String?, fallback: String = "Terminal"): String {
        if (command.isNullOrBlank()) return "Termux"
        val c = command.trim()
        return when {
            c.contains("start_gui_kde") -> "KDE Desktop"
            c.contains("start_gui") -> "XFCE Desktop"
            c.contains("stop_gui") -> "Stop GUI"
            c.contains("proot-distro login") -> {
                val distro = Regex("""proot-distro\s+login\s+(\S+)""")
                    .find(c)?.groupValues?.getOrNull(1) ?: "linux"
                distro.replaceFirstChar { it.uppercase() }
            }
            c.contains("install_nativecode") || c.contains("[STEP") -> "Install"
            c.contains("setup_") -> "Setup"
            else -> fallback.take(24)
        }
    }

    fun subtitleForCommand(command: String?): String {
        if (command.isNullOrBlank()) return "bash · Termux"
        val c = command.trim()
        return when {
            c.contains("start_gui") -> "GUI · $c".take(48)
            c.contains("proot-distro") -> "proot · $c".take(48)
            else -> c.take(48)
        }
    }

    /**
     * Create a new Termux-bootstrap shell session and register it.
     * [sessionClient] receives I/O events; rebind via [attachClient] when switching tabs.
     */
    fun createBootstrapSession(
        context: Context,
        sessionClient: TerminalSessionClient,
        title: String? = null,
        command: String? = null,
        cols: Int = 80,
        rows: Int = 24,
    ): Tab? {
        return try {
            TermuxBootstrapManager.ensureProotDeps(context)
            val launcher = TermuxBootstrapManager.launcherPath()
            val args = TermuxBootstrapManager.buildProotArgs(context)
            val cwd = context.filesDir.absolutePath
            val env = TermuxBootstrapManager.buildEnvironment(context)
            val session = TerminalSession(launcher, cwd, args, env, 2000, sessionClient)
            session.initializeEmulator(cols, rows, 0, 0)

            val n = seq.incrementAndGet()
            val tab = Tab(
                id = UUID.randomUUID().toString(),
                title = title ?: titleForCommand(command, "Shell $n"),
                subtitle = subtitleForCommand(command),
                session = session,
            )
            _tabs.update { it + tab }
            _activeId.value = tab.id
            Log.i(TAG, "Created tab id=${tab.id} title=${tab.title}")

            if (!command.isNullOrBlank()) {
                // Delay so bash RC settles
                Thread({
                    try {
                        Thread.sleep(1200L)
                        if (session.isRunning) session.write(command + "\n")
                    } catch (_: Exception) {}
                }, "term-cmd-${tab.id.take(8)}").apply { isDaemon = true }.start()
            }
            tab
        } catch (e: Exception) {
            Log.e(TAG, "createBootstrapSession failed", e)
            null
        }
    }

    fun attachClient(client: TerminalSessionClient?) {
        for (tab in _tabs.value) {
            try {
                tab.session.updateTerminalSessionClient(client)
            } catch (e: Exception) {
                Log.w(TAG, "attachClient ${tab.id}: ${e.message}")
            }
        }
    }

    fun close(id: String) {
        val tab = _tabs.value.find { it.id == id } ?: return
        try {
            tab.session.finishIfRunning()
        } catch (_: Exception) {}
        _tabs.update { list -> list.filterNot { it.id == id } }
        if (_activeId.value == id) {
            _activeId.value = _tabs.value.lastOrNull()?.id
        }
        Log.i(TAG, "Closed tab $id remaining=${_tabs.value.size}")
    }

    fun closeAll() {
        for (t in _tabs.value.toList()) {
            try { t.session.finishIfRunning() } catch (_: Exception) {}
        }
        _tabs.value = emptyList()
        _activeId.value = null
    }

    fun markFinished(session: TerminalSession) {
        val id = _tabs.value.find { it.session === session }?.id ?: return
        _tabs.update { list -> list.filterNot { it.id == id } }
        if (_activeId.value == id) {
            _activeId.value = _tabs.value.lastOrNull()?.id
        }
    }

    fun ensureBootstrapReady(context: Context): Boolean =
        TermuxBootstrapManager.isInstalled(context)
}
