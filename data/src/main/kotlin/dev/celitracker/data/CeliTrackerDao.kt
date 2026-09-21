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

    @Query("SELECT * FROM reglages WHERE id = 0")
    suspend fun settings(): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: SettingsEntity)

    @Query("DELETE FROM profil")
    suspend fun viderProfil()

    @Query("DELETE FROM plafonds")
    suspend fun viderPlafonds()

    @Query("DELETE FROM transactions")
    suspend fun viderTransactions()

    @Query("DELETE FROM snapshots_arc")
    suspend fun viderSnapshotsArc()

    @Query("DELETE FROM reglages")
    suspend fun viderReglages()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveLimits(limits: List<LimitEntity>)

    @Insert
    suspend fun addTransactions(transactions: List<TransactionEntity>)

    @Insert
    suspend fun saveCraSnapshots(snapshots: List<CraSnapshotEntity>)

    /**
     * Vide puis remplit toutes les tables en une seule transaction: reserve a
     * l'import JSON, qui remplace whole le content et ne fusionne jamais.
     */
    @Transaction
    suspend fun replaceEverything(
        profile: ProfileEntity?,
        limits: List<LimitEntity>,
        transactions: List<TransactionEntity>,
        snapshots: List<CraSnapshotEntity>,
        settings: SettingsEntity,
    ) {
        viderProfil()
        viderPlafonds()
        viderTransactions()
        viderSnapshotsArc()
        viderReglages()
        profile?.let { saveProfile(it) }
        saveLimits(limits)
        addTransactions(transactions)
        saveCraSnapshots(snapshots)
        saveSettings(settings)
    }
}
