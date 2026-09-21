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
 * Page de l'ARC qui enonce le limit TFSA de l'year en cours. Une donnee,
 * labelStep une constante figee dans le code appelant: une reorganisation du site se
 * corrige dans les settings, sans nouvelle version de l'application.
 */
const val DEFAULT_CRA_PAGE_URL =
    "https://www.canada.ca/fr/agence-revenu/services/impot/particuliers/sujets/" +
        "compte-epargne-libre-impot/cotiser/calculer-droits.html"

/**
 * Expose uniquement les types de `:engine`, jamais les entites Room. Aucune
 * value calculee n'est persistee: les room sont recalcules a la lecture
 * par `:engine`.
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
     * Rejette les saisies incoherentes ici, labelStep dans le moteur: une
     * transaction earlier a l'year d'admissibilite produirait des
     * resultats differents selon le moteur consulte.
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

    suspend fun settings(): Settings = dao.settings()?.let { Settings(it.urlPageArc, it.lastCheckDate) }
        ?: Settings(urlPageArc = DEFAULT_CRA_PAGE_URL, lastCheckDate = null)

    suspend fun saveSettings(settings: Settings) {
        dao.saveSettings(SettingsEntity(urlPageArc = settings.urlPageArc, lastCheckDate = settings.lastCheckDate))
    }

    /**
     * Ecrit la seule date de verification, sans relire ni reecrire l'address:
     * une lecture ARC en cours ne doit labelStep ecraser une address que
     * l'utilisateur vient de changer.
     */
    suspend fun recordCraCheck(date: Instant) {
        if (dao.recordCraCheck(date) == 0) {
            dao.saveSettings(SettingsEntity(urlPageArc = settings().urlPageArc, lastCheckDate = date))
        }
    }

    /** Reserve a `importJson` : remplace whole le content en une transaction. */
    internal suspend fun replaceEverything(
        profile: ProfileEntity?,
        limits: List<LimitEntity>,
        transactions: List<TransactionEntity>,
        snapshots: List<CraSnapshotEntity>,
        settings: SettingsEntity,
    ) = dao.replaceEverything(profile, limits, transactions, snapshots, settings)
}
