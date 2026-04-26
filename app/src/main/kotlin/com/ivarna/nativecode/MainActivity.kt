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
import com.ivarna.nativecode.ui.components.BottomTab
import com.ivarna.nativecode.ui.components.GlassBottomNavigation
import com.ivarna.nativecode.ui.components.GlassScaffold
import com.ivarna.nativecode.ui.theme.NativeCodeTheme
import com.ivarna.nativecode.core.utils.StateManager
import com.ivarna.nativecode.core.utils.ThemePreferences
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
}

class MainActivity : ComponentActivity() {
    
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
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

    private fun processNextInstallTask() {
        val queueManager = com.ivarna.nativecode.core.utils.InstallationQueueManager
        if (!queueManager.hasPending()) {
            android.widget.Toast.makeText(this, "All Installation Steps Complete! 🎉", android.widget.Toast.LENGTH_LONG).show()
            
            // Mark Distro Installed
            val distroId = queueManager.activeDistroId
            if (distroId != null) {
                com.ivarna.nativecode.core.utils.StateManager.setDistroInstalled(this, distroId, true)
                // Trigger UI refresh via StateFlow
                com.ivarna.nativecode.core.utils.StateManager.triggerRefresh()
            }
            
            queueManager.clear()
            // Reset Progress UI state effectively done by clear()
            return
        }

        val nextTask = queueManager.next() ?: return // advances queue state internal
        
        // Log Update
        android.util.Log.d("NativeCode", "Processing Task: ${nextTask.name}")
        
        android.widget.Toast.makeText(this, "Starting: ${nextTask.name}...", android.widget.Toast.LENGTH_SHORT).show()
        
        if (nextTask.type == com.ivarna.nativecode.core.utils.TaskType.HW_ACCEL || nextTask.type == com.ivarna.nativecode.core.utils.TaskType.COMPONENT) {
            val scriptName = nextTask.scriptName
            val distroId = nextTask.distroId
            
            if (scriptName != null) {
                // Fetch Script Content
                // We need ScriptManager. Since we are in Activity, we can instantiate it.
                val scriptManager = com.ivarna.nativecode.core.data.ScriptManager(this)
                var scriptContent = scriptManager.getScriptContent(scriptName)
                
                // Inject Environment Variables from Task
                if (nextTask.extraEnv.isNotEmpty()) {
                    val envBlock = nextTask.extraEnv.entries.joinToString("\n") { "export ${it.key}=\"${it.value}\"" }
                    // Prepend to script content
                    scriptContent = "$envBlock\n\n$scriptContent"
                }
                
                // Build Intent with Callback
                val intent = com.ivarna.nativecode.core.data.TermuxIntentFactory.buildRunFeatureScriptIntent(
                    distroId = distroId,
                    scriptContent = scriptContent,
                    callbackName = nextTask.id
                )
                
                try {
                    startService(intent) // or startActivity depending on IntentFactory implementation.
                    // buildRunFeatureScriptIntent returns RunCommandService intent?
                    // No, TermuxIntentFactory returns Intent for RUN_COMMAND usually.
                    // Actually buildRunCommandIntent uses 'com.termux.permission.RUN_COMMAND'.
                    // So startService is correct IF it targets Termux Service, 
                    // BUT for Termux RUN_COMMAND we usually use startService.
                    // However, my stub 'onStartServiceStub' used startService.
                } catch (e: Exception) {
                    android.util.Log.e("NativeCode", "Failed to start task: ${nextTask.name}", e)
                    android.widget.Toast.makeText(this, "Failed to start ${nextTask.name}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    @OptIn(ExperimentalPermissionsApi::class, ExperimentalHazeMaterialsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleDeepLink(intent)
        setContent {
            // Watch Theme Preference
            val context = LocalContext.current
            val themePrefs = remember { ThemePreferences(context) }
            
            // Lift state up
            var currentThemeMode by remember { mutableStateOf(themePrefs.getThemeMode()) }

            NativeCodeTheme(themeMode = currentThemeMode) {
                val onboardingComplete = StateManager.isOnboardingComplete(this@MainActivity)
                
                // Permission State (Lifted for Settings and Home access)
                val permissionState = rememberPermissionState(
                    permission = "com.termux.permission.RUN_COMMAND"
                )

                // Navigation state
                var currentScreen by remember { 
                    mutableStateOf(if (onboardingComplete) Screen.HOME else Screen.ONBOARDING) 
                }
                
                var currentTab by remember { mutableStateOf(BottomTab.HOME) }
                
                // Selected Distro for Wizard/Settings
                var selectedDistro by remember { mutableStateOf<com.ivarna.nativecode.core.data.Distro?>(null) }
                
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
                
                @Composable
                fun MainScreenContent(
                    tab: BottomTab,
                    hazeState: HazeState
                ) {
                    when (tab) {
                        BottomTab.HOME -> {
                            com.ivarna.nativecode.ui.screens.HomeScreen(
                                permissionState = permissionState,
                                hazeState = hazeState,
                                scriptRefreshTrigger = refreshKey + lifecycleRefreshKey,
                                onStartService = onStartServiceStub,
                                onStartActivity = onStartActivityStub,
                                // Pass navigation callbacks
                                onNavigateToInstall = onNavigateToInstall,
                                onNavigateToSettings = onNavigateToDistroSettings
                            )
                        }
                        BottomTab.DISTROS -> {
                            com.ivarna.nativecode.ui.screens.DistroScreen(
                                permissionState = permissionState,
                                hazeState = hazeState,
                                onStartService = onStartServiceStub,
                                onStartActivity = onStartActivityStub,
                                onNavigateToInstall = onNavigateToInstall
                            )
                        }
                        BottomTab.PROJECTS -> {
                            com.ivarna.nativecode.ui.screens.ProjectsScreen(
                                hazeState = hazeState,
                                onLaunchTool = { tool, path ->
                                    val intent = when (tool.type) {
                                        com.ivarna.nativecode.ui.screens.ToolType.AI -> {
                                            com.ivarna.nativecode.core.data.TermuxIntentFactory.buildLaunchToolCliIntent(
                                                tool.distroId, path, tool.name, tool.command
                                            )
                                        }
                                        com.ivarna.nativecode.ui.screens.ToolType.IDE -> {
                                            com.ivarna.nativecode.core.data.TermuxIntentFactory.buildLaunchIdeIntent(
                                                tool.distroId, path, tool.command
                                            )
                                        }
                                    }
                                    try {
                                        onStartServiceStub(intent)
                                    } catch (e: Exception) {
                                        android.util.Log.e("NativeCode", "Failed to launch ${tool.name}", e)
                                        android.widget.Toast.makeText(context, "Failed to launch Termux. Make sure it's installed.", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                }
                            )
                        }
                    }
                }

                // Helper for Top Bar
                @Composable
                fun TopBar(
                    hazeState: HazeState,
                    onSettingsClick: () -> Unit
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .hazeChild(
                                state = hazeState,
                                style = HazeStyle(
                                    backgroundColor = MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
                                    blurRadius = 20.dp,
                                    tint = null
                                )
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .windowInsetsPadding(WindowInsets.statusBars),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_logo),
                                    contentDescription = "Logo",
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "NativeCode",
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (StateManager.isTermuxInstalled(LocalContext.current)) {
                                   Text(
                                       text = StateManager.getPackageSize(LocalContext.current, "com.termux"),
                                       style = MaterialTheme.typography.labelSmall,
                                       color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                       modifier = Modifier.padding(end = 8.dp)
                                   )
                                }
                                
                                IconButton(onClick = onSettingsClick) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Settings",
                                        tint = MaterialTheme.colorScheme.onBackground
                                    )
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
                        GlassScaffold(
                            hazeState = hazeState,
                            topBar = {
                                TopBar(
                                    hazeState = hazeState,
                                    onSettingsClick = { currentScreen = Screen.SETTINGS }
                                )
                            },
                            bottomBar = {
                                GlassBottomNavigation(
                                    selectedTab = currentTab,
                                    onTabSelected = { currentTab = it },
                                    hazeState = hazeState
                                )
                            }
                        ) {
                            MainScreenContent(
                                tab = currentTab,
                                hazeState = hazeState
                            )
                        }
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
                            currentTheme = currentThemeMode
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
                                 onBack = { currentScreen = Screen.HOME }, // Or Screen.DISTROS depending on where they came from? Let's just go Home for now or maintain history.
                                 // Actually for simplicity, back goes to tab view.
                                 hazeState = hazeState,
                                 onInstallStart = { components, theme, gpu, desktopEnv ->
                                     if (permissionState.status.isGranted) {
                                         // NEW QUEUE-BASED WORKFLOW
                                         lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                              withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                  android.widget.Toast.makeText(this@MainActivity, "Preparing Queue...", android.widget.Toast.LENGTH_SHORT).show()
                                              }
                                              
                                              val queueManager = com.ivarna.nativecode.core.utils.InstallationQueueManager
                                              queueManager.clear()
                                              
                                              val tasks = mutableListOf<com.ivarna.nativecode.core.utils.InstallTask>()
                                              
                                              // 1. Base Task (Manual)
                                              tasks.add(com.ivarna.nativecode.core.utils.InstallTask(
                                                  id = "base_install",
                                                  name = "Base System Install",
                                                  type = com.ivarna.nativecode.core.utils.TaskType.BASE_INSTALL,
                                                  isManual = true,
                                                  distroId = selectedDistro!!.id,
                                                  extraEnv = mapOf("FLUX_THEME" to theme, "FLUX_GPU" to gpu, "FLUX_DESKTOP_ENV" to desktopEnv)
                                              ))
                                              
                                              // 2. Hardware Acceleration
                                              // Determine if we should run it based on selection
                                              val runHwAccel = gpu != "manual" && selectedDistro!!.id != "termux"
                                              // Even if auto, we run the script which detects.
                                              // If 'virgl' or 'turnip', we pass via Env.
                                              if (selectedDistro!!.id != "termux") {
                                                  // Pass the GPU pref to the script if not 'ask'/'manual'
                                                  // 'ask' means script runs interactively? Queue is non-interactive usually.
                                                  // HwAccel script needs to handle 'ask' by blocking? No, 'proot' execution is tricky for interactivity if wrapped.
                                                  // But here we are automating. 'ask' might just default to 'auto' logic or prompt if we handle it?
                                                  // Best approach: Force explicit choice or auto.
                                                  tasks.add(com.ivarna.nativecode.core.utils.InstallTask(
                                                      id = "hw_accel",
                                                      name = "Hardware Acceleration",
                                                      type = com.ivarna.nativecode.core.utils.TaskType.COMPONENT,
                                                      scriptName = "common/setup_hw_accel_debian.sh",
                                                      distroId = selectedDistro!!.id,
                                                      extraEnv = mapOf("FLUX_GPU" to gpu)
                                                  ))
                                              }
                                              
                                              // 3. If KDE selected, inject kde_plasma component automatically
                                              if (desktopEnv == "KDE") {
                                                  val kdeComp = selectedDistro!!.components.find { it.id == "kde_plasma" }
                                                  if (kdeComp != null) {
                                                      tasks.add(com.ivarna.nativecode.core.utils.InstallTask(
                                                          id = "kde_plasma",
                                                          name = kdeComp.name,
                                                          type = com.ivarna.nativecode.core.utils.TaskType.COMPONENT,
                                                          scriptName = kdeComp.scriptName,
                                                          distroId = selectedDistro!!.id,
                                                          extraEnv = emptyMap()
                                                      ))
                                                  }
                                              }
                                              
                                              // 4. User-selected Components (filter mandatory ones already added above)
                                              val alreadyQueued = setOf("hw_accel", "kde_plasma")
                                              components.filter { it.id !in alreadyQueued }.forEach { comp ->
                                                  tasks.add(com.ivarna.nativecode.core.utils.InstallTask(
                                                      id = comp.id,
                                                      name = comp.name,
                                                      type = com.ivarna.nativecode.core.utils.TaskType.COMPONENT,
                                                      scriptName = comp.scriptName,
                                                      distroId = selectedDistro!!.id,
                                                      extraEnv = mapOf("FLUX_THEME" to theme) // Pass theme if needed by component
                                                  ))
                                              }
                                              
                                              queueManager.enqueue(tasks)
                                              
                                              // Start the first task (Base Install)
                                              val firstTask = queueManager.next()
                                              if (firstTask != null && firstTask.type == com.ivarna.nativecode.core.utils.TaskType.BASE_INSTALL) {
                                                   // Generate Base Script
                                                   val script = com.ivarna.nativecode.core.data.TermuxIntentFactory.getBaseInstallScript(this@MainActivity, selectedDistro!!)
                                                   
                                                   withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                         val server = com.ivarna.nativecode.core.utils.LocalInstallServer()
                                                         lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                                             val port = server.start(script)
                                                             withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                                  // INTERACTIVE COMMAND: Download then Run
                                                                  // Prepend Exports based on Selection
                                                                  // Use one-shot env var syntax: VAR=val command
                                                                  val exports = "FLUX_THEME=$theme FLUX_GPU=$gpu"
                                                                  
                                                                  // Determine Root/Chroot status
                                                                  val isChroot = selectedDistro!!.chrootSupported && !selectedDistro!!.prootSupported
                                                                  
                                                                  // Define Clipboard
                                                                  val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager

                                                                  if (isChroot) {
                                                                       // Chroot/Root Command: simpler, avoids Termux 'pkg' commands
                                                                       // Assumes /system/bin/curl exists (standard on rooted Android 10+)
                                                                       val chrootCommand = "curl -L -o install.sh http://127.0.0.1:$port/install && $exports sh install.sh"
                                                                       
                                                                       val clip = android.content.ClipData.newPlainText("NativeCode Install", chrootCommand)
                                                                       clipboard.setPrimaryClip(clip)

                                                                       android.app.AlertDialog.Builder(this@MainActivity)
                                                                        .setTitle("⚠️ Root Access Required")
                                                                        .setMessage("This distro requires Root/Chroot.\n\n1. Open Termux\n2. Type 'su' and press Enter 🔑\n3. Paste the command and Run it.\n4. Follow prompts.")
                                                                        .setPositiveButton("Open Termux") { _, _ ->
                                                                            server.onDownload = { server.stop() }
                                                                            val launchIntent = com.ivarna.nativecode.core.data.TermuxIntentFactory.buildOpenTermuxIntent(this@MainActivity)
                                                                            if (launchIntent != null) startActivity(launchIntent)
                                                                            currentScreen = Screen.HOME
                                                                        }
                                                                        .setNegativeButton("Cancel") { _, _ -> server.stop() }
                                                                        .setCancelable(false)
                                                                        .show()
                                                                  } else {
                                                                       // Standard Proot Command
                                                                       val installCommand = "pkg update -y && pkg install curl -y && curl -L -o install.sh http://127.0.0.1:$port/install && $exports bash install.sh"
                                                                       val clip = android.content.ClipData.newPlainText("NativeCode Install", installCommand)
                                                                       clipboard.setPrimaryClip(clip)

                                                                       android.app.AlertDialog.Builder(this@MainActivity)
                                                                        .setTitle("Phase 1: Base Install 🚀")
                                                                        .setMessage("Queue initialized!\n\n1. Open Termux\n2. Paste command\n3. Follow prompts (GPU/Theme)\n4. App will auto-launch next steps.")
                                                                        .setPositiveButton("Open Termux") { _, _ ->
                                                                            server.onDownload = { server.stop() }
                                                                            val launchIntent = com.ivarna.nativecode.core.data.TermuxIntentFactory.buildOpenTermuxIntent(this@MainActivity)
                                                                            if (launchIntent != null) startActivity(launchIntent)
                                                                            currentScreen = Screen.HOME
                                                                        }
                                                                        .setNegativeButton("Cancel") { _, _ -> server.stop() }
                                                                        .setCancelable(false)
                                                                        .show()
                                                                  }
                                                             }
                                                         }
                                                   }
                                              }
                                         }
                                     } else {
                                         permissionState.launchPermissionRequest()
                                     }
                                 }
                             )
                         } else {
                             currentScreen = Screen.HOME
                         }
                    }
                    Screen.DISTRO_SETTINGS -> {
                          val hazeState = remember { HazeState() }
                          if (selectedDistro != null) {
                              com.ivarna.nativecode.ui.screens.DistroSettingsScreen(
                                  distro = selectedDistro!!,
                                  onBack = { currentScreen = Screen.HOME },
                                  hazeState = hazeState,
                                   onInstallComponent = { component, extraEnv ->
                                      if (permissionState.status.isGranted) {
                                          lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                              val queueManager = com.ivarna.nativecode.core.utils.InstallationQueueManager
                                              queueManager.clear()
                                              
                                              val task = com.ivarna.nativecode.core.utils.InstallTask(
                                                  id = component.id,
                                                  name = component.name,
                                                  type = com.ivarna.nativecode.core.utils.TaskType.COMPONENT,
                                                  scriptName = component.scriptName,
                                                  distroId = selectedDistro!!.id,
                                                  extraEnv = extraEnv
                                              )
                                              queueManager.enqueue(listOf(task))
                                              
                                              withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                  processNextInstallTask()
                                              }
                                          }
                                      } else {
                                          permissionState.launchPermissionRequest()
                                      }
                                  },
                                 onReinstallDistro = {
                                      if (permissionState.status.isGranted) {
                                          lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                              val queueManager = com.ivarna.nativecode.core.utils.InstallationQueueManager
                                              queueManager.clear()

                                               // Enqueue Base Install (Manual)
                                              val task = com.ivarna.nativecode.core.utils.InstallTask(
                                                  id = "redo_base_install",
                                                  name = "Redo Base Installation",
                                                  type = com.ivarna.nativecode.core.utils.TaskType.BASE_INSTALL,
                                                  isManual = true,
                                                  distroId = selectedDistro!!.id,
                                                  extraEnv = emptyMap() // Use defaults or existing logic
                                              )
                                              queueManager.enqueue(listOf(task))
                                              
                                              withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                   // Reuse InstallConfigScreen logic for Base Install handling
                                                   // We manually trigger the logic here because processNextInstallTask doesn't handle Manual/Base tasks automatically.
                                                   
                                                   val script = com.ivarna.nativecode.core.data.TermuxIntentFactory.getBaseInstallScript(this@MainActivity, selectedDistro!!)
                                                   val server = com.ivarna.nativecode.core.utils.LocalInstallServer()
                                                   
                                                   lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                                        val port = server.start(script)
                                                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                              val isChroot = selectedDistro!!.chrootSupported && !selectedDistro!!.prootSupported
                                                              val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager

                                                              if (isChroot) {
                                                                   // Chroot Logic
                                                                   val chrootCommand = "curl -L -o install.sh http://127.0.0.1:$port/install && sh install.sh"
                                                                   val clip = android.content.ClipData.newPlainText("NativeCode Install", chrootCommand)
                                                                   clipboard.setPrimaryClip(clip)

                                                                   android.app.AlertDialog.Builder(this@MainActivity)
                                                                    .setTitle("⚠️ Root Required (Reinstall)")
                                                                    .setMessage("1. Open Termux\n2. Type 'su' -> Enter 🔑\n3. Paste & Run command.")
                                                                    .setPositiveButton("Open Termux") { _, _ ->
                                                                        server.onDownload = { server.stop() }
                                                                        val launchIntent = com.ivarna.nativecode.core.data.TermuxIntentFactory.buildOpenTermuxIntent(this@MainActivity)
                                                                        if (launchIntent != null) startActivity(launchIntent)
                                                                        currentScreen = Screen.HOME
                                                                    }
                                                                    .setNegativeButton("Cancel") { _, _ -> server.stop() }
                                                                    .setCancelable(false)
                                                                    .show()
                                                              } else {
                                                                   // Proot Logic
                                                                   val curlCommand = "pkg update -y && pkg install curl -y && curl -L -o install.sh http://127.0.0.1:$port/install && bash install.sh"
                                                                   val clip = android.content.ClipData.newPlainText("NativeCode Install", curlCommand)
                                                                   clipboard.setPrimaryClip(clip)
                                                                   
                                                                   android.app.AlertDialog.Builder(this@MainActivity)
                                                                    .setTitle("Reinstalling Base System 🚀")
                                                                    .setMessage("Command copied!\n\n1. Open Termux\n2. Paste command")
                                                                    .setPositiveButton("Open Termux") { _, _ ->
                                                                        server.onDownload = { server.stop() }
                                                                        val launchIntent = com.ivarna.nativecode.core.data.TermuxIntentFactory.buildOpenTermuxIntent(this@MainActivity)
                                                                        if (launchIntent != null) startActivity(launchIntent)
                                                                        currentScreen = Screen.HOME
                                                                    }
                                                                    .setNegativeButton("Cancel") { _, _ -> server.stop() }
                                                                    .setCancelable(false)
                                                                    .show()
                                                              }
                                                        }
                                                   }
                                              }
                                          }
                                      } else {
                                          permissionState.launchPermissionRequest()
                                      }
                                 },
                                 onUninstallDistro = {
                                     // Navigate to Home first, then trigger uninstall
                                     // The uninstall script sends a callback to the app when complete
                                     // which triggers handleScriptCallback to update state
                                      if (permissionState.status.isGranted) {
                                          val intent = com.ivarna.nativecode.core.data.TermuxIntentFactory.buildUninstallIntent(selectedDistro!!.id)
                                          try {
                                              onStartServiceStub(intent)
                                              // DON'T update state here - let the callback handle it
                                              // StateManager will be updated when script sends am start callback
                                              android.widget.Toast.makeText(this@MainActivity, "Uninstalling...", android.widget.Toast.LENGTH_SHORT).show()
                                              currentScreen = Screen.HOME
                                          } catch(e: Exception) {
                                              android.util.Log.e("NativeCode", "Uninstall failed", e)
                                          }
                                      } else {
                                          permissionState.launchPermissionRequest()
                                      }
                                  },
                                  onStartActivity = onStartActivityStub,
                                  onNavigateToStart = { /* Not used in Settings, but if needed */ },
                                  onLaunchXfce = {
                                      if (permissionState.status.isGranted) {
                                          try {
                                              val intent = com.ivarna.nativecode.core.data.TermuxIntentFactory.buildLaunchGuiIntent(selectedDistro!!.id)
                                              onStartServiceStub(intent)
                                          } catch (e: Exception) {
                                              android.util.Log.e("NativeCode", "Launch XFCE4 failed", e)
                                          }
                                      } else {
                                          permissionState.launchPermissionRequest()
                                      }
                                  },
                                  onStopXfce = {
                                      try {
                                          val intent = com.ivarna.nativecode.core.data.TermuxIntentFactory.buildStopGuiIntent(selectedDistro!!.id)
                                          onStartServiceStub(intent)
                                      } catch (e: Exception) {
                                          android.util.Log.e("NativeCode", "Stop XFCE4 failed", e)
                                      }
                                  },
                                  onLaunchKde = {
                                      if (permissionState.status.isGranted) {
                                          try {
                                              val intent = com.ivarna.nativecode.core.data.TermuxIntentFactory.buildLaunchKdeGuiIntent(this@MainActivity, selectedDistro!!.id)
                                              onStartServiceStub(intent)
                                          } catch (e: Exception) {
                                              android.util.Log.e("NativeCode", "Launch KDE failed", e)
                                          }
                                      } else {
                                          permissionState.launchPermissionRequest()
                                      }
                                  },
                                  onStopKde = {
                                      try {
                                          val intent = com.ivarna.nativecode.core.data.TermuxIntentFactory.buildStopKdeGuiIntent(selectedDistro!!.id)
                                          onStartServiceStub(intent)
                                      } catch (e: Exception) {
                                          android.util.Log.e("NativeCode", "Stop KDE failed", e)
                                      }
                                  }
                              )

                         } else {
                             currentScreen = Screen.HOME
                         }
                    }
                }
            }
        }
    }
}
