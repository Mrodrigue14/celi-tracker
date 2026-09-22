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
import org.junit.jupiter.api.extension.ExtendWith
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@ExtendWith(TestMainDispatcher::class)
class JournalViewModelTest {
    private val fixture = TestRepository()
    private val repository = fixture.repository

    /** Born in 2002: eligible for the TFSA in 2020. */
    private val profile = Profile(birthYear = 2002, fhsaOpeningDate = LocalDate.of(2023, 6, 1))

    private fun tfsaDeposit(date: LocalDate, amount: String) = Transaction(Account.TFSA, date, TransactionType.DEPOSIT, BigDecimal(amount))

    @Test
    fun `shows only the account's transactions, most recent first`() = runTest {
        repository.saveProfile(profile)
        repository.addTransaction(tfsaDeposit(LocalDate.of(2024, 1, 10), "100.00"))
        repository.addTransaction(tfsaDeposit(LocalDate.of(2025, 1, 10), "200.00"))
        repository.addTransaction(Transaction(Account.FHSA, LocalDate.of(2024, 5, 1), TransactionType.DEPOSIT, BigDecimal("300.00")))

        val viewModel = JournalViewModel(repository, Account.TFSA)
        val state = viewModel.uiState.first { it.transactions.isNotEmpty() }

        assertEquals(listOf(LocalDate.of(2025, 1, 10), LocalDate.of(2024, 1, 10)), state.transactions.map { it.date })
    }

    @Test
    fun `a valid new transaction is saved and the form closes`() = runTest {
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
    fun `the message is cleared once shown`() = runTest {
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
    fun `a rejected deposit keeps the form open with the error`() = runTest {
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
    fun `an edit replaces the existing transaction`() = runTest {
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
    fun `deletion removes the transaction`() = runTest {
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
    fun `opening an edit always shows two decimals, even for a whole amount`() = runTest {
        repository.saveProfile(profile)
        repository.addTransaction(tfsaDeposit(LocalDate.of(2024, 1, 10), "8000"))
        val viewModel = JournalViewModel(repository, Account.TFSA)
        val existing = viewModel.uiState.first { it.transactions.isNotEmpty() }.transactions.single()

        viewModel.openEdit(existing)
        val form = viewModel.uiState.first().form

        assertEquals("8000.00", form?.amount)
    }

    /** 2020 is the profile's first eligibility year: its room equals its limit. */
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
    fun `a deposit that pushes usage to 95 percent or more asks for confirmation`() = runTest {
        room2020()
        val viewModel = JournalViewModel(repository, Account.TFSA)

        viewModel.enterDeposit("2020-06-01", "5820")
        val warning = assertNotNull(viewModel.uiState.first { it.form?.warning != null }.form?.warning)

        assertEquals(uiText(R.string.journal_warning_critical, 97, Account.TFSA, 2020, BigDecimal("180.00")), warning)
        assertEquals(emptyList(), repository.transactions())
    }

    @Test
    fun `confirming the warning saves the deposit`() = runTest {
        room2020()
        val viewModel = JournalViewModel(repository, Account.TFSA)
        viewModel.enterDeposit("2020-06-01", "5820")
        viewModel.uiState.first { it.form?.warning != null }

        viewModel.save()
        viewModel.uiState.first { it.form == null }

        assertEquals(1, repository.transactions().size)
    }

    @Test
    fun `a deposit beyond the room announces the excess`() = runTest {
        room2020()
        val viewModel = JournalViewModel(repository, Account.TFSA)

        viewModel.enterDeposit("2020-06-01", "6100")
        val warning = assertNotNull(viewModel.uiState.first { it.form?.warning != null }.form?.warning)

        assertEquals(uiText(R.string.journal_warning_exceeded, Account.TFSA, 2020, BigDecimal("100.00")), warning)
    }

    @Test
    fun `between 80 and 95 percent, the deposit goes through and the message states usage`() = runTest {
        room2020()
        val viewModel = JournalViewModel(repository, Account.TFSA)

        viewModel.enterDeposit("2020-06-01", "5100")
        val state = viewModel.uiState.first { it.form == null && it.message != null }

        assertEquals(1, repository.transactions().size)
        assertEquals(uiText(R.string.journal_saved_usage, 85, Account.TFSA, 2020), state.message)
    }

    @Test
    fun `a withdrawal never asks for confirmation`() = runTest {
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
    fun `coming from the Add button opens the input sheet directly`() = runTest {
        repository.saveProfile(profile)

        val viewModel = JournalViewModel(repository, Account.TFSA, openAdd = true)

        assertNotNull(viewModel.uiState.value.form)
    }

    @Test
    fun `opening the journal normally does not open a sheet`() = runTest {
        val viewModel = JournalViewModel(repository, Account.TFSA)

        assertNull(viewModel.uiState.value.form)
    }

    @Test
    fun `switching accounts shows the other account's journal without leaving the screen`() = runTest {
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
    fun `the year requested from the detail screen is targeted only once`() = runTest {
        val viewModel = JournalViewModel(repository, Account.TFSA, scrollToYear = 2024)
        assertEquals(2024, viewModel.uiState.value.scrollToYear)

        viewModel.scrollToYearDone()

        assertNull(viewModel.uiState.value.scrollToYear)
    }
}
