package com.ivarna.nativecode.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PlayArrow
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
import com.ivarna.nativecode.core.data.DistroRepository
import com.ivarna.nativecode.core.data.Distro
import com.ivarna.nativecode.core.data.ScriptManager
import com.ivarna.nativecode.core.data.TermuxIntentFactory

import com.ivarna.nativecode.core.utils.StateManager
import com.ivarna.nativecode.ui.theme.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlinx.coroutines.launch

package com.ivarna.nativecode.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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
import com.ivarna.nativecode.core.utils.StateManager
import com.ivarna.nativecode.ui.theme.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    onLaunchTool: (InstalledTool, String) -> Unit,
    onInstallComponent: (DistroComponent, Map<String, String>) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val refreshKey = remember { mutableStateOf(0) }

    // Sub-screen visibility
    var showAiToolsScreen by remember { mutableStateOf(false) }
    var showIdeToolsScreen by remember { mutableStateOf(false) }
    
    // Config Dialog States
    var showThemeDialog by remember { mutableStateOf(false) }
    var showGpuDialog by remember { mutableStateOf(false) }
    var selectedTheme by remember { mutableStateOf("dark") }
    var selectedGpu by remember { mutableStateOf("auto") }

    // Project state
    var projectPaths by remember { mutableStateOf(StateManager.getProjectPaths(context).toList()) }
    var showAgentDialog by remember { mutableStateOf(false) }
    var selectedProjectPath by remember { mutableStateOf("") }

    // Primary Debian Distro
    val debianDistro = remember { DistroRepository.supportedDistros.find { it.id == "debian" }!! }
    val isInstalled = remember(refreshKey.value, scriptRefreshTrigger) {
        StateManager.isDistroInstalled(context, debianDistro.id)
    }
    
    // React to refresh
    LaunchedEffect(scriptRefreshTrigger) {
        if (scriptRefreshTrigger > 0) {
            refreshKey.value++
            projectPaths = StateManager.getProjectPaths(context).toList()
        }
    }

    // Gather installed tools
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

    // SAF Directory Picker
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            val path = convertUriToLinuxPath(uri)
            if (path != null) {
                if (!projectPaths.contains(path)) {
                    StateManager.addProjectPath(context, path)
                    projectPaths = StateManager.getProjectPaths(context).toList()
                }
            }
        }
    }

    // ── Overlays (AI/IDE Screens) ───────────────────────────────────
    if (showAiToolsScreen) {
        AiToolsScreen(
            distro = debianDistro,
            onBack = { showAiToolsScreen = false },
            onInstallComponent = onInstallComponent,
            hazeState = hazeState
        )
        return
    }

    if (showIdeToolsScreen) {
        IdeToolsScreen(
            distro = debianDistro,
            onBack = { showIdeToolsScreen = false },
            onInstallComponent = onInstallComponent,
            hazeState = hazeState
        )
        return
    }

    // ── Main Layout ────────────────────────────────────────────────
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 48.dp)
    ) {
        // ── 1. Debian Hero Card ──────────────────────────────────────
        item {
            DebianHeroCard(
                distro = debianDistro,
                isInstalled = isInstalled,
                isGuiRunning = StateManager.isGuiRunning(context, debianDistro.id),
                guiRunningType = StateManager.getGuiRunningType(context, debianDistro.id),
                kdeInstalled = StateManager.isComponentInstalled(context, debianDistro.id, "kde_plasma"),
                onInstall = { onNavigateToInstall(debianDistro) },
                onLaunchCli = {
                    if (permissionState.status.isGranted) {
                        onStartService(TermuxIntentFactory.buildLaunchCliIntent(debianDistro.id))
                    } else permissionState.launchPermissionRequest()
                },
                onLaunchXfce = {
                    if (permissionState.status.isGranted) {
                        onStartService(TermuxIntentFactory.buildLaunchGuiIntent(debianDistro.id))
                        StateManager.setGuiRunning(context, debianDistro.id, true)
                        StateManager.setGuiRunningType(context, debianDistro.id, "xfce4")
                        val x11Intent = context.packageManager.getLaunchIntentForPackage("com.termux.x11")
                        if (x11Intent != null) {
                            context.startActivity(x11Intent)
                            com.ivarna.nativecode.core.utils.TermuxX11Preferences.applyToTermux(context)
                        }
                    } else permissionState.launchPermissionRequest()
                },
                onLaunchKde = {
                    if (permissionState.status.isGranted) {
                        onStartService(TermuxIntentFactory.buildLaunchKdeGuiIntent(context, debianDistro.id))
                        StateManager.setGuiRunning(context, debianDistro.id, true)
                        StateManager.setGuiRunningType(context, debianDistro.id, "kde")
                        val x11Intent = context.packageManager.getLaunchIntentForPackage("com.termux.x11")
                        if (x11Intent != null) {
                            context.startActivity(x11Intent)
                            com.ivarna.nativecode.core.utils.TermuxX11Preferences.applyToTermux(context)
                        }
                    } else permissionState.launchPermissionRequest()
                },
                onStop = {
                    val runningType = StateManager.getGuiRunningType(context, debianDistro.id)
                    val intent = if (runningType == "kde") TermuxIntentFactory.buildStopKdeGuiIntent(debianDistro.id)
                                 else TermuxIntentFactory.buildStopGuiIntent(debianDistro.id)
                    onStartService(intent)
                    StateManager.setGuiRunning(context, debianDistro.id, false)
                    StateManager.setGuiRunningType(context, debianDistro.id, "")
                },
                onOpenX11 = {
                    val x11Intent = context.packageManager.getLaunchIntentForPackage("com.termux.x11")
                    if (x11Intent != null) context.startActivity(x11Intent)
                },
                onSettings = { onNavigateToSettings(debianDistro) }
            )
        }

        if (isInstalled) {
            // ── 2. AI & IDE Tools Banners ────────────────────────────
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ToolBanner(
                        modifier = Modifier.weight(1f),
                        title = "AI Agents",
                        description = "Codex, Claude...",
                        icon = Icons.Default.SmartToy,
                        color = Color(0xFF10A37F),
                        onClick = { showAiToolsScreen = true }
                    )
                    ToolBanner(
                        modifier = Modifier.weight(1f),
                        title = "Code Editors",
                        description = "VS Code, Cursor...",
                        icon = Icons.Default.Laptop,
                        color = Color(0xFF007ACC),
                        onClick = { showIdeToolsScreen = true }
                    )
                }
            }

            // ── 3. Projects ───────────────────────────────────────────
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Workspace Projects",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        TextButton(onClick = { launcher.launch(null) }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Folder", fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    if (projectPaths.isEmpty()) {
                        EmptyProjectsState()
                    } else {
                        projectPaths.forEachIndexed { index, path ->
                            ProjectGlassCard(
                                path = path,
                                onClick = { selectedProjectPath = path; showAgentDialog = true },
                                onDelete = {
                                    StateManager.removeProjectPath(context, path)
                                    projectPaths = StateManager.getProjectPaths(context).toList()
                                }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }

            // ── 4. Configuration ─────────────────────────────────────
            item {
                Column {
                    Text(
                        "System Configuration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ConfigCard(
                            modifier = Modifier.weight(1f),
                            title = "Interface Theme",
                            value = "XFCE / KDE",
                            icon = Icons.Default.Palette,
                            onClick = { showThemeDialog = true }
                        )
                        ConfigCard(
                            modifier = Modifier.weight(1f),
                            title = "Graphics Engine",
                            value = StateManager.getHardwareAccelType(context, debianDistro.id).uppercase(),
                            icon = Icons.Default.Speed,
                            onClick = { showGpuDialog = true }
                        )
                    }
                }
            }
        }
        
        item { Spacer(modifier = Modifier.height(40.dp)) }
    }

    // ── Dialogs ──────────────────────────────────────────────────
    if (showAgentDialog) {
        AgentSelectionDialog(
            projectPath = selectedProjectPath,
            installedTools = installedTools,
            onDismiss = { showAgentDialog = false },
            onLaunchTool = { tool ->
                showAgentDialog = false
                onLaunchTool(tool, selectedProjectPath)
            },
            onCopyPath = {
                val clip = ClipData.newPlainText("Project Path", selectedProjectPath)
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                showAgentDialog = false
            },
            onRemove = {
                StateManager.removeProjectPath(context, selectedProjectPath)
                projectPaths = StateManager.getProjectPaths(context).toList()
                showAgentDialog = false
            }
        )
    }

    // ── Configuration Dialogs ─────────────────────────────────────
    
    if (showThemeDialog) {
        GlassDialog(onDismiss = { showThemeDialog = false }) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Customize Desktop", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.height(16.dp))
                
                SettingsThemeOption(name = "Dark Mode (Default)", desc = "Sleek and professional.", id = "dark", selected = selectedTheme == "dark", onSelect = { selectedTheme = "dark" })
                SettingsThemeOption(name = "Light Mode", desc = "Clean and bright.", id = "light", selected = selectedTheme == "light", onSelect = { selectedTheme = "light" })
                
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        showThemeDialog = false
                        // We need the 'customization' component for Debian
                        val component = debianDistro.components.find { it.id == "customization" }
                        if (component != null) {
                            onInstallComponent(component, mapOf("FLUX_THEME" to selectedTheme))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Apply Theme")
                }
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
                Button(
                    onClick = {
                        showGpuDialog = false
                        val component = debianDistro.components.find { it.id == "hw_accel" }
                        if (component != null) {
                            onInstallComponent(component, mapOf("FLUX_GPU" to selectedGpu))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Apply Configuration")
                }
            }
        }
    }
}

@Composable
fun GlassDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF1A1A1A))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(28.dp))
        ) {
            content()
        }
    }
}

@Composable
fun SettingsThemeOption(name: String, desc: String, id: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onSelect() }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect, colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.secondary))
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(name, fontWeight = FontWeight.SemiBold, color = if(selected) MaterialTheme.colorScheme.secondary else Color.White)
            Text(desc, fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
        }
    }
}

@Composable
fun DebianHeroCard(
    distro: Distro,
    isInstalled: Boolean,
    isGuiRunning: Boolean,
    guiRunningType: String,
    kdeInstalled: Boolean,
    onInstall: () -> Unit,
    onLaunchCli: () -> Unit,
    onLaunchXfce: () -> Unit,
    onLaunchKde: () -> Unit,
    onStop: () -> Unit,
    onOpenX11: () -> Unit,
    onSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFE91E63).copy(alpha = 0.15f),
                        Color(0xFF121212).copy(alpha = 0.6f)
                    )
                )
            )
            .border(
                1.dp,
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.25f), Color.Transparent)),
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
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(painter = painterResource(id = R.drawable.distro_debian), contentDescription = null, modifier = Modifier.size(42.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Debian GNU/Linux", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(if (isInstalled) Color(0xFF4CAF50) else Color(0xFFFF9800)))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isInstalled) "Installed & Ready" else "Not Installed", fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f))
                    }
                }
                IconButton(onClick = onSettings, modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.05f))) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White.copy(alpha = 0.7f))
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            if (!isInstalled) {
                Button(
                    onClick = onInstall,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Install Debian System", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LaunchButton(modifier = Modifier.weight(1f), label = "Terminal", icon = Icons.Default.Code, color = Color(0xFF424242), onClick = onLaunchCli)
                    LaunchButton(modifier = Modifier.weight(1f), label = "XFCE4", icon = Icons.Default.DesktopWindows, color = Color(0xFF4A148C), onClick = onLaunchXfce)
                    LaunchButton(modifier = Modifier.weight(1f), label = "KDE", icon = Icons.Default.Waves, color = Color(0xFF1A237E), onClick = onLaunchKde, enabled = kdeInstalled)
                }
                
                if (isGuiRunning) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = onOpenX11,
                            modifier = Modifier.weight(1.5f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.2f), contentColor = Color(0xFF81C784)),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.4f))
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open ${guiRunningType.uppercase()}", fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = onStop,
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252).copy(alpha = 0.2f), contentColor = Color(0xFFFF5252)),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.4f))
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
fun LaunchButton(modifier: Modifier, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(64.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (enabled) color.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.05f),
            contentColor = if (enabled) Color.White else Color.White.copy(alpha = 0.2f)
        ),
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ToolBanner(modifier: Modifier, title: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.1f))
            .border(1.dp, color.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Column {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
            Text(description, fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
        }
    }
}

@Composable
fun ConfigCard(modifier: Modifier, title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = FluxAccentCyan, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(title, fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

// ── Project Components ──────────────────────────────────────────────

@Composable
fun ProjectGlassCard(path: String, onClick: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val folderName = path.substringAfterLast("/").takeIf { it.isNotEmpty() } ?: "Root"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(FluxAccentCyan.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = FluxAccentCyan, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(folderName, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                Text(path, fontSize = 11.sp, color = Color.White.copy(alpha = 0.4f), fontFamily = FontFamily.Monospace)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF5252).copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
fun EmptyProjectsState() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(24.dp)) {
        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color.White.copy(alpha = 0.1f), modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text("No projects linked", color = Color.White.copy(alpha = 0.3f), fontSize = 14.sp)
    }
}

@Composable
fun AgentSelectionDialog(
    projectPath: String,
    installedTools: List<InstalledTool>,
    onDismiss: () -> Unit,
    onLaunchTool: (InstalledTool) -> Unit,
    onCopyPath: () -> Unit,
    onRemove: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier.fillMaxWidth(0.95f).clip(RoundedCornerShape(28.dp)).background(Color(0xFF1A1A1A)).border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(28.dp)).padding(24.dp)
        ) {
            Column {
                Text("Project Options", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                Text(projectPath, fontSize = 12.sp, color = Color.White.copy(alpha = 0.4f), modifier = Modifier.padding(bottom = 24.dp))

                if (installedTools.isNotEmpty()) {
                    Text("OPEN WITH", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = FluxAccentCyan, modifier = Modifier.padding(bottom = 8.dp))
                    installedTools.forEach { tool ->
                        AgentActionButton(
                            label = tool.name, 
                            icon = if (tool.type == ToolType.AI) Icons.Default.SmartToy else Icons.Default.Laptop,
                            color = tool.accentColor,
                            onClick = { onLaunchTool(tool) }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                AgentActionButton(label = "Copy Path", icon = Icons.Default.ContentCopy, color = Color.White.copy(alpha = 0.6f), onClick = onCopyPath)
                Spacer(modifier = Modifier.height(10.dp))
                AgentActionButton(label = "Remove Project", icon = Icons.Default.Delete, color = Color(0xFFFF5252), onClick = onRemove)
                
                Spacer(modifier = Modifier.height(24.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Close", color = Color.White.copy(alpha = 0.6f))
                }
            }
        }
    }
}

@Composable
fun AgentActionButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.1f)).border(1.dp, color.copy(alpha = 0.2f), RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
    }
}

fun convertUriToLinuxPath(uri: Uri): String? {
    val decodedPath = Uri.decode(uri.toString())
    val marker = "tree/primary:"
    return if (decodedPath.contains(marker)) "/sdcard/" + decodedPath.substringAfter(marker) else null
}


