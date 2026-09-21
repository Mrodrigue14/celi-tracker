@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package dev.celitracker.app.ui.settings

import dev.celitracker.app.R
import dev.celitracker.app.TestMainDispatcher
import dev.celitracker.app.TestRepository
import dev.celitracker.app.ui.text.uiText
import dev.celitracker.data.DEFAULT_CRA_PAGE_URL
import dev.celitracker.engine.Account
import dev.celitracker.engine.CraSnapshot
import dev.celitracker.engine.Profile
import dev.celitracker.engine.Settings
import dev.celitracker.engine.UNSAVED_ID
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

    private val craPage: suspend (String) -> String = {
        """<p>Le plafond de cotisation à un CELI <span class="nowrap">pour 2027</span> est de 7 500 $.</p>"""
    }

    /** The startup CRA reading also writes `message`, and a StateFlow keeps only the latest value. */
    private val offline: suspend (String) -> String = { throw java.io.IOException("offline") }

    companion object {
        @JvmStatic
        @BeforeAll
        fun before() = TestMainDispatcher.install()
    }

    @Test
    fun `saveProfile persists the valid profile`() = runTest {
        val viewModel = SettingsViewModel(repository, offline)
        viewModel.updateBirthYear("1995")
        viewModel.updateFhsaOpeningDate("2023-04-01")

        viewModel.saveProfile()
        val state = viewModel.uiState.first { it.message != null }

        assertEquals(uiText(R.string.message_profile_saved), state.message)
        val profile = repository.profile()
        assertEquals(1995, profile?.birthYear)
        assertEquals(2013, profile?.tfsaEligibilityYear)
    }

    @Test
    fun `saveProfile ignores invalid input`() = runTest {
        val viewModel = SettingsViewModel(repository, offline)
        viewModel.updateBirthYear("not-a-number")

        viewModel.saveProfile()

        assertNull(repository.profile())
    }

    @Test
    fun `the displayed eligibility year follows the birth year`() = runTest {
        val viewModel = SettingsViewModel(repository, offline)

        viewModel.updateBirthYear("1995")

        assertEquals(2013, viewModel.uiState.value.tfsaEligibilityYear)
    }

    @Test
    fun `a birth date in the future is not valid input`() = runTest {
        val viewModel = SettingsViewModel(repository, offline)

        viewModel.updateBirthYear((LocalDate.now().year + 1).toString())

        assertNull(viewModel.uiState.value.validBirthYear)
    }

    @Test
    fun `the limit read from the CRA website awaits confirmation`() = runTest {
        val viewModel = SettingsViewModel(repository, craPage)

        val state = viewModel.uiState.first { it.unconfirmedLimits.isNotEmpty() }

        val proposed = state.unconfirmedLimits.single()
        assertEquals(2027, proposed.year)
        assertEquals(BigDecimal("7500.00"), proposed.amount)
        assertTrue(state.confirmedLimits.isEmpty())
    }

    @Test
    fun `confirming a proposal makes it enter the table`() = runTest {
        val viewModel = SettingsViewModel(repository, craPage)
        val proposed = viewModel.uiState.first { it.unconfirmedLimits.isNotEmpty() }.unconfirmedLimits.single()

        viewModel.confirmProposal(proposed)
        val state = viewModel.uiState.first { it.confirmedLimits.isNotEmpty() }

        assertEquals(listOf(2027), state.confirmedLimits.map { it.year })
        assertTrue(state.unconfirmedLimits.isEmpty())
    }

    @Test
    fun `rejecting a proposal erases it`() = runTest {
        val viewModel = SettingsViewModel(repository, craPage)
        val proposed = viewModel.uiState.first { it.unconfirmedLimits.isNotEmpty() }.unconfirmedLimits.single()

        viewModel.rejectProposal(proposed)
        val state = viewModel.uiState.first { it.unconfirmedLimits.isEmpty() }

        assertTrue(state.limits.isEmpty())
    }

    @Test
    fun `a failed reading is reported, not silenced`() = runTest {
        val viewModel = SettingsViewModel(repository) { throw java.io.IOException("network unavailable") }

        val state = viewModel.uiState.first { it.craError != null }

        assertEquals(uiText(R.string.cra_failure_unreachable), state.craError)
    }

    @Test
    fun `an invalid address does not replace the one that works`() = runTest {
        val viewModel = SettingsViewModel(repository, offline)

        viewModel.updateCraPageUrl("https://example.com/limits")
        viewModel.saveCraPageUrl()
        val state = viewModel.uiState.first { it.message != null }

        assertEquals(DEFAULT_CRA_PAGE_URL, state.craPageUrl)
        assertEquals(DEFAULT_CRA_PAGE_URL, repository.settings().craPageUrl)
    }

    @Test
    fun `a valid CRA address is saved as is`() = runTest {
        val otherPage = "https://www.canada.ca/fr/agence-revenu/autre-page.html"
        val viewModel = SettingsViewModel(repository, craPage)

        viewModel.updateCraPageUrl(otherPage)
        viewModel.saveCraPageUrl()
        viewModel.uiState.first { it.message?.id in setOf(R.string.message_address_saved, R.string.message_address_rejected) }

        assertEquals(otherPage, repository.settings().craPageUrl)
    }

    @Test
    fun `export returns the database contents and import reads them back`() = runTest {
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
    fun `an unreadable file is rejected without touching the data`() = runTest {
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
    fun `addLimit persists and clears the input fields`() = runTest {
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
    fun `a negative amount is rejected by validation`() {
        val state = SettingsUiState(newLimitYear = "2026", newLimitAmount = "-100")

        assertTrue(!state.isNewLimitValid)
    }

    @Test
    fun `restoring the default address fixes a broken address saved in the past`() = runTest {
        repository.saveSettings(Settings(craPageUrl = "https://www.canada.ca/fr/agence-renu/page.html", lastCheckDate = null))
        val viewModel = SettingsViewModel(repository, craPage)

        viewModel.restoreCraPageUrl()
        viewModel.uiState.first { it.message?.id in setOf(R.string.message_address_saved, R.string.message_address_rejected) }

        assertEquals(DEFAULT_CRA_PAGE_URL, repository.settings().craPageUrl)
    }

    @Test
    fun `saving a CRA figure stores it, clears the form and confirms`() = runTest {
        val viewModel = SettingsViewModel(repository, offline)
        val date = LocalDate.now().minusDays(3)
        viewModel.updateSnapshotAccount(Account.FHSA)
        viewModel.updateSnapshotDate(date.toString())
        viewModel.updateSnapshotAmount("16000,00")

        viewModel.saveSnapshot()
        val state = viewModel.uiState.first { it.message == uiText(R.string.message_cra_snapshot_saved) }

        assertEquals(listOf(CraSnapshot(state.snapshots.single().id, Account.FHSA, date, BigDecimal("16000.00"))), repository.craSnapshots())
        assertEquals("", state.snapshotAmount)
        assertEquals("", state.snapshotDate)
    }

    @Test
    fun `an invalid CRA figure is not saved`() = runTest {
        val viewModel = SettingsViewModel(repository, offline)
        viewModel.updateSnapshotDate(LocalDate.now().plusDays(1).toString())
        viewModel.updateSnapshotAmount("100")

        viewModel.saveSnapshot()

        assertEquals(emptyList(), repository.craSnapshots())
    }

    @Test
    fun `saved CRA figures are listed newest first and can be deleted`() = runTest {
        val today = LocalDate.now()
        repository.saveCraSnapshot(CraSnapshot(UNSAVED_ID, Account.TFSA, today.minusDays(30), BigDecimal("1")))
        repository.saveCraSnapshot(CraSnapshot(UNSAVED_ID, Account.TFSA, today.minusDays(2), BigDecimal("2")))
        val viewModel = SettingsViewModel(repository, offline)
        val listed = viewModel.uiState.first { it.snapshots.size == 2 }.snapshots
        assertEquals(listOf(BigDecimal("2"), BigDecimal("1")), listed.map { it.declaredRoom })

        viewModel.deleteSnapshot(listed.first())
        val state = viewModel.uiState.first { it.message == uiText(R.string.message_cra_snapshot_deleted) }

        assertEquals(listOf(BigDecimal("1")), state.snapshots.map { it.declaredRoom })
        assertEquals(listOf(BigDecimal("1")), repository.craSnapshots().map { it.declaredRoom })
    }
}
