package dev.one.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import dev.one.launcher.ui.theme.OneTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OneTheme {
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
