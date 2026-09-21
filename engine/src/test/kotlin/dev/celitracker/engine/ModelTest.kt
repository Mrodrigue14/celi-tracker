package dev.celitracker.engine

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class ModelTest {

    @Test
    fun `un montant conserve sa precision decimale exacte`() {
        val tx = Transaction(
            account = Account.TFSA,
            date = LocalDate.of(2026, 4, 6),
            type = TransactionType.DEPOSIT,
            amount = BigDecimal("1234.56"),
        )

        // Si quelqu'un remplace BigDecimal par Double, cette egalite de chaine
        // casse (1234.5600000000001) et le test devient rouge.
        assertEquals("1234.56", tx.amount.toPlainString())
    }

    @Test
    fun `argent normalise l'echelle a deux decimales`() {
        // BigDecimal.equals compare l'echelle: sans normalisation,
        // BigDecimal("6000") != BigDecimal("6000.00").
        assertEquals(BigDecimal("6000.00"), BigDecimal("6000").toMoney())
        assertEquals(BigDecimal("6000.00"), BigDecimal("6000.000").toMoney())
    }

    @Test
    fun `argent arrondit au centime le plus proche`() {
        assertEquals(BigDecimal("10.01"), BigDecimal("10.005").toMoney())
        assertEquals(BigDecimal("10.00"), BigDecimal("10.004").toMoney())
    }

    @Test
    fun `un plafond est confirme par defaut`() {
        val limit = AnnualLimit(Account.TFSA, 2019, BigDecimal("6000.00"))
        assertEquals(true, limit.confirmed)
    }

    @Test
    fun `un profil accepte l'absence de compte CELIAPP`() {
        val profile = Profile(
            birthYear = 2001,
            fhsaOpeningDate = null,
        )
        assertEquals(null, profile.fhsaOpeningDate)
    }

    @Test
    fun `l'annee d'admissibilite est celle des 18 ans`() {
        assertEquals(2013, Profile(birthYear = 1995, fhsaOpeningDate = null).tfsaEligibilityYear)
    }

    @Test
    fun `l'annee d'admissibilite ne precede jamais la creation du CELI`() {
        // 18 ans en 1978, mais le TFSA n'existe qu'en 2009.
        assertEquals(2009, Profile(birthYear = 1960, fhsaOpeningDate = null).tfsaEligibilityYear)
    }

    @Test
    fun `un snapshot arc conserve le compte et les droits declares`() {
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
    fun `des reglages sans verification recente sont acceptes`() {
        val settings = Settings(urlPageArc = "https://arc.gc.ca", lastCheckDate = null)
        assertEquals(null, settings.lastCheckDate)
        assertEquals(settings, settings.copy(lastCheckDate = null))

        val checked = settings.copy(lastCheckDate = Instant.parse("2026-09-08T00:00:00Z"))
        assertEquals(Instant.parse("2026-09-08T00:00:00Z"), checked.lastCheckDate)
    }
}
