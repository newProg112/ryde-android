package uk.rydeapp.ryde

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import uk.rydeapp.ryde.ui.RydeApp
import uk.rydeapp.ryde.ui.theme.RydeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RydeTheme {
                RydeApp()
            }
        }
    }
}
