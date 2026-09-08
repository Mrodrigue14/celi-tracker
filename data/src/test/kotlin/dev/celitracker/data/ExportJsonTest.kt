package dev.celitracker.data

import dev.celitracker.engine.Compte
import dev.celitracker.engine.PlafondAnnuel
import dev.celitracker.engine.Profil
import dev.celitracker.engine.Reglages
import dev.celitracker.engine.SnapshotArc
import dev.celitracker.engine.Transaction
import dev.celitracker.engine.TypeTx
import java.io.File
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class ExportJsonTest {

    private val fichier = File.createTempFile("celi-tracker-export-test", ".db")
    private val base = ouvrirBase(fichier.absolutePath)
    private val depot = Depot(base)

    private val fichierVierge = File.createTempFile("celi-tracker-export-test-vierge", ".db")
    private val baseVierge = ouvrirBase(fichierVierge.absolutePath)
    private val depotVierge = Depot(baseVierge)

    @AfterTest
    fun fermer() {
        base.close()
        fichier.delete()
        baseVierge.close()
        fichierVierge.delete()
    }

    private val profilCeli = Profil(
        anneeAdmissibiliteCeli = 2020,
        anneeNaissance = 2000,
        dateOuvertureCeliapp = LocalDate.of(2023, 6, 1),
    )

    private suspend fun peuplerDonneesTest() {
        depot.enregistrerProfil(profilCeli)
        // Insere en ordre volontairement melange pour verifier le tri au moment de l'export.
        depot.enregistrerPlafond(PlafondAnnuel(Compte.CELIAPP, 2026, BigDecimal("8000.00"), confirme = true))
        depot.enregistrerPlafond(PlafondAnnuel(Compte.CELI, 2026, BigDecimal("7000.00"), confirme = true))
        depot.enregistrerPlafond(PlafondAnnuel(Compte.CELI, 2020, BigDecimal("6000.00"), confirme = false))
        depot.ajouterTransaction(Transaction(Compte.CELI, LocalDate.of(2026, 1, 15), TypeTx.DEPOT, BigDecimal("4321.28")))
        depot.ajouterTransaction(Transaction(Compte.CELIAPP, LocalDate.of(2023, 7, 1), TypeTx.DEPOT, BigDecimal("1000.00")))
        depot.enregistrerSnapshotArc(SnapshotArc(0, Compte.CELI, LocalDate.of(2026, 1, 1), BigDecimal("1234.56")))
        depot.enregistrerReglages(Reglages(urlPageArc = "https://arc.gc.ca", dateDerniereVerification = Instant.parse("2026-09-08T12:00:00Z")))
    }

    @Test
    fun `aller-retour sans perte - reexporter apres import donne une chaine identique`() = runTest {
        peuplerDonneesTest()
        val exportOriginal = depot.exporterJson()

        depotVierge.importerJson(exportOriginal)
        val exportReimporte = depotVierge.exporterJson()

        assertEquals(exportOriginal, exportReimporte)
    }

    @Test
    fun `un montant a deux decimales survit a l'export et a l'import`() = runTest {
        depot.enregistrerProfil(profilCeli)
        depot.ajouterTransaction(Transaction(Compte.CELI, LocalDate.of(2026, 1, 15), TypeTx.DEPOT, BigDecimal("4321.28")))

        val export = depot.exporterJson()

        assertTrue(export.contains("\"montant\":\"4321.28\""))

        depotVierge.importerJson(export)
        val relue = depotVierge.transactions().single()
        assertEquals("4321.28", relue.montant.toPlainString())
    }

    @Test
    fun `une version inconnue est refusee`() = runTest {
        val exportVersionInconnue = """
            {"version":2,"profil":null,"plafonds":[],"transactions":[],"snapshotsArc":[],
             "reglages":{"urlPageArc":"","dateDerniereVerification":null}}
        """.trimIndent()

        assertFailsWith<IllegalArgumentException> {
            depotVierge.importerJson(exportVersionInconnue)
        }
    }

    @Test
    fun `un JSON malforme est refuse sans ecraser la base existante`() = runTest {
        peuplerDonneesTest()
        val avant = depot.exporterJson()

        assertFailsWith<IllegalArgumentException> {
            depot.importerJson("ceci n'est pas du JSON")
        }

        assertEquals(avant, depot.exporterJson())
        assertEquals(profilCeli, depot.profil())
        assertEquals(2, depot.transactions().size)
    }

    @Test
    fun `l'export est deterministe`() = runTest {
        peuplerDonneesTest()

        val premierExport = depot.exporterJson()
        val secondExport = depot.exporterJson()

        assertEquals(premierExport, secondExport)
    }
}
