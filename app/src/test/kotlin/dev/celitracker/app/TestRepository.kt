package dev.celitracker.app

import androidx.room.Room
import dev.celitracker.data.CeliTrackerDatabase
import dev.celitracker.data.Repository
import dev.celitracker.data.configureDatabase
import java.io.File

/**
 * Repository backed by a real temporary SQLite database, like RepositoryTest in `:data`.
 * Shared by several ViewModel test classes: a fake or a mock
 * would hide the real round trip through Room, exactly what these tests
 * are meant to cover.
 *
 * The database is never closed: a ViewModel keeps coroutines in flight on
 * Room's IO dispatcher, and closing the pool from under them made a
 * test fail at random. The temporary file disappears with the test JVM.
 */
class TestRepository {
    private val file = File.createTempFile("celi-tracker-app-test", ".db").apply { deleteOnExit() }
    val database: CeliTrackerDatabase = configureDatabase(Room.databaseBuilder<CeliTrackerDatabase>(name = file.absolutePath))
    val repository = Repository(database)
}
