package uk.rydeapp.ryde

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import uk.rydeapp.ryde.ui.RydeApp
import uk.rydeapp.ryde.ui.theme.RydeTheme
import uk.rydeapp.ryde.data.AppMode
import uk.rydeapp.ryde.data.connected.FirebaseConnectedRepositoryFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appMode = AppMode.valueOf(BuildConfig.RYDE_APP_MODE)
        val connectedRepository = if (appMode == AppMode.CONNECTED) {
            FirebaseConnectedRepositoryFactory.repository
        } else {
            null
        }
        setContent {
            RydeTheme {
                RydeApp(repository = connectedRepository, appMode = appMode)
            }
        }
    }
}
