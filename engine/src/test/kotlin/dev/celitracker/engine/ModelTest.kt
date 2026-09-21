package dev.celitracker.engine

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class ModelTest {

    @Test
    fun `an amount keeps its exact decimal precision`() {
        val tx = Transaction(
            account = Account.TFSA,
            date = LocalDate.of(2026, 4, 6),
            type = TransactionType.DEPOSIT,
            amount = BigDecimal("1234.56"),
        )

        // If someone replaces BigDecimal with Double, this string equality
        // breaks (1234.5600000000001) and the test goes red.
        assertEquals("1234.56", tx.amount.toPlainString())
    }

    @Test
    fun `money normalizes the scale to two decimals`() {
        // BigDecimal.equals compares the scale: without normalization,
        // BigDecimal("6000") != BigDecimal("6000.00").
        assertEquals(BigDecimal("6000.00"), BigDecimal("6000").toMoney())
        assertEquals(BigDecimal("6000.00"), BigDecimal("6000.000").toMoney())
    }

    @Test
    fun `money rounds to the nearest cent`() {
        assertEquals(BigDecimal("10.01"), BigDecimal("10.005").toMoney())
        assertEquals(BigDecimal("10.00"), BigDecimal("10.004").toMoney())
    }

    @Test
    fun `a limit is confirmed by default`() {
        val limit = AnnualLimit(Account.TFSA, 2019, BigDecimal("6000.00"))
        assertEquals(true, limit.confirmed)
    }

    @Test
    fun `a profile accepts having no FHSA account`() {
        val profile = Profile(
            birthYear = 2001,
            fhsaOpeningDate = null,
        )
        assertEquals(null, profile.fhsaOpeningDate)
    }

    @Test
    fun `the eligibility year is the year of turning 18`() {
        assertEquals(2013, Profile(birthYear = 1995, fhsaOpeningDate = null).tfsaEligibilityYear)
    }

    @Test
    fun `the eligibility year never precedes the creation of the TFSA`() {
        // 18 years old in 1978, but the TFSA did not exist until 2009.
        assertEquals(2009, Profile(birthYear = 1960, fhsaOpeningDate = null).tfsaEligibilityYear)
    }

    @Test
    fun `a cra snapshot keeps the account and the declared room`() {
        val snapshot = CraSnapshot(
            id = 1,
            account = Account.FHSA,
            referenceDate = LocalDate.of(2026, 1, 1),
            declaredRoom = BigDecimal("6000.00"),
        )
        assertEquals(Account.FHSA, snapshot.account)
        assertEquals(snapshot, snapshot.copy())
    }

    @Test
    fun `settings without a recent check are accepted`() {
        val settings = Settings(craPageUrl = "https://arc.gc.ca", lastCheckDate = null)
        assertEquals(null, settings.lastCheckDate)
        assertEquals(settings, settings.copy(lastCheckDate = null))

        val checked = settings.copy(lastCheckDate = Instant.parse("2026-09-08T00:00:00Z"))
        assertEquals(Instant.parse("2026-09-08T00:00:00Z"), checked.lastCheckDate)
    }
}
