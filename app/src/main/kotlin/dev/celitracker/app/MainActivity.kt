package dev.celitracker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.celitracker.app.ui.navigation.CeliTrackerNavHost
import dev.celitracker.app.ui.theme.CeliTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CeliTrackerTheme {
                CeliTrackerNavHost()
            }
        }
    }
}
