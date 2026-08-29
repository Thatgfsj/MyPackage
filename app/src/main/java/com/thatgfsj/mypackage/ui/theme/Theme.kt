package com.thatgfsj.mypackage.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun MyPackageTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) {
        darkColorScheme(
            primary = DarkTeal,
            onPrimary = Color(0xFF00332C),
            primaryContainer = Color(0xFF0E4A41),
            onPrimaryContainer = Color(0xFFCFF5EE),
            secondary = DarkTeal,
            onSecondary = Color(0xFF00332C),
            background = DarkBg,
            onBackground = Color(0xFFECEDEE),
            surface = DarkCard,
            onSurface = Color(0xFFECEDEE),
            surfaceVariant = Color(0xFF262B30),
            onSurfaceVariant = Color(0xFFA8ADB2),
            outline = Color(0xFF3A4045),
            error = DangerRed
        )
    } else {
        lightColorScheme(
            primary = Teal,
            onPrimary = Color.White,
            primaryContainer = TealContainer,
            onPrimaryContainer = Color(0xFF00332C),
            secondary = TealDark,
            onSecondary = Color.White,
            background = AppBackground,
            onBackground = TextMain,
            surface = CardWhite,
            onSurface = TextMain,
            surfaceVariant = Color(0xFFEDF1F3),
            onSurfaceVariant = TextSub,
            outline = LineGray,
            error = DangerRed
        )
    }
    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content
    )
}
