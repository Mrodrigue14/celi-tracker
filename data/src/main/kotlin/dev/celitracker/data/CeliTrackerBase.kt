package dev.celitracker.data

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

@Database(
    entities = [
        ProfilEntity::class,
        PlafondEntity::class,
        TransactionEntity::class,
        SnapshotArcEntity::class,
        ReglagesEntity::class,
    ],
    version = 1,
)
@TypeConverters(Convertisseurs::class)
abstract class CeliTrackerBase : RoomDatabase() {
    abstract fun dao(): CeliTrackerDao
}

/** [chemin] est un chemin de fichier absolu; pas de SDK Android, pas de Context. */
fun ouvrirBase(chemin: String): CeliTrackerBase =
    Room.databaseBuilder<CeliTrackerBase>(name = chemin)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
