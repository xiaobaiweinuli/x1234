package com.gitmob.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.gitmob.android.data.ThemeMode

// ── 深色方案 ──
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

// ── 浅色方案 ──
private val LightColorScheme = lightColorScheme(
    primary          = Coral,
    onPrimary        = Color.White,
    primaryContainer = Color(0xFFFFE8E3),
    secondary        = Color(0xFF2563EB),
    background       = Color(0xFFF5F7FA),
    surface          = Color.White,
    surfaceVariant   = Color(0xFFEEF2F7),
    onBackground     = Color(0xFF0F1117),
    onSurface        = Color(0xFF1A1D2E),
    onSurfaceVariant = Color(0xFF4B5563),
    outline          = Color(0xFFD1D9E6),
    outlineVariant   = Color(0xFFB8C4D8),
    error            = Color(0xFFDC2626),
)

@Composable
fun GitMobTheme(
    themeMode: ThemeMode = ThemeMode.LIGHT,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.DARK   -> true
        ThemeMode.LIGHT  -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColorScheme else LightColorScheme,
        typography  = Typography,
        content     = content,
    )
}
