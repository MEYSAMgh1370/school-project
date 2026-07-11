package com.roshdeandishe.student.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// v1: پالت پیش‌فرض Material3، بدون برندینگ سفارشی - رجوع به این فایل وقتی
// design system واقعی برای اپ موبایل تصمیم‌گیری شد.
private val LightColors = lightColorScheme()
private val DarkColors = darkColorScheme()

@Composable
fun RoshdeandisheTheme(content: @Composable () -> Unit) {
    val colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colorScheme, content = content)
}
