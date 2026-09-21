package dev.celitracker.app

import androidx.room.Room
import dev.celitracker.data.CeliTrackerDatabase
import dev.celitracker.data.Repository
import dev.celitracker.data.configureDatabase
import java.io.File

/**
 * Repository sur une vraie database SQLite temporaire, comme RepositoryTest dans `:data`.
 * Partagee par plusieurs classes de test de ViewModel: un fake ou un mock
 * masquerait le round-trip reel a travers Room, exactement ce que ces tests
 * doivent couvrir.
 *
 * La database n'est jamais fermee: un ViewModel garde des coroutines en vol sur le
 * dispatcher IO de Room, et close le pool sous leurs pieds faisait echouer un
 * test au hasard. Le file temporaire disparait avec la JVM de test.
 */
class TestRepository {
    private val file = File.createTempFile("celi-tracker-app-test", ".db").apply { deleteOnExit() }
    val database: CeliTrackerDatabase = configureDatabase(Room.databaseBuilder<CeliTrackerDatabase>(name = file.absolutePath))
    val repository = Repository(database)
}
