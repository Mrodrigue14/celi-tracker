package dev.celitracker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.celitracker.app.ui.navigation.CeliTrackerNavHost
import dev.celitracker.app.ui.theme.CeliTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val themePreference = (application as CeliTrackerApplication).themePreference
        setContent {
            val mode by themePreference.mode.collectAsStateWithLifecycle()
            CeliTrackerTheme(mode) {
                CeliTrackerNavHost()
            }
        }
    }
}
