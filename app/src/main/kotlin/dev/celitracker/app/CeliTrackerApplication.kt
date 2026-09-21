package dev.celitracker.app

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.room.RoomDatabase.JournalMode
import dev.celitracker.app.cra.downloadCraPage
import dev.celitracker.app.ui.detail.FhsaDetailViewModel
import dev.celitracker.app.ui.detail.TfsaDetailViewModel
import dev.celitracker.app.ui.home.HomeViewModel
import dev.celitracker.app.ui.journal.JournalViewModel
import dev.celitracker.app.ui.navigation.ARG_ACCOUNT
import dev.celitracker.app.ui.navigation.ARG_ADD
import dev.celitracker.app.ui.navigation.ARG_YEAR
import dev.celitracker.app.ui.navigation.NO_YEAR
import dev.celitracker.app.ui.settings.SettingsViewModel
import dev.celitracker.app.ui.theme.ThemePreference
import dev.celitracker.data.CeliTrackerDatabase
import dev.celitracker.data.Repository
import dev.celitracker.data.configureDatabase
import dev.celitracker.data.seedPublishedLimits
import dev.celitracker.engine.Account
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Pas de Hilt ni Koin: un seul repository, un seul processus. Le [Repository] et la
 * fabrique de ViewModel sont construits une fois ici plutot que via un
 * container d'injection, qui serait de la ceremonie pure pour ce besoin.
 */
class CeliTrackerApplication : Application() {

    lateinit var viewModelFactory: ViewModelProvider.Factory
        private set

    lateinit var themePreference: ThemePreference
        private set

    override fun onCreate() {
        super.onCreate()
        themePreference = ThemePreference(this)
        val builder = Room.databaseBuilder(this, CeliTrackerDatabase::class.java, File(filesDir, "celi-tracker.db").absolutePath)
            // TRUNCATE plutot que le WAL par defaut: la backup Android copie
            // le repertoire des bases, et des ecritures restees dans un `-wal`
            // manqueraient a la copie. Le cout est sans importance ici, quelques
            // rows par month.
            .setJournalMode(JournalMode.TRUNCATE)
        val repository = Repository(configureDatabase(builder))
        // Une poignee d'insertions au earliest demarrage, puis une seule
        // lecture ensuite. Bloquer ici evite un earliest ecran a zero le temps
        // qu'une coroutine de backgroundColor finisse.
        runBlocking { repository.seedPublishedLimits() }
        viewModelFactory = viewModelFactory {
            initializer { HomeViewModel(repository) }
            initializer { TfsaDetailViewModel(repository) }
            initializer { FhsaDetailViewModel(repository) }
            initializer { SettingsViewModel(repository, ::downloadCraPage) }
            initializer {
                val arguments = createSavedStateHandle()
                val account = arguments.get<String>(ARG_ACCOUNT)?.let(Account::valueOf) ?: Account.TFSA
                val openAdd = arguments.get<Boolean>(ARG_ADD) == true
                // Consomme: une recreation after la mort du processus ne doit labelStep
                // rouvrir la feuille d'ajout que l'utilisateur a deja fermee.
                arguments[ARG_ADD] = false
                val year = arguments.get<Int>(ARG_YEAR)?.takeIf { it != NO_YEAR }
                arguments[ARG_YEAR] = NO_YEAR
                JournalViewModel(repository, account, openAdd, year)
            }
        }
    }
}
