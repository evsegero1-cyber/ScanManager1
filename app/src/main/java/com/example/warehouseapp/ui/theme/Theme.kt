package com.example.warehouseapp.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.warehouseapp.data.AppSettings
import com.example.warehouseapp.data.ThemeMode

val AccentPresets = listOf(
    0xFF1E5BD8 to "Синий",
    0xFF0E9F6E to "Зелёный",
    0xFFF06A00 to "Оранжевый",
    0xFF7C4DFF to "Фиолетовый",
    0xFFE91E63 to "Розовый",
    0xFF00A3BF to "Бирюзовый",
)

private fun onColorFor(c: Color): Color =
    if (0.299 * c.red + 0.587 * c.green + 0.114 * c.blue > 0.6) Color(0xFF111318) else Color.White

private fun lightScheme(seed: Color): ColorScheme {
    val neutralBg = Color(0xFFF6F7FB)
    return lightColorScheme(
        primary = seed,
        onPrimary = onColorFor(seed),
        primaryContainer = lerp(seed, Color.White, 0.82f),
        onPrimaryContainer = lerp(seed, Color.Black, 0.55f),
        secondary = lerp(seed, Color(0xFF5B6170), 0.55f),
        secondaryContainer = lerp(seed, Color.White, 0.88f),
        onSecondaryContainer = lerp(seed, Color.Black, 0.6f),
        tertiary = Color(0xFFE59E00),
        tertiaryContainer = Color(0xFFFFEFC7),
        onTertiaryContainer = Color(0xFF3D2A00),
        background = neutralBg,
        surface = neutralBg,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFFFFFFF),
        surfaceContainer = lerp(Color(0xFFF0F2F7), seed, 0.03f),
        surfaceContainerHigh = lerp(Color(0xFFE9ECF3), seed, 0.04f),
        surfaceContainerHighest = lerp(Color(0xFFE2E6EE), seed, 0.05f),
        outlineVariant = Color(0xFFDDE1EA),
    )
}

private fun darkScheme(seed: Color): ColorScheme {
    val p = lerp(seed, Color.White, 0.25f)
    val bg = Color(0xFF101217)
    return darkColorScheme(
        primary = p,
        onPrimary = onColorFor(p),
        primaryContainer = lerp(seed, Color.Black, 0.45f),
        onPrimaryContainer = lerp(seed, Color.White, 0.8f),
        secondary = lerp(seed, Color(0xFFB8BDCB), 0.6f),
        secondaryContainer = lerp(seed, Color(0xFF22252D), 0.75f),
        onSecondaryContainer = lerp(seed, Color.White, 0.85f),
        tertiary = Color(0xFFFFC94D),
        tertiaryContainer = Color(0xFF4A3600),
        onTertiaryContainer = Color(0xFFFFE6A8),
        background = bg,
        surface = bg,
        surfaceContainerLowest = Color(0xFF0B0D11),
        surfaceContainerLow = Color(0xFF171A20),
        surfaceContainer = Color(0xFF1B1E25),
        surfaceContainerHigh = Color(0xFF22262E),
        surfaceContainerHighest = Color(0xFF2B2F38),
        outlineVariant = Color(0xFF353A45),
    )
}

private val AppTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

val MonoCell = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 0.5.sp)

@Composable
fun ScanTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val ctx = LocalContext.current
    val scheme = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkScheme(Color(settings.accent))
        else -> lightScheme(Color(settings.accent))
    }
    MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = Shapes(), content = content)
}
