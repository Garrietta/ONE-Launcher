package dev.one.launcher.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val OneGreen = Color(0xFF2E7D32)
private val OneGreenDark = Color(0xFF7ED957)

private val OneLightColors = lightColorScheme(
    primary = OneGreen,
    secondary = Color(0xFF5C6BC0),
    tertiary = Color(0xFFFF8F00)
)

private val OneDarkColors = darkColorScheme(
    primary = OneGreenDark,
    secondary = Color(0xFF9FA8DA),
    tertiary = Color(0xFFFFB74D)
)

@Composable
fun OneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> OneDarkColors
        else -> OneLightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
