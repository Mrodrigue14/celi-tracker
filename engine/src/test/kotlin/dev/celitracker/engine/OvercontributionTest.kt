package dev.celitracker.engine

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun toMoney(value: String): BigDecimal = BigDecimal(value).toMoney()

class OvercontributionTest {

    private val profile = Profile(2001, null)
    private val limits = listOf(
        AnnualLimit(Account.TFSA, 2019, toMoney("6000.00")),
        AnnualLimit(Account.TFSA, 2020, toMoney("6000.00")),
    )

    private fun tx(date: String, type: TransactionType, amount: String) = Transaction(Account.TFSA, LocalDate.parse(date), type, toMoney(amount))

    @Test
    fun `no excess when contributions stay within room`() {
        val transactions = listOf(tx("2019-03-15", TransactionType.DEPOSIT, "6000.00"))

        val excesses = Overcontribution.tfsaExcesses(
            profile,
            limits,
            transactions,
            upTo = YearMonth.of(2019, 12),
        )

        assertTrue(excesses.isEmpty())
    }

    @Test
    fun `an excess persists every month until the end of the year`() {
        // 10000 - 6000 = 4000.
        val transactions = listOf(tx("2019-03-15", TransactionType.DEPOSIT, "10000.00"))

        val excesses = Overcontribution.tfsaExcesses(
            profile,
            limits,
            transactions,
            upTo = YearMonth.of(2019, 12),
        )

        // March through December: 10 months.
        assertEquals(10, excesses.size)
        assertEquals(3, excesses.first().month)
        assertEquals(toMoney("4000.00"), excesses.first().maxExcess)
        assertEquals(toMoney("40.00"), excesses.first().penalty)
        assertEquals(12, excesses.last().month)
        assertEquals(toMoney("4000.00"), excesses.last().maxExcess)
    }

    @Test
    fun `re-contributing an amount withdrawn the same year recreates the excess`() {
        val transactions = listOf(
            tx("2019-02-01", TransactionType.DEPOSIT, "6000.00"),
            tx("2019-04-01", TransactionType.WITHDRAWAL, "6000.00"),
            tx("2019-06-01", TransactionType.DEPOSIT, "6000.00"),
        )

        val excesses = Overcontribution.tfsaExcesses(
            profile,
            limits,
            transactions,
            upTo = YearMonth.of(2019, 12),
        ).associateBy { it.month }

        assertTrue(excesses[2] == null)
        assertTrue(excesses[5] == null)
        assertEquals(toMoney("6000.00"), excesses.getValue(6).maxExcess)
        assertEquals(toMoney("60.00"), excesses.getValue(6).penalty)
        // June through December: 7 months.
        assertEquals(7, excesses.size)
        assertEquals(toMoney("6000.00"), excesses.getValue(12).maxExcess)
    }

    @Test
    fun `a withdrawal cancels the excess but the month remains billed`() {
        val transactions = listOf(
            tx("2019-02-01", TransactionType.DEPOSIT, "6000.00"),
            tx("2019-03-01", TransactionType.DEPOSIT, "1000.00"),
            tx("2019-04-15", TransactionType.WITHDRAWAL, "1000.00"),
        )

        val excesses = Overcontribution.tfsaExcesses(
            profile,
            limits,
            transactions,
            upTo = YearMonth.of(2019, 12),
        ).associateBy { it.month }

        assertEquals(toMoney("1000.00"), excesses.getValue(3).maxExcess)
        assertEquals(toMoney("10.00"), excesses.getValue(3).penalty)
        // April is billed on the month's highest excess: 1000 until the 15th.
        assertEquals(toMoney("1000.00"), excesses.getValue(4).maxExcess)
        assertTrue(excesses[5] == null)
    }

    @Test
    fun `the excess is absorbed by the following year's room`() {
        // 10000 - 6000 = 4000 excess; 2020 startRoom = -4000 + 6000 = 2000.
        val transactions = listOf(tx("2019-03-15", TransactionType.DEPOSIT, "10000.00"))

        val excesses = Overcontribution.tfsaExcesses(
            profile,
            limits,
            transactions,
            upTo = YearMonth.of(2020, 12),
        )

        assertEquals(2019, excesses.last().year)
        assertEquals(12, excesses.last().month)
        assertTrue(excesses.none { it.year == 2020 })
    }

    @Test
    fun `no excess without a transaction`() {
        val excesses = Overcontribution.tfsaExcesses(
            profile,
            limits,
            emptyList(),
            upTo = YearMonth.of(2019, 12),
        )

        assertTrue(excesses.isEmpty())
    }

    @Test
    fun `a transaction before eligibility creates no excess`() {
        // TfsaEngine ignores it, so it must not become a billed excess here.
        val transactions = listOf(tx("2018-05-01", TransactionType.DEPOSIT, "9000.00"))

        val excesses = Overcontribution.tfsaExcesses(
            profile,
            limits,
            transactions,
            upTo = YearMonth.of(2019, 12),
        )

        assertTrue(excesses.isEmpty())
    }
}
