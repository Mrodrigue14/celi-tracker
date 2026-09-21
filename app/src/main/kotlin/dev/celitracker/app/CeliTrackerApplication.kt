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

/** Inside `files/`, not `databases/`: the backup rules must name it under domain `file`. */
internal const val DATABASE_FILE_NAME = "celi-tracker.db"

/** Manual wiring instead of Hilt or Koin: one repository in one process needs no container. */
class CeliTrackerApplication : Application() {

    lateinit var viewModelFactory: ViewModelProvider.Factory
        private set

    lateinit var themePreference: ThemePreference
        private set

    override fun onCreate() {
        super.onCreate()
        themePreference = ThemePreference(this)
        val builder = Room.databaseBuilder(this, CeliTrackerDatabase::class.java, File(filesDir, DATABASE_FILE_NAME).absolutePath)
            // TRUNCATE, not WAL: Android backup copies the directory and would miss writes still in the `-wal` file.
            .setJournalMode(JournalMode.TRUNCATE)
        val repository = Repository(configureDatabase(builder))
        // Blocking so the first screen never shows empty limits while seeding runs.
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
                // Consumed so a recreation after process death does not reopen the closed add sheet.
                arguments[ARG_ADD] = false
                val year = arguments.get<Int>(ARG_YEAR)?.takeIf { it != NO_YEAR }
                arguments[ARG_YEAR] = NO_YEAR
                JournalViewModel(repository, account, openAdd, year)
            }
        }
    }
}
