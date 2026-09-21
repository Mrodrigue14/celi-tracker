package dev.celitracker.data

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

@Database(
    entities = [
        ProfileEntity::class,
        LimitEntity::class,
        TransactionEntity::class,
        CraSnapshotEntity::class,
        SettingsEntity::class,
    ],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2, spec = CeliTrackerDatabase.DropEligibilityYearColumn::class)],
)
@TypeConverters(Converters::class)
abstract class CeliTrackerDatabase : RoomDatabase() {
    abstract fun dao(): CeliTrackerDao

    /** L'year d'admissibilite se calcule desormais depuis l'year de birth. */
    @DeleteColumn(tableName = "profil", columnName = "anneeAdmissibiliteCeli")
    class DropEligibilityYearColumn : AutoMigrationSpec
}

/**
 * Applique la configuration commune (pilote SQLite embarque, context de
 * coroutines pour les requetes) a un [builder] deja cree par l'appelant avec
 * la surcharge de `Room.databaseBuilder` propre a sa plateforme, puis
 * construit la database. `:data` ne reference ainsi plus aucune surcharge de
 * `Room.databaseBuilder`: c'est a chaque consommateur (JVM ou Android) de
 * creer le builder avec la sienne.
 */
fun configureDatabase(builder: RoomDatabase.Builder<CeliTrackerDatabase>): CeliTrackerDatabase = builder
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.IO)
    .build()
