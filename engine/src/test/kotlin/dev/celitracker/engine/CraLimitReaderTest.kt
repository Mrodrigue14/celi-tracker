package dev.celitracker.engine

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CraLimitReaderTest {

    /** Structure captured from the CRA page on 2026-09-17, spans included. */
    private val craPage = """
        <h2 id="toc3">Plafond de cotisation à un CELI pour 2026</h2>
        <p>Le plafond de cotisation à un compte d'épargne libre <span class="nowrap">d'impôt (CELI)</span>
        <span class="nowrap">pour 2026</span> est <span class="nowrap">de 7 000 $</span>.
        Il sera ajouté à vos droits de cotisation <span class="nowrap">le 1er janvier 2026</span>.</p>
    """.trimIndent()

    @Test
    fun `reads the year and amount through the tags`() {
        val limit = readTfsaLimitFromCraPage(craPage)

        assertEquals(AnnualLimit(Account.TFSA, 2026, BigDecimal("7000.00"), confirmed = false), limit)
    }

    @Test
    fun `the non-breaking space in thousands is accepted`() {
        val withNonBreakingSpace = craPage.replace("7 000", "7 000")

        assertEquals(BigDecimal("7000.00"), readTfsaLimitFromCraPage(withNonBreakingSpace)?.amount)
    }

    @Test
    fun `the limit read is never confirmed`() {
        assertEquals(false, readTfsaLimitFromCraPage(craPage)?.confirmed)
    }

    @Test
    fun `a page without the expected sentence gives nothing`() {
        assertNull(readTfsaLimitFromCraPage("<h1>Page non trouvée</h1><p>Erreur 404</p>"))
    }

    @Test
    fun `a sentence without a readable amount gives nothing`() {
        assertNull(readTfsaLimitFromCraPage("<p>Le plafond de cotisation pour 2027 est de bientôt $.</p>"))
    }

    @Test
    fun `a zero amount is rejected`() {
        assertNull(readTfsaLimitFromCraPage("<p>Le plafond de cotisation pour 2027 est de 0 $.</p>"))
    }

    @Test
    fun `an https address on canada dot ca is valid`() {
        assertTrue(isValidCraPageUrl("https://www.canada.ca/fr/agence-revenu/services.html"))
    }

    @Test
    fun `an address outside canada dot ca or without https is rejected`() {
        assertFalse(isValidCraPageUrl("http://www.canada.ca/fr.html"))
        assertFalse(isValidCraPageUrl("https://example.com/limits"))
        assertFalse(isValidCraPageUrl("not an address"))
        assertFalse(isValidCraPageUrl(""))
    }
}
