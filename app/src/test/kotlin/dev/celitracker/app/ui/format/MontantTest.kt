package dev.celitracker.app.ui.format

import java.math.BigDecimal
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MontantTest {

    @Test
    fun `montant positif contient le symbole dollar et les decimales`() {
        val texte = BigDecimal("7000.00").formatMontant()
        assertTrue("$" in texte, "attendu un symbole dollar dans '$texte'")
        assertTrue("7" in texte && "000" in texte, "attendu les chiffres du montant dans '$texte'")
        assertTrue("00" in texte, "attendu les decimales dans '$texte'")
    }

    @Test
    fun `montant negatif conserve le signe`() {
        val texte = BigDecimal("-150.50").formatMontant()
        assertTrue("-" in texte, "attendu un signe negatif dans '$texte'")
    }

    @Test
    fun `montant zero ne leve pas d'exception`() {
        val texte = BigDecimal.ZERO.setScale(2).formatMontant()
        assertTrue("0" in texte)
    }

    @Test
    fun `une date s'ecrit en toutes lettres, dans la langue demandee`() {
        val date = java.time.LocalDate.of(2026, 9, 4)

        assertEquals("4 septembre 2026", date.formatDate(Locale.CANADA_FRENCH))
        assertEquals("September 4, 2026", date.formatDate(Locale.CANADA))
    }

    @Test
    fun `la devise reste le dollar canadien quelle que soit la langue`() {
        val montant = BigDecimal("1234.50")

        // En anglais americain, sans devise fixee, ce serait des dollars americains.
        assertEquals("CA$1,234.50", montant.formatMontant(Locale.US))
        assertTrue(montant.formatMontant(Locale.CANADA_FRENCH).startsWith("1"))
        assertTrue(montant.formatMontant(Locale.CANADA_FRENCH).endsWith("$"))
    }
}
