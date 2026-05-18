package dev.hwrecon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.hwrecon.viewmodel.HwReconViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val viewModel: HwReconViewModel = viewModel()
                val state by viewModel.state.collectAsState()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0A0C0F))
                        .padding(16.dp)
                ) {
                    Text("HW·RECON", fontFamily = FontFamily.Monospace, fontSize = 28.sp, color = Color(0xFF00C8FF))
                    Spacer(Modifier.height(24.dp))
                    Text("Device: ${state.deviceModel}", color = Color.White, fontSize = 16.sp)
                    Text("Root: ${if (state.isRooted) "Yes" else "No"}", color = Color.White, fontSize = 16.sp)
                    Spacer(Modifier.height(32.dp))
                    Text("App is working!", color = Color(0xFFA8FF3E), fontSize = 18.sp)
                }
            }
        }
    }
}
