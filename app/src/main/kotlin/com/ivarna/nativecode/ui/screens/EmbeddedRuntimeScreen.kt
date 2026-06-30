package com.ivarna.nativecode.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.ivarna.nativecode.core.runtime.EmbeddedRuntime
import com.ivarna.nativecode.core.runtime.ShellSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmbeddedRuntimeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val runtime = remember { EmbeddedRuntime(context) }
    val session = remember { ShellSession(runtime) }

    var isReady by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Initializing rootfs...") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isBusy by remember { mutableStateOf(false) }

    // Start shell session when rootfs is ready
    LaunchedEffect(Unit) {
        lifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val result = runtime.ensureRootfs()
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    statusMessage = "Starting terminal session..."
                    val started = session.start()
                    if (started) {
                        isReady = true
                    } else {
                        statusMessage = "✗ Failed to start terminal shell session"
                    }
                } else {
                    statusMessage = "✗ Rootfs setup failed: ${result.exceptionOrNull()?.message}"
                }
            }
        }
    }

    // Stream shell output to WebView
    LaunchedEffect(isReady) {
        if (isReady) {
            lifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                session.output.collect { chunk ->
                    webViewRef?.let { webView ->
                        val base64 = android.util.Base64.encodeToString(
                            chunk.toByteArray(Charsets.UTF_8),
                            android.util.Base64.NO_WRAP
                        )
                        withContext(Dispatchers.Main) {
                            webView.evaluateJavascript("writeBase64('$base64')", null)
                        }
                    }
                }
            }
        }
    }

    // Clean up process on dispose
    DisposableEffect(Unit) {
        onDispose {
            session.kill()
        }
    }

    fun runSetupScript() {
        if (!isReady) return
        isBusy = true
        lifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Copy script to files/tmp directory
                val scriptAssetPath = "scripts/embedded/setup_alpine_embedded.sh"
                val scriptFileName = scriptAssetPath.substringAfterLast('/')
                val tmpDir = java.io.File(context.filesDir, "tmp")
                tmpDir.mkdirs()
                val scriptFile = java.io.File(tmpDir, scriptFileName)
                
                context.assets.open(scriptAssetPath).use { input ->
                    scriptFile.outputStream().use { output -> input.copyTo(output) }
                }
                scriptFile.setExecutable(true, false)

                // Simulate keyboard typing to run it inside xterm
                session.sendInput("sh /tmp/$scriptFileName\n")
            } catch (e: Exception) {
                session.sendInput("\r\nError copying setup script: ${e.message}\r\n")
            }
            withContext(Dispatchers.Main) { isBusy = false }
        }
    }

    fun runUpdatePackages() {
        if (!isReady) return
        session.sendInput("apk update && apk upgrade\n")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Alpine Linux 3.20", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Interactive Terminal Runtime",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF5C6BC0)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Quick action chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = { runSetupScript() },
                    label = { Text("Setup Alpine", fontSize = 12.sp) },
                    enabled = isReady && !isBusy,
                    leadingIcon = {
                        Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = Color(0xFF5C6BC0).copy(alpha = 0.12f),
                        labelColor = Color(0xFF5C6BC0),
                        leadingIconContentColor = Color(0xFF5C6BC0)
                    ),
                    border = AssistChipDefaults.assistChipBorder(
                        enabled = true,
                        borderColor = Color(0xFF5C6BC0).copy(alpha = 0.3f)
                    )
                )
                AssistChip(
                    onClick = { runUpdatePackages() },
                    label = { Text("Update Pkgs", fontSize = 12.sp) },
                    enabled = isReady,
                    leadingIcon = {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = Color(0xFF4CAF50).copy(alpha = 0.08f),
                        labelColor = Color(0xFF4CAF50),
                        leadingIconContentColor = Color(0xFF4CAF50)
                    ),
                    border = AssistChipDefaults.assistChipBorder(
                        enabled = true,
                        borderColor = Color(0xFF4CAF50).copy(alpha = 0.3f)
                    )
                )
            }

            if (!isReady) {
                // Loading screen
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(12.dp)
                        .background(Color(0xFF0D1117), shape = MaterialTheme.shapes.medium),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(color = Color(0xFF5C6BC0))
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFE6EDF3)
                        )
                    }
                }
            } else {
                // Interactive WebView Terminal
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .background(Color(0xFF0D1117), shape = MaterialTheme.shapes.medium)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.run {
                                    javaScriptEnabled = true
                                    allowFileAccess = true
                                    allowContentAccess = true
                                    @Suppress("DEPRECATION")
                                    allowFileAccessFromFileURLs = true
                                    @Suppress("DEPRECATION")
                                    allowUniversalAccessFromFileURLs = true
                                    domStorageEnabled = true
                                    databaseEnabled = true
                                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                }
                                setBackgroundColor(0xFF0D1117.toInt())
                                webChromeClient = object : android.webkit.WebChromeClient() {
                                    override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                                        android.util.Log.d("WebViewConsole", "${consoleMessage?.messageLevel()}: ${consoleMessage?.message()} (at ${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()})")
                                        return true
                                    }
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        webViewRef = this@apply
                                        
                                        // Replay any output buffer accumulated during WebView load time
                                        val buffered = session.getBufferedOutput()
                                        if (buffered.isNotEmpty()) {
                                            val base64 = android.util.Base64.encodeToString(
                                                buffered.toByteArray(Charsets.UTF_8),
                                                android.util.Base64.NO_WRAP
                                            )
                                            evaluateJavascript("writeBase64('$base64')", null)
                                        }
                                        
                                        // Wait a moment for terminal script to initialize, then trigger shell refresh
                                        postDelayed({
                                            session.sendInput("\r")
                                        }, 200)
                                    }
                                }
                                addJavascriptInterface(object {
                                    @android.webkit.JavascriptInterface
                                    fun onTerminalInput(data: String) {
                                        session.sendInput(data)
                                    }
                                    @android.webkit.JavascriptInterface
                                    fun onResize(cols: Int, rows: Int) {
                                        // resize logic if needed
                                    }
                                }, "Android")
                                loadUrl("file:///android_asset/terminal/index.html")
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
