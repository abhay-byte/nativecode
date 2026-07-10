package com.ivarna.nativecode.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.nativecode.R
import com.ivarna.nativecode.core.data.Distro
import com.ivarna.nativecode.core.data.DistroComponent
import com.ivarna.nativecode.core.data.DistroRepository
import com.ivarna.nativecode.core.data.TermuxIntentFactory
import com.ivarna.nativecode.core.model.BackgroundTask
import com.ivarna.nativecode.core.model.BackgroundTaskStatus
import com.ivarna.nativecode.core.model.Project
import com.ivarna.nativecode.core.model.ProjectCategory
import com.ivarna.nativecode.core.utils.StateManager
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream

enum class ToolType { AI, IDE }

data class InstalledTool(
    val id: String,
    val name: String,
    val command: String,
    val type: ToolType,
    val accentColor: Color,
    val distroId: String
)

@OptIn(ExperimentalPermissionsApi::class, ExperimentalHazeMaterialsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    permissionState: PermissionState,
    hazeState: HazeState,
    scriptRefreshTrigger: Int = 0,
    onStartService: (android.content.Intent) -> Unit,
    onStartActivity: (android.content.Intent) -> Unit,
    onNavigateToInstall: (Distro) -> Unit,
    onNavigateToSettings: (Distro) -> Unit,
    onNavigateToSettingsScreen: () -> Unit,
    onNavigateToTerminal: (command: String?) -> Unit = {},
    onLaunchTool: (InstalledTool, String) -> Unit,
    onInstallComponent: (DistroComponent, Map<String, String>) -> Unit
) {
    val context = LocalContext.current
    val refreshKey = remember { mutableStateOf(0) }

    var showAiToolsScreen by remember { mutableStateOf(false) }
    var showIdeToolsScreen by remember { mutableStateOf(false) }
    
    var showThemeDialog by remember { mutableStateOf(false) }
    var showGpuDialog by remember { mutableStateOf(false) }
    var selectedTheme by remember { mutableStateOf("dark") }
    var selectedGpu by remember { mutableStateOf("auto") }

    var projects by remember { mutableStateOf(StateManager.getProjects(context)) }
    var showAgentDialog by remember { mutableStateOf(false) }
    var selectedProject by remember { mutableStateOf<Project?>(null) }

    var showGitCloneDialog by remember { mutableStateOf(false) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var showGitDiffDialog by remember { mutableStateOf(false) }
    var showApkListDialog by remember { mutableStateOf(false) }
    var showDirectoryDialog by remember { mutableStateOf(false) }
    var showBackgroundTasks by remember { mutableStateOf(false) }
    var showAddImageDialog by remember { mutableStateOf(false) }
    var showShareImageDialog by remember { mutableStateOf(false) }

    var gitDiffContent by remember { mutableStateOf("") }
    var apkFiles by remember { mutableStateOf<List<String>>(emptyList()) }
    var directoryItems by remember { mutableStateOf<List<String>>(emptyList()) }
    var sharedImageUri by remember { mutableStateOf<Uri?>(null) }

    val debianDistro = remember { DistroRepository.supportedDistros.find { it.id == "debian" }!! }
    val chrootDebianDistro = remember {
        DistroRepository.supportedDistros.find { it.id == "debian13_chroot" }!!
    }
    val isInstalled = remember(refreshKey.value, scriptRefreshTrigger) {
        StateManager.isDistroInstalled(context, debianDistro.id)
    }
    val isChrootInstalled = remember(refreshKey.value, scriptRefreshTrigger) {
        StateManager.isDistroInstalled(context, chrootDebianDistro.id)
    }
    
    // Collect operation results from StateManager
    val collectedGitDiff by StateManager.gitDiffResult.collectAsState()
    val collectedApkList by StateManager.apkListResult.collectAsState()
    val collectedDirList by StateManager.directoryListResult.collectAsState()
    val backgroundTasks by StateManager.backgroundTasks.collectAsState()
    val pendingSharedImage by StateManager.pendingSharedImageUri.collectAsState()

    LaunchedEffect(scriptRefreshTrigger) {
        if (scriptRefreshTrigger > 0) {
            refreshKey.value++
            projects = StateManager.getProjects(context)
        }
    }

    // Handle git diff result
    LaunchedEffect(collectedGitDiff) {
        collectedGitDiff?.let {
            gitDiffContent = it
            showGitDiffDialog = true
            StateManager.setGitDiffResult(null)
        }
    }

    // Handle APK list result
    LaunchedEffect(collectedApkList) {
        collectedApkList?.let {
            apkFiles = it
            showApkListDialog = true
            StateManager.setApkListResult(null)
        }
    }

    // Handle directory list result
    LaunchedEffect(collectedDirList) {
        collectedDirList?.let {
            directoryItems = it
            showDirectoryDialog = true
            StateManager.setDirectoryListResult(null)
        }
    }

    // Handle pending shared image
    LaunchedEffect(pendingSharedImage) {
        pendingSharedImage?.let { uriStr ->
            sharedImageUri = Uri.parse(uriStr)
            showShareImageDialog = true
        }
    }

    val installedTools by remember(refreshKey.value, scriptRefreshTrigger) {
        derivedStateOf {
            val tools = mutableListOf<InstalledTool>()
            val installedDistros = StateManager.getInstalledDistros(context)
            for (distroId in installedDistros) {
                aiTools.filter { StateManager.isComponentInstalled(context, distroId, it.component.id) }.forEach {
                    tools.add(InstalledTool(it.id, it.name, it.command, ToolType.AI, it.accentColor, distroId))
                }
                ideTools.filter { StateManager.isComponentInstalled(context, distroId, it.component.id) }.forEach {
                    tools.add(InstalledTool(it.id, it.name, it.command, ToolType.IDE, it.accentColor, distroId))
                }
            }
            tools
        }
    }

    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            val path = convertUriToLinuxPath(uri)
            if (path != null) {
                if (projects.none { it.path == path }) {
                    StateManager.addProjectPath(context, path)
                    projects = StateManager.getProjects(context)
                }
            }
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null && selectedProject != null) {
            copyImageToProject(context, uri, selectedProject!!.path)
            projects = StateManager.getProjects(context)
            showAddImageDialog = false
        }
    }

    if (showAiToolsScreen) {
        AiToolsScreen(distro = debianDistro, onBack = { showAiToolsScreen = false }, onInstallComponent = onInstallComponent, hazeState = hazeState)
        return
    }

    if (showIdeToolsScreen) {
        IdeToolsScreen(distro = debianDistro, onBack = { showIdeToolsScreen = false }, onInstallComponent = onInstallComponent, hazeState = hazeState)
        return
    }

    com.ivarna.nativecode.ui.components.GlassScaffold(
        hazeState = hazeState,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(painter = painterResource(id = R.drawable.logo), contentDescription = null, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("NativeCode", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    if (backgroundTasks.isNotEmpty()) {
                        BadgedBox(badge = { Badge { Text(backgroundTasks.size.toString()) } }) {
                            IconButton(onClick = { showBackgroundTasks = true }) {
                                Icon(Icons.Default.TaskAlt, contentDescription = "Background Tasks", tint = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }

                    IconButton(onClick = {
                        // Open embedded X11 display (same package, no second launcher icon)
                        try {
                            com.ivarna.nativecode.core.termux.GuiSessionLauncher
                                .openX11Activity(context)
                        } catch (e: Exception) {
                            android.util.Log.e("HomeScreen", "Open X11 failed", e)
                            android.widget.Toast.makeText(
                                context,
                                "X11: ${e.message}",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }) {
                        Icon(
                            Icons.Default.DesktopWindows,
                            contentDescription = "X11 Display",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { onNavigateToTerminal(null) }) {
                        Icon(Icons.Default.Terminal, contentDescription = "Terminal", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = onNavigateToSettingsScreen) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.hazeChild(state = hazeState, style = HazeMaterials.thin())
            )
        }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
            contentPadding = PaddingValues(top = 24.dp, bottom = 48.dp)
        ) {
            item {
                DebianHeroCard(
                    distro = debianDistro,
                    isInstalled = isInstalled,
                    isGuiRunning = StateManager.isGuiRunning(context, debianDistro.id),
                    guiRunningType = StateManager.getGuiRunningType(context, debianDistro.id),
                    kdeInstalled = StateManager.isComponentInstalled(context, debianDistro.id, "kde_plasma"),
                    onInstall = { onNavigateToInstall(debianDistro) },
                    onLaunchCli = { onNavigateToTerminal("proot-distro login ${debianDistro.id}") },
                    onLaunchXfce = {
                        StateManager.setGuiRunning(context, debianDistro.id, true)
                        StateManager.setGuiRunningType(context, debianDistro.id, "xfce")
                        onNavigateToTerminal("bash ~/start_gui.sh ${debianDistro.id}")
                    },
                    onLaunchKde = {
                        StateManager.setGuiRunning(context, debianDistro.id, true)
                        StateManager.setGuiRunningType(context, debianDistro.id, "kde")
                        onNavigateToTerminal("bash ~/start_gui_kde.sh ${debianDistro.id}")
                    },
                    onStop = {
                        StateManager.setGuiRunning(context, debianDistro.id, false)
                        StateManager.setGuiRunningType(context, debianDistro.id, "")
                        try {
                            com.ivarna.nativecode.core.termux.GuiSessionLauncher.stopAll()
                        } catch (_: Exception) {}
                        // Run stop_gui.sh in internal terminal (same as start path)
                        onNavigateToTerminal("bash ~/stop_gui.sh ${debianDistro.id}")
                    },
                    onOpenX11 = {
                        // Open embedded NativeCode X11 display (not external com.termux.x11 package)
                        com.ivarna.nativecode.core.termux.GuiSessionLauncher.openX11Activity(context)
                    },
                    onSettings = { onNavigateToSettings(debianDistro) }
                )
            }

            // Debian chroot (rooted) — separate from proot Debian above
            item {
                ChrootDebianCard(
                    distro = chrootDebianDistro,
                    isInstalled = isChrootInstalled,
                    isGuiRunning = StateManager.isGuiRunning(context, chrootDebianDistro.id),
                    onInstall = { onNavigateToInstall(chrootDebianDistro) },
                    onLaunchCli = {
                        onNavigateToTerminal(
                            "su -c 'sh /data/local/tmp/enter_debian13.sh' " +
                                "|| su -c 'sh /data/local/tmp/enter_debian13_root.sh' " +
                                "|| echo 'Chroot not ready — install first or check root.'"
                        )
                    },
                    onLaunchGui = {
                        StateManager.setGuiRunning(context, chrootDebianDistro.id, true)
                        StateManager.setGuiRunningType(context, chrootDebianDistro.id, "xfce")
                        // Pre-start embedded X, then chroot GUI helper (written by setup)
                        Thread({
                            try {
                                com.ivarna.nativecode.core.termux.GuiSessionLauncher
                                    .ensureXServer(context)
                            } catch (_: Exception) {}
                        }, "x11-chroot-pre").apply { isDaemon = true }.start()
                        onNavigateToTerminal(
                            "bash /data/local/tmp/start_debian13_gui.sh 2>/dev/null " +
                                "|| su -c 'sh /data/local/tmp/start_debian13_gui.sh' " +
                                "|| echo 'GUI helper missing — finish chroot install first.'"
                        )
                    },
                    onStop = {
                        StateManager.setGuiRunning(context, chrootDebianDistro.id, false)
                        StateManager.setGuiRunningType(context, chrootDebianDistro.id, "")
                        try {
                            com.ivarna.nativecode.core.termux.GuiSessionLauncher.stopAll()
                        } catch (_: Exception) {}
                        onNavigateToTerminal(
                            "bash /data/local/tmp/stop_debian13_gui.sh 2>/dev/null " +
                                "|| su -c 'sh /data/local/tmp/stop_debian13_gui.sh' " +
                                "|| true"
                        )
                    },
                    onOpenX11 = {
                        com.ivarna.nativecode.core.termux.GuiSessionLauncher.openX11Activity(context)
                    },
                    onSettings = { onNavigateToSettings(chrootDebianDistro) }
                )
            }

            if (isInstalled) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ToolBanner(modifier = Modifier.weight(1f), title = "AI Agents", description = "Codex, Claude...", icon = Icons.Default.SmartToy, color = MaterialTheme.colorScheme.primary, onClick = { showAiToolsScreen = true })
                        ToolBanner(modifier = Modifier.weight(1f), title = "Code Editors", description = "VS Code, Cursor...", icon = Icons.Default.Laptop, color = MaterialTheme.colorScheme.secondary, onClick = { showIdeToolsScreen = true })
                    }
                }

                item {
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Workspace Projects", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Row {
                                TextButton(onClick = { showGitCloneDialog = true }) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Clone", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                TextButton(onClick = { folderLauncher.launch(null) }) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                        if (projects.isEmpty()) EmptyProjectsState()
                        else projects.forEachIndexed { _, project ->
                            ProjectGlassCard(
                                project = project,
                                onClick = { selectedProject = project; showAgentDialog = true },
                                onDelete = { StateManager.removeProject(context, project.path); projects = StateManager.getProjects(context) },
                                onCategoryClick = { selectedProject = project; showCategoryDialog = true }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }

                item {
                    Column {
                        Text("System Configuration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ConfigCard(modifier = Modifier.weight(1f), title = "Interface Theme", value = "XFCE / KDE", icon = Icons.Default.Palette, onClick = { showThemeDialog = true })
                            ConfigCard(modifier = Modifier.weight(1f), title = "Graphics Engine", value = StateManager.getHardwareAccelType(context, debianDistro.id).uppercase(), icon = Icons.Default.Speed, onClick = { showGpuDialog = true })
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(40.dp)) }
        }
    }

    if (showAgentDialog && selectedProject != null) {
        AgentSelectionDialog(
            project = selectedProject!!,
            installedTools = installedTools,
            onDismiss = { showAgentDialog = false },
            onLaunchTool = { tool -> showAgentDialog = false; onLaunchTool(tool, selectedProject!!.path) },
            onCopyPath = { val clip = ClipData.newPlainText("Project Path", selectedProject!!.path); (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip); showAgentDialog = false },
            onRemove = { StateManager.removeProject(context, selectedProject!!.path); projects = StateManager.getProjects(context); showAgentDialog = false },
            onGitDiff = {
                showAgentDialog = false
                val taskId = "git_diff_${System.currentTimeMillis()}"
                StateManager.addBackgroundTask(BackgroundTask(taskId, "Git Diff: ${selectedProject!!.name}"))
                onStartService(TermuxIntentFactory.buildGitDiffIntent(selectedProject!!.path, runInBackground = true))
            },
            onFindApks = {
                showAgentDialog = false
                val taskId = "find_apks_${System.currentTimeMillis()}"
                StateManager.addBackgroundTask(BackgroundTask(taskId, "Find APKs: ${selectedProject!!.name}"))
                onStartService(TermuxIntentFactory.buildFindApksIntent(selectedProject!!.path, runInBackground = true))
            },
            onBrowseDirectory = {
                showAgentDialog = false
                val taskId = "dir_list_${System.currentTimeMillis()}"
                StateManager.addBackgroundTask(BackgroundTask(taskId, "Browse: ${selectedProject!!.name}"))
                onStartService(TermuxIntentFactory.buildDirectoryListIntent(selectedProject!!.path, runInBackground = true))
            },
            onAddImage = {
                showAgentDialog = false
                showAddImageDialog = true
            },
            onCategory = {
                showAgentDialog = false
                showCategoryDialog = true
            }
        )
    }

    if (showGitCloneDialog) {
        GitCloneDialog(
            onDismiss = { showGitCloneDialog = false },
            onClone = { repoUrl, targetName ->
                showGitCloneDialog = false
                val parentPath = "/sdcard/NativeCodeProjects"
                val targetPath = "$parentPath/$targetName"
                val taskId = "git_clone_${System.currentTimeMillis()}"
                StateManager.addBackgroundTask(BackgroundTask(taskId, "Clone: $targetName"))
                onStartService(TermuxIntentFactory.buildGitCloneIntent(repoUrl, targetPath, runInBackground = true))
            }
        )
    }

    if (showCategoryDialog && selectedProject != null) {
        CategorySelectionDialog(
            currentCategory = selectedProject!!.category,
            onDismiss = { showCategoryDialog = false },
            onSelect = { category ->
                StateManager.setProjectCategory(context, selectedProject!!.path, category)
                projects = StateManager.getProjects(context)
                showCategoryDialog = false
            }
        )
    }

    if (showGitDiffDialog) {
        TextOutputDialog(
            title = "Git Diff",
            content = gitDiffContent,
            onDismiss = { showGitDiffDialog = false }
        )
    }

    if (showApkListDialog) {
        ApkListDialog(
            apkFiles = apkFiles,
            onDismiss = { showApkListDialog = false }
        )
    }

    if (showDirectoryDialog) {
        TextOutputDialog(
            title = "Project Directory",
            content = directoryItems.joinToString("\n"),
            onDismiss = { showDirectoryDialog = false }
        )
    }

    if (showBackgroundTasks) {
        BackgroundTasksDialog(
            tasks = backgroundTasks,
            onDismiss = { showBackgroundTasks = false },
            onClear = { backgroundTasks.forEach { StateManager.removeBackgroundTask(it.id) } }
        )
    }

    if (showAddImageDialog && selectedProject != null) {
        AlertDialog(
            onDismissRequest = { showAddImageDialog = false },
            title = { Text("Add Image") },
            text = { Text("Select an image to copy to ${selectedProject!!.name}/images/") },
            confirmButton = {
                TextButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                    Text("Pick Image")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddImageDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showShareImageDialog && sharedImageUri != null) {
        ShareImageDialog(
            projects = projects,
            onDismiss = {
                showShareImageDialog = false
                sharedImageUri = null
                StateManager.setPendingSharedImageUri(null)
            },
            onSelectProject = { project ->
                sharedImageUri?.let { uri ->
                    copyImageToProject(context, uri, project.path)
                    projects = StateManager.getProjects(context)
                }
                showShareImageDialog = false
                sharedImageUri = null
                StateManager.setPendingSharedImageUri(null)
            }
        )
    }

    if (showThemeDialog) {
        GlassDialog(onDismiss = { showThemeDialog = false }) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Customize Desktop", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.height(16.dp))
                SettingsThemeOption(name = "Dark Mode (Default)", desc = "Sleek and professional.", id = "dark", selected = selectedTheme == "dark", onSelect = { selectedTheme = "dark" })
                SettingsThemeOption(name = "Light Mode", desc = "Clean and bright.", id = "light", selected = selectedTheme == "light", onSelect = { selectedTheme = "light" })
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { showThemeDialog = false; val component = debianDistro.components.find { it.id == "customization" }; if (component != null) onInstallComponent(component, mapOf("FLUX_THEME" to selectedTheme)) }, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Apply Theme") }
            }
        }
    }

    if (showGpuDialog) {
        GlassDialog(onDismiss = { showGpuDialog = false }) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Hardware Acceleration", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.height(16.dp))
                SettingsThemeOption(name = "Auto Detect", desc = "Recommended for most devices.", id = "auto", selected = selectedGpu == "auto", onSelect = { selectedGpu = "auto" })
                SettingsThemeOption(name = "VirGL (Universal)", desc = "Compatible with most devices.", id = "virgl", selected = selectedGpu == "virgl", onSelect = { selectedGpu = "virgl" })
                SettingsThemeOption(name = "Turnip/Zink", desc = "High performance for Snapdragon.", id = "turnip", selected = selectedGpu == "turnip", onSelect = { selectedGpu = "turnip" })
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { showGpuDialog = false; val component = debianDistro.components.find { it.id == "hw_accel" }; if (component != null) onInstallComponent(component, mapOf("FLUX_GPU" to selectedGpu)) }, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Apply Configuration") }
            }
        }
    }
}

@Composable
fun GlassDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(modifier = Modifier.fillMaxWidth(0.9f).clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(28.dp))) { content() }
    }
}

@Composable
fun SettingsThemeOption(name: String, desc: String, id: String, selected: Boolean, onSelect: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onSelect() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onSelect, colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.secondary))
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(name, fontWeight = FontWeight.SemiBold, color = if(selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface)
            Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
    }
}

@Composable
fun ChrootDebianCard(
    distro: Distro,
    isInstalled: Boolean,
    isGuiRunning: Boolean,
    onInstall: () -> Unit,
    onLaunchCli: () -> Unit,
    onLaunchGui: () -> Unit,
    onStop: () -> Unit,
    onOpenX11: () -> Unit,
    onSettings: () -> Unit,
) {
    val accent = Color(0xFFE65100) // root / power accent
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(accent.copy(alpha = 0.14f), Color.Transparent)
                )
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.4f), Color.Transparent)
                ),
                RoundedCornerShape(28.dp)
            )
            .padding(24.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.distro_debian),
                        contentDescription = null,
                        modifier = Modifier.size(42.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Debian (Chroot / Root)",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Debian 13 Trixie · real root · max performance",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isInstalled) MaterialTheme.colorScheme.primary
                                    else accent
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (isInstalled) "Installed & Ready" else "Requires Magisk / root",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
                IconButton(
                    onClick = onSettings,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Manage",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            if (!isInstalled) {
                Button(
                    onClick = onInstall,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Install Debian Chroot", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Text(
                    "Uses chroot under /data/local/tmp (not proot). Root required.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 10.dp)
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LaunchButton(
                        modifier = Modifier.weight(1f),
                        label = "Terminal",
                        icon = Icons.Default.Code,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        onClick = onLaunchCli
                    )
                    LaunchButton(
                        modifier = Modifier.weight(1f),
                        label = "XFCE4",
                        icon = Icons.Default.DesktopWindows,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        onClick = onLaunchGui
                    )
                }
                if (isGuiRunning) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onOpenX11,
                            modifier = Modifier.weight(1.5f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Visibility, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Open Display", fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = onStop,
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Stop", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DebianHeroCard(distro: Distro, isInstalled: Boolean, isGuiRunning: Boolean, guiRunningType: String, kdeInstalled: Boolean, onInstall: () -> Unit, onLaunchCli: () -> Unit, onLaunchXfce: () -> Unit, onLaunchKde: () -> Unit, onStop: () -> Unit, onOpenX11: () -> Unit, onSettings: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(colors = listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), Color.Transparent))).border(1.dp, Brush.verticalGradient(listOf(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), Color.Transparent)), RoundedCornerShape(28.dp)).padding(24.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) { Image(painter = painterResource(id = R.drawable.distro_debian), contentDescription = null, modifier = Modifier.size(42.dp)) }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Debian GNU/Linux", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                    Text("PRoot · no root required", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(if (isInstalled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isInstalled) "Installed & Ready" else "Not Installed", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    }
                }
                IconButton(onClick = onSettings, modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))) { Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)) }
            }
            Spacer(modifier = Modifier.height(28.dp))
            if (!isInstalled) {
                Button(onClick = onInstall, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(16.dp)) { Text("Install Debian System", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LaunchButton(modifier = Modifier.weight(1f), label = "Terminal", icon = Icons.Default.Code, color = MaterialTheme.colorScheme.surfaceVariant, onClick = onLaunchCli)
                    LaunchButton(modifier = Modifier.weight(1f), label = "XFCE4", icon = Icons.Default.DesktopWindows, color = MaterialTheme.colorScheme.primaryContainer, onClick = onLaunchXfce)
                    LaunchButton(modifier = Modifier.weight(1f), label = "KDE", icon = Icons.Default.Waves, color = MaterialTheme.colorScheme.secondaryContainer, onClick = onLaunchKde, enabled = kdeInstalled)
                }
                if (isGuiRunning) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = onOpenX11,
                            modifier = Modifier.weight(1.5f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open Display", fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = onStop,
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                        ) {
                            Text("Stop", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AlpineRuntimeCard(onOpenTerminal: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Check if rootfs has already been extracted
    val rootfsReady = remember {
        java.io.File(context.filesDir, "rootfs/bin/busybox").exists()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF5C6BC0).copy(alpha = 0.12f),
                        Color.Transparent
                    )
                )
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF5C6BC0).copy(alpha = 0.35f),
                        Color.Transparent
                    )
                ),
                RoundedCornerShape(28.dp)
            )
            .padding(24.dp)
    ) {
        Column {
            // Header row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.distro_alpine),
                        contentDescription = null,
                        modifier = Modifier.size(42.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Alpine Linux",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Embedded Proot Runtime",
                        fontSize = 12.sp,
                        color = Color(0xFF5C6BC0),
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (rootfsReady) Color(0xFF4CAF50)
                                    else MaterialTheme.colorScheme.tertiary
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (rootfsReady) "Rootfs Ready" else "Not Extracted",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Info chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("No Root", "apk pkgs", "glibc-free").forEach { label ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF5C6BC0).copy(alpha = 0.1f))
                            .border(1.dp, Color(0xFF5C6BC0).copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(label, fontSize = 11.sp, color = Color(0xFF5C6BC0), fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action button
            Button(
                onClick = onOpenTerminal,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF5C6BC0)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    Icons.Default.Terminal,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (rootfsReady) "Open Terminal" else "Open & Extract",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
fun DebianRuntimeCard(onOpenTerminal: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val rootfsReady = remember {
        java.io.File(context.filesDir, "rootfs-debian/usr/bin/sudo").exists()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFA07050).copy(alpha = 0.12f),
                        Color.Transparent
                    )
                )
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFA07050).copy(alpha = 0.35f),
                        Color.Transparent
                    )
                ),
                RoundedCornerShape(28.dp)
            )
            .padding(24.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.distro_debian),
                        contentDescription = null,
                        modifier = Modifier.size(42.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Debian GNU/Linux",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Proot Runtime · user: flux",
                        fontSize = 12.sp,
                        color = Color(0xFFA07050),
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (rootfsReady) Color(0xFF4CAF50)
                                    else MaterialTheme.colorScheme.tertiary
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (rootfsReady) "Rootfs Ready" else "Tap to download (~25 MB)",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("apt pkgs", "user: flux", "no password").forEach { label ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFA07050).copy(alpha = 0.1f))
                            .border(1.dp, Color(0xFFA07050).copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(label, fontSize = 11.sp, color = Color(0xFFA07050), fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onOpenTerminal,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFA07050)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    Icons.Default.Terminal,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (rootfsReady) "Open Terminal" else "Open & Bootstrap",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
fun LaunchButton(modifier: Modifier, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit, enabled: Boolean = true) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.height(64.dp), colors = ButtonDefaults.buttonColors(containerColor = if (enabled) color.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), contentColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)), shape = RoundedCornerShape(18.dp), contentPadding = PaddingValues(0.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)); Spacer(modifier = Modifier.height(4.dp)); Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun ToolBanner(modifier: Modifier, title: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Box(modifier = modifier.clip(RoundedCornerShape(20.dp)).background(color.copy(alpha = 0.05f)).border(1.dp, Brush.verticalGradient(listOf(color.copy(alpha = 0.3f), Color.Transparent)), RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(16.dp)) {
        Column { Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp)); Spacer(modifier = Modifier.height(12.dp)); Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp); Text(description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)) }
    }
}

@Composable
fun ConfigCard(modifier: Modifier, title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(modifier = modifier.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.02f)).border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)); Spacer(modifier = Modifier.width(10.dp)); Column { Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)); Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) } }
    }
}

@Composable
fun ProjectGlassCard(project: Project, onClick: () -> Unit, onDelete: () -> Unit, onCategoryClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.02f)).border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(24.dp)).clickable(onClick = onClick).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(project.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    CategoryChip(category = project.category, onClick = onCategoryClick)
                }
                Text(project.path, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), fontFamily = FontFamily.Monospace)
                if (project.gitRemoteUrl != null) {
                    Text(project.gitRemoteUrl, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), fontFamily = FontFamily.Monospace)
                }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)) }
        }
    }
}

@Composable
fun CategoryChip(category: String, onClick: () -> Unit) {
    val color = when (category.lowercase()) {
        "android" -> Color(0xFF4CAF50)
        "web" -> Color(0xFF2196F3)
        "ai / ml" -> Color(0xFF9C27B0)
        "desktop" -> Color(0xFF795548)
        "embedded" -> Color(0xFFFF9800)
        "game dev" -> Color(0xFFE91E63)
        "data science" -> Color(0xFF00BCD4)
        "cybersec" -> Color(0xFFF44336)
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(category, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun EmptyProjectsState() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(24.dp)) { Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), modifier = Modifier.size(64.dp)); Spacer(modifier = Modifier.height(12.dp)); Text("No projects linked", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), fontSize = 14.sp) }
}

@Composable
fun AgentSelectionDialog(
    project: Project,
    installedTools: List<InstalledTool>,
    onDismiss: () -> Unit,
    onLaunchTool: (InstalledTool) -> Unit,
    onCopyPath: () -> Unit,
    onRemove: () -> Unit,
    onGitDiff: () -> Unit,
    onFindApks: () -> Unit,
    onBrowseDirectory: () -> Unit,
    onAddImage: () -> Unit,
    onCategory: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.85f).clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(28.dp)).padding(24.dp)) {
            LazyColumn {
                item {
                    Text("Project Options", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(project.path, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), modifier = Modifier.padding(bottom = 24.dp))
                }
                item {
                    if (installedTools.isNotEmpty()) {
                        Text("OPEN WITH", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
                        installedTools.forEach { tool ->
                            AgentActionButton(label = tool.name, icon = if (tool.type == ToolType.AI) Icons.Default.SmartToy else Icons.Default.Laptop, color = tool.accentColor, onClick = { onLaunchTool(tool) })
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
                item {
                    Text("PROJECT TOOLS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(bottom = 8.dp))
                    AgentActionButton(label = "Git Diff", icon = Icons.Default.CompareArrows, color = Color(0xFF4CAF50), onClick = onGitDiff)
                    Spacer(modifier = Modifier.height(10.dp))
                    AgentActionButton(label = "Find APKs", icon = Icons.Default.Android, color = Color(0xFF4CAF50), onClick = onFindApks)
                    Spacer(modifier = Modifier.height(10.dp))
                    AgentActionButton(label = "Browse Directory", icon = Icons.Default.FolderOpen, color = MaterialTheme.colorScheme.primary, onClick = onBrowseDirectory)
                    Spacer(modifier = Modifier.height(10.dp))
                    AgentActionButton(label = "Add Image", icon = Icons.Default.Image, color = Color(0xFF9C27B0), onClick = onAddImage)
                    Spacer(modifier = Modifier.height(10.dp))
                    AgentActionButton(label = "Category: ${project.category}", icon = Icons.Default.Category, color = MaterialTheme.colorScheme.tertiary, onClick = onCategory)
                    Spacer(modifier = Modifier.height(10.dp))
                    AgentActionButton(label = "Copy Path", icon = Icons.Default.ContentCopy, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), onClick = onCopyPath)
                    Spacer(modifier = Modifier.height(10.dp))
                    AgentActionButton(label = "Remove Project", icon = Icons.Default.Delete, color = MaterialTheme.colorScheme.error, onClick = onRemove)
                    Spacer(modifier = Modifier.height(24.dp))
                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Close", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) }
                }
            }
        }
    }
}

@Composable
fun AgentActionButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.1f)).border(1.dp, color.copy(alpha = 0.2f), RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp)); Spacer(modifier = Modifier.width(12.dp)); Text(label, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp) }
}

@Composable
fun GitCloneDialog(onDismiss: () -> Unit, onClone: (repoUrl: String, targetName: String) -> Unit) {
    var repoUrl by remember { mutableStateOf("") }
    var targetName by remember { mutableStateOf("") }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(modifier = Modifier.fillMaxWidth(0.95f).clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(28.dp)).padding(24.dp)) {
            Column {
                Text("Clone from GitHub", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = repoUrl,
                    onValueChange = { repoUrl = it },
                    label = { Text("Repository URL") },
                    placeholder = { Text("https://github.com/user/repo.git") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = targetName,
                    onValueChange = { targetName = it },
                    label = { Text("Project Name") },
                    placeholder = { Text("my-project") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (repoUrl.isNotBlank() && targetName.isNotBlank()) {
                                onClone(repoUrl, targetName)
                            }
                        },
                        enabled = repoUrl.isNotBlank() && targetName.isNotBlank()
                    ) { Text("Clone") }
                }
            }
        }
    }
}

@Composable
fun CategorySelectionDialog(currentCategory: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(modifier = Modifier.fillMaxWidth(0.9f).clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(28.dp)).padding(24.dp)) {
            Column {
                Text("Select Category", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                ProjectCategory.entries.forEach { category ->
                    val selected = category.displayName == currentCategory
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(category.displayName) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected, onClick = { onSelect(category.displayName) })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(category.displayName, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            }
        }
    }
}

@Composable
fun TextOutputDialog(title: String, content: String, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.8f).clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(28.dp)).padding(24.dp)) {
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Box(modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)).padding(16.dp)) {
                    val scrollState = rememberScrollState()
                    Text(
                        text = content.ifBlank { "No output." },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        modifier = Modifier.verticalScroll(scrollState)
                    )
                }
            }
        }
    }
}

@Composable
fun ApkListDialog(apkFiles: List<String>, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(modifier = Modifier.fillMaxWidth(0.95f).clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(28.dp)).padding(24.dp)) {
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("APK Files", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
                }
                Spacer(modifier = Modifier.height(16.dp))
                if (apkFiles.isEmpty()) {
                    Text("No APK files found.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                } else {
                    apkFiles.forEach { apk ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Android, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(apk.substringAfterLast("/"), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(apk, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), fontFamily = FontFamily.Monospace)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun BackgroundTasksDialog(tasks: List<BackgroundTask>, onDismiss: () -> Unit, onClear: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(modifier = Modifier.fillMaxWidth(0.95f).clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(28.dp)).padding(24.dp)) {
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Background Tasks", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onClear) { Text("Clear All") }
                }
                Spacer(modifier = Modifier.height(16.dp))
                if (tasks.isEmpty()) {
                    Text("No background tasks.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                } else {
                    tasks.forEach { task ->
                        val statusColor = when (task.status) {
                            BackgroundTaskStatus.RUNNING -> MaterialTheme.colorScheme.primary
                            BackgroundTaskStatus.SUCCESS -> Color(0xFF4CAF50)
                            BackgroundTaskStatus.FAILED -> MaterialTheme.colorScheme.error
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(statusColor.copy(alpha = 0.1f)).border(1.dp, statusColor.copy(alpha = 0.2f), RoundedCornerShape(12.dp)).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                when (task.status) {
                                    BackgroundTaskStatus.RUNNING -> Icons.Default.Sync
                                    BackgroundTaskStatus.SUCCESS -> Icons.Default.CheckCircle
                                    BackgroundTaskStatus.FAILED -> Icons.Default.Error
                                },
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(task.name, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(task.status.name, fontSize = 11.sp, color = statusColor)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Close") }
            }
        }
    }
}

@Composable
fun ShareImageDialog(projects: List<Project>, onDismiss: () -> Unit, onSelectProject: (Project) -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.8f).clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(28.dp)).padding(24.dp)) {
            Column {
                Text("Save Image To Project", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Select a project to save the shared image:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(16.dp))
                if (projects.isEmpty()) {
                    Text("No projects available. Add a project first.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                } else {
                    projects.forEach { project ->
                        AgentActionButton(
                            label = project.name,
                            icon = Icons.Default.FolderOpen,
                            color = MaterialTheme.colorScheme.primary,
                            onClick = { onSelectProject(project) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            }
        }
    }
}

fun convertUriToLinuxPath(uri: Uri): String? {
    val decodedPath = Uri.decode(uri.toString())
    val marker = "tree/primary:"
    return if (decodedPath.contains(marker)) "/sdcard/" + decodedPath.substringAfter(marker) else null
}

fun copyImageToProject(context: Context, imageUri: Uri, projectPath: String) {
    try {
        val imagesDir = File(projectPath, "images")
        imagesDir.mkdirs()
        val timestamp = System.currentTimeMillis()
        val destFile = File(imagesDir, "image_$timestamp.jpg")
        context.contentResolver.openInputStream(imageUri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
        android.widget.Toast.makeText(context, "Image saved to ${destFile.absolutePath}", android.widget.Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        android.util.Log.e("NativeCode", "Failed to copy image", e)
        android.widget.Toast.makeText(context, "Failed to save image: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
    }
}
