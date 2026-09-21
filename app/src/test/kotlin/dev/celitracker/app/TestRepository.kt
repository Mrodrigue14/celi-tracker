package dev.celitracker.app

import androidx.room.Room
import dev.celitracker.data.CeliTrackerDatabase
import dev.celitracker.data.Repository
import dev.celitracker.data.configureDatabase
import java.io.File

/**
 * A real temporary database, not a fake: the tests cover the actual Room round trip.
 * Never closed: closing the pool under ViewModel coroutines still on Room's IO dispatcher failed tests at random.
 */
class TestRepository {
    private val file = File.createTempFile("celi-tracker-app-test", ".db").apply { deleteOnExit() }
    val database: CeliTrackerDatabase = configureDatabase(Room.databaseBuilder<CeliTrackerDatabase>(name = file.absolutePath))
    val repository = Repository(database)
}
