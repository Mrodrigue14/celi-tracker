@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package dev.celitracker.app.ui.detail

import dev.celitracker.app.TestMainDispatcher
import dev.celitracker.app.TestRepository
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
import kotlin.test.assertTrue

class TfsaDetailViewModelTest {

    private val fixture = TestRepository()
    private val repository = fixture.repository
    private val currentYear = LocalDate.now().year

    companion object {
        @JvmStatic
        @BeforeAll
        fun before() = TestMainDispatcher.install()
    }

    @Test
    fun `without a profile, no rows`() = runTest {
        val viewModel = TfsaDetailViewModel(repository).also { it.load() }

        assertEquals(emptyList(), viewModel.uiState.value.rows)
    }

    @Test
    fun `a missing limit is flagged as limitMissing`() = runTest {
        // Born 19 years ago: TFSA-eligible since last year.
        repository.saveProfile(Profile(currentYear - 19, null))
        // No limit recorded for currentYear - 1 or currentYear.

        val viewModel = TfsaDetailViewModel(repository).also { it.load() }
        val state = viewModel.uiState.first { it.rows.isNotEmpty() }

        assertTrue(state.rows.all { it.limitMissing })
    }

    @Test
    fun `a confirmed limit is not flagged`() = runTest {
        repository.saveProfile(Profile(currentYear - 18, null))
        repository.saveLimit(AnnualLimit(Account.TFSA, currentYear, BigDecimal("7000.00"), confirmed = true))

        val viewModel = TfsaDetailViewModel(repository).also { it.load() }
        val state = viewModel.uiState.first { it.rows.isNotEmpty() }

        assertEquals(listOf(false), state.rows.map { it.limitMissing })
    }

    @Test
    fun `only years with TFSA transactions offer a link to the journal`() = runTest {
        repository.saveProfile(Profile(currentYear - 20, LocalDate.of(currentYear - 1, 1, 1)))
        repository.addTransaction(Transaction(Account.TFSA, LocalDate.of(currentYear, 1, 5), TransactionType.DEPOSIT, BigDecimal("100.00")))
        repository.addTransaction(Transaction(Account.FHSA, LocalDate.of(currentYear - 1, 6, 1), TransactionType.DEPOSIT, BigDecimal("100.00")))

        val viewModel = TfsaDetailViewModel(repository).also { it.load() }
        val state = viewModel.uiState.first { it.rows.isNotEmpty() }

        assertEquals(setOf(currentYear), state.yearsWithTransactions)
    }
}
