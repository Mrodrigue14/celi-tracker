package dev.celitracker.app

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.room.RoomDatabase.JournalMode
import dev.celitracker.app.arc.telechargerPageArc
import dev.celitracker.app.ui.accueil.AccueilViewModel
import dev.celitracker.app.ui.detail.DetailCeliViewModel
import dev.celitracker.app.ui.detail.DetailCeliappViewModel
import dev.celitracker.app.ui.journal.JournalViewModel
import dev.celitracker.app.ui.navigation.ARG_AJOUTER
import dev.celitracker.app.ui.navigation.ARG_ANNEE
import dev.celitracker.app.ui.navigation.ARG_COMPTE
import dev.celitracker.app.ui.navigation.AUCUNE_ANNEE
import dev.celitracker.app.ui.reglages.ReglagesViewModel
import dev.celitracker.data.CeliTrackerBase
import dev.celitracker.data.Depot
import dev.celitracker.data.configurerBase
import dev.celitracker.data.garnirPlafondsPublies
import dev.celitracker.engine.Compte
import kotlinx.coroutines.runBlocking
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
        val builder = Room.databaseBuilder(this, CeliTrackerBase::class.java, File(filesDir, "celi-tracker.db").absolutePath)
            // TRUNCATE plutot que le WAL par defaut: la sauvegarde Android copie
            // le repertoire des bases, et des ecritures restees dans un `-wal`
            // manqueraient a la copie. Le cout est sans importance ici, quelques
            // lignes par mois.
            .setJournalMode(JournalMode.TRUNCATE)
        val depot = Depot(configurerBase(builder))
        // Une poignee d'insertions au premier demarrage, puis une seule
        // lecture ensuite. Bloquer ici evite un premier ecran a zero le temps
        // qu'une coroutine de fond finisse.
        runBlocking { depot.garnirPlafondsPublies() }
        viewModelFactory = viewModelFactory {
            initializer { AccueilViewModel(depot) }
            initializer { DetailCeliViewModel(depot) }
            initializer { DetailCeliappViewModel(depot) }
            initializer { ReglagesViewModel(depot, ::telechargerPageArc) }
            initializer {
                val arguments = createSavedStateHandle()
                val compte = arguments.get<String>(ARG_COMPTE)?.let(Compte::valueOf) ?: Compte.CELI
                val ouvrirAjout = arguments.get<Boolean>(ARG_AJOUTER) == true
                // Consomme: une recreation apres la mort du processus ne doit pas
                // rouvrir la feuille d'ajout que l'utilisateur a deja fermee.
                arguments[ARG_AJOUTER] = false
                val annee = arguments.get<Int>(ARG_ANNEE)?.takeIf { it != AUCUNE_ANNEE }
                arguments[ARG_ANNEE] = AUCUNE_ANNEE
                JournalViewModel(depot, compte, ouvrirAjout, annee)
            }
        }
    }
}
