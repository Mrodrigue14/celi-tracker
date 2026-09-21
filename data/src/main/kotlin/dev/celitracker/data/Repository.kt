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

/**
 * CRA page that states the current year's TFSA limit. A piece of data, not a
 * constant frozen in the calling code: a site reorganization is fixed in
 * settings, without a new version of the app.
 */
const val DEFAULT_CRA_PAGE_URL =
    "https://www.canada.ca/fr/agence-revenu/services/impot/particuliers/sujets/" +
        "compte-epargne-libre-impot/cotiser/calculer-droits.html"

/**
 * Only exposes types from `:engine`, never Room entities. No calculated
 * value is persisted: contribution room is recalculated on read by `:engine`.
 */
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
        dao.saveLimits(limits.map { LimitEntity(it.account, it.year, it.amount, it.confirmed) })
    }

    suspend fun saveLimit(limit: AnnualLimit) {
        dao.saveLimit(LimitEntity(limit.account, limit.year, limit.amount, limit.confirmed))
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

    /**
     * Rejects inconsistent input here, not in the engine: a transaction
     * earlier than the eligibility year would produce different results
     * depending on which engine is consulted.
     */
    private suspend fun validate(transaction: Transaction) {
        if (transaction.amount <= BigDecimal.ZERO) rejectInput(InputRejectionReason.NON_POSITIVE_AMOUNT)
        val profile = profile() ?: rejectInput(InputRejectionReason.MISSING_PROFILE)
        when (transaction.account) {
            Account.TFSA -> if (transaction.date.isBefore(LocalDate.of(profile.tfsaEligibilityYear, 1, 1))) {
                rejectInput(InputRejectionReason.TFSA_BEFORE_ELIGIBILITY)
            }

            Account.FHSA -> {
                val opening = profile.fhsaOpeningDate ?: rejectInput(InputRejectionReason.FHSA_NOT_OPENED)
                if (transaction.date.isBefore(opening)) rejectInput(InputRejectionReason.FHSA_BEFORE_OPENING)
            }
        }
    }

    private fun Transaction.toEntity() = TransactionEntity(id, account, date, type, amount)

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

    suspend fun settings(): Settings = dao.settings()?.let { Settings(it.craPageUrl, it.lastCheckDate) }
        ?: Settings(craPageUrl = DEFAULT_CRA_PAGE_URL, lastCheckDate = null)

    suspend fun saveSettings(settings: Settings) {
        dao.saveSettings(SettingsEntity(craPageUrl = settings.craPageUrl, lastCheckDate = settings.lastCheckDate))
    }

    /**
     * Writes only the check date, without re-reading or rewriting the
     * address: an in-progress CRA read must not overwrite an address the
     * user just changed.
     */
    suspend fun recordCraCheck(date: Instant) {
        if (dao.recordCraCheck(date) == 0) {
            dao.saveSettings(SettingsEntity(craPageUrl = settings().craPageUrl, lastCheckDate = date))
        }
    }

    /** Reserved for `importJson`: replaces the entire content in one transaction. */
    internal suspend fun replaceEverything(
        profile: ProfileEntity?,
        limits: List<LimitEntity>,
        transactions: List<TransactionEntity>,
        snapshots: List<CraSnapshotEntity>,
        settings: SettingsEntity,
    ) = dao.replaceEverything(profile, limits, transactions, snapshots, settings)
}
