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
        val preferenceTheme = (application as CeliTrackerApplication).preferenceTheme
        setContent {
            val mode by preferenceTheme.mode.collectAsStateWithLifecycle()
            CeliTrackerTheme(mode) {
                CeliTrackerNavHost()
            }
        }
    }
}
