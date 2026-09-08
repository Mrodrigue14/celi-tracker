package dev.celitracker.engine

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class ModeleTest {

    @Test
    fun `un montant conserve sa precision decimale exacte`() {
        val tx = Transaction(
            compte = Compte.CELI,
            date = LocalDate.of(2026, 4, 6),
            type = TypeTx.DEPOT,
            montant = BigDecimal("1234.56"),
        )

        // Si quelqu'un remplace BigDecimal par Double, cette egalite de chaine
        // casse (1234.5600000000001) et le test devient rouge.
        assertEquals("1234.56", tx.montant.toPlainString())
    }

    @Test
    fun `argent normalise l'echelle a deux decimales`() {
        // BigDecimal.equals compare l'echelle: sans normalisation,
        // BigDecimal("6000") != BigDecimal("6000.00").
        assertEquals(BigDecimal("6000.00"), BigDecimal("6000").argent())
        assertEquals(BigDecimal("6000.00"), BigDecimal("6000.000").argent())
    }

    @Test
    fun `argent arrondit au centime le plus proche`() {
        assertEquals(BigDecimal("10.01"), BigDecimal("10.005").argent())
        assertEquals(BigDecimal("10.00"), BigDecimal("10.004").argent())
    }

    @Test
    fun `un plafond est confirme par defaut`() {
        val plafond = PlafondAnnuel(Compte.CELI, 2019, BigDecimal("6000.00"))
        assertEquals(true, plafond.confirme)
    }

    @Test
    fun `un profil accepte l'absence de compte CELIAPP`() {
        val profil = Profil(
            anneeAdmissibiliteCeli = 2019,
            anneeNaissance = 2001,
            dateOuvertureCeliapp = null,
        )
        assertEquals(null, profil.dateOuvertureCeliapp)
    }

    @Test
    fun `un snapshot arc conserve le compte et les droits declares`() {
        val snapshot = SnapshotArc(
            id = 1,
            compte = Compte.CELIAPP,
            dateReference = LocalDate.of(2026, 1, 1),
            droitsDeclares = BigDecimal("6000.00"),
        )
        assertEquals(Compte.CELIAPP, snapshot.compte)
        assertEquals(snapshot, snapshot.copy())
    }

    @Test
    fun `des reglages sans verification recente sont acceptes`() {
        val reglages = Reglages(urlPageArc = "https://arc.gc.ca", dateDerniereVerification = null)
        assertEquals(null, reglages.dateDerniereVerification)
        assertEquals(reglages, reglages.copy(dateDerniereVerification = null))

        val verifies = reglages.copy(dateDerniereVerification = Instant.parse("2026-09-08T00:00:00Z"))
        assertEquals(Instant.parse("2026-09-08T00:00:00Z"), verifies.dateDerniereVerification)
    }
}
