package com.ivarna.nativecode.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.nativecode.R

import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.HazeStyle

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun OnboardingScreen(
    onGetStarted: () -> Unit
) {
    val hazeState = androidx.compose.runtime.remember { HazeState() }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Background layer for blur
        Box(
            modifier = Modifier
                .fillMaxSize()
                .haze(state = hazeState)
        )

        // Footer Image
        Image(
            painter = painterResource(id = R.drawable.onboarding_bg_1),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            contentScale = ContentScale.FillWidth
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(80.dp))
            
            // Title
            Text(
                text = "NativeCode",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Subtitle
            Text(
                text = "Run Full Linux Distributions on Android",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha=0.7f),
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // Feature Cards
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FeatureCard(
                    hazeState = hazeState,
                    icon = "🐧",
                    title = "Multiple Distros",
                    description = "Debian, Ubuntu, Arch and more"
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                FeatureCard(
                    hazeState = hazeState,
                    icon = "🖥️",
                    title = "Full Desktop Environment",
                    description = "XFCE4 with complete GUI support"
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                FeatureCard(
                    hazeState = hazeState,
                    icon = "⚡",
                    title = "No Root Required",
                    description = "PRoot mode works on any device"
                )

                Spacer(modifier = Modifier.height(32.dp))
                
                // Get Started Button
                Button(
                    onClick = onGetStarted,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "Get Started",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun FeatureCard(
    hazeState: HazeState,
    icon: String,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .hazeChild(
                state = hazeState,
                style = HazeStyle(
                    backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    tint = null
                )
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        Text(
            text = icon,
            fontSize = 32.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(end = 16.dp)
        )
        
        Column {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
        }
    }
}
