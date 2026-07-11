package com.ivarna.nativecode.ui.screens

import android.content.Context
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ivarna.nativecode.core.termux.TerminalSessionHub
import com.ivarna.nativecode.core.termux.TermuxBootstrapManager
import com.ivarna.nativecode.core.termux.TermuxBootstrapManager.BootstrapState
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import kotlinx.coroutines.launch

private val TermBg = Color(0xFF0D1117)
private val TermBar = Color(0xFF161B22)
private val TermSide = Color(0xFF0D1117)
private val TermFg = Color(0xFFD4D4D4)
private val TermMuted = Color(0xFF8B949E)
private val TermGreen = Color(0xFF3FB950)
private val TermBlue = Color(0xFF58A6FF)
private val TermAccent = Color(0xFF6E40C9)
private val TermActive = Color(0xFF21262D)
private val TermDanger = Color(0xFFFF7B72)

/**
 * Multi-session Termux terminal host.
 *
 * Sidebar lists all live sessions (Termux shell, Debian proot, XFCE start_gui, …).
 * Sessions live in [TerminalSessionHub] so leaving the screen does not kill them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermuxTerminalScreen(
    onBack: () -> Unit,
    initialCommand: String? = null,
    installTitle: String = "Installing…",
    totalSteps: Int = 1,
    onInstallComplete: (() -> Unit)? = null,
    /** Increment from host each time user navigates to open a new tab. */
    openEpoch: Long = 0L,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    var bootstrapReady by remember { mutableStateOf(TermuxBootstrapManager.isInstalled(context)) }
    var bootstrapState by remember { mutableStateOf<BootstrapState>(BootstrapState.NotInstalled) }

    val tabs by TerminalSessionHub.tabs.collectAsState()
    val activeId by TerminalSessionHub.activeId.collectAsState()
    val activeTab = tabs.find { it.id == activeId } ?: tabs.lastOrNull()

    val termViewRef = remember { mutableStateOf<TerminalView?>(null) }
    var ctrlDown by remember { mutableStateOf(false) }

    var currentStep by remember { mutableStateOf(0) }
    var currentStepName by remember { mutableStateOf("") }
    var installDone by remember { mutableStateOf(false) }
    var x11Opened by remember { mutableStateOf(false) }
    val isInstalling = initialCommand != null && totalSteps > 0
    val isGuiLaunch = initialCommand?.contains("start_gui") == true

    // Track which openEpoch we already created a tab for
    var lastOpenEpoch by remember { mutableStateOf(-1L) }

    val sessionClient = remember {
        object : TerminalSessionClient {
            override fun onTextChanged(s: TerminalSession) {
                termViewRef.value?.onScreenUpdated()
                // Cheap path: only scan when install/GUI markers matter — never rebuild
                // full multi-thousand-line transcript on every keystroke (was a major lag source).
                if (!isInstalling && !isGuiLaunch) return
                if (installDone && (!isGuiLaunch || x11Opened)) return
                try {
                    val screen = s.emulator?.screen ?: return
                    // Prefer compact API; only keep a short tail for marker scans
                    val full = screen.transcriptTextWithoutJoinedLines ?: return
                    val text = if (full.length > 2500) full.takeLast(2500) else full
                    if (text.isEmpty()) return

                    if (isInstalling) {
                        val stepRegex = Regex("""\[STEP (\d+)\] (.+)""")
                        stepRegex.findAll(text).lastOrNull()?.let { m ->
                            currentStep = m.groupValues[1].toIntOrNull() ?: return@let
                            currentStepName = m.groupValues[2].trim()
                        }
                        if (!installDone && text.contains("✅ Installation complete!")) {
                            installDone = true
                            onInstallComplete?.invoke()
                        }
                    }

                    if (isGuiLaunch && !x11Opened && text.contains("X11 ready")) {
                        x11Opened = true
                        android.util.Log.i("TermuxTerminalScreen", "X11 ready — opening embedded display")
                        // Always open from Activity on main looper (same task as host)
                        val act = context as? android.app.Activity ?: return
                        act.runOnUiThread {
                            com.ivarna.nativecode.core.termux.GuiSessionLauncher.openX11Activity(act)
                        }
                    }
                } catch (_: Exception) {}
            }

            override fun onTitleChanged(s: TerminalSession) {}
            override fun onSessionFinished(s: TerminalSession) {
                TerminalSessionHub.markFinished(s)
            }
            override fun onCopyTextToClipboard(s: TerminalSession, text: String) {}
            override fun onPasteTextFromClipboard(s: TerminalSession) {}
            override fun onBell(s: TerminalSession) {}
            override fun onColorsChanged(s: TerminalSession) {}
            override fun onTerminalCursorStateChange(state: Boolean) {}
            override fun getTerminalCursorStyle(): Int? = null
            override fun logError(tag: String, message: String) { android.util.Log.e(tag, message) }
            override fun logWarn(tag: String, message: String) { android.util.Log.w(tag, message) }
            override fun logInfo(tag: String, message: String) { android.util.Log.i(tag, message) }
            override fun logDebug(tag: String, message: String) { android.util.Log.d(tag, message) }
            override fun logVerbose(tag: String, message: String) { android.util.Log.v(tag, message) }
            override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {
                android.util.Log.e(tag, message, e)
            }
            override fun logStackTrace(tag: String, e: Exception) {
                android.util.Log.e(tag, "stacktrace", e)
            }
        }
    }

    fun openNewShell(command: String? = null, title: String? = null) {
        if (!bootstrapReady) {
            android.widget.Toast.makeText(context, "Bootstrap not ready", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        TerminalSessionHub.attachClient(sessionClient)
        val tab = TerminalSessionHub.createBootstrapSession(
            context = context,
            sessionClient = sessionClient,
            title = title,
            command = command,
        )
        if (tab == null) {
            android.widget.Toast.makeText(
                context,
                "Failed to open terminal (see logcat: TerminalSessionHub)",
                android.widget.Toast.LENGTH_LONG,
            ).show()
        }
    }

    // Open a new hub tab when host navigates here (openEpoch bumps each time)
    LaunchedEffect(bootstrapReady, openEpoch) {
        if (!bootstrapReady) return@LaunchedEffect
        if (openEpoch == lastOpenEpoch && tabs.isNotEmpty()) return@LaunchedEffect
        lastOpenEpoch = openEpoch
        TerminalSessionHub.attachClient(sessionClient)
        openNewShell(
            command = initialCommand,
            title = if (isInstalling && totalSteps > 0) installTitle
            else TerminalSessionHub.titleForCommand(initialCommand),
        )
    }

    // Re-bind client + attach active session to view
    LaunchedEffect(activeId, tabs.size) {
        TerminalSessionHub.attachClient(sessionClient)
        val session = activeTab?.session
        val view = termViewRef.value
        if (session != null && view != null) {
            view.attachSession(session)
            view.updateSize()
            view.onScreenUpdated()
        }
    }

    // Do NOT kill hub sessions on dispose — only detach client
    DisposableEffect(Unit) {
        onDispose {
            TerminalSessionHub.attachClient(null)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = TermSide,
                modifier = Modifier.width(280.dp)
            ) {
                Column(
                    Modifier
                        .fillMaxHeight()
                        .padding(top = 12.dp)
                ) {
                    Text(
                        "Terminals",
                        color = TermFg,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    Text(
                        "${tabs.size} running",
                        color = TermMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Divider(color = Color(0xFF30363D))

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        if (tabs.isEmpty()) {
                            item {
                                Text(
                                    "No open terminals",
                                    color = TermMuted,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                        items(tabs, key = { it.id }) { tab ->
                            val selected = tab.id == activeTab?.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (selected) TermActive else Color.Transparent)
                                    .clickable {
                                        TerminalSessionHub.setActive(tab.id)
                                        scope.launch { drawerState.close() }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Terminal,
                                    contentDescription = null,
                                    tint = if (selected) TermAccent else TermMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                androidx.compose.foundation.layout.Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        tab.title,
                                        color = TermFg,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        tab.subtitle,
                                        color = TermMuted,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        TerminalSessionHub.close(tab.id)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = TermDanger,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    Divider(color = Color(0xFF30363D))
                    TextButton(
                        onClick = {
                            openNewShell()
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = TermAccent)
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("New terminal", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(TermBg)
                .systemBarsPadding()
        ) {
            // ── Top Bar (always visible, including when no sessions) ───────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TermBar)
                    .heightIn(min = 52.dp)
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Always show Back — leave hub sessions alive
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TermFg,
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(
                    onClick = {
                        scope.launch {
                            if (drawerState.isClosed) drawerState.open() else drawerState.close()
                        }
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Menu, "Sessions", tint = TermFg)
                }
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                ) {
                    Text(
                        activeTab?.title
                            ?: if (isInstalling) installTitle else "Termux Terminal",
                        color = TermFg,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (tabs.isEmpty()) "No open sessions"
                        else "${tabs.size} session${if (tabs.size == 1) "" else "s"}",
                        color = TermMuted,
                        fontSize = 11.sp
                    )
                }
                // New terminal
                IconButton(
                    onClick = { openNewShell() },
                    enabled = bootstrapReady,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Add, "New terminal", tint = if (bootstrapReady) TermGreen else TermMuted)
                }
                Text(
                    if (bootstrapReady) "bash" else "setup",
                    color = if (bootstrapReady) TermGreen else TermBlue,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            // ── Install Progress ───────────────────────────────────────────
            if (isInstalling && bootstrapReady && totalSteps > 0) {
                val progress = if (currentStep > 0)
                    (currentStep.toFloat() / totalSteps.toFloat()).coerceIn(0f, 1f)
                else 0f
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0D1F12))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            if (currentStepName.isNotBlank()) currentStepName else "Preparing…",
                            fontSize = 12.sp,
                            color = TermGreen,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            if (currentStep > 0) "$currentStep / $totalSteps" else "0 / $totalSteps",
                            fontSize = 11.sp,
                            color = TermFg.copy(alpha = 0.6f)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = TermGreen,
                        trackColor = TermGreen.copy(alpha = 0.15f)
                    )
                }
            }

            // ── Content ────────────────────────────────────────────────────
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (!bootstrapReady) {
                    BootstrapSetupScreen(
                        state = bootstrapState,
                        onInstall = {
                            scope.launch {
                                TermuxBootstrapManager.install(context) { state ->
                                    bootstrapState = state
                                    if (state is BootstrapState.Done) {
                                        bootstrapReady = true
                                    }
                                }
                            }
                        }
                    )
                } else {
                    val session = activeTab?.session
                    if (session == null) {
                        // Empty state — explicit Back so user is never stuck
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(TermBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Terminal,
                                    contentDescription = null,
                                    tint = TermMuted,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text("No terminal open", color = TermFg, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Start a shell or go back home.",
                                    color = TermMuted,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(4.dp))
                                Button(
                                    onClick = { openNewShell() },
                                    colors = ButtonDefaults.buttonColors(containerColor = TermAccent),
                                    modifier = Modifier.fillMaxWidth(0.75f).height(48.dp)
                                ) {
                                    Icon(Icons.Default.Add, null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("New terminal")
                                }
                                OutlinedButton(
                                    onClick = onBack,
                                    modifier = Modifier.fillMaxWidth(0.75f).height(48.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TermFg),
                                    border = ButtonDefaults.outlinedButtonBorder(enabled = true)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Back to Home")
                                }
                            }
                        }
                    } else {
                        key(activeTab?.id ?: "none") {
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
                                        isFocusable = true
                                        isFocusableInTouchMode = true
                                        termViewRef.value = this
                                        attachSession(session)
                                        updateSize()
                                    }
                                },
                                update = { view ->
                                    termViewRef.value = view
                                    if (view.currentSession !== session) {
                                        view.attachSession(session)
                                        view.updateSize()
                                        view.onScreenUpdated()
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            if (bootstrapReady && activeTab?.session != null) {
                ExtraKeysRow(
                    ctrlDown = ctrlDown,
                    onCtrlToggle = { ctrlDown = !ctrlDown },
                    onKey = { sequence -> activeTab?.session?.write(sequence) }
                )
            }
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
    val isExtracting = state is BootstrapState.Extracting
    val isError = state is BootstrapState.Error
    val isBusy = isDownloading || isExtracting

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
        Triple("ESC", "\u001b", false),
        Triple("TAB", "\t", false),
        Triple("↑", "\u001b[A", false),
        Triple("↓", "\u001b[B", false),
        Triple("←", "\u001b[D", false),
        Triple("→", "\u001b[C", false),
        Triple("HOME", "\u001b[H", false),
        Triple("END", "\u001b[F", false),
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
