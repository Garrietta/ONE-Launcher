package dev.one.launcher.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Portside's look: fixed dark, cinematic, cover art forward. No
 * dynamic color, no light theme — the game-library look Garrett
 * asked for is dark on every device, and a wallpaper-driven palette
 * would fight the cover art. One restrained accent (lime, echoing the
 * Portside icon's first mockup) carries Play buttons, chips, and the
 * selected state; everything else stays near-black, dark grey, and
 * off-white. Flat, no gloss. Tighter radii than Material 3
 * Expressive (4/8/12dp) — the shelf and the spines are rectangles,
 * not pills.
 */
private val PortsideBackground = Color(0xFF0B0B0D)
private val PortsideSurface = Color(0xFF17171B)
private val PortsideSurfaceVariant = Color(0xFF232329)
private val PortsideOnSurface = Color(0xFFF2F2ED)
private val PortsideOnSurfaceVariant = Color(0xFFA8A8AD)
private val PortsideLime = Color(0xFFA3E635)
private val PortsideLimeContainer = Color(0xFF2A3310)
private val PortsideOnLime = Color(0xFF101205)
private val PortsideError = Color(0xFFFF8A80)

private val PortsideColors = darkColorScheme(
    primary = PortsideLime,
    onPrimary = PortsideOnLime,
    primaryContainer = PortsideLimeContainer,
    onPrimaryContainer = PortsideLime,
    secondary = Color(0xFF9DB4C0),
    secondaryContainer = PortsideSurfaceVariant,
    onSecondaryContainer = PortsideOnSurface,
    tertiary = Color(0xFFD6C25A),
    tertiaryContainer = PortsideSurfaceVariant,
    onTertiaryContainer = PortsideOnSurface,
    background = PortsideBackground,
    onBackground = PortsideOnSurface,
    surface = PortsideSurface,
    onSurface = PortsideOnSurface,
    surfaceVariant = PortsideSurfaceVariant,
    onSurfaceVariant = PortsideOnSurfaceVariant,
    error = PortsideError,
    outline = Color(0xFF3A3A40)
)

private val PortsideShapes = Shapes(
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp)
)

@Composable
fun PortsideTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = PortsideColors,
        shapes = PortsideShapes,
        content = content
    )
}
