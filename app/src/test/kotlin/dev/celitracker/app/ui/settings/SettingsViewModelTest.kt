@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package dev.celitracker.app.ui.settings

import dev.celitracker.app.R
import dev.celitracker.app.TestMainDispatcher
import dev.celitracker.app.TestRepository
import dev.celitracker.app.ui.text.uiText
import dev.celitracker.data.DEFAULT_CRA_PAGE_URL
import dev.celitracker.engine.Profile
import dev.celitracker.engine.Settings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeAll
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsViewModelTest {

    private val fixture = TestRepository()
    private val repository = fixture.repository

    /** Telechargement bouchonne: aucun test de cette classe ne touche au reseau. */
    private val craPage: suspend (String) -> String = {
        """<p>Le plafond de cotisation à un CELI <span class="nowrap">pour 2027</span> est de 7 500 $.</p>"""
    }

    /**
     * Pour les tests qui ne portent labelStep sur l'ARC. Sans ca, la lecture lancee a
     * la construction du ViewModel ecrit elle aussi dans `message`, et un
     * StateFlow ne garde que la latest value: le message expected par le test
     * peut disparaitre before d'etre vu.
     */
    private val offline: suspend (String) -> String = { throw java.io.IOException("offline") }

    companion object {
        @JvmStatic
        @BeforeAll
        fun before() = TestMainDispatcher.install()
    }

    @Test
    fun `enregistrerProfil persiste le profil valide`() = runTest {
        val viewModel = SettingsViewModel(repository, offline)
        viewModel.updateBirthYear("1995")
        viewModel.updateFhsaOpeningDate("2023-04-01")

        viewModel.saveProfile()
        val state = viewModel.uiState.first { it.message != null }

        assertEquals(uiText(R.string.message_profile_saved), state.message)
        val profile = repository.profile()
        assertEquals(1995, profile?.birthYear)
        // Derivee de la birth, jamais input.
        assertEquals(2013, profile?.tfsaEligibilityYear)
    }

    @Test
    fun `enregistrerProfil ignore une saisie invalide`() = runTest {
        val viewModel = SettingsViewModel(repository, offline)
        viewModel.updateBirthYear("not-a-number")

        viewModel.saveProfile()

        assertNull(repository.profile())
    }

    @Test
    fun `l'annee d'admissibilite affichee suit l'annee de naissance`() = runTest {
        val viewModel = SettingsViewModel(repository, offline)

        viewModel.updateBirthYear("1995")

        assertEquals(2013, viewModel.uiState.value.tfsaEligibilityYear)
    }

    @Test
    fun `une naissance dans le futur n'est pas une saisie valide`() = runTest {
        val viewModel = SettingsViewModel(repository, offline)

        viewModel.updateBirthYear((LocalDate.now().year + 1).toString())

        assertNull(viewModel.uiState.value.validBirthYear)
    }

    @Test
    fun `le plafond lu sur le site de l'ARC attend une confirmation`() = runTest {
        val viewModel = SettingsViewModel(repository, craPage)

        val state = viewModel.uiState.first { it.proposals.isNotEmpty() }

        val proposed = state.proposals.single()
        assertEquals(2027, proposed.year)
        assertEquals(BigDecimal("7500.00"), proposed.amount)
        // La proposition ne account labelStep comme un limit de la table.
        assertTrue(state.confirmedLimits.isEmpty())
    }

    @Test
    fun `confirmer une proposition la fait entrer dans la table`() = runTest {
        val viewModel = SettingsViewModel(repository, craPage)
        val proposed = viewModel.uiState.first { it.proposals.isNotEmpty() }.proposals.single()

        viewModel.confirmProposal(proposed)
        val state = viewModel.uiState.first { it.confirmedLimits.isNotEmpty() }

        assertEquals(listOf(2027), state.confirmedLimits.map { it.year })
        assertTrue(state.proposals.isEmpty())
    }

    @Test
    fun `rejeter une proposition l'efface`() = runTest {
        val viewModel = SettingsViewModel(repository, craPage)
        val proposed = viewModel.uiState.first { it.proposals.isNotEmpty() }.proposals.single()

        viewModel.rejectProposal(proposed)
        val state = viewModel.uiState.first { it.proposals.isEmpty() }

        assertTrue(state.limits.isEmpty())
    }

    @Test
    fun `une lecture impossible est dite, pas tue`() = runTest {
        val viewModel = SettingsViewModel(repository) { throw java.io.IOException("network unavailable") }

        val state = viewModel.uiState.first { it.craError != null }

        assertEquals(uiText(R.string.cra_failure_unreachable), state.craError)
    }

    @Test
    fun `une adresse invalide ne remplace pas celle qui marche`() = runTest {
        val viewModel = SettingsViewModel(repository, offline)

        viewModel.updateCraPageUrl("https://example.com/limits")
        viewModel.saveCraPageUrl()
        val state = viewModel.uiState.first { it.message != null }

        assertEquals(DEFAULT_CRA_PAGE_URL, state.urlPageArc)
        assertEquals(DEFAULT_CRA_PAGE_URL, repository.settings().urlPageArc)
    }

    @Test
    fun `une adresse valide de l'ARC est enregistree telle quelle`() = runTest {
        val otherPage = "https://www.canada.ca/fr/agence-revenu/autre-page.html"
        val viewModel = SettingsViewModel(repository, craPage)

        viewModel.updateCraPageUrl(otherPage)
        viewModel.saveCraPageUrl()
        viewModel.uiState.first { it.message?.id in setOf(R.string.message_address_saved, R.string.message_address_rejected) }

        assertEquals(otherPage, repository.settings().urlPageArc)
    }

    @Test
    fun `l'export rend le contenu de la base et l'import le relit`() = runTest {
        val viewModel = SettingsViewModel(repository, offline)
        viewModel.updateBirthYear("1995")
        viewModel.saveProfile()
        viewModel.uiState.first { it.message == uiText(R.string.message_profile_saved) }

        var exported = ""
        viewModel.exportData { exported = it }
        viewModel.uiState.first { it.message == uiText(R.string.message_data_exported) }

        fixture.repository.saveProfile(Profile(birthYear = 1980, fhsaOpeningDate = null))
        viewModel.importData { exported }
        viewModel.uiState.first { it.message == uiText(R.string.message_data_imported) }

        assertEquals(1995, repository.profile()?.birthYear)
    }

    @Test
    fun `un fichier illisible est refuse sans toucher aux donnees`() = runTest {
        val viewModel = SettingsViewModel(repository, offline)
        viewModel.updateBirthYear("1995")
        viewModel.saveProfile()
        viewModel.uiState.first { it.message == uiText(R.string.message_profile_saved) }

        viewModel.importData { "this is not JSON" }
        val state = viewModel.uiState.first { it.message?.id == R.string.message_import_rejected }

        assertEquals(uiText(R.string.message_import_rejected, uiText(R.string.import_malformed_json)), state.message)
        assertEquals(1995, repository.profile()?.birthYear)
    }

    @Test
    fun `ajouterPlafond persiste et vide les champs de saisie`() = runTest {
        val viewModel = SettingsViewModel(repository, offline)
        viewModel.updateNewLimitYear("2026")
        viewModel.updateNewLimitAmount("7000.00")

        viewModel.addLimit()
        val state = viewModel.uiState.first { it.limits.isNotEmpty() }

        assertEquals(BigDecimal("7000.00"), state.limits.single().amount)
        assertEquals("", state.newLimitYear)
        assertEquals("", state.newLimitAmount)
    }

    @Test
    fun `un montant negatif est refuse par la validation`() {
        val state = SettingsUiState(newLimitYear = "2026", newLimitAmount = "-100")

        assertTrue(!state.isNewLimitValid)
    }

    @Test
    fun `retablir l'adresse d'origine repare une adresse cassee enregistree autrefois`() = runTest {
        repository.saveSettings(Settings(urlPageArc = "https://www.canada.ca/fr/agence-renu/page.html", lastCheckDate = null))
        val viewModel = SettingsViewModel(repository, craPage)

        viewModel.restoreCraPageUrl()
        viewModel.uiState.first { it.message?.id in setOf(R.string.message_address_saved, R.string.message_address_rejected) }

        assertEquals(DEFAULT_CRA_PAGE_URL, repository.settings().urlPageArc)
    }
}
