package dev.one.launcher.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garrietta.one.R

/**
 * Marquee's look (v1.9.0): fixed dark, cinematic, cover art forward.
 * Every screen is washed in its platform's color (see
 * [dev.one.launcher.data.PlatformTint]); on top of that the UI is
 * only white and greys. The primary action is a solid off-white
 * pill with dark text; secondary actions are translucent dark pills.
 * Type is Archivo (OFL, bundled): SemiBold for titles and big numbers,
 * Black for the wordmark alone; Regular/Medium for everything else.
 * No dynamic color, no light theme — a wallpaper palette would fight
 * the platform tint and the cover art.
 */
val Archivo = FontFamily(
    Font(R.font.archivo_regular, FontWeight.Normal),
    Font(R.font.archivo_medium, FontWeight.Medium),
    Font(R.font.archivo_semibold, FontWeight.SemiBold),
    Font(R.font.archivo_extrabold, FontWeight.ExtraBold),
    Font(R.font.archivo_black, FontWeight.Black)
)

val MarqueeBackground = Color(0xFF0B0B0D)
val MarqueeInk = Color(0xFFF4F1EA)
val MarqueeInkMuted = Color(0xFFF4F1EA).copy(alpha = 0.66f)
/** Translucent dark fill for secondary pills and chips on a tinted screen. */
val MarqueeGlass = Color(0xFF000000).copy(alpha = 0.32f)

private val MarqueeColors = darkColorScheme(
    primary = MarqueeInk,
    onPrimary = Color(0xFF111114),
    primaryContainer = Color(0xFF2B2B30),
    onPrimaryContainer = MarqueeInk,
    secondary = Color(0xFFCFCBC2),
    onSecondary = Color(0xFF111114),
    secondaryContainer = Color(0xFF26262B),
    onSecondaryContainer = MarqueeInk,
    tertiary = Color(0xFFE8C76A),
    tertiaryContainer = Color(0xFF26262B),
    onTertiaryContainer = MarqueeInk,
    background = MarqueeBackground,
    onBackground = MarqueeInk,
    surface = Color(0xFF151518),
    onSurface = MarqueeInk,
    surfaceVariant = Color(0xFF222227),
    onSurfaceVariant = Color(0xFFB4B1AA),
    surfaceContainer = Color(0xFF18181C),
    surfaceContainerHigh = Color(0xFF1E1E23),
    error = Color(0xFFFF8A80),
    outline = Color(0xFF45454C),
    outlineVariant = Color(0xFF2E2E34)
)

private val MarqueeShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

private fun style(size: Int, weight: FontWeight, line: Int, tracking: Double = 0.0) =
    TextStyle(
        fontFamily = Archivo,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = line.sp,
        letterSpacing = tracking.sp
    )

/*
 * Titles are SemiBold, not Black: a title reads by its size and by
 * Archivo's wide shape, so the screen keeps one loud thing - the
 * platform's light - and the type sits on it calmly. The wordmark keeps
 * its Black; it is the logo.
 */
private val MarqueeTypography = Typography(
    displayLarge = style(52, FontWeight.SemiBold, 56, -1.0),
    displayMedium = style(42, FontWeight.SemiBold, 46, -0.8),
    displaySmall = style(34, FontWeight.SemiBold, 38, -0.6),
    headlineLarge = style(30, FontWeight.SemiBold, 34, -0.4),
    headlineMedium = style(26, FontWeight.SemiBold, 30, -0.3),
    headlineSmall = style(22, FontWeight.SemiBold, 26, -0.2),
    titleLarge = style(20, FontWeight.SemiBold, 24, -0.1),
    titleMedium = style(16, FontWeight.SemiBold, 22),
    titleSmall = style(14, FontWeight.SemiBold, 20),
    bodyLarge = style(16, FontWeight.Normal, 22),
    bodyMedium = style(14, FontWeight.Normal, 20),
    bodySmall = style(12, FontWeight.Normal, 16),
    labelLarge = style(14, FontWeight.SemiBold, 18),
    labelMedium = style(12, FontWeight.SemiBold, 16, 0.2),
    labelSmall = style(11, FontWeight.Medium, 14, 0.3)
)

@Composable
fun MarqueeTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MarqueeColors,
        shapes = MarqueeShapes,
        typography = MarqueeTypography,
        content = content
    )
}
