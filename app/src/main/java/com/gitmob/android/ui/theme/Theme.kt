package com.gitmob.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary          = Coral,
    onPrimary        = Color.White,
    primaryContainer = CoralDim,
    secondary        = BlueColor,
    background       = BgDeep,
    surface          = BgCard,
    surfaceVariant   = BgItem,
    onBackground     = TextPrimary,
    onSurface        = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outline          = Border,
    outlineVariant   = Border2,
    error            = RedColor,
)

@Composable
fun GitMobTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content,
    )
}
