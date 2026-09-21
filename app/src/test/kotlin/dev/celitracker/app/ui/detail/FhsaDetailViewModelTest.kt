@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package dev.celitracker.app.ui.detail

import dev.celitracker.app.TestMainDispatcher
import dev.celitracker.app.TestRepository
import dev.celitracker.engine.FhsaEngine
import dev.celitracker.engine.Profile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeAll
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class FhsaDetailViewModelTest {

    private val fixture = TestRepository()
    private val repository = fixture.repository
    private val currentYear = LocalDate.now().year

    companion object {
        @JvmStatic
        @BeforeAll
        fun before() = TestMainDispatcher.install()
    }

    @Test
    fun `without an FHSA open, no rows`() = runTest {
        repository.saveProfile(Profile(1990, fhsaOpeningDate = null))

        val viewModel = FhsaDetailViewModel(repository).also { it.load() }

        assertEquals(emptyList(), viewModel.uiState.value.rows)
    }

    @Test
    fun `with an FHSA open, the rows come from the engine`() = runTest {
        val opening = LocalDate.of(currentYear, 3, 1)
        val profile = Profile(1990, fhsaOpeningDate = opening)
        repository.saveProfile(profile)

        val viewModel = FhsaDetailViewModel(repository).also { it.load() }
        val state = viewModel.uiState.first { it.rows.isNotEmpty() }

        val expected = FhsaEngine.roomByYear(profile, repository.transactions(), currentYear)
        assertEquals(expected, state.rows)
    }
}
