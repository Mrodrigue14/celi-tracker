package dev.celitracker.data

import androidx.room.Room
import dev.celitracker.engine.Account
import dev.celitracker.engine.AnnualLimit
import dev.celitracker.engine.Settings
import kotlinx.coroutines.test.runTest
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CraCheckTest {

    private val file = File.createTempFile("celi-tracker-arc", ".db")
    private val database = configureDatabase(Room.databaseBuilder<CeliTrackerDatabase>(name = file.absolutePath))
    private val repository = Repository(database)
    private val today = LocalDate.of(2026, 9, 17)

    @AfterTest
    fun close() {
        database.close()
        file.delete()
    }

    private val craPage = """
        <p>Le plafond de cotisation à un compte d'épargne libre <span class="nowrap">d'impôt (CELI)</span>
        <span class="nowrap">pour 2027</span> est <span class="nowrap">de 7 500 $</span>.</p>
    """.trimIndent()

    @Test
    fun `the limit read is saved as a proposal`() = runTest {
        repository.saveLimit(AnnualLimit(Account.TFSA, 2026, BigDecimal("7000.00"), confirmed = true))

        val result = repository.checkCraLimits({ craPage }, today)

        assertIs<CraCheckResult.Proposed>(result)
        val proposed = repository.limits().single { it.year == 2027 }
        assertEquals(BigDecimal("7500.00"), proposed.amount)
        assertEquals(false, proposed.confirmed)
    }

    @Test
    fun `no read when the current and next year are already known`() = runTest {
        repository.saveLimit(AnnualLimit(Account.TFSA, 2026, BigDecimal("7000.00"), confirmed = true))
        repository.saveLimit(AnnualLimit(Account.TFSA, 2027, BigDecimal("7500.00"), confirmed = true))

        val result = repository.checkCraLimits({ error("the page must not be downloaded") }, today)

        assertEquals(CraCheckResult.NotNeeded, result)
    }

    @Test
    fun `a check less than a month old is not redone`() = runTest {
        repository.saveSettings(
            Settings(
                craPageUrl = DEFAULT_CRA_PAGE_URL,
                lastCheckDate = today.minusDays(5).atStartOfDay(ZoneOffset.UTC).toInstant(),
            ),
        )

        val result = repository.checkCraLimits({ error("the page must not be downloaded") }, today)

        assertEquals(CraCheckResult.NotNeeded, result)
    }

    @Test
    fun `an unreachable page is a failure, and the date is still recorded`() = runTest {
        val result = repository.checkCraLimits({ throw java.io.IOException("network unavailable") }, today)

        assertIs<CraCheckResult.Failed>(result)
        assertEquals(
            today.atStartOfDay(ZoneOffset.UTC).toInstant(),
            repository.settings().lastCheckDate,
        )
    }

    @Test
    fun `an unreadable page is a failure and writes no limit`() = runTest {
        val result = repository.checkCraLimits({ "<h1>Page non trouvée</h1>" }, today)

        assertIs<CraCheckResult.Failed>(result)
        assertTrue(repository.limits().isEmpty())
    }

    @Test
    fun `a limit already entered for the read year is not replaced`() = runTest {
        repository.saveLimit(AnnualLimit(Account.TFSA, 2027, BigDecimal("7000.00"), confirmed = true))

        val result = repository.checkCraLimits({ craPage }, today)

        assertEquals(CraCheckResult.NotNeeded, result)
        assertEquals(BigDecimal("7000.00"), repository.limits().single { it.year == 2027 }.amount)
    }

    @Test
    fun `an address whose page gives the limit is saved`() = runTest {
        val other = "https://www.canada.ca/fr/agence-revenu/autre-page.html"

        val result = repository.changeCraPageUrl(other) { craPage }

        assertEquals(CraAddressResult.Saved, result)
        assertEquals(other, repository.settings().craPageUrl)
    }

    @Test
    fun `a canada dot ca page without the limit keeps the previous address`() = runTest {
        val result = repository.changeCraPageUrl("https://www.canada.ca/fr/autre.html") { "<h1>Page non trouvée</h1>" }

        assertIs<CraAddressResult.Rejected>(result)
        assertEquals(DEFAULT_CRA_PAGE_URL, repository.settings().craPageUrl)
    }

    @Test
    fun `an unreachable page keeps the previous address`() = runTest {
        val result = repository.changeCraPageUrl("https://www.canada.ca/fr/autre.html") { throw java.io.IOException("hors ligne") }

        assertIs<CraAddressResult.Rejected>(result)
        assertEquals(DEFAULT_CRA_PAGE_URL, repository.settings().craPageUrl)
    }

    @Test
    fun `an address outside canada dot ca is not even downloaded`() = runTest {
        val result = repository.changeCraPageUrl("https://example.com/limits") { error("must not be downloaded") }

        assertIs<CraAddressResult.Rejected>(result)
        assertEquals(DEFAULT_CRA_PAGE_URL, repository.settings().craPageUrl)
    }
}
