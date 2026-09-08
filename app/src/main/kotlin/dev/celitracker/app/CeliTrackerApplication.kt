package dev.celitracker.app

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.celitracker.app.ui.accueil.AccueilViewModel
import dev.celitracker.app.ui.detail.DetailCeliViewModel
import dev.celitracker.app.ui.detail.DetailCeliappViewModel
import dev.celitracker.app.ui.reglages.ReglagesViewModel
import dev.celitracker.data.Depot
import dev.celitracker.data.ouvrirBase
import java.io.File

/**
 * Pas de Hilt ni Koin: un seul depot, un seul processus. Le [Depot] et la
 * fabrique de ViewModel sont construits une fois ici plutot que via un
 * conteneur d'injection, qui serait de la ceremonie pure pour ce besoin.
 */
class CeliTrackerApplication : Application() {

    lateinit var viewModelFactory: ViewModelProvider.Factory
        private set

    override fun onCreate() {
        super.onCreate()
        val depot = Depot(ouvrirBase(File(filesDir, "celi-tracker.db").absolutePath))
        viewModelFactory = viewModelFactory {
            initializer { AccueilViewModel(depot) }
            initializer { DetailCeliViewModel(depot) }
            initializer { DetailCeliappViewModel(depot) }
            initializer { ReglagesViewModel(depot) }
        }
    }
}
