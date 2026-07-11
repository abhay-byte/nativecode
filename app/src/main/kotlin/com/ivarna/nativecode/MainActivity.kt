package com.ivarna.nativecode

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.isGranted
import com.ivarna.nativecode.ui.theme.NativeCodeTheme
import com.ivarna.nativecode.core.utils.StateManager
import com.ivarna.nativecode.core.utils.ThemePreferences
import com.ivarna.nativecode.ui.screens.ToolType
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi

// Screen navigation enum
enum class Screen {
    ONBOARDING,
    PREREQUISITES,
    HOME,
    SETTINGS,
    TROUBLESHOOTING,
    ROOT_ACCESS,
    INSTALL_WIZARD,
    DISTRO_SETTINGS,
    // Embedded terminal (TerminalView library) + in-app X11 display
    EMBEDDED_TERMINAL,
    X11_DISPLAY,
    // Termux bootstrap terminal (real Termux packages, no root)
    TERMUX_TERMINAL,
}

class MainActivity : ComponentActivity() {
    
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
        handleSharedImage(intent)
    }

    private fun handleDeepLink(intent: android.content.Intent) {
        android.util.Log.d("NativeCode", "handleDeepLink called with action: ${intent.action}, data: ${intent.data}")
        if (intent.action != android.content.Intent.ACTION_VIEW || intent.data?.scheme != "nativecode") return

        val uri = intent.data ?: return
        when (uri.host) {
            "callback" -> handleScriptCallback(uri)
            "codex-response", "codex-oauth" -> handleCodexResponse(uri)
        }
    }

    private fun handleSharedImage(intent: android.content.Intent) {
        if (intent.action == android.content.Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            val imageUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(android.content.Intent.EXTRA_STREAM)
            }
            if (imageUri != null) {
                android.util.Log.d("NativeCode", "Received shared image: $imageUri")
                StateManager.setPendingSharedImageUri(imageUri.toString())
            }
        }
    }

    private fun handleCodexResponse(uri: android.net.Uri) {
        val id = uri.getQueryParameter("id") ?: return
        val status = uri.getQueryParameter("status") ?: "error"
        val responseB64 = uri.getQueryParameter("response") ?: ""

        val response = try {
            String(android.util.Base64.decode(responseB64, android.util.Base64.URL_SAFE), Charsets.UTF_8)
        } catch (e: Exception) {
            android.util.Log.e("NativeCode", "Failed to decode Codex response", e)
            responseB64
        }

        val result = if (status == "error") {
            Result.failure(Exception(response))
        } else {
            Result.success(response)
        }

        val completed = com.ivarna.nativecode.core.codex.CodexResponseBridge.complete(id, result)
        android.util.Log.d("NativeCode", "Codex response handled: id=$id, status=$status, completed=$completed")
    }

    private fun handleScriptCallback(uri: android.net.Uri) {
        val result = uri.getQueryParameter("result")
        val scriptName = uri.getQueryParameter("name") ?: "unknown"

        android.util.Log.d("NativeCode", "Script callback: result=$result, scriptName=$scriptName")

        if (result == "success") {
             // Handle project operation callbacks
             when {
                 scriptName.startsWith("git_diff_") -> {
                     readTermuxOutputFile(scriptName) { content ->
                         StateManager.setGitDiffResult(content)
                         StateManager.updateBackgroundTask(scriptName, com.ivarna.nativecode.core.model.BackgroundTaskStatus.SUCCESS, content)
                     }
                     return
                 }
                 scriptName.startsWith("find_apks_") -> {
                     readTermuxOutputFile(scriptName) { content ->
                         val apkFiles = content.lines().filter { it.endsWith(".apk") }
                         StateManager.setApkListResult(apkFiles)
                         StateManager.updateBackgroundTask(scriptName, com.ivarna.nativecode.core.model.BackgroundTaskStatus.SUCCESS, content)
                     }
                     return
                 }
                 scriptName.startsWith("dir_list_") -> {
                     readTermuxOutputFile(scriptName) { content ->
                         val lines = content.lines().filter { it.isNotBlank() }
                         StateManager.setDirectoryListResult(lines)
                         StateManager.updateBackgroundTask(scriptName, com.ivarna.nativecode.core.model.BackgroundTaskStatus.SUCCESS, content)
                     }
                     return
                 }
                 scriptName.startsWith("git_clone_") -> {
                     readTermuxOutputFile(scriptName) { content ->
                         val pathLine = content.lines().find { it.startsWith("PATH:") }
                         val clonedPath = pathLine?.removePrefix("PATH:")?.trim()
                         if (clonedPath != null) {
                             StateManager.addProject(this, com.ivarna.nativecode.core.model.Project(
                                 path = clonedPath,
                                 name = clonedPath.substringAfterLast("/").takeIf { it.isNotEmpty() } ?: "Root",
                                 category = "General"
                             ))
                             StateManager.triggerRefresh()
                             android.widget.Toast.makeText(this, "Project cloned successfully! ✅", android.widget.Toast.LENGTH_SHORT).show()
                         }
                         StateManager.updateBackgroundTask(scriptName, com.ivarna.nativecode.core.model.BackgroundTaskStatus.SUCCESS, content)
                     }
                     return
                 }
             }
             
             val queueManager = com.ivarna.nativecode.core.utils.InstallationQueueManager
             val currentTask = queueManager.currentTask

             if (currentTask != null && (scriptName == currentTask.id || scriptName == "base_install")) {
                 android.widget.Toast.makeText(this, "Task '${currentTask.name}' Complete. Proceeding...", android.widget.Toast.LENGTH_SHORT).show()

                 val distroId = currentTask.distroId
                 if (currentTask.type == com.ivarna.nativecode.core.utils.TaskType.COMPONENT) {
                     StateManager.setComponentInstalled(this, distroId, currentTask.id, true)
                 }
                 StateManager.triggerRefresh()
              } else {
                  if (scriptName.startsWith("distro_install_")) {
                      val distroId = scriptName.removePrefix("distro_install_")
                      StateManager.setDistroInstalled(this, distroId, true)
                      android.widget.Toast.makeText(this, "$distroId Installed! ✅", android.widget.Toast.LENGTH_LONG).show()
                  } else if (scriptName.startsWith("distro_uninstall_")) {
                      val distroId = scriptName.removePrefix("distro_uninstall_")
                      StateManager.clearDistroState(this, distroId)
                      android.widget.Toast.makeText(this, "$distroId Uninstalled! 🗑️", android.widget.Toast.LENGTH_LONG).show()
                  } else {
                      // Tool/IDE install callback (app may have restarted, so currentTask is null)
                      StateManager.setScriptStatus(this, scriptName, true)
                      // Mark component as installed for all distros so UI shows "Installed"
                      val installedDistros = StateManager.getInstalledDistros(this)
                      for (distroId in installedDistros) {
                          StateManager.setComponentInstalled(this, distroId, scriptName, true)
                      }
                      android.widget.Toast.makeText(this, "Tool '$scriptName' installed! ✅", android.widget.Toast.LENGTH_SHORT).show()
                      StateManager.triggerRefresh()
                  }
              }

             processNextInstallTask()
        } else {
             android.widget.Toast.makeText(this, "Task '$scriptName' failed! ❌", android.widget.Toast.LENGTH_LONG).show()
             com.ivarna.nativecode.core.utils.InstallationQueueManager.clear()
        }
    }

    private fun readTermuxOutputFile(callbackName: String, onContent: (String) -> Unit) {
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val outputFile = java.io.File("/data/data/com.termux/files/home/.nativecode_output/$callbackName.txt")
                val content = if (outputFile.exists()) {
                    outputFile.readText(Charsets.UTF_8)
                } else {
                    "Output file not found."
                }
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onContent(content)
                }
            } catch (e: Exception) {
                android.util.Log.e("NativeCode", "Failed to read Termux output file", e)
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onContent("Error reading output: ${e.message}")
                }
            }
        }
    }

    /**
     * Bound from Compose so queue tasks open the **internal** Termux terminal
     * (not external com.termux RUN_COMMAND).
     */
    private var openInternalTerminal: (
        (command: String, title: String, steps: Int, distroId: String?, componentId: String?) -> Unit
    )? = null

    private fun processNextInstallTask() {
        val queueManager = com.ivarna.nativecode.core.utils.InstallationQueueManager
        if (!queueManager.hasPending()) {
            android.widget.Toast.makeText(this, "All Installation Steps Complete! 🎉", android.widget.Toast.LENGTH_LONG).show()

            val distroId = queueManager.activeDistroId
            if (distroId != null) {
                com.ivarna.nativecode.core.utils.StateManager.setDistroInstalled(this, distroId, true)
                com.ivarna.nativecode.core.utils.StateManager.triggerRefresh()
            }

            queueManager.clear()
            return
        }

        val nextTask = queueManager.next() ?: return
        android.util.Log.d("NativeCode", "Processing Task: ${nextTask.name}")
        android.widget.Toast.makeText(this, "Starting: ${nextTask.name}...", android.widget.Toast.LENGTH_SHORT).show()

        if (nextTask.type == com.ivarna.nativecode.core.utils.TaskType.HW_ACCEL ||
            nextTask.type == com.ivarna.nativecode.core.utils.TaskType.COMPONENT
        ) {
            val scriptName = nextTask.scriptName ?: return
            val distroId = nextTask.distroId
            try {
                val scriptManager = com.ivarna.nativecode.core.data.ScriptManager(this)
                val scriptContent = scriptManager.getScriptContent(scriptName)
                val command = com.ivarna.nativecode.core.data.TermuxIntentFactory.buildRunFeatureScriptCommand(
                    distroId = distroId,
                    scriptContent = scriptContent,
                    callbackName = nextTask.id,
                    extraEnv = nextTask.extraEnv,
                )
                openInternalTerminal?.invoke(
                    command,
                    "Installing ${nextTask.name}",
                    1,
                    distroId,
                    nextTask.id,
                ) ?: run {
                    android.util.Log.e("NativeCode", "openInternalTerminal not bound")
                    android.widget.Toast.makeText(this, "Terminal not ready", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("NativeCode", "Failed to start task: ${nextTask.name}", e)
                android.widget.Toast.makeText(this, "Failed to start ${nextTask.name}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    @OptIn(ExperimentalPermissionsApi::class, ExperimentalHazeMaterialsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleDeepLink(intent)
        handleSharedImage(intent)
        // Keep ~/start_gui.sh (etc.) in sync with APK assets on every cold start
        try {
            com.ivarna.nativecode.core.data.ScriptManager(this).deployLaunchScriptsToHome()
        } catch (e: Exception) {
            android.util.Log.w("NativeCode", "Launch script deploy onCreate: ${e.message}")
        }
        setContent {
            // Watch Theme Preference
            val context = LocalContext.current
            val themePrefs = remember { ThemePreferences(context) }
            
            // Lift state up
            var currentThemeMode by remember { mutableStateOf(themePrefs.getThemeMode()) }

            val darkTheme = themePrefs.isDarkTheme(context)
            NativeCodeTheme(darkTheme = darkTheme) {
                // Onboarding temporarily disabled.
                val onboardingComplete = true
                
                // Permission State (Lifted for Settings and Home access)
                val permissionState = rememberPermissionState(
                    permission = "com.termux.permission.RUN_COMMAND"
                )

                // Navigation state
                var currentScreen by remember { 
                    mutableStateOf(Screen.HOME)
                }
                
                // Selected Distro for Wizard/Settings
                var selectedDistro by remember { mutableStateOf<com.ivarna.nativecode.core.data.Distro?>(null) }

                // Command to auto-run in the internal terminal (set before navigating to TERMUX_TERMINAL)
                var terminalInstallCommand by remember { mutableStateOf<String?>(null) }
                // How many steps the install script contains (for progress bar)
                var terminalInstallSteps by remember { mutableStateOf(1) }
                var terminalInstallTitle by remember { mutableStateOf("Installing…") }
                // Distro being installed (for completion callback)
                var terminalInstallDistroId by remember { mutableStateOf<String?>(null) }
                // Bump each navigation so TermuxTerminalScreen opens a new hub tab
                var terminalOpenEpoch by remember { mutableStateOf(0L) }
                // Component id being installed via internal terminal (for mark-installed on complete)
                var terminalInstallComponentId by remember { mutableStateOf<String?>(null) }

                // Refresh key to force UI update on resume
                // Collected from StateManager for remote triggers too
                val refreshKey by com.ivarna.nativecode.core.utils.StateManager.refreshTrigger.collectAsState()
                
                // ALSO react to Lifecycle
                var lifecycleRefreshKey by remember { mutableStateOf(0) }
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            lifecycleRefreshKey++
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                fun launchInternalTerminal(
                    command: String,
                    title: String,
                    steps: Int = 1,
                    distroId: String? = null,
                    componentId: String? = null,
                ) {
                    try {
                        com.ivarna.nativecode.core.data.ScriptManager(this@MainActivity)
                            .deployLaunchScriptsToHome()
                    } catch (_: Exception) {}
                    // GUI: never route through PTY terminal (outer proot + emulator lag)
                    if (command.contains("start_gui")) {
                        com.ivarna.nativecode.core.termux.GuiSessionLauncher
                            .launchGui(this@MainActivity, command)
                        return
                    }
                    terminalInstallCommand = command
                    terminalInstallTitle = title
                    terminalInstallSteps = steps
                    terminalInstallDistroId = distroId
                    terminalInstallComponentId = componentId
                    terminalOpenEpoch++
                    currentScreen = Screen.TERMUX_TERMINAL
                }

                // Bind Activity queue processor → Compose navigation
                SideEffect {
                    openInternalTerminal = { cmd, title, steps, distroId, componentId ->
                        launchInternalTerminal(cmd, title, steps, distroId, componentId)
                    }
                }
                
                // Helpers for service/activity
                val onStartServiceStub: (android.content.Intent) -> Unit = { intent ->
                    try { startService(intent) } catch (e: Exception) { android.util.Log.e("NativeCode", "StartService failed", e) }
                }
                val onStartActivityStub: (android.content.Intent) -> Unit = { intent ->
                    try { startActivity(intent) } catch (e: Exception) { android.util.Log.e("NativeCode", "StartActivity failed", e) }
                }
                
                // Navigation Callbacks
                val onNavigateToInstall: (com.ivarna.nativecode.core.data.Distro) -> Unit = { distro ->
                    selectedDistro = distro
                    currentScreen = Screen.INSTALL_WIZARD
                }
                val onNavigateToDistroSettings: (com.ivarna.nativecode.core.data.Distro) -> Unit = { distro ->
                    selectedDistro = distro
                    currentScreen = Screen.DISTRO_SETTINGS
                }


                val onInstallComponentStub: (com.ivarna.nativecode.core.data.DistroComponent, Map<String, String>) -> Unit = { component, extraEnv ->
                    val distroId = selectedDistro?.id
                        ?: com.ivarna.nativecode.core.data.DistroRepository.supportedDistros
                            .firstOrNull { it.id == "debian" }?.id
                        ?: "debian"
                    val scriptName = component.scriptName
                    if (scriptName.isNullOrBlank()) {
                        android.widget.Toast.makeText(
                            this@MainActivity,
                            "No install script for ${component.name}",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            try {
                                val scriptManager = com.ivarna.nativecode.core.data.ScriptManager(this@MainActivity)
                                val scriptContent = scriptManager.getScriptContent(scriptName)
                                val command = com.ivarna.nativecode.core.data.TermuxIntentFactory
                                    .buildRunFeatureScriptCommand(
                                        distroId = distroId,
                                        scriptContent = scriptContent,
                                        callbackName = component.id,
                                        extraEnv = extraEnv,
                                    )
                                withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    launchInternalTerminal(
                                        command = command,
                                        title = "Installing ${component.name}",
                                        steps = 1,
                                        distroId = distroId,
                                        componentId = component.id,
                                    )
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("NativeCode", "Component install prepare failed", e)
                                withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    android.widget.Toast.makeText(
                                        this@MainActivity,
                                        "Failed: ${e.message}",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    }
                }
                
                // Show appropriate screen based on state
                when (currentScreen) {
                    Screen.ONBOARDING -> {
                        val showPrerequisites = remember { mutableStateOf(false) }
                        if (!showPrerequisites.value) {
                            com.ivarna.nativecode.ui.screens.OnboardingScreen(
                                onGetStarted = { showPrerequisites.value = true }
                            )
                        } else {
                            com.ivarna.nativecode.ui.screens.PrerequisitesScreen(
                                onComplete = {
                                    StateManager.setOnboardingComplete(this@MainActivity, true)
                                    currentScreen = Screen.HOME
                                }
                            )
                        }
                    }
                    Screen.HOME -> {
                        val hazeState = remember { HazeState() }
                        com.ivarna.nativecode.ui.screens.HomeScreen(
                            permissionState = permissionState,
                            hazeState = hazeState,
                            scriptRefreshTrigger = refreshKey + lifecycleRefreshKey,
                            onStartService = onStartServiceStub,
                            onStartActivity = onStartActivityStub,
                            onNavigateToInstall = onNavigateToInstall,
                            onNavigateToSettings = onNavigateToDistroSettings,
                            onNavigateToSettingsScreen = { currentScreen = Screen.SETTINGS },
                            onNavigateToTerminal = { cmd ->
                                // Always redeploy launch scripts from assets → termux-home
                                try {
                                    com.ivarna.nativecode.core.data.ScriptManager(this@MainActivity)
                                        .deployLaunchScriptsToHome()
                                } catch (e: Exception) {
                                    android.util.Log.w("NativeCode", "Script redeploy skipped: ${e.message}")
                                }

                                // XFCE/KDE: skip internal Termux PTY (slow outer proot + emulator).
                                // Fluxlinux on real Termux is fast (native shell); we match with
                                // ProcessBuilder + X11 only — see GuiSessionLauncher.launchGui.
                                if (cmd != null && cmd.contains("start_gui")) {
                                    com.ivarna.nativecode.core.termux.GuiSessionLauncher
                                        .launchGui(this@MainActivity, cmd)
                                } else {
                                    terminalInstallCommand = cmd
                                    terminalInstallSteps = 0
                                    terminalInstallTitle = when {
                                        cmd == null -> "Terminal"
                                        else -> "Launching…"
                                    }
                                    terminalInstallDistroId = null
                                    terminalOpenEpoch++
                                    currentScreen = Screen.TERMUX_TERMINAL
                                }
                            },
                            onInstallComponent = onInstallComponentStub,
                            onLaunchTool = { tool, path ->
                                val intent = when (tool.type) {
                                    ToolType.AI -> {
                                        com.ivarna.nativecode.core.data.TermuxIntentFactory.buildLaunchToolCliIntent(
                                            tool.distroId, path, tool.name, tool.command
                                        )
                                    }
                                    ToolType.IDE -> {
                                        com.ivarna.nativecode.core.data.TermuxIntentFactory.buildLaunchIdeIntent(
                                            tool.distroId, path, tool.command
                                        )
                                    }
                                    else -> null
                                }
                                if (intent != null) {
                                    try {
                                        onStartServiceStub(intent)
                                    } catch (e: Exception) {
                                        android.util.Log.e("NativeCode", "Failed to launch ${tool.name}", e)
                                        android.widget.Toast.makeText(context, "Failed to launch Termux. Make sure it's installed.", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        )
                    }
                    Screen.SETTINGS -> {
                        com.ivarna.nativecode.ui.screens.SettingsScreen(
                            onBack = { currentScreen = Screen.HOME },
                            permissionState = permissionState,
                            onStartService = onStartServiceStub,
                            onStartActivity = onStartActivityStub,
                            onNavigateToOnboarding = {
                                StateManager.setOnboardingComplete(this@MainActivity, false)
                                currentScreen = Screen.ONBOARDING
                            },
                            onNavigateToTroubleshooting = { currentScreen = Screen.TROUBLESHOOTING },
                            onNavigateToRootCheck = { currentScreen = Screen.ROOT_ACCESS },
                            onThemeChanged = { newMode ->
                                themePrefs.setThemeMode(newMode)
                                currentThemeMode = newMode
                            },
                            currentTheme = currentThemeMode,
                            onNavigateToTerminal = { cmd ->
                                terminalInstallCommand = cmd
                                terminalInstallSteps = 0
                                terminalInstallTitle = "Setup"
                                terminalInstallDistroId = null
                                terminalOpenEpoch++
                                currentScreen = Screen.TERMUX_TERMINAL
                            }
                        )
                    }
                    Screen.TROUBLESHOOTING -> {
                        com.ivarna.nativecode.ui.screens.TroubleshootingScreen(
                            onBack = { currentScreen = Screen.SETTINGS }
                        )
                    }
                    Screen.PREREQUISITES -> { currentScreen = Screen.HOME }
                    Screen.ROOT_ACCESS -> {
                        com.ivarna.nativecode.ui.screens.RootAccessScreen(
                            onBack = { currentScreen = Screen.SETTINGS },
                            onEnableChroot = {
                                android.widget.Toast.makeText(this@MainActivity, "Chroot Mode Enabled", android.widget.Toast.LENGTH_SHORT).show()
                                currentScreen = Screen.SETTINGS
                            }
                        )
                    }
                    Screen.INSTALL_WIZARD -> {
                         val hazeState = remember { HazeState() }
                         if (selectedDistro != null) {
                             com.ivarna.nativecode.ui.screens.InstallConfigScreen(
                                 distro = selectedDistro!!,
                                 onBack = { currentScreen = Screen.HOME },
                                 hazeState = hazeState,
                                 onInstallStart = { components, theme, gpu, desktopEnv ->
                                     lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                         try {
                                             val distro = selectedDistro ?: return@launch
                                             val isChroot = distro.chrootSupported && !distro.prootSupported
                                             val scriptManager = com.ivarna.nativecode.core.data.ScriptManager(this@MainActivity)

                                             // ── Env exports header ──────────────────────────────────────
                                             val header = """
                                                 #!/bin/bash
                                                 export FLUX_THEME="$theme"
                                                 export FLUX_GPU="$gpu"
                                                 export FLUX_DESKTOP_ENV="$desktopEnv"
                                                 export DEBIAN_FRONTEND=noninteractive

                                                 CURRENT_STEP=1
                                                 log_step() {
                                                     echo -e "\n\033[1;36m[STEP ${'$'}{CURRENT_STEP}] ${'$'}1\033[0m"
                                                     ((CURRENT_STEP++))
                                                 }
                                             """.trimIndent() + "\n\n"

                                             val sb = StringBuilder(header)
                                             var stepCount = 0

                                             if (!isChroot) {
                                                 // Step 1: Termux dependencies (setup_termux.sh stripped of shebang/markers)
                                                 sb.append("log_step \"Setting up Termux Dependencies...\"\n")
                                                 stepCount++
                                                 var termuxSetup = scriptManager.getScriptContent("common/setup_termux.sh")
                                                 termuxSetup = termuxSetup
                                                     .replace("#!/bin/bash", "")
                                                     .replace(Regex("""if \[ -f "\${'$'}MARKER_FILE" \]; then[\s\S]*?fi\n?"""), "")
                                                     .replace(Regex("am start.*\n?"), "")
                                                     .replace(Regex("trap.*\n?"), "")
                                                 sb.append(termuxSetup).append("\n\n")

                                                 // Step 2: proot-distro install
                                                 sb.append("log_step \"Downloading & Installing ${distro.name} image...\"\n")
                                                 stepCount++
                                                 sb.append("proot-distro install ${distro.id} 2>/dev/null || echo '${distro.name} image already present'\n\n")

                                                 // Step 3: Base distro config (setup_debian_family.sh etc.) inside proot
                                                 val baseScript = when (distro.id) {
                                                     "archlinux" -> "common/setup_arch_family.sh"
                                                     "alpine"    -> "common/setup_alpine_family.sh"
                                                     else        -> "common/setup_debian_family.sh"
                                                 }
                                                 sb.append("log_step \"Configuring ${distro.name} base system...\"\n")
                                                 stepCount++
                                                 val baseConfigB64 = android.util.Base64.encodeToString(
                                                     scriptManager.getScriptContent(baseScript).toByteArray(), android.util.Base64.NO_WRAP)
                                                 sb.append("echo '$baseConfigB64' | base64 -d > \$HOME/flux_base_setup.sh\n")
                                                 sb.append("chmod +x \$HOME/flux_base_setup.sh\n")
                                                 sb.append("proot-distro login ${distro.id} --shared-tmp -- bash -c \"export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin FLUX_THEME=$theme FLUX_GPU=$gpu && bash /data/data/com.termux/files/home/flux_base_setup.sh\"\n")
                                                 sb.append("rm -f \$HOME/flux_base_setup.sh\n\n")

                                                 // Step 4+: Selected components
                                                 for (comp in components.filter { it.id !in setOf("hw_accel") }) {
                                                     if (comp.scriptName != null) {
                                                         sb.append("log_step \"Installing ${comp.name}...\"\n")
                                                         stepCount++
                                                         val compB64 = android.util.Base64.encodeToString(
                                                             scriptManager.getScriptContent(comp.scriptName!!).toByteArray(), android.util.Base64.NO_WRAP)
                                                         sb.append("echo '$compB64' | base64 -d > \$HOME/comp_setup.sh\n")
                                                         sb.append("chmod +x \$HOME/comp_setup.sh\n")
                                                         sb.append("proot-distro login ${distro.id} --shared-tmp -- bash -c \"export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin FLUX_THEME=$theme && bash /data/data/com.termux/files/home/comp_setup.sh\"\n")
                                                         sb.append("rm -f \$HOME/comp_setup.sh\n\n")
                                                     }
                                                 }

                                                 // Deploy GUI launch scripts (same assets as ScriptManager.deployLaunchScriptsToHome)
                                                 sb.append("log_step \"Deploying launch scripts...\"\n")
                                                 stepCount++
                                                 sb.append(scriptManager.buildDeployLaunchScriptsShell())
                                                 // Also write immediately into app termux-home (host side, no shell)
                                                 scriptManager.deployLaunchScriptsToHome()
                                             } else {
                                                 // ── Chroot path (debian13_chroot / debian_chroot) ──
                                                 // Must run as real root (su). Script installs under /data/local/tmp.
                                                 val chrootSetup = when (distro.id) {
                                                     "debian13_chroot" -> "chroot/setup_debian13_chroot.sh"
                                                     "debian_chroot" -> "chroot/setup_debian_chroot.sh"
                                                     "arch_chroot" -> "chroot/setup_arch_chroot.sh"
                                                     else -> "chroot/setup_debian13_chroot.sh"
                                                 }
                                                 sb.append("log_step \"Installing chroot rootfs ($chrootSetup)...\"\n")
                                                 stepCount++
                                                 val chrootB64 = android.util.Base64.encodeToString(
                                                     scriptManager.getScriptContent(chrootSetup).toByteArray(),
                                                     android.util.Base64.NO_WRAP
                                                 )
                                                 sb.append("echo '$chrootB64' | base64 -d > /data/local/tmp/nativecode_chroot_setup.sh\n")
                                                 sb.append("chmod +x /data/local/tmp/nativecode_chroot_setup.sh\n")
                                                 sb.append("sh /data/local/tmp/nativecode_chroot_setup.sh\n")
                                                 sb.append("rm -f /data/local/tmp/nativecode_chroot_setup.sh\n\n")

                                                 // Optional XFCE chroot helper
                                                 try {
                                                     val xfceSetup = scriptManager.getScriptContent("chroot/setup_xfce_chroot.sh")
                                                     if (xfceSetup.isNotBlank()) {
                                                         sb.append("log_step \"Installing XFCE in chroot...\"\n")
                                                         stepCount++
                                                         val xfceB64 = android.util.Base64.encodeToString(
                                                             xfceSetup.toByteArray(), android.util.Base64.NO_WRAP
                                                         )
                                                         sb.append("echo '$xfceB64' | base64 -d > /data/local/tmp/nativecode_xfce_chroot.sh\n")
                                                         sb.append("chmod +x /data/local/tmp/nativecode_xfce_chroot.sh\n")
                                                         sb.append("sh /data/local/tmp/nativecode_xfce_chroot.sh || true\n")
                                                         sb.append("rm -f /data/local/tmp/nativecode_xfce_chroot.sh\n\n")
                                                     }
                                                 } catch (_: Exception) {}
                                             }

                                             sb.append("\necho -e \"\\n\\033[1;32m✅ Installation complete! Restart the app to launch.\\033[0m\\n\"\n")

                                             val fullScript = sb.toString()

                                             if (isChroot) {
                                                 // Write install script to bootstrap home; run via internal terminal + su
                                                 val homeDir = com.ivarna.nativecode.core.termux.TermuxBootstrapManager
                                                     .homeDir(this@MainActivity)
                                                 val scriptFile = java.io.File(homeDir, "install_chroot_${distro.id}.sh")
                                                 scriptFile.writeText(fullScript)
                                                 scriptFile.setExecutable(true)
                                                 // Host path is bound as /data/data/com.termux/files/home inside bootstrap
                                                 val runCmd = """
                                                     echo "NativeCode: Chroot install needs root (Magisk)…"
                                                     su -c "bash /data/data/com.termux/files/home/install_chroot_${distro.id}.sh"
                                                     ec=${'$'}?
                                                     if [ ${'$'}ec -eq 0 ]; then
                                                       echo -e "\n\033[1;32m✅ Installation complete!\033[0m"
                                                     else
                                                       echo -e "\n\033[1;31m❌ Install failed (exit ${'$'}ec). Is Magisk/root granted?\033[0m"
                                                     fi
                                                 """.trimIndent()
                                                 withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                     launchInternalTerminal(
                                                         command = runCmd,
                                                         title = "Install ${distro.name} (chroot)",
                                                         steps = stepCount.coerceAtLeast(1),
                                                         distroId = distro.id,
                                                         componentId = null,
                                                     )
                                                 }
                                             } else {
                                                 // Delete old marker so setup_termux runs fresh
                                                 val homeDir = com.ivarna.nativecode.core.termux.TermuxBootstrapManager.homeDir(this@MainActivity)
                                                 java.io.File(homeDir, ".nativecode/setup_termux.done").delete()

                                                 // Write script directly to bootstrap home (~/install_nativecode.sh inside proot)
                                                 val scriptFile = java.io.File(homeDir, "install_nativecode.sh")
                                                 scriptFile.writeText(fullScript)
                                                 scriptFile.setExecutable(true)

                                                 withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                     terminalInstallTitle  = "Installing ${distro.name}"
                                                     terminalInstallSteps  = stepCount
                                                     terminalInstallCommand = "bash ~/install_nativecode.sh"
                                                     terminalInstallDistroId = distro.id
                                                     terminalOpenEpoch++
                                                     currentScreen = Screen.TERMUX_TERMINAL
                                                 }
                                             }
                                         } catch (e: Exception) {
                                             android.util.Log.e("NativeCode", "Failed to prepare install", e)
                                             withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                 android.widget.Toast.makeText(this@MainActivity, "Failed to prepare install: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                                             }
                                         }
                                     }
                                 }
                             )
                         } else currentScreen = Screen.HOME
                    }
                    Screen.DISTRO_SETTINGS -> {
                        val hazeState = remember { HazeState() }
                        if (selectedDistro != null) {
                            com.ivarna.nativecode.ui.screens.DistroSettingsScreen(
                                distro = selectedDistro!!,
                                onBack = { currentScreen = Screen.HOME },
                                onInstallComponent = onInstallComponentStub,
                                onUninstallDistro = {
                                    val id = selectedDistro!!.id
                                    val cmd = com.ivarna.nativecode.core.data.TermuxIntentFactory
                                        .buildUninstallCommand(id)
                                    launchInternalTerminal(
                                        command = cmd,
                                        title = "Uninstalling $id",
                                        steps = 1,
                                        distroId = id,
                                        componentId = "distro_uninstall_$id",
                                    )
                                },
                                onReinstallDistro = {
                                    selectedDistro?.let { onNavigateToInstall(it) }
                                },
                                onNavigateToTerminal = { cmd ->
                                    launchInternalTerminal(
                                        command = cmd ?: "true",
                                        title = if (cmd == null) "Terminal" else "Running…",
                                        steps = 0,
                                        distroId = selectedDistro?.id,
                                    )
                                },
                                onStartActivity = onStartActivityStub,
                                hazeState = hazeState
                            )
                        } else currentScreen = Screen.HOME
                    }

                    Screen.EMBEDDED_TERMINAL -> {
                        com.ivarna.nativecode.ui.screens.EmbeddedTerminalScreen(
                            onBack = { currentScreen = Screen.HOME },
                            onX11Active = { currentScreen = Screen.X11_DISPLAY }
                        )
                    }
                    Screen.TERMUX_TERMINAL -> {
                        com.ivarna.nativecode.ui.screens.TermuxTerminalScreen(
                            onBack = {
                                terminalInstallCommand = null
                                terminalInstallSteps = 1
                                terminalInstallTitle = "Installing…"
                                terminalInstallDistroId = null
                                terminalInstallComponentId = null
                                currentScreen = Screen.HOME
                            },
                            initialCommand = terminalInstallCommand,
                            installTitle = terminalInstallTitle,
                            totalSteps = terminalInstallSteps,
                            openEpoch = terminalOpenEpoch,
                            onInstallComplete = {
                                val distroId = terminalInstallDistroId
                                val componentId = terminalInstallComponentId
                                if (componentId != null && componentId.startsWith("distro_uninstall_") && distroId != null) {
                                    com.ivarna.nativecode.core.utils.StateManager.clearDistroState(
                                        this@MainActivity, distroId
                                    )
                                    android.widget.Toast.makeText(
                                        this@MainActivity, "$distroId uninstalled", android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                } else if (componentId != null && distroId != null) {
                                    com.ivarna.nativecode.core.utils.StateManager.setComponentInstalled(
                                        this@MainActivity, distroId, componentId, true
                                    )
                                    com.ivarna.nativecode.core.utils.StateManager.setScriptStatus(
                                        this@MainActivity, componentId, true
                                    )
                                    android.widget.Toast.makeText(
                                        this@MainActivity, "Installed $componentId", android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                } else if (distroId != null) {
                                    com.ivarna.nativecode.core.utils.StateManager.setDistroInstalled(
                                        this@MainActivity, distroId, true
                                    )
                                }
                                com.ivarna.nativecode.core.utils.StateManager.triggerRefresh()
                                // Continue queue (if any) then home
                                lifecycleScope.launch {
                                    kotlinx.coroutines.delay(1500L)
                                    val qm = com.ivarna.nativecode.core.utils.InstallationQueueManager
                                    if (qm.hasPending()) {
                                        processNextInstallTask()
                                    } else {
                                        terminalInstallCommand = null
                                        terminalInstallSteps = 1
                                        terminalInstallTitle = "Installing…"
                                        terminalInstallDistroId = null
                                        terminalInstallComponentId = null
                                        currentScreen = Screen.HOME
                                    }
                                }
                            }
                        )
                    }
                    Screen.X11_DISPLAY -> {
                        com.ivarna.nativecode.ui.screens.X11DisplayScreen(
                            onBack = { currentScreen = Screen.EMBEDDED_TERMINAL }
                        )
                    }
                }
            }
        }
    }
}
