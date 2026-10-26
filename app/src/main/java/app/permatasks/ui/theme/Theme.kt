package app.permatasks.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.permatasks.container

data class Palette(val key: String, val name: String, val seed: Color, val secondary: Color, val tertiary: Color)

val Palettes = listOf(
    Palette("indigo", "Indigo", Color(0xFF4C5BD4), Color(0xFF5B5D72), Color(0xFF8B4A8C)),
    Palette("ocean", "Ocean", Color(0xFF00658E), Color(0xFF4E616D), Color(0xFF615A7D)),
    Palette("forest", "Forest", Color(0xFF2E6C39), Color(0xFF52634F), Color(0xFF39656B)),
    Palette("sunset", "Sunset", Color(0xFFB4441D), Color(0xFF77574C), Color(0xFF6C5D2F)),
    Palette("graphite", "Graphite", Color(0xFF3F5A6B), Color(0xFF575E66), Color(0xFF6A5778)),
)

private fun mix(a: Color, b: Color, t: Float) = lerp(a, b, t)

/** A flat, low-contrast-surface scheme built from a seed, in the spirit of Material 3 tonal palettes. */
private fun schemeFor(p: Palette, dark: Boolean): ColorScheme {
    val white = Color.White
    val black = Color(0xFF111318)
    return if (!dark) {
        lightColorScheme(
            primary = p.seed,
            onPrimary = white,
            primaryContainer = mix(p.seed, white, 0.82f),
            onPrimaryContainer = mix(p.seed, black, 0.6f),
            secondary = p.secondary,
            onSecondary = white,
            secondaryContainer = mix(p.secondary, white, 0.84f),
            onSecondaryContainer = mix(p.secondary, black, 0.6f),
            tertiary = p.tertiary,
            onTertiary = white,
            tertiaryContainer = mix(p.tertiary, white, 0.84f),
            onTertiaryContainer = mix(p.tertiary, black, 0.6f),
            background = Color(0xFFFBFBFE),
            surface = Color(0xFFFBFBFE),
            surfaceVariant = mix(p.seed, Color(0xFFF1F1F4), 0.94f),
            surfaceContainerLowest = white,
            surfaceContainerLow = mix(p.seed, Color(0xFFF7F7FA), 0.97f),
            surfaceContainer = mix(p.seed, Color(0xFFF1F1F5), 0.95f),
            surfaceContainerHigh = mix(p.seed, Color(0xFFECECF0), 0.94f),
            surfaceContainerHighest = mix(p.seed, Color(0xFFE6E6EB), 0.93f),
            onSurface = Color(0xFF1A1B20),
            onSurfaceVariant = Color(0xFF5B5E68),
            outline = Color(0xFF8E909A),
            outlineVariant = Color(0xFFDDDEE5),
        )
    } else {
        val light = mix(p.seed, white, 0.55f)
        darkColorScheme(
            primary = light,
            onPrimary = mix(p.seed, black, 0.7f),
            primaryContainer = mix(p.seed, black, 0.45f),
            onPrimaryContainer = mix(p.seed, white, 0.85f),
            secondary = mix(p.secondary, white, 0.55f),
            onSecondary = mix(p.secondary, black, 0.7f),
            secondaryContainer = mix(p.secondary, black, 0.5f),
            onSecondaryContainer = mix(p.secondary, white, 0.85f),
            tertiary = mix(p.tertiary, white, 0.55f),
            onTertiary = mix(p.tertiary, black, 0.7f),
            tertiaryContainer = mix(p.tertiary, black, 0.5f),
            onTertiaryContainer = mix(p.tertiary, white, 0.85f),
            background = Color(0xFF111318),
            surface = Color(0xFF111318),
            surfaceVariant = Color(0xFF2A2C33),
            surfaceContainerLowest = Color(0xFF0C0E12),
            surfaceContainerLow = mix(p.seed, Color(0xFF191B20), 0.95f),
            surfaceContainer = mix(p.seed, Color(0xFF1D1F25), 0.94f),
            surfaceContainerHigh = mix(p.seed, Color(0xFF272A30), 0.94f),
            surfaceContainerHighest = mix(p.seed, Color(0xFF32353B), 0.94f),
            onSurface = Color(0xFFE3E3E8),
            onSurfaceVariant = Color(0xFFC4C6D0),
            outline = Color(0xFF8E909A),
            outlineVariant = Color(0xFF3C3F47),
        )
    }
}

private val AppTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = t.bodyLarge.copy(fontSize = 16.sp, lineHeight = 23.sp),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun PermatasksTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val settings = context.container.settings
    @Suppress("UNUSED_VARIABLE") val tick by settings.changes.collectAsState()
    val dark = when (settings.themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val palette = Palettes.firstOrNull { it.key == settings.palette }
    val scheme = when {
        palette != null -> schemeFor(palette, dark)
        dark -> dynamicDarkColorScheme(context)
        else -> dynamicLightColorScheme(context)
    }
    MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
}

/** Semantic colours that don't come from the scheme. */
object AppColors {
    val Success = Color(0xFF2E9E5B)
    val Warning = Color(0xFFE08A00)
    val Snooze = Color(0xFF6E62E5)
    fun argb(c: Color) = c.toArgb()
}

val MonoStyle = TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 14.sp)
