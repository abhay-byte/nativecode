package com.ivarna.nativecode.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.SmartToy
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
import com.ivarna.nativecode.core.utils.StateManager
import com.ivarna.nativecode.ui.theme.FluxAccentCyan
import com.ivarna.nativecode.ui.theme.FluxAccentMagenta
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay

data class InstalledTool(
    val id: String,
    val name: String,
    val command: String,
    val type: ToolType, // AI or IDE
    val accentColor: Color,
    val distroId: String
)

enum class ToolType { AI, IDE }

@Composable
fun ProjectsScreen(
    hazeState: HazeState,
    onBack: (() -> Unit)? = null,
    onLaunchTool: (InstalledTool, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    var projectPaths by remember { mutableStateOf(StateManager.getProjectPaths(context).toList()) }

    // Agent selection dialog state
    var showAgentDialog by remember { mutableStateOf(false) }
    var selectedProjectPath by remember { mutableStateOf("") }

    // Gather installed tools across all distros
    val installedTools by remember {
        derivedStateOf {
            val tools = mutableListOf<InstalledTool>()
            val installedDistros = StateManager.getInstalledDistros(context)

            for (distroId in installedDistros) {
                // Check AI tools
                for (tool in aiTools) {
                    if (StateManager.isComponentInstalled(context, distroId, tool.component.id)) {
                        tools.add(
                            InstalledTool(
                                id = tool.id,
                                name = tool.name,
                                command = tool.command,
                                type = ToolType.AI,
                                accentColor = tool.accentColor,
                                distroId = distroId
                            )
                        )
                    }
                }
                // Check IDE tools
                for (tool in ideTools) {
                    if (StateManager.isComponentInstalled(context, distroId, tool.component.id)) {
                        tools.add(
                            InstalledTool(
                                id = tool.id,
                                name = tool.name,
                                command = tool.command,
                                type = ToolType.IDE,
                                accentColor = tool.accentColor,
                                distroId = distroId
                            )
                        )
                    }
                }
            }
            tools
        }
    }

    // Launcher for selecting a directory
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            val path = convertUriToLinuxPath(uri)
            if (path != null) {
                if (!projectPaths.contains(path)) {
                    StateManager.addProjectPath(context, path)
                    projectPaths = StateManager.getProjectPaths(context).toList()
                }
            } else {
                android.widget.Toast.makeText(
                    context,
                    "Could not map selected folder to /sdcard/",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 100.dp)
    ) {
        // ── Header ─────────────────────────────────────────────────
        item {
            Column {
                Text(
                    "Linked Folders",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Select folders to expose their exact Linux path for Termux and AI tools.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ── Add Project Crystal Button ─────────────────────────────
        item {
            CrystalButton(
                onClick = { launcher.launch(null) },
                text = "Select or Create Folder",
                icon = Icons.Default.Add
            )
        }

        // ── Empty State ────────────────────────────────────────────
        if (projectPaths.isEmpty()) {
            item {
                Spacer(modifier = Modifier.height(32.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyProjectsState()
                }
            }
        } else {
            // ── Project Cards ──────────────────────────────────────
            itemsIndexed(projectPaths) { index, path ->
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    delay(index * 60L)
                    visible = true
                }
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn() + scaleIn(initialScale = 0.92f)
                ) {
                    ProjectGlassCard(
                        path = path,
                        onClick = {
                            selectedProjectPath = path
                            showAgentDialog = true
                        },
                        onDelete = {
                            StateManager.removeProjectPath(context, path)
                            projectPaths = StateManager.getProjectPaths(context).toList()
                        }
                    )
                }
            }
        }
    }

    // ── Agent Selection Dialog ───────────────────────────────────
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
                val clip = android.content.ClipData.newPlainText("Project Path", selectedProjectPath)
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                clipboard.setPrimaryClip(clip)
                android.widget.Toast.makeText(context, "Path copied!", android.widget.Toast.LENGTH_SHORT).show()
                showAgentDialog = false
            },
            onRemove = {
                StateManager.removeProjectPath(context, selectedProjectPath)
                projectPaths = StateManager.getProjectPaths(context).toList()
                showAgentDialog = false
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Crystal Button — glassmorphism styled add button
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun CrystalButton(
    onClick: () -> Unit,
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        FluxAccentCyan.copy(alpha = 0.18f),
                        FluxAccentMagenta.copy(alpha = 0.08f)
                    )
                )
            )
            .border(
                width = 1.5.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.25f),
                        Color.White.copy(alpha = 0.05f)
                    )
                ),
                shape = RoundedCornerShape(28.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // Subtle glow behind
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            FluxAccentCyan.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = FluxAccentCyan,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = text,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Glass Project Card
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ProjectGlassCard(path: String, onClick: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val folderName = path.substringAfterLast("/").takeIf { it.isNotEmpty() } ?: "Root"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.10f),
                        Color.White.copy(alpha = 0.03f)
                    )
                )
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.20f),
                        Color.White.copy(alpha = 0.05f)
                    )
                ),
                RoundedCornerShape(24.dp)
            )
            .clickable(onClick = onClick)
    ) {
        // Top glow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            FluxAccentCyan.copy(alpha = 0.06f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // ── Top row: icon + name + delete ──────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Folder icon with glass bg
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    FluxAccentCyan.copy(alpha = 0.15f),
                                    FluxAccentMagenta.copy(alpha = 0.08f)
                                )
                            )
                        )
                        .border(
                            1.dp,
                            FluxAccentCyan.copy(alpha = 0.20f),
                            RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = FluxAccentCyan,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = folderName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // Path chip — glass code aesthetic
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF1A1A2E).copy(alpha = 0.8f),
                                        Color(0xFF16213E).copy(alpha = 0.6f)
                                    )
                                )
                            )
                            .border(
                                1.dp,
                                Color.White.copy(alpha = 0.08f),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                val clip = android.content.ClipData.newPlainText("Project Path", path)
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                        as android.content.ClipboardManager
                                clipboard.setPrimaryClip(clip)
                                android.widget.Toast.makeText(context, "Path copied!", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                tint = Color(0xFF81C784),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = path,
                                color = Color(0xFFA5D6A7),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // Delete button
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFF5252).copy(alpha = 0.10f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove",
                        tint = Color(0xFFFF5252).copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty State — glass styled illustration
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun EmptyProjectsState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(32.dp)
    ) {
        // Glass orb illustration
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            FluxAccentCyan.copy(alpha = 0.12f),
                            FluxAccentMagenta.copy(alpha = 0.06f)
                        )
                    )
                )
                .border(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    ),
                    RoundedCornerShape(32.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                modifier = Modifier.size(56.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "No projects linked yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Tap the button above to select a folder.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Agent Selection Dialog — glassmorphism bottom sheet style
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AgentSelectionDialog(
    projectPath: String,
    installedTools: List<InstalledTool>,
    onDismiss: () -> Unit,
    onLaunchTool: (InstalledTool) -> Unit,
    onCopyPath: () -> Unit,
    onRemove: () -> Unit
) {
    val folderName = projectPath.substringAfterLast("/").takeIf { it.isNotEmpty() } ?: "Root"

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Dialog content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF1E1E1E).copy(alpha = 0.98f),
                                Color(0xFF121212).copy(alpha = 0.99f)
                            )
                        )
                    )
                    .border(
                        1.dp,
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.15f),
                                Color.Transparent
                            )
                        ),
                        RoundedCornerShape(28.dp)
                    )
                    .padding(24.dp)
                    .clickable(enabled = false) { /* consume clicks */ }
            ) {
                // Header
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        FluxAccentCyan.copy(alpha = 0.15f),
                                        FluxAccentMagenta.copy(alpha = 0.08f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = FluxAccentCyan,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = folderName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = projectPath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))

                Spacer(modifier = Modifier.height(16.dp))

                // ── Installed Tools Section ─────────────────────────────
                if (installedTools.isNotEmpty()) {
                    Text(
                        text = "Open With",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )

                    installedTools.forEach { tool ->
                        val icon = when (tool.type) {
                            ToolType.AI -> Icons.Default.SmartToy
                            ToolType.IDE -> Icons.Default.Laptop
                        }
                        AgentActionButton(
                            icon = icon,
                            iconTint = tool.accentColor,
                            label = tool.name,
                            description = when (tool.type) {
                                ToolType.AI -> "Terminal AI agent — ${tool.command}"
                                ToolType.IDE -> "Code editor — ${tool.command}"
                            },
                            onClick = { onLaunchTool(tool) }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    // No tools installed — show a helpful hint
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.03f))
                            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "No AI or IDE tools installed yet.\nInstall them from Distro Settings → AI Tools / IDE & Code Editors.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Secondary: Copy Path
                AgentActionButton(
                    icon = Icons.Default.ContentCopy,
                    iconTint = FluxAccentCyan,
                    label = "Copy Linux Path",
                    description = "Copy to clipboard for Termux",
                    onClick = onCopyPath
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Destructive: Remove
                AgentActionButton(
                    icon = Icons.Default.Delete,
                    iconTint = Color(0xFFFF5252),
                    label = "Remove Project",
                    description = "Unlink this folder",
                    onClick = onRemove
                )

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Cancel",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun AgentActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    label: String,
    description: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Parses an Android SAF DocumentTree URI into a standard Linux path
 */
fun convertUriToLinuxPath(uri: Uri): String? {
    val defaultStoragePrefix = "/sdcard/"

    val decodedPath = Uri.decode(uri.toString())

    // Look for the primary storage marker in SAF URIs
    // e.g. content://com.android.externalstorage.documents/tree/primary:Projects/test
    val primaryMarker = "tree/primary:"

    if (decodedPath.contains(primaryMarker)) {
        val relativePath = decodedPath.substringAfter(primaryMarker)
        return defaultStoragePrefix + relativePath
    }

    return null
}
