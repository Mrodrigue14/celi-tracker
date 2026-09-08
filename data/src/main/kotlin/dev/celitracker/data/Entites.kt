package dev.celitracker.data

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
    val anneeAdmissibiliteCeli: Int,
    val anneeNaissance: Int,
    val dateOuvertureCeliapp: LocalDate?,
)

@Entity(tableName = "plafonds", primaryKeys = ["compte", "annee"])
data class PlafondEntity(
    val compte: Compte,
    val annee: Int,
    val montant: BigDecimal,
    val confirme: Boolean,
)

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val compte: Compte,
    val date: LocalDate,
    val type: TypeTx,
    val montant: BigDecimal,
)

@Entity(tableName = "snapshots_arc")
data class SnapshotArcEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val compte: Compte,
    val dateReference: LocalDate,
    val droitsDeclares: BigDecimal,
)

@Entity(tableName = "reglages")
data class ReglagesEntity(
    @PrimaryKey val id: Int = 0,
    val urlPageArc: String,
    val dateDerniereVerification: Instant?,
)

/**
 * Conversions vers TEXT: SQLite n'a pas de type decimal exact, un REAL
 * reintroduirait la derive de virgule flottante que BigDecimal elimine.
 */
class Convertisseurs {
    @TypeConverter
    fun compteVersTexte(compte: Compte): String = compte.name

    @TypeConverter
    fun texteVersCompte(texte: String): Compte = Compte.valueOf(texte)

    @TypeConverter
    fun typeTxVersTexte(type: TypeTx): String = type.name

    @TypeConverter
    fun texteVersTypeTx(texte: String): TypeTx = TypeTx.valueOf(texte)

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
