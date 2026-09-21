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

    @DeleteColumn(tableName = "profil", columnName = "anneeAdmissibiliteCeli")
    class DropEligibilityYearColumn : AutoMigrationSpec
}

/** The caller creates [builder] with its platform's `Room.databaseBuilder` overload, so `:data` references none. */
fun configureDatabase(builder: RoomDatabase.Builder<CeliTrackerDatabase>): CeliTrackerDatabase = builder
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.IO)
    .build()
