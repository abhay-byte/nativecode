package com.ivarna.nativecode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

@Composable
fun GlassScaffold(
    hazeState: HazeState,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = topBar,
            content = { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .haze(state = hazeState)
                        .padding(top = paddingValues.calculateTopPadding())
                ) {
                    content()
                }
            }
        )
        
        // Overlay Floating Bottom Bar
        Box(
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.BottomCenter)
                .navigationBarsPadding()
        ) {
            bottomBar()
        }
    }
}
