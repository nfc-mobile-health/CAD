package com.example.cad.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val MapleScheme = lightColorScheme(
    primary = MapleColors.Pine, onPrimary = MapleColors.White,
    primaryContainer = MapleColors.Mist2, onPrimaryContainer = MapleColors.PineDeep,
    surfaceVariant = MapleColors.Mist, onSurfaceVariant = MapleColors.Moss,
    tertiary = MapleColors.Amber, onTertiary = MapleColors.Ink,
    tertiaryContainer = MapleColors.AmberTint, onTertiaryContainer = MapleColors.Ink,
    error = MapleColors.Red, onError = MapleColors.White,
    errorContainer = MapleColors.RedTint, onErrorContainer = MapleColors.Ink,
    background = MapleColors.Sage, onBackground = MapleColors.Ink,
    surface = MapleColors.White, onSurface = MapleColors.Ink, outline = MapleColors.Line,
)

@Composable
fun CADTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MapleScheme,
        typography = Typography,
        shapes = Shapes(
            small = RoundedCornerShape(16.dp), medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}
