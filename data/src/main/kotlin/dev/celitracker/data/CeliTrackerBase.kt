package dev.celitracker.data

import androidx.room.Database
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

/**
 * Applique la configuration commune (pilote SQLite embarque, contexte de
 * coroutines pour les requetes) a un [builder] deja cree par l'appelant avec
 * la surcharge de `Room.databaseBuilder` propre a sa plateforme, puis
 * construit la base. `:data` ne reference ainsi plus aucune surcharge de
 * `Room.databaseBuilder`: c'est a chaque consommateur (JVM ou Android) de
 * creer le builder avec la sienne.
 */
fun configurerBase(builder: RoomDatabase.Builder<CeliTrackerBase>): CeliTrackerBase =
    builder
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
