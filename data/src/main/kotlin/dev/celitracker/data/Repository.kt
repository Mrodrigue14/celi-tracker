package dev.celitracker.data

import dev.celitracker.engine.Account
import dev.celitracker.engine.AnnualLimit
import dev.celitracker.engine.CraSnapshot
import dev.celitracker.engine.Profile
import dev.celitracker.engine.Settings
import dev.celitracker.engine.Transaction
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/** Only a default: a site reorganization is fixed in settings, without a new app version. */
const val DEFAULT_CRA_PAGE_URL =
    "https://www.canada.ca/fr/agence-revenu/services/impot/particuliers/sujets/" +
        "compte-epargne-libre-impot/cotiser/calculer-droits.html"

class Repository(private val database: CeliTrackerDatabase) {
    private val dao get() = database.dao()

    suspend fun profile(): Profile? = dao.profile()?.let {
        Profile(
            birthYear = it.birthYear,
            fhsaOpeningDate = it.fhsaOpeningDate,
        )
    }

    suspend fun saveProfile(profile: Profile) {
        dao.saveProfile(
            ProfileEntity(
                birthYear = profile.birthYear,
                fhsaOpeningDate = profile.fhsaOpeningDate,
            ),
        )
    }

    suspend fun limits(): List<AnnualLimit> = dao.limits().map { AnnualLimit(it.account, it.year, it.amount, it.confirmed) }

    suspend fun saveLimits(limits: List<AnnualLimit>) {
        dao.saveLimits(limits.map { it.toEntity() })
    }

    suspend fun saveLimit(limit: AnnualLimit) {
        dao.saveLimit(limit.toEntity())
    }

    suspend fun deleteLimit(account: Account, year: Int) = dao.deleteLimit(account, year)

    suspend fun transactions(): List<Transaction> = dao.transactions().map { Transaction(it.account, it.date, it.type, it.amount, it.id) }

    suspend fun addTransaction(transaction: Transaction) {
        validate(transaction)
        dao.addTransaction(transaction.toEntity())
    }

    suspend fun updateTransaction(transaction: Transaction) {
        validate(transaction)
        val updatedRows = dao.updateTransaction(transaction.toEntity())
        if (updatedRows != 1) rejectInput(InputRejectionReason.TRANSACTION_NOT_FOUND)
    }

    private suspend fun validate(transaction: Transaction) {
        transactionRejection(transaction, profile())?.let(::rejectInput)
    }

    private fun Transaction.toEntity() = TransactionEntity(id, account, date, type, amount)

    private fun AnnualLimit.toEntity() = LimitEntity(account, year, amount, confirmed)

    suspend fun deleteTransaction(id: Long) = dao.deleteTransaction(id)

    suspend fun craSnapshots(): List<CraSnapshot> = dao.craSnapshots().map { CraSnapshot(it.id, it.account, it.referenceDate, it.declaredRoom) }

    suspend fun saveCraSnapshot(snapshot: CraSnapshot) {
        dao.saveCraSnapshot(
            CraSnapshotEntity(
                account = snapshot.account,
                referenceDate = snapshot.referenceDate,
                declaredRoom = snapshot.declaredRoom,
            ),
        )
    }

    suspend fun deleteCraSnapshot(id: Long) = dao.deleteCraSnapshot(id)

    suspend fun settings(): Settings = dao.settings()?.let { Settings(it.craPageUrl, it.lastCheckDate) }
        ?: Settings(craPageUrl = DEFAULT_CRA_PAGE_URL, lastCheckDate = null)

    suspend fun saveSettings(settings: Settings) {
        dao.saveSettings(SettingsEntity(craPageUrl = settings.craPageUrl, lastCheckDate = settings.lastCheckDate))
    }

    /** Writes only the date: a CRA read in progress must not overwrite an address the user just changed. */
    suspend fun recordCraCheck(date: Instant) {
        if (dao.recordCraCheck(date) == 0) {
            dao.saveSettings(SettingsEntity(craPageUrl = settings().craPageUrl, lastCheckDate = date))
        }
    }

    internal suspend fun replaceEverything(
        profile: ProfileEntity?,
        limits: List<LimitEntity>,
        transactions: List<TransactionEntity>,
        snapshots: List<CraSnapshotEntity>,
        settings: SettingsEntity,
    ) = dao.replaceEverything(profile, limits, transactions, snapshots, settings)
}

/**
 * Null when [transaction] may be recorded for [profile]. Saving and importing share it, and it lives here rather
 * than in the engines: they would disagree on a transaction before eligibility.
 */
internal fun transactionRejection(transaction: Transaction, profile: Profile?): InputRejectionReason? {
    if (transaction.amount <= BigDecimal.ZERO) return InputRejectionReason.NON_POSITIVE_AMOUNT
    if (profile == null) return InputRejectionReason.MISSING_PROFILE
    return when (transaction.account) {
        Account.TFSA ->
            InputRejectionReason.TFSA_BEFORE_ELIGIBILITY
                .takeIf { transaction.date.isBefore(LocalDate.of(profile.tfsaEligibilityYear, 1, 1)) }

        Account.FHSA -> when {
            profile.fhsaOpeningDate == null -> InputRejectionReason.FHSA_NOT_OPENED
            transaction.date.isBefore(profile.fhsaOpeningDate) -> InputRejectionReason.FHSA_BEFORE_OPENING
            else -> null
        }
    }
}
