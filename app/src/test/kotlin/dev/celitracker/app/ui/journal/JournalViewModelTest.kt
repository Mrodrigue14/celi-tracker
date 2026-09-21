@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package dev.celitracker.app.ui.journal

import dev.celitracker.app.R
import dev.celitracker.app.TestMainDispatcher
import dev.celitracker.app.TestRepository
import dev.celitracker.app.ui.text.uiText
import dev.celitracker.engine.Account
import dev.celitracker.engine.AnnualLimit
import dev.celitracker.engine.Profile
import dev.celitracker.engine.Transaction
import dev.celitracker.engine.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeAll
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JournalViewModelTest {

    private val fixture = TestRepository()
    private val repository = fixture.repository

    /** Naissance en 2002: admissible au TFSA en 2020. */
    private val profile = Profile(birthYear = 2002, fhsaOpeningDate = LocalDate.of(2023, 6, 1))

    companion object {
        @JvmStatic
        @BeforeAll
        fun before() = TestMainDispatcher.install()
    }

    private fun tfsaDeposit(date: LocalDate, amount: String) = Transaction(Account.TFSA, date, TransactionType.DEPOSIT, BigDecimal(amount))

    @Test
    fun `n'affiche que les transactions du compte, la plus recente d'abord`() = runTest {
        repository.saveProfile(profile)
        repository.addTransaction(tfsaDeposit(LocalDate.of(2024, 1, 10), "100.00"))
        repository.addTransaction(tfsaDeposit(LocalDate.of(2025, 1, 10), "200.00"))
        repository.addTransaction(Transaction(Account.FHSA, LocalDate.of(2024, 5, 1), TransactionType.DEPOSIT, BigDecimal("300.00")))

        val viewModel = JournalViewModel(repository, Account.TFSA)
        val state = viewModel.uiState.first { it.transactions.isNotEmpty() }

        assertEquals(listOf(LocalDate.of(2025, 1, 10), LocalDate.of(2024, 1, 10)), state.transactions.map { it.date })
    }

    @Test
    fun `une nouvelle transaction valide est enregistree et le formulaire se ferme`() = runTest {
        repository.saveProfile(profile)
        val viewModel = JournalViewModel(repository, Account.TFSA)

        viewModel.openNew()
        viewModel.updateDate("2025-02-03")
        viewModel.updateAmount("150,25")
        viewModel.save()
        viewModel.uiState.first { it.form == null }

        assertEquals(listOf(tfsaDeposit(LocalDate.of(2025, 2, 3), "150.25")), repository.transactions().map { it.copy(id = 0) })
    }

    @Test
    fun `le message est efface une fois affiche`() = runTest {
        repository.saveProfile(profile)
        val viewModel = JournalViewModel(repository, Account.TFSA)

        viewModel.openNew()
        viewModel.updateDate("2025-02-03")
        viewModel.updateAmount("150,25")
        viewModel.save()
        viewModel.uiState.first { it.message != null }
        viewModel.messageShown()

        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun `un refus du depot garde le formulaire ouvert avec l'erreur`() = runTest {
        repository.saveProfile(profile)
        val viewModel = JournalViewModel(repository, Account.TFSA)

        viewModel.openNew()
        viewModel.updateDate("2019-12-31")
        viewModel.updateAmount("100")
        viewModel.save()
        val form = viewModel.uiState.first { it.form?.error != null }.form

        assertNotNull(form)
        assertEquals(emptyList(), repository.transactions())
    }

    @Test
    fun `une modification remplace la transaction existante`() = runTest {
        repository.saveProfile(profile)
        repository.addTransaction(tfsaDeposit(LocalDate.of(2024, 1, 10), "100.00"))
        val viewModel = JournalViewModel(repository, Account.TFSA)
        val existing = viewModel.uiState.first { it.transactions.isNotEmpty() }.transactions.single()

        viewModel.openEdit(existing)
        viewModel.updateType(TransactionType.WITHDRAWAL)
        viewModel.save()
        viewModel.uiState.first { it.form == null }

        assertEquals(listOf(existing.copy(type = TransactionType.WITHDRAWAL)), repository.transactions())
    }

    @Test
    fun `la suppression retire la transaction`() = runTest {
        repository.saveProfile(profile)
        repository.addTransaction(tfsaDeposit(LocalDate.of(2024, 1, 10), "100.00"))
        val viewModel = JournalViewModel(repository, Account.TFSA)
        val existing = viewModel.uiState.first { it.transactions.isNotEmpty() }.transactions.single()

        viewModel.openEdit(existing)
        viewModel.delete()
        val state = viewModel.uiState.first { it.form == null }

        assertEquals(emptyList(), state.transactions)
    }

    @Test
    fun `ouvrir une modification affiche toujours deux decimales, meme sur un montant entier`() = runTest {
        repository.saveProfile(profile)
        repository.addTransaction(tfsaDeposit(LocalDate.of(2024, 1, 10), "8000"))
        val viewModel = JournalViewModel(repository, Account.TFSA)
        val existing = viewModel.uiState.first { it.transactions.isNotEmpty() }.transactions.single()

        viewModel.openEdit(existing)
        val form = viewModel.uiState.first().form

        assertEquals("8000.00", form?.amount)
    }

    /** 2020 est la premiere year d'admissibilite du profile: ses room valent son limit. */
    private suspend fun room2020(limit: String = "6000.00") {
        repository.saveProfile(profile)
        repository.saveLimit(AnnualLimit(Account.TFSA, 2020, BigDecimal(limit)))
    }

    private fun JournalViewModel.enterDeposit(date: String, amount: String) {
        openNew()
        updateDate(date)
        updateAmount(amount)
        save()
    }

    @Test
    fun `un depot qui porte l'utilisation a 95 pour cent ou plus demande une confirmation`() = runTest {
        room2020()
        val viewModel = JournalViewModel(repository, Account.TFSA)

        viewModel.enterDeposit("2020-06-01", "5820")
        val warning = assertNotNull(viewModel.uiState.first { it.form?.warning != null }.form?.warning)

        assertEquals(uiText(R.string.journal_warning_critical, 97, Account.TFSA, 2020, BigDecimal("180.00")), warning)
        assertEquals(emptyList(), repository.transactions())
    }

    @Test
    fun `confirmer l'avertissement enregistre le depot`() = runTest {
        room2020()
        val viewModel = JournalViewModel(repository, Account.TFSA)
        viewModel.enterDeposit("2020-06-01", "5820")
        viewModel.uiState.first { it.form?.warning != null }

        viewModel.save()
        viewModel.uiState.first { it.form == null }

        assertEquals(1, repository.transactions().size)
    }

    @Test
    fun `un depot au-dela des droits annonce l'excedent`() = runTest {
        room2020()
        val viewModel = JournalViewModel(repository, Account.TFSA)

        viewModel.enterDeposit("2020-06-01", "6100")
        val warning = assertNotNull(viewModel.uiState.first { it.form?.warning != null }.form?.warning)

        assertEquals(uiText(R.string.journal_warning_exceeded, Account.TFSA, 2020, BigDecimal("100.00")), warning)
    }

    @Test
    fun `entre 80 et 95 pour cent, le depot passe et le message donne l'utilisation`() = runTest {
        room2020()
        val viewModel = JournalViewModel(repository, Account.TFSA)

        viewModel.enterDeposit("2020-06-01", "5100")
        val state = viewModel.uiState.first { it.form == null && it.message != null }

        assertEquals(1, repository.transactions().size)
        assertEquals(uiText(R.string.journal_saved_usage, 85, Account.TFSA, 2020), state.message)
    }

    @Test
    fun `un retrait ne demande jamais de confirmation`() = runTest {
        room2020()
        repository.addTransaction(Transaction(Account.TFSA, LocalDate.of(2020, 3, 1), TransactionType.DEPOSIT, BigDecimal("5900.00")))
        val viewModel = JournalViewModel(repository, Account.TFSA)

        viewModel.openNew()
        viewModel.updateDate("2020-06-01")
        viewModel.updateType(TransactionType.WITHDRAWAL)
        viewModel.updateAmount("100")
        viewModel.save()
        viewModel.uiState.first { it.form == null }

        assertEquals(2, repository.transactions().size)
    }

    @Test
    fun `venir du bouton Ajouter ouvre directement la feuille de saisie`() = runTest {
        repository.saveProfile(profile)

        val viewModel = JournalViewModel(repository, Account.TFSA, openAdd = true)

        assertNotNull(viewModel.uiState.value.form)
    }

    @Test
    fun `ouvrir le journal normalement n'ouvre pas de feuille`() = runTest {
        val viewModel = JournalViewModel(repository, Account.TFSA)

        assertNull(viewModel.uiState.value.form)
    }

    @Test
    fun `changer de compte affiche le journal de l'autre compte sans quitter l'ecran`() = runTest {
        repository.saveProfile(profile)
        repository.addTransaction(tfsaDeposit(LocalDate.of(2024, 1, 10), "100.00"))
        repository.addTransaction(Transaction(Account.FHSA, LocalDate.of(2024, 5, 1), TransactionType.DEPOSIT, BigDecimal("300.00")))
        val viewModel = JournalViewModel(repository, Account.TFSA)
        viewModel.uiState.first { it.transactions.isNotEmpty() }

        viewModel.changeAccount(Account.FHSA)
        val state = viewModel.uiState.first { it.account == Account.FHSA && it.transactions.isNotEmpty() }

        assertEquals(listOf(Account.FHSA), state.transactions.map { it.account })
    }

    @Test
    fun `l'annee demandee depuis le detail n'est visee qu'une fois`() = runTest {
        val viewModel = JournalViewModel(repository, Account.TFSA, targetYear = 2024)
        assertEquals(2024, viewModel.uiState.value.targetYear)

        viewModel.targetYearReached()

        assertNull(viewModel.uiState.value.targetYear)
    }
}
