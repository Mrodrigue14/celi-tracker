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
    fun `aucun excedent quand les cotisations respectent les droits`() {
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
    fun `un excedent persiste chaque mois jusqu'a la fin de l'annee`() {
        // Droits 2019 = 6000, repository de 10000 -> excess de 4000.
        val transactions = listOf(tx("2019-03-15", TransactionType.DEPOSIT, "10000.00"))

        val excesses = Overcontribution.tfsaExcesses(
            profile,
            limits,
            transactions,
            upTo = YearMonth.of(2019, 12),
        )

        // Mars a decembre inclus = 10 month.
        assertEquals(10, excesses.size)
        assertEquals(3, excesses.first().month)
        assertEquals(toMoney("4000.00"), excesses.first().maxExcess)
        assertEquals(toMoney("40.00"), excesses.first().penalty)
        assertEquals(12, excesses.last().month)
        assertEquals(toMoney("4000.00"), excesses.last().maxExcess)
    }

    @Test
    fun `re-cotiser un montant retire la meme annee recree l'excedent`() {
        // LE piege du regime. Un withdrawal annule l'excess existant, mais ne
        // redonne AUCUN droit before le 1er janvier suivant. Re-cotiser le meme
        // amount la meme year cree donc un excess plein.
        val transactions = listOf(
            tx("2019-02-01", TransactionType.DEPOSIT, "6000.00"), // room epuises, 0 excess
            tx("2019-04-01", TransactionType.WITHDRAWAL, "6000.00"), // aucun droit restitue
            tx("2019-06-01", TransactionType.DEPOSIT, "6000.00"), // re-cotisation -> excess
        )

        val excesses = Overcontribution.tfsaExcesses(
            profile,
            limits,
            transactions,
            upTo = YearMonth.of(2019, 12),
        ).associateBy { it.month }

        // Fevrier a mai: les room couvrent les cotisations, aucun excess.
        assertTrue(excesses[2] == null)
        assertTrue(excesses[5] == null)
        // Juin: les room etaient deja epuises, le withdrawal n'en a labelStep rendu.
        assertEquals(toMoney("6000.00"), excesses.getValue(6).maxExcess)
        assertEquals(toMoney("60.00"), excesses.getValue(6).penalty)
        // L'excess persiste jusqu'a la end de l'year: juin a decembre.
        assertEquals(7, excesses.size)
        assertEquals(toMoney("6000.00"), excesses.getValue(12).maxExcess)
    }

    @Test
    fun `un retrait annule l'excedent mais le mois reste facture`() {
        val transactions = listOf(
            tx("2019-02-01", TransactionType.DEPOSIT, "6000.00"),
            tx("2019-03-01", TransactionType.DEPOSIT, "1000.00"), // depassement de 1000
            tx("2019-04-15", TransactionType.WITHDRAWAL, "1000.00"), // corrige en avril
        )

        val excesses = Overcontribution.tfsaExcesses(
            profile,
            limits,
            transactions,
            upTo = YearMonth.of(2019, 12),
        ).associateBy { it.month }

        assertEquals(toMoney("1000.00"), excesses.getValue(3).maxExcess)
        assertEquals(toMoney("10.00"), excesses.getValue(3).penalty)
        // Avril reste facture: la penalty porte sur l'excess le PLUS ELEVE
        // du month, et il valait 1000 jusqu'au 15.
        assertEquals(toMoney("1000.00"), excesses.getValue(4).maxExcess)
        // Mai est propre: l'excess a ete annule par le withdrawal.
        assertTrue(excesses[5] == null)
    }

    @Test
    fun `l'excedent est absorbe par les droits de l'annee suivante`() {
        // Droits 2019 = 6000, repository de 10000 -> excess de 4000 jusqu'en
        // decembre. Au 1er janvier 2020, le limit de 6000 absorbed l'excess
        // (startRoom 2020 = -4000 + 6000 = 2000 > 0).
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
    fun `aucun excedent sans transaction`() {
        val excesses = Overcontribution.tfsaExcesses(
            profile,
            limits,
            emptyList(),
            upTo = YearMonth.of(2019, 12),
        )

        assertTrue(excesses.isEmpty())
    }

    @Test
    fun `une transaction anterieure a l'admissibilite ne cree aucun excedent`() {
        // TfsaEngine ignore cette transaction (sa boucle demarre en 2019).
        // Overcontribution doit l'ignorer aussi, sans quoi les 9000 deviendraient
        // un excess facture a 1 % par month.
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
