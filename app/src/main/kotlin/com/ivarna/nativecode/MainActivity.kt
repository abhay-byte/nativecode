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

                val onInstallComponentStub: (com.ivarna.nativecode.core.data.DistroComponent, Map<String, String>) -> Unit = { component, extraEnv ->
                    val distroId = selectedDistro?.id ?: "debian"
                    if (permissionState.status.isGranted) {
                        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            val queueManager = com.ivarna.nativecode.core.utils.InstallationQueueManager
                            queueManager.clear()
                            val task = com.ivarna.nativecode.core.utils.InstallTask(
                                id = component.id,
                                name = component.name,
                                type = com.ivarna.nativecode.core.utils.TaskType.COMPONENT,
                                scriptName = component.scriptName,
                                distroId = distroId,
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
                                 onBack = { currentScreen = Screen.HOME },
                                 hazeState = hazeState,
                                 onInstallStart = { components, theme, gpu, desktopEnv ->
                                     if (permissionState.status.isGranted) {
                                         lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                              withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                  android.widget.Toast.makeText(this@MainActivity, "Preparing Queue...", android.widget.Toast.LENGTH_SHORT).show()
                                              }
                                              val queueManager = com.ivarna.nativecode.core.utils.InstallationQueueManager
                                              queueManager.clear()
                                              val tasks = mutableListOf<com.ivarna.nativecode.core.utils.InstallTask>()
                                              tasks.add(com.ivarna.nativecode.core.utils.InstallTask(id = "base_install", name = "Base System Install", type = com.ivarna.nativecode.core.utils.TaskType.BASE_INSTALL, isManual = true, distroId = selectedDistro!!.id, extraEnv = mapOf("FLUX_THEME" to theme, "FLUX_GPU" to gpu, "FLUX_DESKTOP_ENV" to desktopEnv)))
                                              if (selectedDistro!!.id != "termux") {
                                                  tasks.add(com.ivarna.nativecode.core.utils.InstallTask(id = "hw_accel", name = "Hardware Acceleration", type = com.ivarna.nativecode.core.utils.TaskType.COMPONENT, scriptName = "common/setup_hw_accel_debian.sh", distroId = selectedDistro!!.id, extraEnv = mapOf("FLUX_GPU" to gpu)))
                                              }
                                              if (desktopEnv == "KDE") {
                                                  selectedDistro!!.components.find { it.id == "kde_plasma" }?.let { comp ->
                                                      tasks.add(com.ivarna.nativecode.core.utils.InstallTask(id = comp.id, name = comp.name, type = com.ivarna.nativecode.core.utils.TaskType.COMPONENT, scriptName = comp.scriptName, distroId = selectedDistro!!.id, extraEnv = emptyMap()))
                                                  }
                                              }
                                              components.filter { it.id !in setOf("hw_accel", "kde_plasma") }.forEach { comp ->
                                                  tasks.add(com.ivarna.nativecode.core.utils.InstallTask(id = comp.id, name = comp.name, type = com.ivarna.nativecode.core.utils.TaskType.COMPONENT, scriptName = comp.scriptName, distroId = selectedDistro!!.id, extraEnv = mapOf("FLUX_THEME" to theme)))
                                              }
                                              queueManager.enqueue(tasks)
                                              val firstTask = queueManager.next()
                                              if (firstTask != null && firstTask.type == com.ivarna.nativecode.core.utils.TaskType.BASE_INSTALL) {
                                                   val script = com.ivarna.nativecode.core.data.TermuxIntentFactory.getBaseInstallScript(this@MainActivity, selectedDistro!!)
                                                   withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                         val server = com.ivarna.nativecode.core.utils.LocalInstallServer()
                                                         lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                                             val port = server.start(script)
                                                             withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                                  val exports = "FLUX_THEME=$theme FLUX_GPU=$gpu"
                                                                  val isChroot = selectedDistro!!.chrootSupported && !selectedDistro!!.prootSupported
                                                                  val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                                  if (isChroot) {
                                                                       val chrootCommand = "curl -L -o install.sh http://127.0.0.1:$port/install && $exports sh install.sh"
                                                                       clipboard.setPrimaryClip(android.content.ClipData.newPlainText("NativeCode Install", chrootCommand))
                                                                       android.app.AlertDialog.Builder(this@MainActivity).setTitle("⚠️ Root Required").setMessage("1. Open Termux\n2. Type 'su'\n3. Paste & Run command.").setPositiveButton("Open Termux") { _, _ -> server.onDownload = { server.stop() }; com.ivarna.nativecode.core.data.TermuxIntentFactory.buildOpenTermuxIntent(this@MainActivity)?.let { startActivity(it) }; currentScreen = Screen.HOME }.setNegativeButton("Cancel") { _, _ -> server.stop() }.show()
                                                                  } else {
                                                                       val installCommand = "pkg update -y && pkg install curl -y && curl -L -o install.sh http://127.0.0.1:$port/install && $exports bash install.sh"
                                                                       clipboard.setPrimaryClip(android.content.ClipData.newPlainText("NativeCode Install", installCommand))
                                                                       android.app.AlertDialog.Builder(this@MainActivity).setTitle("Phase 1: Base Install").setMessage("1. Open Termux\n2. Paste command.").setPositiveButton("Open Termux") { _, _ -> server.onDownload = { server.stop() }; com.ivarna.nativecode.core.data.TermuxIntentFactory.buildOpenTermuxIntent(this@MainActivity)?.let { startActivity(it) }; currentScreen = Screen.HOME }.setNegativeButton("Cancel") { _, _ -> server.stop() }.show()
                                                                  }
                                                             }
                                                         }
                                                   }
                                              }
                                         }
                                     } else permissionState.launchPermissionRequest()
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
                                    onStartServiceStub(com.ivarna.nativecode.core.data.TermuxIntentFactory.buildUninstallIntent(selectedDistro!!.id))
                                    currentScreen = Screen.HOME
                                },
                                onReinstallDistro = {
                                    selectedDistro?.let { onNavigateToInstall(it) }
                                },
                                onStartActivity = onStartActivityStub,
                                hazeState = hazeState
                            )
                        } else currentScreen = Screen.HOME
                    }
                }
            }
        }
    }
}
