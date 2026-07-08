package com.ivarna.nativecode.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.nativecode.core.runtime.parseAnsi
import com.ivarna.nativecode.core.runtime.EmbeddedRuntime
import com.ivarna.nativecode.core.runtime.Distro
import com.ivarna.nativecode.core.runtime.ShellSession
import com.ivarna.nativecode.core.runtime.TerminalLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.collectLatest
import java.io.File

private val TerminalBg = Color(0xFF0D1117)
private val TerminalFg = Color(0xFFD4D4D4)
private val TerminalPrompt = Color(0xFF6CB6FF)
private val TerminalError = Color(0xFFF48771)
private val TerminalDim = Color(0xFF666666)

private const val MAX_OUTPUT_LINES = 500

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmbeddedRuntimeScreen(onBack: () -> Unit, distro: Distro = Distro.Alpine) {
    val context = LocalContext.current
    val runtime = remember(distro) { EmbeddedRuntime(context, distro) }
    val session = remember(distro) { ShellSession(runtime) }
    val scope = rememberCoroutineScope()

    var isReady by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Initializing ${distro.displayName}...") }
    var isRunning by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    val output = remember { mutableStateListOf<TerminalLine>() }
    val listState = rememberLazyListState()

    LaunchedEffect(distro) {
        val result = withContext(Dispatchers.IO) { runtime.ensureRootfs() }
        if (result.isSuccess) {
            statusMessage = "Starting ${distro.displayName} session..."
            val started = session.start()
            if (started) {
                isReady = true
                isRunning = true
            } else {
                statusMessage = "✗ Failed to start terminal shell session"
            }
        } else {
            statusMessage = "✗ Rootfs setup failed: ${result.exceptionOrNull()?.message}"
        }
    }

    LaunchedEffect(isReady) {
        if (!isReady) return@LaunchedEffect
        session.lines.collect { line ->
            output.add(line)
            if (output.size > MAX_OUTPUT_LINES) {
                output.subList(0, output.size - MAX_OUTPUT_LINES).clear()
            }
        }
    }

    // Auto-scroll to bottom on new output
    LaunchedEffect(output.size) {
        if (output.isNotEmpty()) listState.scrollToItem(output.size - 1)
    }

    DisposableEffect(Unit) { onDispose { session.kill() } }

    fun submit() {
        if (!isReady) return
        val cmd = inputText.trim()
        if (cmd.isEmpty()) return
        inputText = ""
        if (cmd == "clear") { output.clear(); return }
        output.add(TerminalLine.Command(cmd))
        session.sendInput(cmd)
    }

    Scaffold(
        topBar = {
                TopAppBar(
                title = {
                    Column {
                        Text(distro.displayName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (distro == Distro.Debian) "Proot Runtime · user: flux" else "Embedded Proot Runtime",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF5C6BC0)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = {
                        if (isReady) {
                            // Each distro has its own bootstrap script shipped as an asset.
                            // We tell the user how to run it inside the proot so the screen
                            // stays the same: type the command, the script streams its output.
                            val cmd = when (distro) {
                                Distro.Alpine -> "sh /data/data/com.ivarna.nativecode/files/${Distro.Alpine.setupScriptName}"
                                Distro.Debian  -> "sh /data/data/com.ivarna.nativecode/files/${Distro.Debian.setupScriptName}"
                            }
                            output.add(TerminalLine.Command(cmd))
                            session.sendInput(cmd)
                        }
                    },
                    label = {
                        Text(
                            when (distro) {
                                Distro.Alpine -> "Setup Alpine"
                                Distro.Debian  -> "Setup Debian"
                            },
                            fontSize = 12.sp
                        )
                    },
                    enabled = isReady,
                    leadingIcon = { Icon(Icons.Default.Build, null, Modifier.size(16.dp)) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = Color(0xFF5C6BC0).copy(alpha = 0.12f),
                        labelColor = Color(0xFF5C6BC0),
                        leadingIconContentColor = Color(0xFF5C6BC0),
                    ),
                    border = AssistChipDefaults.assistChipBorder(
                        enabled = true,
                        borderColor = Color(0xFF5C6BC0).copy(alpha = 0.3f)
                    )
                )
                AssistChip(
                    onClick = {
                        if (isReady) {
                            val cmd = "apk update && apk upgrade"
                            output.add(TerminalLine.Command(cmd))
                            session.sendInput(cmd)
                        }
                    },
                    label = { Text("Update Pkgs", fontSize = 12.sp) },
                    enabled = isReady,
                    leadingIcon = { Icon(Icons.Default.Refresh, null, Modifier.size(16.dp)) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = Color(0xFF4CAF50).copy(alpha = 0.08f),
                        labelColor = Color(0xFF4CAF50),
                        leadingIconContentColor = Color(0xFF4CAF50),
                    ),
                    border = AssistChipDefaults.assistChipBorder(
                        enabled = true,
                        borderColor = Color(0xFF4CAF50).copy(alpha = 0.3f)
                    )
                )

                if (distro == Distro.Alpine) {
                    AssistChip(
                        onClick = {
                            if (isReady) {
                                scope.launch(Dispatchers.IO) {
                                    val scriptFile = File(context.filesDir, "setup_alpine_family.sh")
                                    if (!scriptFile.exists()) {
                                        context.assets.open("scripts/common/setup_alpine_family.sh").use { input ->
                                            scriptFile.outputStream().use { output -> input.copyTo(output) }
                                        }
                                        scriptFile.setExecutable(true)
                                    }
                                }
                                val cmd = "sh /data/data/com.ivarna.nativecode/files/setup_alpine_family.sh"
                                output.add(TerminalLine.Command(cmd))
                                session.sendInput(cmd)
                            }
                        },
                        label = { Text("Setup XFCE4", fontSize = 12.sp) },
                        enabled = isReady,
                        leadingIcon = { Icon(Icons.Default.DesktopWindows, null, Modifier.size(16.dp)) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color(0xFFFF7043).copy(alpha = 0.12f),
                            labelColor = Color(0xFFFF7043),
                            leadingIconContentColor = Color(0xFFFF7043),
                        ),
                        border = AssistChipDefaults.assistChipBorder(
                            enabled = true,
                            borderColor = Color(0xFFFF7043).copy(alpha = 0.3f)
                        )
                    )
                    AssistChip(
                        onClick = {
                            if (isReady) {
                                val cmd = "export DISPLAY=:0 PULSE_SERVER=tcp:127.0.0.1 XDG_RUNTIME_DIR=/tmp && xfconf-query -c xfwm4 -p /general/use_compositing -s false 2>/dev/null && dbus-launch --exit-with-session startxfce4"
                                output.add(TerminalLine.Command(cmd))
                                session.sendInput(cmd)
                            }
                        },
                        label = { Text("Start GUI", fontSize = 12.sp) },
                        enabled = isReady,
                        leadingIcon = { Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp)) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color(0xFF4CAF50).copy(alpha = 0.08f),
                            labelColor = Color(0xFF4CAF50),
                            leadingIconContentColor = Color(0xFF4CAF50),
                        ),
                        border = AssistChipDefaults.assistChipBorder(
                            enabled = true,
                            borderColor = Color(0xFF4CAF50).copy(alpha = 0.3f)
                        )
                    )
                }
            }

            if (!isReady) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(12.dp)
                        .background(TerminalBg, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(color = Color(0xFF5C6BC0))
                        Text(statusMessage, color = TerminalFg)
                    }
                }
            } else {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .background(TerminalBg, RoundedCornerShape(12.dp))
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        itemsIndexed(output, key = { i, _ -> i }) { _, line ->
                            TerminalLineRow(line)
                        }
                    }
                }

                HorizontalDivider(color = TerminalDim.copy(alpha = 0.2f))

                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(TerminalBg)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "$",
                        color = TerminalPrompt,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = TerminalFg
                        ),
                        placeholder = {
                            Text(
                                "type a command…",
                                fontFamily = FontFamily.Monospace,
                                color = TerminalDim
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            cursorColor = TerminalPrompt,
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { submit() }),
                        singleLine = true,
                    )
                    IconButton(
                        onClick = { submit() },
                        enabled = isReady && inputText.isNotBlank()
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Run",
                            tint = if (inputText.isNotBlank()) TerminalPrompt else TerminalDim,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TerminalLineRow(line: TerminalLine) {
    val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
    when (line) {
        is TerminalLine.Command -> {
            Spacer(Modifier.height(4.dp))
            Text("$ ${line.text}", style = mono.copy(color = TerminalPrompt))
        }
        is TerminalLine.Output -> Text(parseAnsi(line.text, TerminalFg), style = mono)
        is TerminalLine.Error -> Text(parseAnsi(line.text, TerminalError), style = mono)
    }
}
