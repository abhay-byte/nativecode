package com.ivarna.nativecode.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.nativecode.core.codex.CodexAuthManager
import com.ivarna.nativecode.core.codex.CodexMessage
import com.ivarna.nativecode.core.codex.CodexRole
import com.ivarna.nativecode.core.codex.CodexSessionManager
import com.ivarna.nativecode.ui.theme.FluxAccentCyan
import com.ivarna.nativecode.ui.theme.FluxAccentMagenta
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodexScreen(
    projectPath: String,
    distroId: String,
    onBack: () -> Unit,
    hazeState: HazeState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val sessionManager = remember { CodexSessionManager(context, distroId, projectPath) }
    val authManager = remember { CodexAuthManager(context) }

    var messages by remember { mutableStateOf(listOf<CodexMessage>()) }
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var showStartBanner by remember { mutableStateOf(true) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    val isLoggedIn = remember { mutableStateOf(authManager.isLoggedIn()) }

    // OAuth flow state
    var oauthState by remember { mutableStateOf(OAuthState.IDLE) }
    var pendingAuthUrl by remember { mutableStateOf<String?>(null) }

    val folderName = projectPath.substringAfterLast("/").takeIf { it.isNotEmpty() } ?: "Project"

    LaunchedEffect(messages.size, isLoading) {
        if (messages.isNotEmpty() || isLoading) {
            val lastIndex = (messages.size + if (isLoading) 1 else 0) - 1
            if (lastIndex >= 0) {
                listState.animateScrollToItem(lastIndex)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
    ) {
        // ── Top Bar ─────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folderName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Codex Agent • $distroId",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (isLoading) Color(0xFFFFA726) else Color(0xFF66BB6A)
                    )
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(bottom = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        )

        // ── Chat Area ───────────────────────────────────────────────
        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                if (messages.isEmpty() && showStartBanner) {
                    item {
                        CodexWelcomeCard(
                            projectName = folderName,
                            isLoggedIn = isLoggedIn.value,
                            oauthState = oauthState,
                            onOAuthSignIn = {
                                oauthState = OAuthState.LOADING
                                scope.launch {
                                    val result = sessionManager.triggerOAuth()
                                    result.fold(
                                        onSuccess = { url ->
                                            pendingAuthUrl = url
                                            oauthState = OAuthState.URL_CAPTURED
                                        },
                                        onFailure = { error ->
                                            oauthState = OAuthState.IDLE
                                            errorText = error.message
                                        }
                                    )
                                }
                            },
                            onSetApiKey = { showApiKeyDialog = true },
                            onDismiss = { showStartBanner = false }
                        )
                    }
                }

                items(messages, key = { it.id }) { msg ->
                    CodexMessageBubble(message = msg)
                }

                if (isLoading) {
                    item { CodexTypingIndicator() }
                }

                if (errorText != null) {
                    item {
                        ErrorBanner(
                            message = errorText!!,
                            onDismiss = { errorText = null }
                        )
                    }
                }
            }
        }

        // ── Input Bar ───────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    RoundedCornerShape(24.dp)
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = {
                    Text(
                        "Ask Codex to code, debug, refactor...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.weight(1f),
                enabled = !isLoading,
                textStyle = LocalTextStyle.current.copy(
                    color = MaterialTheme.colorScheme.onSurface
                )
            )

            IconButton(
                onClick = {
                    if (inputText.isBlank()) return@IconButton
                    if (!authManager.isLoggedIn()) {
                        showApiKeyDialog = true
                        return@IconButton
                    }
                    val prompt = inputText.trim()
                    inputText = ""
                    errorText = null

                    messages = messages + CodexMessage(role = CodexRole.USER, content = prompt)
                    isLoading = true

                    scope.launch {
                        val apiKey = authManager.getApiKey()
                        val result = sessionManager.sendPrompt(prompt, apiKey)
                        isLoading = false
                        result.fold(
                            onSuccess = { response ->
                                // Check if response contains an auth URL
                                val urlRegex = Regex("""https://auth\.openai\.com/oauth/authorize\?[^\s]+""")
                                val authUrl = urlRegex.find(response)?.value
                                if (authUrl != null) {
                                    pendingAuthUrl = authUrl
                                    messages = messages + CodexMessage(
                                        role = CodexRole.SYSTEM,
                                        content = "Codex requires authentication. Tap the button below to open the sign-in page in your browser."
                                    )
                                } else {
                                    messages = messages + CodexMessage(
                                        role = CodexRole.ASSISTANT,
                                        content = response
                                    )
                                }
                            },
                            onFailure = { error ->
                                errorText = error.message ?: "Unknown error"
                                messages = messages + CodexMessage(
                                    role = CodexRole.SYSTEM,
                                    content = "Error: ${error.message ?: "Unknown error"}"
                                )
                            }
                        )
                    }
                },
                enabled = !isLoading && inputText.isNotBlank()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (inputText.isNotBlank() && !isLoading)
                        FluxAccentCyan else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
        }

        // Auth URL launcher
        if (pendingAuthUrl != null) {
            AuthUrlBanner(
                url = pendingAuthUrl!!,
                onOpen = {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(pendingAuthUrl))
                    context.startActivity(intent)
                },
                onDismiss = { pendingAuthUrl = null }
            )
        }

        // API Key Dialog
        if (showApiKeyDialog) {
            ApiKeyDialog(
                initialValue = authManager.getApiKey() ?: "",
                onDismiss = { showApiKeyDialog = false },
                onSave = { key ->
                    authManager.saveApiKey(key)
                    isLoggedIn.value = true
                    showApiKeyDialog = false
                    android.widget.Toast.makeText(context, "API key saved securely", android.widget.Toast.LENGTH_SHORT).show()
                },
                onClear = {
                    authManager.clearApiKey()
                    isLoggedIn.value = false
                    showApiKeyDialog = false
                    android.widget.Toast.makeText(context, "API key removed", android.widget.Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Auth URL Banner
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AuthUrlBanner(url: String, onOpen: () -> Unit, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF10A37F).copy(alpha = 0.12f))
            .border(1.dp, Color(0xFF10A37F).copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = "Authentication Required",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onOpen,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF10A37F),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open Sign-In Page in Browser", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("Dismiss", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Welcome Card
// ─────────────────────────────────────────────────────────────────────────────
enum class OAuthState {
    IDLE,
    LOADING,
    URL_CAPTURED
}

@Composable
fun CodexWelcomeCard(
    projectName: String,
    isLoggedIn: Boolean,
    oauthState: OAuthState,
    onOAuthSignIn: () -> Unit,
    onSetApiKey: () -> Unit,
    onDismiss: () -> Unit
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        FluxAccentCyan.copy(alpha = 0.10f),
                        FluxAccentMagenta.copy(alpha = 0.05f)
                    )
                )
            )
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                RoundedCornerShape(20.dp)
            )
            .padding(20.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF10A37F).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = Color(0xFF10A37F),
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Codex is ready",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "OpenAI's terminal-native AI coding agent is running inside your Linux workspace for $projectName.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (!isLoggedIn) {
                when (oauthState) {
                    OAuthState.IDLE -> {
                        Button(
                            onClick = onOAuthSignIn,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF10A37F),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Sign in with OpenAI", fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        TextButton(
                            onClick = onSetApiKey,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Use API Key Instead", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    OAuthState.LOADING -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color(0xFF10A37F),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Starting Codex auth server...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp
                            )
                        }
                    }
                    OAuthState.URL_CAPTURED -> {
                        Text(
                            "Auth URL captured! Check below to open browser.",
                            color = Color(0xFF81C784),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Text(
                    text = "✓ Authenticated",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF81C784),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Message Bubble
// ─────────────────────────────────────────────────────────────────────────────
// ─────────────────────────────────────────────────────────────────────────────
// Message Content Parsing — splits text and markdown code blocks
// ─────────────────────────────────────────────────────────────────────────────
private sealed class MessageSegment {
    data class Text(val content: String) : MessageSegment()
    data class CodeBlock(val language: String, val code: String) : MessageSegment()
}

private fun parseMessageSegments(raw: String): List<MessageSegment> {
    val segments = mutableListOf<MessageSegment>()
    val regex = Regex("```(\\w*)\\n?(.*?)\\n?```", RegexOption.DOT_MATCHES_ALL)
    var lastIndex = 0

    for (match in regex.findAll(raw)) {
        val start = match.range.first
        if (start > lastIndex) {
            val text = raw.substring(lastIndex, start).trim()
            if (text.isNotBlank()) segments.add(MessageSegment.Text(text))
        }
        val lang = match.groupValues[1].trim().lowercase()
        val code = match.groupValues[2].trimEnd()
        segments.add(MessageSegment.CodeBlock(lang, code))
        lastIndex = match.range.last + 1
    }

    if (lastIndex < raw.length) {
        val text = raw.substring(lastIndex).trim()
        if (text.isNotBlank()) segments.add(MessageSegment.Text(text))
    }

    if (segments.isEmpty() && raw.isNotBlank()) {
        segments.add(MessageSegment.Text(raw))
    }

    return segments
}

@Composable
fun CodexMessageBubble(message: CodexMessage) {
    val isUser = message.role == CodexRole.USER
    val context = LocalContext.current
    val segments = remember(message.content) { parseMessageSegments(message.content) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
        ) {
            if (!isUser) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF10A37F).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = Color(0xFF10A37F),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            val bgModifier = if (isUser) {
                Modifier.background(
                    Brush.linearGradient(
                        colors = listOf(
                            FluxAccentCyan.copy(alpha = 0.20f),
                            FluxAccentCyan.copy(alpha = 0.08f)
                        )
                    )
                )
            } else if (message.role == CodexRole.SYSTEM) {
                Modifier.background(Color(0xFFFF5252).copy(alpha = 0.10f))
            } else {
                Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            }
            val borderColor = if (isUser) {
                FluxAccentCyan.copy(alpha = 0.25f)
            } else if (message.role == CodexRole.SYSTEM) {
                Color(0xFFFF5252).copy(alpha = 0.20f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            }

            Box(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .then(bgModifier)
                    .border(1.dp, borderColor, RoundedCornerShape(18.dp))
                    .padding(14.dp)
            ) {
                CodexMessageContent(
                    segments = segments,
                    isSystem = message.role == CodexRole.SYSTEM
                )
            }

            if (isUser) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(FluxAccentCyan.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = FluxAccentCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (!isUser && message.role != CodexRole.SYSTEM) {
            Row(
                modifier = Modifier.padding(start = 40.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        val clip = android.content.ClipData.newPlainText("Codex Response", message.content)
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                        clipboard.setPrimaryClip(clip)
                        android.widget.Toast.makeText(context, "Copied full response", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CodexMessageContent(segments: List<MessageSegment>, isSystem: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        segments.forEach { segment ->
            when (segment) {
                is MessageSegment.Text -> {
                    SelectionContainer {
                        Text(
                            text = segment.content,
                            color = if (isSystem) Color(0xFFFF8A80) else MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    }
                }
                is MessageSegment.CodeBlock -> {
                    CodeBlockView(language = segment.language, code = segment.code)
                }
            }
        }
    }
}

@Composable
private fun CodeBlockView(language: String, code: String) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E1E2E))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF252537))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = language.ifBlank { "code" },
                color = Color(0xFF81C784),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        val clip = android.content.ClipData.newPlainText("Code", code)
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                        clipboard.setPrimaryClip(clip)
                        android.widget.Toast.makeText(context, "Code copied", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy code",
                        tint = Color(0xFF81C784).copy(alpha = 0.8f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // Code body
        Box(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .padding(12.dp)
        ) {
            Text(
                text = code,
                color = Color(0xFFA5D6A7),
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontFamily = FontFamily.Monospace,
                softWrap = false
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Typing Indicator
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun CodexTypingIndicator() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.SmartToy,
            contentDescription = null,
            tint = Color(0xFF10A37F),
            modifier = Modifier.size(18.dp)
        )

        val dots = listOf(0, 1, 2)
        dots.forEach { index ->
            val alphaAnim = remember { Animatable(0.3f) }
            LaunchedEffect(Unit) {
                delay(index * 150L)
                while (true) {
                    alphaAnim.animateTo(1f, tween(600))
                    alphaAnim.animateTo(0.3f, tween(600))
                }
            }
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alphaAnim.value))
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Error Banner
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFFF5252).copy(alpha = 0.12f))
            .border(1.dp, Color(0xFFFF5252).copy(alpha = 0.20f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = Color(0xFFFF8A80),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = message,
                color = Color(0xFFFF8A80),
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Text("✕", color = Color(0xFFFF8A80), fontSize = 14.sp)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// API Key Dialog
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ApiKeyDialog(
    initialValue: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onClear: () -> Unit
) {
    var keyText by remember { mutableStateOf(initialValue) }
    var isVisible by remember { mutableStateOf(false) }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    RoundedCornerShape(24.dp)
                )
                .padding(24.dp)
        ) {
            Column {
                Text(
                    text = "OpenAI API Key",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Your key is encrypted and stored only on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(20.dp))

                TextField(
                    value = keyText,
                    onValueChange = { keyText = it },
                    label = { Text("API Key") },
                    placeholder = { Text("sk-...") },
                    singleLine = true,
                    visualTransformation = if (isVisible)
                        androidx.compose.ui.text.input.VisualTransformation.None
                    else
                        androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isVisible = !isVisible }) {
                            Text(
                                if (isVisible) "🙈" else "👁",
                                fontSize = 18.sp
                            )
                        }
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        focusedIndicatorColor = FluxAccentCyan,
                        unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (initialValue.isNotBlank()) {
                        TextButton(
                            onClick = onClear,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Remove", color = Color(0xFFFF8A80))
                        }
                    }

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Button(
                        onClick = { onSave(keyText.trim()) },
                        enabled = keyText.trim().startsWith("sk-") && keyText.trim().length > 20,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF10A37F),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.5f)
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
