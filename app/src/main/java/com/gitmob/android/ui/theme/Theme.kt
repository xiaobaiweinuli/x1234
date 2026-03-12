package com.gitmob.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.gitmob.android.auth.ThemeMode

// ── 深色方案（原有配色） ──
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

// ── 浅色方案（默认） ──
private val LightColorScheme = lightColorScheme(
    primary          = Coral,
    onPrimary        = Color.White,
    primaryContainer = Color(0xFFFFE5DF),
    secondary        = Color(0xFF1565C0),
    background       = Color(0xFFF8F9FC),
    surface          = Color(0xFFFFFFFF),
    surfaceVariant   = Color(0xFFF1F3F8),
    onBackground     = Color(0xFF0F1117),
    onSurface        = Color(0xFF1A1D27),
    onSurfaceVariant = Color(0xFF5C6475),
    outline          = Color(0xFFDDE1EC),
    outlineVariant   = Color(0xFFC8CDDE),
    error            = Color(0xFFD32F2F),
)

@Composable
fun GitMobTheme(
    themeMode: ThemeMode = ThemeMode.LIGHT,
    content: @Composable () -> Unit,
) {
    val useDark = when (themeMode) {
        ThemeMode.LIGHT  -> false
        ThemeMode.DARK   -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (useDark) DarkColorScheme else LightColorScheme,
        typography  = Typography,
        content     = content,
    )
}
