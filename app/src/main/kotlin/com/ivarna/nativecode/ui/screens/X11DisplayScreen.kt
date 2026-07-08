package com.ivarna.nativecode.ui.screens

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.nativecode.core.termux.X11SessionManager
import kotlinx.coroutines.delay

private val X11Bg = Color(0xFF0A0E14)
private val X11Accent = Color(0xFF58A6FF)
private val X11Green = Color(0xFF3FB950)

/**
 * Screen shown when an X11 display server is detected.
 *
 * Launches [com.termux.x11.MainActivity] (merged into our APK from the library module)
 * as a separate Activity — preserving all of termux-x11's native rendering, input handling,
 * and OpenGL ES surface management without requiring direct LorieView embedding.
 *
 * When the user returns from X11 display, [X11SessionManager.resetX11State] is called so
 * the terminal can detect the next display server launch.
 */
@Composable
fun X11DisplayScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var launched by remember { mutableStateOf(false) }

    val alpha by animateFloatAsState(
        targetValue = if (launched) 0.6f else 1f,
        animationSpec = tween(600),
        label = "fade"
    )

    // Auto-launch X11 display immediately on composition
    LaunchedEffect(Unit) {
        delay(300) // brief pause for user to see the transition screen
        launchX11Activity(context)
        launched = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(X11Bg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .alpha(alpha)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Icon
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = X11Accent.copy(alpha = 0.15f),
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.DesktopWindows,
                        contentDescription = null,
                        tint = X11Accent,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }

            // Status
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "X11 Display Active",
                    color = X11Green,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Display server detected on :0\nLaunching X11 viewer...",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }

            if (!launched) {
                CircularProgressIndicator(
                    color = X11Accent,
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    "X11 window opened",
                    color = X11Green.copy(alpha = 0.8f),
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Re-open button (if user dismissed X11 window)
            OutlinedButton(
                onClick = { launchX11Activity(context) },
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(X11Accent.copy(alpha = 0.4f))
                )
            ) {
                Icon(Icons.Default.Launch, contentDescription = null, tint = X11Accent, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Re-open X11 Display", color = X11Accent, fontSize = 13.sp)
            }

            // Back to terminal
            TextButton(onClick = {
                X11SessionManager.resetX11State(context)
                onBack()
            }) {
                Text(
                    "← Back to Terminal",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 13.sp
                )
            }
        }
    }
}

private fun launchX11Activity(context: android.content.Context) {
    try {
        val intent = Intent(context, com.termux.x11.MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        android.util.Log.e("X11DisplayScreen", "Failed to launch X11 activity", e)
    }
}
