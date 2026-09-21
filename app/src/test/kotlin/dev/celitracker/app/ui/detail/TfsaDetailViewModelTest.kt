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
    fun `sans profil, aucune ligne`() = runTest {
        val viewModel = TfsaDetailViewModel(repository).also { it.load() }

        assertEquals(emptyList(), viewModel.uiState.value.rows)
    }

    @Test
    fun `un plafond absent est signale plafondManquant`() = runTest {
        // Naissance il y a 19 ans: admissible au TFSA depuis l'an dernier.
        repository.saveProfile(Profile(currentYear - 19, null))
        // Aucun limit enregistre pour currentYear - 1 ni currentYear.

        val viewModel = TfsaDetailViewModel(repository).also { it.load() }
        val state = viewModel.uiState.first { it.rows.isNotEmpty() }

        assertTrue(state.rows.all { it.limitMissing })
    }

    @Test
    fun `un plafond confirme n'est pas signale`() = runTest {
        repository.saveProfile(Profile(currentYear - 18, null))
        repository.saveLimit(AnnualLimit(Account.TFSA, currentYear, BigDecimal("7000.00"), confirmed = true))

        val viewModel = TfsaDetailViewModel(repository).also { it.load() }
        val state = viewModel.uiState.first { it.rows.isNotEmpty() }

        assertEquals(listOf(false), state.rows.map { it.limitMissing })
    }

    @Test
    fun `seules les annees avec des transactions CELI offrent un lien vers le journal`() = runTest {
        repository.saveProfile(Profile(currentYear - 20, LocalDate.of(currentYear - 1, 1, 1)))
        repository.addTransaction(Transaction(Account.TFSA, LocalDate.of(currentYear, 1, 5), TransactionType.DEPOSIT, BigDecimal("100.00")))
        repository.addTransaction(Transaction(Account.FHSA, LocalDate.of(currentYear - 1, 6, 1), TransactionType.DEPOSIT, BigDecimal("100.00")))

        val viewModel = TfsaDetailViewModel(repository).also { it.load() }
        val state = viewModel.uiState.first { it.rows.isNotEmpty() }

        assertEquals(setOf(currentYear), state.yearsWithTransactions)
    }
}
