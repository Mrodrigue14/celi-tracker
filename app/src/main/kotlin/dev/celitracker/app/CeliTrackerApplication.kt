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
 * No Hilt or Koin: a single repository, a single process. The [Repository] and the
 * ViewModel factory are built once here rather than through an
 * injection container, which would be pure ceremony for this need.
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
            // TRUNCATE rather than the default WAL: Android backup copies
            // the database directory, and writes still sitting in a `-wal`
            // file would be missed by the copy. The cost does not matter here, just a
            // few rows per month.
            .setJournalMode(JournalMode.TRUNCATE)
        val repository = Repository(configureDatabase(builder))
        // A handful of inserts on first launch, then only reads afterward.
        // Blocking here avoids a first screen showing zero while a
        // background coroutine finishes.
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
                // Consumed: a recreation after the process is killed must not
                // reopen the add sheet the user already closed.
                arguments[ARG_ADD] = false
                val year = arguments.get<Int>(ARG_YEAR)?.takeIf { it != NO_YEAR }
                arguments[ARG_YEAR] = NO_YEAR
                JournalViewModel(repository, account, openAdd, year)
            }
        }
    }
}
