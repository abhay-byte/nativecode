package com.ivarna.nativecode.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.nativecode.R
import com.ivarna.nativecode.core.data.Distro
import com.ivarna.nativecode.core.data.DistroComponent
import com.ivarna.nativecode.core.utils.StateManager
import com.ivarna.nativecode.ui.components.GlassScaffold
import com.ivarna.nativecode.ui.theme.FluxAccentCyan
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

data class IdeTool(
    val id: String,
    val name: String,
    val description: String,
    val command: String,
    val sizeEstimate: String,
    val accentColor: Color,
    val iconRes: Int?,
    val component: DistroComponent
)

val ideTools = listOf(
    IdeTool(
        id = "vscode",
        name = "VS Code",
        description = "Microsoft's lightweight but powerful code editor with rich extension ecosystem. Runs natively on ARM64 Linux.",
        command = "code",
        sizeEstimate = "~350 MB",
        accentColor = Color(0xFF007ACC),
        iconRes = null,
        component = DistroComponent(
            id = "ide_tools_vscode",
            name = "VS Code",
            description = "Install Visual Studio Code (ARM64 tarball).",
            scriptName = "common/setup_vscode_debian.sh",
            sizeEstimate = "~350 MB"
        )
    ),
    IdeTool(
        id = "cursor",
        name = "Cursor",
        description = "The AI-first code editor built on VS Code. Get intelligent code suggestions, multi-file edits, and natural-language coding right inside your editor.",
        command = "cursor",
        sizeEstimate = "~400 MB",
        accentColor = Color(0xFF6C5CE7),
        iconRes = null,
        component = DistroComponent(
            id = "ide_tools_cursor",
            name = "Cursor",
            description = "Install Cursor AI Code Editor (ARM64).",
            scriptName = "common/setup_cursor_debian.sh",
            sizeEstimate = "~400 MB"
        )
    ),
    IdeTool(
        id = "antigravity",
        name = "Antigravity",
        description = "AI-first code editor with intelligent code completion and natural language coding support. Optimized for ARM64 Linux.",
        command = "antigravity",
        sizeEstimate = "~150 MB",
        accentColor = Color(0xFFFF6B35),
        iconRes = null,
        component = DistroComponent(
            id = "ide_tools_antigravity",
            name = "Antigravity",
            description = "Install Antigravity AI Code Editor.",
            scriptName = "common/setup_antigravity_ide_debian.sh",
            sizeEstimate = "~150 MB"
        )
    ),
    IdeTool(
        id = "nexus",
        name = "Nexus",
        description = "NativeCode's own IDE — purpose-built for mobile Linux development. Deep integration with your NativeCode workspace.",
        command = "nexus",
        sizeEstimate = "~200 MB",
        accentColor = Color(0xFF00BFA5),
        iconRes = null,
        component = DistroComponent(
            id = "ide_tools_nexus",
            name = "Nexus IDE",
            description = "Install Nexus IDE (NativeCode's IDE).",
            scriptName = "common/setup_nexus_debian.sh",
            sizeEstimate = "~200 MB"
        )
    ),
    IdeTool(
        id = "windsurf",
        name = "Windsurf",
        description = "Codeium's AI-powered code editor. Cascade AI agent, intelligent completions, and natural language coding in a VS Code-based editor.",
        command = "windsurf",
        sizeEstimate = "~400 MB",
        accentColor = Color(0xFF00D4AA),
        iconRes = null,
        component = DistroComponent(
            id = "ide_tools_windsurf",
            name = "Windsurf",
            description = "Install Windsurf (Codeium) AI Code Editor.",
            scriptName = "common/setup_windsurf_debian.sh",
            sizeEstimate = "~400 MB"
        )
    )
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
@Composable
fun IdeToolsScreen(
    distro: Distro,
    onBack: () -> Unit,
    onInstallComponent: (DistroComponent, Map<String, String>) -> Unit,
    hazeState: HazeState
) {
    val context = LocalContext.current

    GlassScaffold(
        hazeState = hazeState,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "IDE & Code Editors",
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.hazeChild(
                    state = hazeState,
                    shape = androidx.compose.ui.graphics.RectangleShape,
                    style = HazeMaterials.thin()
                )
            )
        },
        content = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 24.dp, bottom = 48.dp)
            ) {
                item {
                    Column {
                        Text(
                            "Code Editors & IDEs",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "One-tap install for AI-powered code editors that run inside your Linux workspace.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                items(ideTools.size) { idx ->
                    val tool = ideTools[idx]
                    val isInstalled = remember(tool.id) {
                        StateManager.isComponentInstalled(context, distro.id, tool.component.id)
                    }
                    IdeToolCard(
                        tool = tool,
                        isInstalled = isInstalled,
                        onInstall = { onInstallComponent(tool.component, emptyMap()) }
                    )
                }
            }
        }
    )
}

@Composable
fun IdeToolCard(
    tool: IdeTool,
    isInstalled: Boolean,
    onInstall: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        tool.accentColor.copy(alpha = 0.10f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.30f)
                    )
                )
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        tool.accentColor.copy(alpha = 0.40f),
                        Color.Transparent
                    )
                ),
                RoundedCornerShape(20.dp)
            )
            .padding(20.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (tool.iconRes != null) {
                        Icon(
                            painter = painterResource(id = tool.iconRes),
                            contentDescription = tool.name,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(36.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Laptop,
                            contentDescription = tool.name,
                            tint = tool.accentColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        tool.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .background(tool.accentColor.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                            .border(1.dp, tool.accentColor.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "$ ${tool.command}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = tool.accentColor
                        )
                    }
                }

                if (isInstalled) {
                    Box(
                        modifier = Modifier
                            .background(
                                Color(0xFF1B5E20).copy(alpha = 0.5f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF81C784),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Installed",
                                fontSize = 10.sp,
                                color = Color(0xFF81C784),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                tool.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Size: ${tool.sizeEstimate}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onInstall,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isInstalled)
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    else
                        tool.accentColor,
                    contentColor = if (isInstalled)
                        MaterialTheme.colorScheme.onSurface
                    else
                        Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                elevation = ButtonDefaults.buttonElevation(0.dp)
            ) {
                Text(
                    if (isInstalled) "Re-run Install" else "Install",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}