package com.burakkarakas.smsfilter.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppColors = darkColorScheme(
    primary = Color(0xFF2F8CFF),
    onPrimary = Color.White,
    background = Color.Black,
    onBackground = Color(0xFFF2F2F2),
    surface = Color.Black,
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF1C222B),
    onSurfaceVariant = Color(0xFF9AA4B2),
    error = Color(0xFFFF5A5F),
)

@Composable
fun SmsFilterTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AppColors, content = content)
}
