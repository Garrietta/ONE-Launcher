package dev.one.launcher

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.one.launcher.ui.OneApp
import dev.one.launcher.ui.OneViewModel
import dev.one.launcher.ui.OneViewModelFactory
import dev.one.launcher.ui.theme.MarqueeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The theme is FIXED dark (MarqueeTheme: darkColorScheme,
        // background #0B0B0D, no light theme) on every device — but
        // bare enableEdgeToEdge() follows the SYSTEM theme, so on a
        // light-system device the status-bar icons are dark (black)
        // on the app's black background and the time/battery are
        // unreadable (Garrett's v1.8.7 video). Pin both bars to the
        // dark style, always: light icons on the dark theme.
        // Not JVM-testable — device retest: time/battery visible in
        // light icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            MarqueeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: OneViewModel =
                        viewModel(factory = OneViewModelFactory(application))
                    OneApp(viewModel = viewModel)
                }
            }
        }
    }
}
