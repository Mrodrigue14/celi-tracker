package dev.celitracker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import dev.celitracker.engine.Compte
import dev.celitracker.engine.TypeTx
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Entites Room. Elles ne sortent jamais de ce module: [dev.celitracker.data.Depot]
 * expose uniquement les types de `:engine`.
 */

@Entity(tableName = "profil")
data class ProfilEntity(
    @PrimaryKey val id: Int = 0,
    @ColumnInfo(name = "anneeNaissance") val anneeNaissance: Int,
    @ColumnInfo(name = "dateOuvertureCeliapp") val dateOuvertureCeliapp: LocalDate?,
)

@Entity(tableName = "plafonds", primaryKeys = ["compte", "annee"])
data class PlafondEntity(
    @ColumnInfo(name = "compte") val compte: Compte,
    @ColumnInfo(name = "annee") val annee: Int,
    @ColumnInfo(name = "montant") val montant: BigDecimal,
    @ColumnInfo(name = "confirme") val confirme: Boolean,
)

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "compte") val compte: Compte,
    val date: LocalDate,
    val type: TypeTx,
    @ColumnInfo(name = "montant") val montant: BigDecimal,
)

@Entity(tableName = "snapshots_arc")
data class SnapshotArcEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "compte") val compte: Compte,
    @ColumnInfo(name = "dateReference") val dateReference: LocalDate,
    @ColumnInfo(name = "droitsDeclares") val droitsDeclares: BigDecimal,
)

@Entity(tableName = "reglages")
data class ReglagesEntity(
    @PrimaryKey val id: Int = 0,
    @ColumnInfo(name = "urlPageArc") val urlPageArc: String,
    @ColumnInfo(name = "dateDerniereVerification") val dateDerniereVerification: Instant?,
)

/**
 * Conversions vers TEXT: SQLite n'a pas de type decimal exact, un REAL
 * reintroduirait la derive de virgule flottante que BigDecimal elimine.
 */
class Convertisseurs {
    @TypeConverter
    fun compteVersTexte(compte: Compte): String = compte.storedValue()

    @TypeConverter
    fun texteVersCompte(texte: String): Compte = storedAccount(texte)

    @TypeConverter
    fun typeTxVersTexte(type: TypeTx): String = type.storedValue()

    @TypeConverter
    fun texteVersTypeTx(texte: String): TypeTx = storedTransactionType(texte)

    @TypeConverter
    fun montantVersTexte(montant: BigDecimal): String = montant.toPlainString()

    @TypeConverter
    fun texteVersMontant(texte: String): BigDecimal = BigDecimal(texte)

    @TypeConverter
    fun dateVersTexte(date: LocalDate): String = date.toString()

    @TypeConverter
    fun texteVersDate(texte: String): LocalDate = LocalDate.parse(texte)

    @TypeConverter
    fun dateNullableVersTexte(date: LocalDate?): String? = date?.toString()

    @TypeConverter
    fun texteVersDateNullable(texte: String?): LocalDate? = texte?.let { LocalDate.parse(it) }

    @TypeConverter
    fun instantVersTexte(instant: Instant?): String? = instant?.toString()

    @TypeConverter
    fun texteVersInstant(texte: String?): Instant? = texte?.let { Instant.parse(it) }
}

/**
 * Values written to the database and to backup files since the first version.
 * They are spelled out here so that renaming an enum constant in code never
 * changes what is stored.
 */
internal fun Compte.storedValue(): String = when (this) {
    Compte.CELI -> "CELI"
    Compte.CELIAPP -> "CELIAPP"
}

internal fun storedAccount(value: String): Compte = Compte.entries.first { it.storedValue() == value }

internal fun TypeTx.storedValue(): String = when (this) {
    TypeTx.DEPOT -> "DEPOT"
    TypeTx.RETRAIT -> "RETRAIT"
}

internal fun storedTransactionType(value: String): TypeTx = TypeTx.entries.first { it.storedValue() == value }
