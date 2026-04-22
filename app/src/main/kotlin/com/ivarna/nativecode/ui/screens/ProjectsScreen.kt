package com.ivarna.nativecode.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.nativecode.core.utils.StateManager
import dev.chrisbanes.haze.HazeState

@Composable
fun ProjectsScreen(hazeState: HazeState) {
    val context = LocalContext.current
    var projectPaths by remember { mutableStateOf(StateManager.getProjectPaths(context).toList()) }

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
                android.widget.Toast.makeText(context, "Could not map selected folder to /sdcard/", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Title
        Text(
            text = "Your Projects",
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.align(Alignment.Start)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Select folders to expose their exact Linux path for Termux and AI tools.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Create / Add Project Button
        Button(
            onClick = { launcher.launch(null) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary
            ),
            shape = RoundedCornerShape(16.dp),
            elevation = ButtonDefaults.buttonElevation(0.dp)
        ) {
            Icon(Icons.Default.CreateNewFolder, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Select or Create Folder",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (projectPaths.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No projects linked yet.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(projectPaths) { path ->
                    ProjectCard(
                        path = path,
                        onDelete = {
                            StateManager.removeProjectPath(context, path)
                            projectPaths = StateManager.getProjectPaths(context).toList()
                        }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(100.dp)) // Spacing for Bottom Nav
                }
            }
        }
    }
}

@Composable
fun ProjectCard(path: String, onDelete: () -> Unit) {
    val context = LocalContext.current
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                val folderName = path.substringAfterLast("/")
                Text(
                    text = if (folderName.isNotEmpty()) folderName else "Root",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                // Path string pill
                Box(
                    modifier = Modifier
                        .clickable {
                            val clip = android.content.ClipData.newPlainText("Project Path", path)
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(clip)
                            android.widget.Toast.makeText(context, "Path copied!", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        .background(Color(0xFF1E1E1E), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = path,
                        color = Color(0xFFA5D6A7),
                        fontSize = 11.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }
            
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }
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
