package dev.celitracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import dev.celitracker.engine.Account
import java.time.Instant

@Dao
interface CeliTrackerDao {
    @Query("SELECT * FROM profil WHERE id = 0")
    suspend fun profile(): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(profile: ProfileEntity)

    @Query("SELECT * FROM plafonds")
    suspend fun limits(): List<LimitEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveLimit(limit: LimitEntity)

    @Query("DELETE FROM plafonds WHERE compte = :account AND annee = :year")
    suspend fun deleteLimit(account: Account, year: Int)

    @Query("SELECT * FROM transactions")
    suspend fun transactions(): List<TransactionEntity>

    @Insert
    suspend fun addTransaction(transaction: TransactionEntity)

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity): Int

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransaction(id: Long)

    @Query("UPDATE reglages SET dateDerniereVerification = :date WHERE id = 0")
    suspend fun recordCraCheck(date: Instant): Int

    @Query("SELECT * FROM snapshots_arc")
    suspend fun craSnapshots(): List<CraSnapshotEntity>

    @Insert
    suspend fun saveCraSnapshot(snapshot: CraSnapshotEntity)

    @Query("DELETE FROM snapshots_arc WHERE id = :id")
    suspend fun deleteCraSnapshot(id: Long)

    @Query("SELECT * FROM reglages WHERE id = 0")
    suspend fun settings(): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: SettingsEntity)

    @Query("DELETE FROM profil")
    suspend fun clearProfile()

    @Query("DELETE FROM plafonds")
    suspend fun clearLimits()

    @Query("DELETE FROM transactions")
    suspend fun clearTransactions()

    @Query("DELETE FROM snapshots_arc")
    suspend fun clearCraSnapshots()

    @Query("DELETE FROM reglages")
    suspend fun clearSettings()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveLimits(limits: List<LimitEntity>)

    @Insert
    suspend fun addTransactions(transactions: List<TransactionEntity>)

    @Insert
    suspend fun saveCraSnapshots(snapshots: List<CraSnapshotEntity>)

    /** One transaction, so a failed import never leaves a half-emptied database. */
    @Transaction
    suspend fun replaceEverything(
        profile: ProfileEntity?,
        limits: List<LimitEntity>,
        transactions: List<TransactionEntity>,
        snapshots: List<CraSnapshotEntity>,
        settings: SettingsEntity,
    ) {
        clearProfile()
        clearLimits()
        clearTransactions()
        clearCraSnapshots()
        clearSettings()
        profile?.let { saveProfile(it) }
        saveLimits(limits)
        addTransactions(transactions)
        saveCraSnapshots(snapshots)
        saveSettings(settings)
    }
}
