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

    /** The eligibility year is now calculated from the birth year. */
    @DeleteColumn(tableName = "profil", columnName = "anneeAdmissibiliteCeli")
    class DropEligibilityYearColumn : AutoMigrationSpec
}

/**
 * Applies the shared configuration (bundled SQLite driver, coroutine context
 * for queries) to a [builder] already created by the caller with the
 * `Room.databaseBuilder` overload for its platform, then builds the database.
 * This way `:data` never references any `Room.databaseBuilder` overload
 * itself: it is up to each consumer (JVM or Android) to create the builder
 * with its own.
 */
fun configureDatabase(builder: RoomDatabase.Builder<CeliTrackerDatabase>): CeliTrackerDatabase = builder
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.IO)
    .build()
