package com.ivarna.nativecode.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
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
import com.ivarna.nativecode.core.runtime.ShellSession
import com.ivarna.nativecode.core.runtime.TerminalLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.collectLatest

private val TerminalBg = Color(0xFF0D1117)
private val TerminalFg = Color(0xFFD4D4D4)
private val TerminalPrompt = Color(0xFF6CB6FF)
private val TerminalError = Color(0xFFF48771)
private val TerminalDim = Color(0xFF666666)

private const val MAX_OUTPUT_LINES = 500

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmbeddedRuntimeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val runtime = remember { EmbeddedRuntime(context) }
    val session = remember { ShellSession(runtime) }
    val scope = rememberCoroutineScope()

    var isReady by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Initializing rootfs...") }
    var isRunning by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    val output = remember { mutableStateListOf<TerminalLine>() }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) { runtime.ensureRootfs() }
        if (result.isSuccess) {
            statusMessage = "Starting terminal session..."
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
                        Text("Alpine Linux 3.20", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Embedded Proot Runtime",
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
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = {
                        if (isReady) {
                            val script = "sh /tmp/setup_alpine_embedded.sh"
                            output.add(TerminalLine.Command(script))
                            session.sendInput(script)
                        }
                    },
                    label = { Text("Setup Alpine", fontSize = 12.sp) },
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
                        items(output, key = { it.hashCode() }) { line ->
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
