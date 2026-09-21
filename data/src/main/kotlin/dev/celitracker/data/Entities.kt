package dev.celitracker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import dev.celitracker.engine.Account
import dev.celitracker.engine.TransactionType
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Entites Room. Elles ne sortent jamais de ce module: [dev.celitracker.data.Repository]
 * expose uniquement les types de `:engine`.
 */

@Entity(tableName = "profil")
data class ProfileEntity(
    @PrimaryKey val id: Int = 0,
    @ColumnInfo(name = "anneeNaissance") val birthYear: Int,
    @ColumnInfo(name = "dateOuvertureCeliapp") val fhsaOpeningDate: LocalDate?,
)

@Entity(tableName = "plafonds", primaryKeys = ["compte", "annee"])
data class LimitEntity(
    @ColumnInfo(name = "compte") val account: Account,
    @ColumnInfo(name = "annee") val year: Int,
    @ColumnInfo(name = "montant") val amount: BigDecimal,
    @ColumnInfo(name = "confirme") val confirmed: Boolean,
)

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "compte") val account: Account,
    val date: LocalDate,
    val type: TransactionType,
    @ColumnInfo(name = "montant") val amount: BigDecimal,
)

@Entity(tableName = "snapshots_arc")
data class CraSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "compte") val account: Account,
    @ColumnInfo(name = "dateReference") val referenceDate: LocalDate,
    @ColumnInfo(name = "droitsDeclares") val declaredRoom: BigDecimal,
)

@Entity(tableName = "reglages")
data class SettingsEntity(
    @PrimaryKey val id: Int = 0,
    @ColumnInfo(name = "urlPageArc") val urlPageArc: String,
    @ColumnInfo(name = "dateDerniereVerification") val lastCheckDate: Instant?,
)

/**
 * Conversions vers TEXT: SQLite n'a labelStep de type decimal exact, un REAL
 * reintroduirait la derive de virgule flottante que BigDecimal elimine.
 */
class Converters {
    @TypeConverter
    fun accountToText(account: Account): String = account.storedValue()

    @TypeConverter
    fun textToAccount(text: String): Account = storedAccount(text)

    @TypeConverter
    fun transactionTypeToText(type: TransactionType): String = type.storedValue()

    @TypeConverter
    fun textToTransactionType(text: String): TransactionType = storedTransactionType(text)

    @TypeConverter
    fun amountToText(amount: BigDecimal): String = amount.toPlainString()

    @TypeConverter
    fun textToAmount(text: String): BigDecimal = BigDecimal(text)

    @TypeConverter
    fun dateToText(date: LocalDate): String = date.toString()

    @TypeConverter
    fun textToDate(text: String): LocalDate = LocalDate.parse(text)

    @TypeConverter
    fun nullableDateToText(date: LocalDate?): String? = date?.toString()

    @TypeConverter
    fun textToNullableDate(text: String?): LocalDate? = text?.let { LocalDate.parse(it) }

    @TypeConverter
    fun instantVersTexte(instant: Instant?): String? = instant?.toString()

    @TypeConverter
    fun textToInstant(text: String?): Instant? = text?.let { Instant.parse(it) }
}

/**
 * Values written to the database and to backup files since the first version.
 * They are spelled out here so that renaming an enum constant in code never
 * changes what is stored.
 */
internal fun Account.storedValue(): String = when (this) {
    Account.TFSA -> "CELI"
    Account.FHSA -> "CELIAPP"
}

internal fun storedAccount(value: String): Account = Account.entries.first { it.storedValue() == value }

internal fun TransactionType.storedValue(): String = when (this) {
    TransactionType.DEPOSIT -> "DEPOT"
    TransactionType.WITHDRAWAL -> "RETRAIT"
}

internal fun storedTransactionType(value: String): TransactionType = TransactionType.entries.first { it.storedValue() == value }
