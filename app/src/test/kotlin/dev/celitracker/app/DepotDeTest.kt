package dev.celitracker.app

import androidx.room.Room
import dev.celitracker.data.CeliTrackerBase
import dev.celitracker.data.Depot
import dev.celitracker.data.configurerBase
import java.io.File

/**
 * Depot sur une vraie base SQLite temporaire, comme DepotTest dans `:data`.
 * Partagee par plusieurs classes de test de ViewModel: un fake ou un mock
 * masquerait le round-trip reel a travers Room, exactement ce que ces tests
 * doivent couvrir.
 *
 * La base n'est jamais fermee: un ViewModel garde des coroutines en vol sur le
 * dispatcher IO de Room, et fermer le pool sous leurs pieds faisait echouer un
 * test au hasard. Le fichier temporaire disparait avec la JVM de test.
 */
class DepotDeTest {
    private val fichier = File.createTempFile("celi-tracker-app-test", ".db").apply { deleteOnExit() }
    val base: CeliTrackerBase = configurerBase(Room.databaseBuilder<CeliTrackerBase>(name = fichier.absolutePath))
    val depot = Depot(base)
}
