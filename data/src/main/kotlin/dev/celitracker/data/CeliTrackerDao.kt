package dev.celitracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

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
    suspend fun enregistrerPlafonds(plafonds: List<PlafondEntity>)

    @Insert
    suspend fun ajouterTransactions(transactions: List<TransactionEntity>)

    @Insert
    suspend fun enregistrerSnapshotsArc(snapshots: List<SnapshotArcEntity>)

    /**
     * Vide puis remplit toutes les tables en une seule transaction: reserve a
     * l'import JSON, qui remplace tout le contenu et ne fusionne jamais.
     */
    @Transaction
    suspend fun remplacerTout(
        profil: ProfilEntity?,
        plafonds: List<PlafondEntity>,
        transactions: List<TransactionEntity>,
        snapshots: List<SnapshotArcEntity>,
        reglages: ReglagesEntity,
    ) {
        viderProfil()
        viderPlafonds()
        viderTransactions()
        viderSnapshotsArc()
        viderReglages()
        profil?.let { enregistrerProfil(it) }
        enregistrerPlafonds(plafonds)
        ajouterTransactions(transactions)
        enregistrerSnapshotsArc(snapshots)
        enregistrerReglages(reglages)
    }
}
