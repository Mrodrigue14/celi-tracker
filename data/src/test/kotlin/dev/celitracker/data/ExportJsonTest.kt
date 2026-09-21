package dev.celitracker.data

import androidx.room.Room
import dev.celitracker.engine.Compte
import dev.celitracker.engine.PlafondAnnuel
import dev.celitracker.engine.Profil
import dev.celitracker.engine.Reglages
import dev.celitracker.engine.SnapshotArc
import dev.celitracker.engine.Transaction
import dev.celitracker.engine.TypeTx
import kotlinx.coroutines.test.runTest
import java.io.File
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ExportJsonTest {

    private val fichier = File.createTempFile("celi-tracker-export-test", ".db")
    private val base = configurerBase(Room.databaseBuilder<CeliTrackerBase>(name = fichier.absolutePath))
    private val depot = Depot(base)

    private val fichierVierge = File.createTempFile("celi-tracker-export-test-vierge", ".db")
    private val baseVierge = configurerBase(Room.databaseBuilder<CeliTrackerBase>(name = fichierVierge.absolutePath))
    private val depotVierge = Depot(baseVierge)

    @AfterTest
    fun fermer() {
        base.close()
        fichier.delete()
        baseVierge.close()
        fichierVierge.delete()
    }

    private val profilCeli = Profil(
        anneeNaissance = 2002,
        dateOuvertureCeliapp = LocalDate.of(2023, 6, 1),
    )

    private suspend fun peuplerDonneesTest() {
        depot.enregistrerProfil(profilCeli)
        // Insere en ordre volontairement melange pour verifier le tri au moment de l'export.
        depot.enregistrerPlafond(PlafondAnnuel(Compte.CELIAPP, 2026, BigDecimal("8000.00"), confirme = true))
        depot.enregistrerPlafond(PlafondAnnuel(Compte.CELI, 2026, BigDecimal("7000.00"), confirme = true))
        depot.enregistrerPlafond(PlafondAnnuel(Compte.CELI, 2020, BigDecimal("6000.00"), confirme = false))
        depot.ajouterTransaction(Transaction(Compte.CELI, LocalDate.of(2026, 1, 15), TypeTx.DEPOT, BigDecimal("1234.56")))
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
        depot.ajouterTransaction(Transaction(Compte.CELI, LocalDate.of(2026, 1, 15), TypeTx.DEPOT, BigDecimal("1234.56")))

        val export = depot.exporterJson()

        assertTrue(export.contains("\"montant\":\"1234.56\""))

        depotVierge.importerJson(export)
        val relue = depotVierge.transactions().single()
        assertEquals("1234.56", relue.montant.toPlainString())
    }

    @Test
    fun `une version inconnue est refusee`() = runTest {
        val exportVersionInconnue = """
            {"version":99,"profil":null,"plafonds":[],"transactions":[],"snapshotsArc":[],
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
    fun `un export de version 1 reste importable, son annee d'admissibilite ignoree`() = runTest {
        val exportV1 = """
            {"version":1,
             "profil":{"anneeAdmissibiliteCeli":1999,"anneeNaissance":2002,"dateOuvertureCeliapp":"2023-06-01"},
             "plafonds":[],"transactions":[],"snapshotsArc":[],
             "reglages":{"urlPageArc":"https://arc.gc.ca","dateDerniereVerification":null}}
        """.trimIndent()

        depot.importerJson(exportV1)

        // 2002 + 18, pas le 1999 ecrit dans le fichier.
        assertEquals(2020, depot.profil()?.anneeAdmissibiliteCeli)
    }

    @Test
    fun `un echec en cours d'ecriture ne laisse pas la base a moitie videe`() = runTest {
        peuplerDonneesTest()
        val avant = depot.exporterJson()

        // Version et JSON valides, mais deux transactions partagent le meme id :
        // la deuxieme insertion viole la cle primaire apres que les tables aient
        // deja ete videes et qu'une partie (profil, plafonds) ait deja ete ecrite.
        val exportEcritureEchoue = """
            {"version":1,
             "profil":{"anneeAdmissibiliteCeli":2020,"anneeNaissance":2000,"dateOuvertureCeliapp":"2023-06-01"},
             "plafonds":[{"compte":"CELI","annee":2026,"montant":"7000.00","confirme":true}],
             "transactions":[
               {"id":1,"compte":"CELI","date":"2026-01-15","type":"DEPOT","montant":"100.00"},
               {"id":1,"compte":"CELI","date":"2026-01-16","type":"DEPOT","montant":"200.00"}
             ],
             "snapshotsArc":[],
             "reglages":{"urlPageArc":"https://arc.gc.ca","dateDerniereVerification":null}}
        """.trimIndent()

        assertFailsWith<Throwable> {
            depot.importerJson(exportEcritureEchoue)
        }

        assertEquals(avant, depot.exporterJson())
        assertEquals(profilCeli, depot.profil())
        assertEquals(3, depot.plafonds().size)
        assertEquals(2, depot.transactions().size)
        assertEquals(1, depot.snapshotsArc().size)
        assertEquals(
            Reglages(urlPageArc = "https://arc.gc.ca", dateDerniereVerification = Instant.parse("2026-09-08T12:00:00Z")),
            depot.reglages(),
        )
    }

    @Test
    fun `l'export est deterministe`() = runTest {
        peuplerDonneesTest()

        val premierExport = depot.exporterJson()
        val secondExport = depot.exporterJson()

        assertEquals(premierExport, secondExport)
    }

    /**
     * A version 2 backup file as the app writes it today. Users keep these
     * files; field names and stored values must survive any renaming in code.
     */
    private val exportVersion2 =
        """{"version":2,"profil":{"anneeNaissance":2002,"dateOuvertureCeliapp":"2023-06-01"},""" +
            """"plafonds":[{"compte":"CELI","annee":2026,"montant":"7000.00","confirme":true},""" +
            """{"compte":"CELIAPP","annee":2026,"montant":"8000.00","confirme":false}],""" +
            """"transactions":[{"id":1,"compte":"CELI","date":"2026-01-15","type":"DEPOT","montant":"1234.56"},""" +
            """{"id":2,"compte":"CELIAPP","date":"2025-03-01","type":"RETRAIT","montant":"500.00"}],""" +
            """"snapshotsArc":[{"id":1,"compte":"CELI","dateReference":"2026-01-01","droitsDeclares":"41800.00"}],""" +
            """"reglages":{"urlPageArc":"https://example.org/arc","dateDerniereVerification":"2026-09-08T12:00:00Z"}}"""

    @Test
    fun `a version 2 backup imports and exports back byte for byte`() = runTest {
        depotVierge.importerJson(exportVersion2)

        assertEquals(Profil(anneeNaissance = 2002, dateOuvertureCeliapp = LocalDate.of(2023, 6, 1)), depotVierge.profil())
        assertEquals(
            listOf(
                Transaction(Compte.CELI, LocalDate.of(2026, 1, 15), TypeTx.DEPOT, BigDecimal("1234.56"), id = 1),
                Transaction(Compte.CELIAPP, LocalDate.of(2025, 3, 1), TypeTx.RETRAIT, BigDecimal("500.00"), id = 2),
            ),
            depotVierge.transactions().sortedBy { it.id },
        )
        assertEquals(exportVersion2, depotVierge.exporterJson())
    }
}
