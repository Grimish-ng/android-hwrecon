package dev.hwrecon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.hwrecon.ui.ReconScreen
import dev.hwrecon.ui.ReconTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = ReconTheme.bg,
                    surface    = ReconTheme.bg1,
                    primary    = ReconTheme.accent,
                    onPrimary  = Color.Black,
                    secondary  = ReconTheme.accent2,
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color    = ReconTheme.bg,
                ) {
                    ReconScreen()
                }
            }
        }
    }
}
