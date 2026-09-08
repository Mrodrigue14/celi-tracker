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
 */
class DepotDeTest {
    private val fichier = File.createTempFile("celi-tracker-app-test", ".db")
    val base: CeliTrackerBase = configurerBase(Room.databaseBuilder<CeliTrackerBase>(name = fichier.absolutePath))
    val depot = Depot(base)

    fun fermer() {
        base.close()
        fichier.delete()
    }
}
