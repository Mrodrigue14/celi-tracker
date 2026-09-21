package dev.celitracker.engine

import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UsageTest {

    private fun usage(contributed: String) = Usage(room = BigDecimal("10000.00"), contributed = BigDecimal(contributed))

    @Test
    fun `sous 80 pour cent, rien a signaler`() {
        assertEquals(UsageLevel.NORMAL, usage("7999.99").level)
    }

    @Test
    fun `80 pour cent tout juste declenche l'attention`() {
        assertEquals(UsageLevel.WARNING, usage("8000.00").level)
    }

    @Test
    fun `95 pour cent tout juste est critique`() {
        assertEquals(UsageLevel.CRITICAL, usage("9500.00").level)
    }

    @Test
    fun `utiliser exactement tous ses droits n'est pas une sur-cotisation`() {
        assertEquals(UsageLevel.CRITICAL, usage("10000.00").level)
    }

    @Test
    fun `un cent de trop est une sur-cotisation`() {
        val exceeded = usage("10000.01")

        assertEquals(UsageLevel.EXCEEDED, exceeded.level)
        assertEquals(BigDecimal("0.01"), exceeded.excess)
        assertEquals(BigDecimal("0.00"), exceeded.remaining)
    }

    @Test
    fun `le pourcentage est arrondi a l'entier`() {
        assertEquals(85, usage("8450.00").percent)
    }

    @Test
    fun `sans droits, pas de pourcentage, et tout depot est un depassement`() {
        val withoutRoom = Usage(room = BigDecimal.ZERO, contributed = BigDecimal.ZERO)

        assertNull(withoutRoom.percent)
        assertEquals(UsageLevel.NORMAL, withoutRoom.level)
        assertEquals(UsageLevel.EXCEEDED, withoutRoom.withDeposit(BigDecimal("1.00")).level)
    }

    @Test
    fun `l'utilisation CELI vient des droits du 1er janvier et des depots de l'annee`() {
        // Naissance en 2008: admissible au TFSA en 2026.
        val profile = Profile(birthYear = 2008, fhsaOpeningDate = null)
        val limits = listOf(AnnualLimit(Account.TFSA, 2026, BigDecimal("7000.00")))
        val repository = Transaction(Account.TFSA, LocalDate.of(2026, 3, 1), TransactionType.DEPOSIT, BigDecimal("6000.00"))

        val usage = tfsaUsage(profile, limits, listOf(repository), 2026)

        assertEquals(Usage(BigDecimal("7000.00"), BigDecimal("6000.00")), usage)
    }

    @Test
    fun `sans CELIAPP ouvert, pas d'utilisation`() {
        val profile = Profile(birthYear = 2000, fhsaOpeningDate = null)

        assertNull(fhsaUsage(profile, emptyList(), 2026))
    }

    @Test
    fun `sans plafond connu, l'utilisation CELI est inconnue plutot que fausse`() {
        val profile = Profile(birthYear = 2008, fhsaOpeningDate = null)
        val repository = Transaction(Account.TFSA, LocalDate.of(2026, 3, 1), TransactionType.DEPOSIT, BigDecimal("100.00"))

        assertNull(tfsaUsage(profile, emptyList(), listOf(repository), 2026))
    }
}
