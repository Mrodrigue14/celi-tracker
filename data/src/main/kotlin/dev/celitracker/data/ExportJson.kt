package dev.celitracker.data

import dev.celitracker.engine.Account
import dev.celitracker.engine.TransactionType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

private const val EXPORT_VERSION = 2

/** Version 1 also carried the eligibility year, now computed; that field is ignored so old backups stay readable. */
private val ACCEPTED_VERSIONS = setOf(1, EXPORT_VERSION)

private val json = Json { ignoreUnknownKeys = true }

@Serializable
private data class ProfileJson(
    @SerialName("anneeNaissance") val birthYear: Int,
    @SerialName("dateOuvertureCeliapp") val fhsaOpeningDate: String?,
)

@Serializable
private data class LimitJson(
    @SerialName("compte") val account: String,
    @SerialName("annee") val year: Int,
    @SerialName("montant") val amount: String,
    @SerialName("confirme") val confirmed: Boolean,
)

@Serializable
private data class TransactionJson(
    val id: Long,
    @SerialName("compte") val account: String,
    val date: String,
    val type: String,
    @SerialName("montant") val amount: String,
)

@Serializable
private data class CraSnapshotJson(
    val id: Long,
    @SerialName("compte") val account: String,
    @SerialName("dateReference") val referenceDate: String,
    @SerialName("droitsDeclares") val declaredRoom: String,
)

@Serializable
private data class SettingsJson(
    @SerialName("urlPageArc") val craPageUrl: String,
    @SerialName("dateDerniereVerification") val lastCheckDate: String?,
)

@Serializable
private data class ExportFile(
    val version: Int,
    @SerialName("profil") val profile: ProfileJson?,
    @SerialName("plafonds") val limits: List<LimitJson>,
    val transactions: List<TransactionJson>,
    @SerialName("snapshotsArc") val craSnapshots: List<CraSnapshotJson>,
    @SerialName("reglages") val settings: SettingsJson,
)

/** Amounts are strings, never JSON numbers: most readers go through a `double`. Sorted so equal content exports equal strings. */
suspend fun Repository.exportJson(): String {
    val data = ExportFile(
        version = EXPORT_VERSION,
        profile = profile()?.let {
            ProfileJson(
                birthYear = it.birthYear,
                fhsaOpeningDate = it.fhsaOpeningDate?.toString(),
            )
        },
        limits = limits()
            .sortedWith(compareBy({ it.account.storedValue() }, { it.year }))
            .map { LimitJson(it.account.storedValue(), it.year, it.amount.toPlainString(), it.confirmed) },
        transactions = transactions()
            .sortedBy { it.id }
            .map { TransactionJson(it.id, it.account.storedValue(), it.date.toString(), it.type.storedValue(), it.amount.toPlainString()) },
        craSnapshots = craSnapshots()
            .sortedBy { it.id }
            .map { CraSnapshotJson(it.id, it.account.storedValue(), it.referenceDate.toString(), it.declaredRoom.toPlainString()) },
        settings = settings().let { SettingsJson(it.craPageUrl, it.lastCheckDate?.toString()) },
    )
    return json.encodeToString(ExportFile.serializer(), data)
}

/** Replaces everything in one transaction, never merges; a failed import leaves the database untouched. */
suspend fun Repository.importJson(content: String) {
    val data = try {
        val version = json.parseToJsonElement(content).jsonObject["version"]?.jsonPrimitive?.intOrNull
        if (version !in ACCEPTED_VERSIONS) throw InvalidImport(ImportFailureReason.UNKNOWN_VERSION)
        json.decodeFromString(ExportFile.serializer(), content)
    } catch (e: InvalidImport) {
        throw e
    } catch (e: Exception) {
        throw InvalidImport(ImportFailureReason.MALFORMED_JSON, e)
    }

    val profile = data.profile?.let {
        ProfileEntity(
            birthYear = it.birthYear,
            fhsaOpeningDate = it.fhsaOpeningDate?.let(LocalDate::parse),
        )
    }
    val limits = data.limits.map {
        LimitEntity(
            account = storedAccount(it.account),
            year = it.year,
            amount = BigDecimal(it.amount),
            confirmed = it.confirmed,
        )
    }
    val transactions = data.transactions.map {
        TransactionEntity(
            id = it.id,
            account = storedAccount(it.account),
            date = LocalDate.parse(it.date),
            type = storedTransactionType(it.type),
            amount = BigDecimal(it.amount),
        )
    }
    val snapshots = data.craSnapshots.map {
        CraSnapshotEntity(
            id = it.id,
            account = storedAccount(it.account),
            referenceDate = LocalDate.parse(it.referenceDate),
            declaredRoom = BigDecimal(it.declaredRoom),
        )
    }
    val settings = SettingsEntity(
        craPageUrl = data.settings.craPageUrl,
        lastCheckDate = data.settings.lastCheckDate?.let(Instant::parse),
    )

    replaceEverything(profile, limits, transactions, snapshots, settings)
}
