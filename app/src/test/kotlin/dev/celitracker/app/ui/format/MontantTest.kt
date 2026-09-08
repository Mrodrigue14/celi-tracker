package dev.celitracker.app.ui.format

import java.math.BigDecimal
import kotlin.test.Test
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
}
