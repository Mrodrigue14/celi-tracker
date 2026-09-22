package dev.celitracker.engine

import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SnapshotComparisonTest {

    private val profile = AcceptanceScenario.profile.copy(fhsaOpeningDate = LocalDate.of(2023, 4, 1))

    private val tfsaRows = TfsaEngine.roomByYear(profile, AcceptanceScenario.limits, AcceptanceScenario.deposits, 2026)

    private val fhsaRows = FhsaEngine.roomByYear(profile, emptyList(), 2026)

    private fun snapshot(account: Account, date: String, amount: String, id: Long = 1) = CraSnapshot(id, account, LocalDate.parse(date), BigDecimal(amount))

    @Test
    fun `a TFSA figure is compared with the room on January 1 of its year`() {
        val comparison = tfsaSnapshotComparison(listOf(snapshot(Account.TFSA, "2026-03-01", "41000")), tfsaRows)

        assertEquals(BigDecimal("41800.00"), comparison?.calculatedRoom)
        assertEquals(BigDecimal("-800.00"), comparison?.difference)
    }

    @Test
    fun `deposits made during the year do not move the comparison`() {
        val withDepositThisYear = AcceptanceScenario.deposits + Transaction(Account.TFSA, LocalDate.of(2026, 2, 1), TransactionType.DEPOSIT, BigDecimal("3000"))
        val rows = TfsaEngine.roomByYear(profile, AcceptanceScenario.limits, withDepositThisYear, 2026)

        val comparison = tfsaSnapshotComparison(listOf(snapshot(Account.TFSA, "2026-06-01", "41800")), rows)

        assertEquals(BigDecimal("0.00"), comparison?.difference)
    }

    @Test
    fun `a figure from an earlier year is compared with that year`() {
        val comparison = tfsaSnapshotComparison(listOf(snapshot(Account.TFSA, "2024-06-01", "29000")), tfsaRows)

        assertEquals(BigDecimal("29000.00"), comparison?.calculatedRoom)
        assertEquals(BigDecimal("0.00"), comparison?.difference)
    }

    @Test
    fun `a declared room of zero is compared like any other`() {
        val comparison = tfsaSnapshotComparison(listOf(snapshot(Account.TFSA, "2026-03-01", "0")), tfsaRows)

        assertEquals(BigDecimal("-41800.00"), comparison?.difference)
    }

    @Test
    fun `the most recent reference date wins, then the highest id`() {
        val snapshots = listOf(
            snapshot(Account.TFSA, "2025-01-01", "1", id = 9),
            snapshot(Account.TFSA, "2026-03-01", "2", id = 1),
            snapshot(Account.TFSA, "2026-03-01", "3", id = 2),
        )

        assertEquals(BigDecimal("3"), tfsaSnapshotComparison(snapshots, tfsaRows)?.snapshot?.declaredRoom)
    }

    @Test
    fun `a snapshot of the other account is ignored`() {
        assertNull(tfsaSnapshotComparison(listOf(snapshot(Account.FHSA, "2026-03-01", "16000")), tfsaRows))
        assertNull(fhsaSnapshotComparison(listOf(snapshot(Account.TFSA, "2026-03-01", "41800")), fhsaRows))
    }

    @Test
    fun `no comparison without a snapshot`() {
        assertNull(tfsaSnapshotComparison(emptyList(), tfsaRows))
        assertNull(fhsaSnapshotComparison(emptyList(), fhsaRows))
    }

    @Test
    fun `no comparison for a year before eligibility`() {
        assertNull(tfsaSnapshotComparison(listOf(snapshot(Account.TFSA, "2015-06-01", "5000")), tfsaRows))
    }

    @Test
    fun `no comparison when a limit is missing up to the reference year`() {
        val rows = TfsaEngine.roomByYear(profile, AcceptanceScenario.limits.filter { it.year != 2022 }, AcceptanceScenario.deposits, 2026)

        assertNull(tfsaSnapshotComparison(listOf(snapshot(Account.TFSA, "2026-03-01", "41800")), rows))
    }

    @Test
    fun `a missing limit after the reference year does not block the comparison`() {
        val rows = TfsaEngine.roomByYear(profile, AcceptanceScenario.limits.filter { it.year != 2026 }, AcceptanceScenario.deposits, 2026)

        assertEquals(BigDecimal("29000.00"), tfsaSnapshotComparison(listOf(snapshot(Account.TFSA, "2024-06-01", "29000")), rows)?.calculatedRoom)
    }

    @Test
    fun `an FHSA figure is compared with the room of its year`() {
        val comparison = fhsaSnapshotComparison(listOf(snapshot(Account.FHSA, "2026-03-01", "16000")), fhsaRows)

        assertEquals(BigDecimal("16000.00"), comparison?.calculatedRoom)
        assertEquals(BigDecimal("0.00"), comparison?.difference)
    }

    @Test
    fun `no FHSA comparison before the account was opened`() {
        assertNull(fhsaSnapshotComparison(listOf(snapshot(Account.FHSA, "2022-06-01", "8000")), fhsaRows))
    }
}
