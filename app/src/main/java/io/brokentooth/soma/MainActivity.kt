package io.brokentooth.soma

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.brokentooth.soma.ui.chat.ChatScreen
import io.brokentooth.soma.ui.theme.SomaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SomaTheme {
                ChatScreen()
            }
        }
    }
}
