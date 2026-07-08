package com.ivarna.nativecode.ui.screens

import android.view.View
import android.view.inputmethod.InputMethodManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ivarna.nativecode.core.termux.X11SessionManager
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import kotlinx.coroutines.flow.collectLatest

private val TermBg = Color(0xFF0D1117)
private val TermFg = Color(0xFFD4D4D4)
private val TermAccent = Color(0xFF58A6FF)
private val TermGreen = Color(0xFF3FB950)
private val TermRed = Color(0xFFF48771)

/**
 * Full-screen embedded terminal screen powered by the termux terminal-view library.
 *
 * - Hosts [TerminalView] via [AndroidView] interop
 * - Connects to [X11SessionManager.session] for the live PTY-backed session
 * - Monitors [X11SessionManager.isX11Active]; auto-calls [onX11Active] when display server detected
 * - Extra keys bar: Tab, Ctrl, Esc, ↑, ↓, ←, →
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmbeddedTerminalScreen(
    onBack: () -> Unit,
    onX11Active: () -> Unit,
) {
    val context = LocalContext.current
    val isX11Active by X11SessionManager.isX11Active.collectAsState()
    val isRunning by X11SessionManager.isTerminalRunning.collectAsState()
    val terminalSession by X11SessionManager.session.collectAsState()

    // Auto-navigate when X11 socket appears
    LaunchedEffect(Unit) {
        X11SessionManager.isX11Active.collectLatest { active ->
            if (active) onX11Active()
        }
    }

    // Start terminal on first entry
    LaunchedEffect(Unit) {
        if (!X11SessionManager.isTerminalRunning.value) {
            X11SessionManager.startTerminal(context)
        }
    }

    val terminalView = remember { mutableStateOf<TerminalView?>(null) }

    DisposableEffect(Unit) {
        X11SessionManager.onTextChangedListener = {
            terminalView.value?.onScreenUpdated()
        }
        onDispose {
            X11SessionManager.onTextChangedListener = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TermBg)
            .systemBarsPadding()
    ) {
        // ── Top Bar ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF161B22))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TermFg
                )
            }

            Text(
                text = "Terminal",
                color = TermFg,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            // X11 status badge
            AnimatedVisibility(visible = isX11Active, enter = fadeIn(), exit = fadeOut()) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = TermGreen.copy(alpha = 0.2f),
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.DesktopWindows,
                            contentDescription = null,
                            tint = TermGreen,
                            modifier = Modifier.size(14.dp)
                        )
                        Text("X11 LIVE", color = TermGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            IconButton(onClick = {
                terminalView.value?.let { view ->
                    view.requestFocus()
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
                }
            }) {
                Icon(Icons.Default.Keyboard, contentDescription = "Keyboard", tint = TermFg.copy(alpha = 0.7f))
            }

            // Stop session
            IconButton(onClick = {
                X11SessionManager.stopTerminal()
                onBack()
            }) {
                Icon(Icons.Default.Stop, contentDescription = "Stop", tint = TermRed.copy(alpha = 0.8f))
            }
        }

        // ── Terminal View ────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AndroidView(
                factory = { ctx ->
                    TerminalView(ctx, null).apply {
                        isFocusable = true
                        isFocusableInTouchMode = true
                        setTextSize(40)
                        setTerminalViewClient(SimpleTerminalViewClient {
                            requestFocus()
                            val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                            imm.showSoftInput(this, 0)
                        })
                        if (terminalSession != null) {
                            attachSession(terminalSession)
                        }
                        terminalView.value = this
                        requestFocus()
                    }
                },
                update = { view ->
                    if (terminalSession != null && view.currentSession != terminalSession) {
                        view.attachSession(terminalSession)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Status overlay when not running
            if (!isRunning) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(TermBg.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = TermAccent, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Starting terminal...", color = TermFg, fontSize = 14.sp)
                    }
                }
            }
        }

        // ── Extra Keys Bar ───────────────────────────────────────────────────
        ExtraKeysBar()
    }
}

@Composable
private fun ExtraKeysBar() {
    val keys = listOf(
        "Tab" to "\t",
        "Esc" to "\u001b",
        "Ctrl-C" to "\u0003",
        "Ctrl-D" to "\u0004",
        "↑" to "\u001b[A",
        "↓" to "\u001b[B",
        "←" to "\u001b[D",
        "→" to "\u001b[C",
        "Home" to "\u001b[H",
        "End" to "\u001b[F",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF161B22))
            .padding(vertical = 4.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        keys.forEach { (label, sequence) ->
            Surface(
                onClick = { X11SessionManager.write(sequence) },
                shape = RoundedCornerShape(4.dp),
                color = Color(0xFF21262D),
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = label,
                        color = TermFg,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Minimal [TerminalViewClient] implementation — handles required callbacks.
 * Input dispatching is handled natively by [TerminalView] itself.
 */
private class SimpleTerminalViewClient(private val onSingleTap: () -> Unit) : TerminalViewClient {
    override fun logError(tag: String?, message: String?) { android.util.Log.e(tag ?: "Terminal", message ?: "") }
    override fun logWarn(tag: String?, message: String?) { android.util.Log.w(tag ?: "Terminal", message ?: "") }
    override fun logInfo(tag: String?, message: String?) { android.util.Log.i(tag ?: "Terminal", message ?: "") }
    override fun logDebug(tag: String?, message: String?) { android.util.Log.d(tag ?: "Terminal", message ?: "") }
    override fun logVerbose(tag: String?, message: String?) { android.util.Log.v(tag ?: "Terminal", message ?: "") }
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) { android.util.Log.e(tag ?: "Terminal", message ?: "", e) }
    override fun logStackTrace(tag: String?, e: Exception?) { android.util.Log.e(tag ?: "Terminal", "stack", e) }

    override fun onScale(scale: Float): Float = scale
    override fun onSingleTapUp(e: android.view.MotionEvent?) {
        onSingleTap()
    }
    override fun shouldBackButtonBeMappedToEscape(): Boolean = false
    override fun shouldEnforceCharBasedInput(): Boolean = true
    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
    override fun copyModeChanged(copyMode: Boolean) = Unit
    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?) = false
    override fun onEmulatorSet() = Unit
    override fun isTerminalViewSelected(): Boolean = true
    override fun onLongPress(event: android.view.MotionEvent?): Boolean = false

    override fun readControlKey(): Boolean = false
    override fun readAltKey(): Boolean = false
    override fun readShiftKey(): Boolean = false
    override fun readFnKey(): Boolean = false
    override fun onKeyDown(keyCode: Int, e: android.view.KeyEvent?, session: TerminalSession?): Boolean = false
    override fun onKeyUp(keyCode: Int, e: android.view.KeyEvent?): Boolean = false
}
