package com.daybreak.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Score band colors, paired with a non-color label/icon for accessibility (PRD §9). */
val ScoreGreen = Color(0xFF2E7D32)
val ScoreAmber = Color(0xFFF9A825)
val ScoreRed = Color(0xFFC62828)

private val LightColors = lightColorScheme(primary = ScoreGreen)
private val DarkColors = darkColorScheme(primary = ScoreGreen)

@Composable
fun DaybreakTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
