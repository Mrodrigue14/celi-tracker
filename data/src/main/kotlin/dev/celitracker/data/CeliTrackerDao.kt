package dev.celitracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CeliTrackerDao {
    @Query("SELECT * FROM profil WHERE id = 0")
    suspend fun profil(): ProfilEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enregistrerProfil(profil: ProfilEntity)

    @Query("SELECT * FROM plafonds")
    suspend fun plafonds(): List<PlafondEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enregistrerPlafond(plafond: PlafondEntity)

    @Query("SELECT * FROM transactions")
    suspend fun transactions(): List<TransactionEntity>

    @Insert
    suspend fun ajouterTransaction(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun supprimerTransaction(id: Long)

    @Query("SELECT * FROM snapshots_arc")
    suspend fun snapshotsArc(): List<SnapshotArcEntity>

    @Insert
    suspend fun enregistrerSnapshotArc(snapshot: SnapshotArcEntity)

    @Query("SELECT * FROM reglages WHERE id = 0")
    suspend fun reglages(): ReglagesEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enregistrerReglages(reglages: ReglagesEntity)
}
