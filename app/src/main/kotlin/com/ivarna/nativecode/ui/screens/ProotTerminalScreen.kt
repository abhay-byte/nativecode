package com.ivarna.nativecode.ui.screens

import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ivarna.nativecode.core.runtime.Distro
import com.ivarna.nativecode.core.termux.ProotTerminalManager
import com.ivarna.nativecode.core.termux.TerminalService
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProotTerminalScreen(
    onBack: () -> Unit,
    distro: Distro = Distro.Alpine,
) {
    val context = LocalContext.current

    // Start TerminalService for CLI/RUN_COMMAND support
    LaunchedEffect(Unit) {
        context.startService(Intent(context, TerminalService::class.java).apply {
            putExtra(TerminalService.EXTRA_DISTRO, distro.name)
        })
    }

    val manager = remember(distro) { ProotTerminalManager(context, distro) }

    val terminalView = remember { mutableStateOf<TerminalView?>(null) }

    DisposableEffect(manager) {
        manager.onTextChangedListener = {
            terminalView.value?.onScreenUpdated()
        }
        onDispose {
            manager.onTextChangedListener = null
        }
    }

    var session by remember { mutableStateOf<TerminalSession?>(null) }
    var ready by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Initializing ${distro.displayName}...") }

    LaunchedEffect(distro) {
        status = "Extracting rootfs..."
        val ok = withContext(Dispatchers.IO) { manager.ensureReady() }
        if (ok) {
            val s = manager.createSession()
            session = s
            ready = true
            status = "${distro.displayName} Terminal"
        } else {
            status = "Failed to initialize rootfs"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(distro.displayName, style = MaterialTheme.typography.titleMedium)
                        if (!ready) {
                            Text(status, style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF5C6BC0))
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        manager.destroySession()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF0D1117))
        ) {
            if (!ready) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFF5C6BC0))
                        Spacer(Modifier.height(12.dp))
                        Text(status, color = Color(0xFFD4D4D4))
                    }
                }
            } else {
                AndroidView(
                    factory = { ctx ->
                        TerminalView(ctx, null).apply {
                            setTextSize(40)
                            setTerminalViewClient(object : TerminalViewClient {
                                override fun onScale(scale: Float): Float = 1.0f
                                override fun onSingleTapUp(e: android.view.MotionEvent) {}
                                override fun shouldBackButtonBeMappedToEscape(): Boolean = false
                                override fun shouldEnforceCharBasedInput(): Boolean = false
                                override fun shouldUseCtrlSpaceWorkaround(): Boolean = true
                                override fun isTerminalViewSelected(): Boolean = true
                                override fun copyModeChanged(copyMode: Boolean) {}
                                override fun onKeyDown(
                                    keyCode: Int, e: KeyEvent,
                                    session: TerminalSession
                                ): Boolean = false
                                override fun onKeyUp(code: Int, e: KeyEvent): Boolean = false
                                override fun onLongPress(e: android.view.MotionEvent): Boolean = false
                                override fun readControlKey(): Boolean = false
                                override fun readAltKey(): Boolean = false
                                override fun readShiftKey(): Boolean = false
                                override fun readFnKey(): Boolean = false
                                override fun onCodePoint(
                                    codePoint: Int, ctrlDown: Boolean,
                                    session: TerminalSession
                                ): Boolean = false
                                override fun onEmulatorSet() {}
                                override fun logError(t: String, m: String) { android.util.Log.e(t, m) }
                                override fun logWarn(t: String, m: String) { android.util.Log.w(t, m) }
                                override fun logInfo(t: String, m: String) { android.util.Log.i(t, m) }
                                override fun logDebug(t: String, m: String) { android.util.Log.d(t, m) }
                                override fun logVerbose(t: String, m: String) { android.util.Log.v(t, m) }
                                override fun logStackTraceWithMessage(t: String, m: String, e: Exception) {
                                    android.util.Log.e(t, m, e)
                                }
                                override fun logStackTrace(t: String, e: Exception) {
                                    android.util.Log.e(t, "stacktrace", e)
                                }
                            })
                            terminalView.value = this
                        }
                    },
                    update = { view ->
                        session?.let { s ->
                            view.attachSession(s)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
