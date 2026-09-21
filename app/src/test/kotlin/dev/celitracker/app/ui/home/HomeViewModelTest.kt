@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package dev.celitracker.app.ui.home

import dev.celitracker.app.TestMainDispatcher
import dev.celitracker.app.TestRepository
import dev.celitracker.engine.Account
import dev.celitracker.engine.AnnualLimit
import dev.celitracker.engine.Profile
import dev.celitracker.engine.TfsaEngine
import dev.celitracker.engine.Transaction
import dev.celitracker.engine.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeAll
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HomeViewModelTest {

    private val fixture = TestRepository()
    private val repository = fixture.repository
    private val currentYear = LocalDate.now().year

    companion object {
        @JvmStatic
        @BeforeAll
        fun before() = TestMainDispatcher.install()
    }

    @Test
    fun `without a profile, the state stays empty once the database has loaded`() = runTest {
        val viewModel = HomeViewModel(repository).also { it.load() }

        val state = viewModel.uiState.first { it.loaded }

        assertFalse(state.hasProfile)
    }

    @Test
    fun `with a profile and limit, the room comes from the engine`() = runTest {
        val profile = Profile(
            birthYear = 1990,
            fhsaOpeningDate = null,
        )
        repository.saveProfile(profile)
        val limit = AnnualLimit(Account.TFSA, currentYear, BigDecimal("7000.00"), confirmed = true)
        repository.saveLimit(limit)
        repository.addTransaction(
            Transaction(Account.TFSA, LocalDate.of(currentYear, 1, 15), TransactionType.DEPOSIT, BigDecimal("1000.00")),
        )

        val viewModel = HomeViewModel(repository).also { it.load() }
        val state = viewModel.uiState.first { it.hasProfile }

        val expected = TfsaEngine.roomByYear(
            profile,
            listOf(limit),
            repository.transactions(),
            currentYear,
        ).last()
        assertEquals(expected, state.tfsaCurrentYear)
    }
}
