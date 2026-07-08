package com.ivarna.nativecode.ui.screens

import android.content.Context
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ivarna.nativecode.core.termux.TermuxBootstrapManager
import com.ivarna.nativecode.core.termux.TermuxBootstrapManager.BootstrapState
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import kotlinx.coroutines.launch

private val TermBg    = Color(0xFF0D1117)
private val TermBar   = Color(0xFF161B22)
private val TermFg    = Color(0xFFD4D4D4)
private val TermGreen = Color(0xFF3FB950)
private val TermBlue  = Color(0xFF58A6FF)
private val TermAccent = Color(0xFF6E40C9)

/**
 * Full-screen terminal screen that runs a REAL Termux bootstrap environment.
 *
 * On first launch: shows download UI (~10 MB Termux bootstrap zip).
 * After bootstrap: opens bash from the extracted prefix — full pkg/apt available.
 * No root required.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermuxTerminalScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ── State machine ──────────────────────────────────────────────────────────
    var bootstrapReady by remember { mutableStateOf(TermuxBootstrapManager.isInstalled(context)) }
    var bootstrapState by remember { mutableStateOf<BootstrapState>(BootstrapState.NotInstalled) }

    val termViewRef = remember { mutableStateOf<TerminalView?>(null) }
    val sessionRef  = remember { mutableStateOf<TerminalSession?>(null) }
    var ctrlDown    by remember { mutableStateOf(false) }

    // Session client
    val sessionClient = remember {
        object : TerminalSessionClient {
            override fun onTextChanged(s: TerminalSession)   { termViewRef.value?.onScreenUpdated() }
            override fun onTitleChanged(s: TerminalSession)  {}
            override fun onSessionFinished(s: TerminalSession) { sessionRef.value = null }
            override fun onCopyTextToClipboard(s: TerminalSession, text: String) {}
            override fun onPasteTextFromClipboard(s: TerminalSession) {}
            override fun onBell(s: TerminalSession) {}
            override fun onColorsChanged(s: TerminalSession) {}
            override fun onTerminalCursorStateChange(state: Boolean) {}
            override fun getTerminalCursorStyle(): Int? = null
            override fun logError(tag: String, message: String)   { android.util.Log.e(tag, message) }
            override fun logWarn(tag: String, message: String)    { android.util.Log.w(tag, message) }
            override fun logInfo(tag: String, message: String)    { android.util.Log.i(tag, message) }
            override fun logDebug(tag: String, message: String)   { android.util.Log.d(tag, message) }
            override fun logVerbose(tag: String, message: String) { android.util.Log.v(tag, message) }
            override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {
                android.util.Log.e(tag, message, e)
            }
            override fun logStackTrace(tag: String, e: Exception) {
                android.util.Log.e(tag, "stacktrace", e)
            }
        }
    }

    // Create Termux session once bootstrap is ready
    LaunchedEffect(bootstrapReady) {
        if (!bootstrapReady) return@LaunchedEffect
        try {
            TermuxBootstrapManager.ensureProotDeps(context)
            val launcher = TermuxBootstrapManager.launcherPath()   // /system/bin/linker64
            val args  = TermuxBootstrapManager.buildProotArgs(context)
            val cwd   = context.filesDir.absolutePath   // real path; proot handles chdir internally
            val env   = TermuxBootstrapManager.buildEnvironment(context)
            android.util.Log.d("TermuxTerminalScreen",
                "Launching: $launcher args=${args.take(3).joinToString()}")
            val s = TerminalSession(launcher, cwd, args, env, 2000, sessionClient)
            s.initializeEmulator(80, 24, 0, 0)
            sessionRef.value = s
        } catch (e: Exception) {
            android.util.Log.e("TermuxTerminalScreen", "Session init failed", e)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            sessionRef.value?.finishIfRunning()
            sessionRef.value = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TermBg)
            .systemBarsPadding()
    ) {
        // ── Top Bar ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TermBar)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                sessionRef.value?.finishIfRunning()
                onBack()
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TermFg)
            }
            Text(
                "Termux Terminal",
                color = TermFg,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (bootstrapReady) "bash" else "setup",
                color = if (bootstrapReady) TermGreen else TermBlue,
                fontSize = 12.sp,
                modifier = Modifier.padding(end = 8.dp)
            )
        }

        // ── Content ─────────────────────────────────────────────────────────────
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (!bootstrapReady) {
                // Show setup / download screen
                BootstrapSetupScreen(
                    state = bootstrapState,
                    onInstall = {
                        android.util.Log.e("TermuxBootstrap", "INSTALL CLICKED")
                        try {
                            scope.launch {
                                android.util.Log.e("TermuxBootstrap", "Coroutine launched")
                                TermuxBootstrapManager.install(context) { state ->
                                    android.util.Log.e("TermuxBootstrap", "State: $state")
                                    bootstrapState = state
                                    if (state is BootstrapState.Done) {
                                        bootstrapReady = true
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("TermuxBootstrap", "Exception: ${e.message}", e)
                        }
                    }
                )
            } else {
                // Terminal view
                val session = sessionRef.value
                AndroidView(
                    factory = { ctx ->
                        TerminalView(ctx, null).apply {
                            setTextSize(38)
                            setTerminalViewClient(object : TerminalViewClient {
                                override fun onScale(scale: Float): Float = 1.0f
                                override fun onSingleTapUp(e: android.view.MotionEvent) {
                                    requestFocus()
                                    val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE)
                                        as InputMethodManager
                                    imm.showSoftInput(this@apply, InputMethodManager.SHOW_FORCED)
                                }
                                override fun shouldEnforceCharBasedInput(): Boolean = true
                                override fun shouldBackButtonBeMappedToEscape(): Boolean = false
                                override fun shouldUseCtrlSpaceWorkaround(): Boolean = true
                                override fun isTerminalViewSelected(): Boolean = true
                                override fun copyModeChanged(copyMode: Boolean) {}
                                override fun onKeyDown(keyCode: Int, e: KeyEvent, s: TerminalSession): Boolean = false
                                override fun onKeyUp(code: Int, e: KeyEvent): Boolean = false
                                override fun onLongPress(e: android.view.MotionEvent): Boolean = false
                                override fun readControlKey(): Boolean = ctrlDown
                                override fun readAltKey(): Boolean = false
                                override fun readShiftKey(): Boolean = false
                                override fun readFnKey(): Boolean = false
                                override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, s: TerminalSession): Boolean = false
                                override fun onEmulatorSet() {}
                                override fun logError(t: String, m: String)   { android.util.Log.e(t, m) }
                                override fun logWarn(t: String, m: String)    { android.util.Log.w(t, m) }
                                override fun logInfo(t: String, m: String)    { android.util.Log.i(t, m) }
                                override fun logDebug(t: String, m: String)   { android.util.Log.d(t, m) }
                                override fun logVerbose(t: String, m: String) { android.util.Log.v(t, m) }
                                override fun logStackTraceWithMessage(t: String, m: String, e: Exception) {
                                    android.util.Log.e(t, m, e)
                                }
                                override fun logStackTrace(t: String, e: Exception) {
                                    android.util.Log.e(t, "stacktrace", e)
                                }
                            })
                            isFocusable = true
                            isFocusableInTouchMode = true
                            termViewRef.value = this
                        }
                    },
                    update = { view ->
                        session?.let { s ->
                            if (view.currentSession == null) {
                                view.attachSession(s)
                                view.updateSize()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Only show extra keys when terminal is active
        if (bootstrapReady) {
            ExtraKeysRow(
                ctrlDown = ctrlDown,
                onCtrlToggle = { ctrlDown = !ctrlDown },
                onKey = { sequence -> sessionRef.value?.write(sequence) }
            )
        }
    }
}

// ── Bootstrap setup screen ─────────────────────────────────────────────────────

@Composable
private fun BootstrapSetupScreen(
    state: BootstrapState,
    onInstall: () -> Unit
) {
    val isDownloading = state is BootstrapState.Downloading
    val isExtracting  = state is BootstrapState.Extracting
    val isError       = state is BootstrapState.Error
    val isBusy        = isDownloading || isExtracting

    // Pulsing animation for icon
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(TermAccent.copy(alpha = if (isBusy) pulseAlpha else 0.3f), Color.Transparent)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isBusy) Icons.Default.Download else Icons.Default.Terminal,
                    contentDescription = null,
                    tint = TermAccent,
                    modifier = Modifier.size(48.dp)
                )
            }

            Text(
                "Termux Environment",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TermFg
            )

            Text(
                "Download the real Termux package environment (~10 MB).\n" +
                "Includes bash, pkg, apt, and hundreds of packages.",
                fontSize = 14.sp,
                color = TermFg.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            // Status row
            when (state) {
                is BootstrapState.Downloading -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LinearProgressIndicator(
                            progress = { state.percent / 100f },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = TermBlue
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Downloading… ${state.percent}%", fontSize = 12.sp, color = TermBlue)
                    }
                }
                is BootstrapState.Extracting -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = TermGreen
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Extracting… ${state.count} files", fontSize = 12.sp, color = TermGreen)
                    }
                }
                is BootstrapState.Error -> {
                    Text(
                        "Error: ${state.message}",
                        fontSize = 12.sp,
                        color = Color(0xFFFF5555),
                        textAlign = TextAlign.Center
                    )
                }
                else -> {}
            }

            // Install button (only when not busy)
            if (!isBusy) {
                Button(
                    onClick = onInstall,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TermAccent),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isError) "Retry Download" else "Install Termux (~10 MB)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }

            // Feature chips
            if (!isBusy) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("bash", "pkg/apt", "No Root", "1000+ pkgs").forEach { label ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(TermAccent.copy(alpha = 0.1f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(label, fontSize = 11.sp, color = TermAccent, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

// ── Extra keys ─────────────────────────────────────────────────────────────────

@Composable
private fun ExtraKeysRow(
    ctrlDown: Boolean,
    onCtrlToggle: () -> Unit,
    onKey: (String) -> Unit,
) {
    val keys = listOf(
        Triple("ESC",  "\u001b",   false),
        Triple("TAB",  "\t",       false),
        Triple("↑",    "\u001b[A", false),
        Triple("↓",    "\u001b[B", false),
        Triple("←",    "\u001b[D", false),
        Triple("→",    "\u001b[C", false),
        Triple("HOME", "\u001b[H", false),
        Triple("END",  "\u001b[F", false),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF161B22))
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Button(
            onClick = onCtrlToggle,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (ctrlDown) TermBlue else Color(0xFF21262D)
            ),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.height(34.dp)
        ) {
            Text("CTRL", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
        }

        keys.forEach { (label, seq, _) ->
            Button(
                onClick = { onKey(seq) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF21262D)),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text(label, fontSize = 11.sp, color = TermFg, fontWeight = FontWeight.Medium)
            }
        }
    }
}
