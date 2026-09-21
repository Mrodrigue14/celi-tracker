package dev.celitracker.data

import androidx.room.Room
import dev.celitracker.engine.Account
import dev.celitracker.engine.AnnualLimit
import dev.celitracker.engine.CraSnapshot
import dev.celitracker.engine.Profile
import dev.celitracker.engine.Settings
import dev.celitracker.engine.Transaction
import dev.celitracker.engine.TransactionType
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

    private val file = File.createTempFile("celi-tracker-export-test", ".db")
    private val database = configureDatabase(Room.databaseBuilder<CeliTrackerDatabase>(name = file.absolutePath))
    private val repository = Repository(database)

    private val emptyFile = File.createTempFile("celi-tracker-export-test-empty", ".db")
    private val emptyDatabase = configureDatabase(Room.databaseBuilder<CeliTrackerDatabase>(name = emptyFile.absolutePath))
    private val emptyRepository = Repository(emptyDatabase)

    @AfterTest
    fun close() {
        database.close()
        file.delete()
        emptyDatabase.close()
        emptyFile.delete()
    }

    private val tfsaProfile = Profile(
        birthYear = 2002,
        fhsaOpeningDate = LocalDate.of(2023, 6, 1),
    )

    private suspend fun populateTestData() {
        repository.saveProfile(tfsaProfile)
        // Insere en ordre volontairement melange pour verifier le tri au moment de l'export.
        repository.saveLimit(AnnualLimit(Account.FHSA, 2026, BigDecimal("8000.00"), confirmed = true))
        repository.saveLimit(AnnualLimit(Account.TFSA, 2026, BigDecimal("7000.00"), confirmed = true))
        repository.saveLimit(AnnualLimit(Account.TFSA, 2020, BigDecimal("6000.00"), confirmed = false))
        repository.addTransaction(Transaction(Account.TFSA, LocalDate.of(2026, 1, 15), TransactionType.DEPOSIT, BigDecimal("1234.56")))
        repository.addTransaction(Transaction(Account.FHSA, LocalDate.of(2023, 7, 1), TransactionType.DEPOSIT, BigDecimal("1000.00")))
        repository.saveCraSnapshot(CraSnapshot(0, Account.TFSA, LocalDate.of(2026, 1, 1), BigDecimal("1234.56")))
        repository.saveSettings(Settings(urlPageArc = "https://arc.gc.ca", lastCheckDate = Instant.parse("2026-09-08T12:00:00Z")))
    }

    @Test
    fun `aller-retour sans perte - reexporter apres import donne une chaine identique`() = runTest {
        populateTestData()
        val exportOriginal = repository.exportJson()

        emptyRepository.importJson(exportOriginal)
        val exportReimporte = emptyRepository.exportJson()

        assertEquals(exportOriginal, exportReimporte)
    }

    @Test
    fun `un montant a deux decimales survit a l'export et a l'import`() = runTest {
        repository.saveProfile(tfsaProfile)
        repository.addTransaction(Transaction(Account.TFSA, LocalDate.of(2026, 1, 15), TransactionType.DEPOSIT, BigDecimal("1234.56")))

        val export = repository.exportJson()

        assertTrue(export.contains("\"montant\":\"1234.56\""))

        emptyRepository.importJson(export)
        val readBack = emptyRepository.transactions().single()
        assertEquals("1234.56", readBack.amount.toPlainString())
    }

    @Test
    fun `une version inconnue est refusee`() = runTest {
        val exportVersionInconnue = """
            {"version":99,"profil":null,"plafonds":[],"transactions":[],"snapshotsArc":[],
             "reglages":{"urlPageArc":"","dateDerniereVerification":null}}
        """.trimIndent()

        assertFailsWith<IllegalArgumentException> {
            emptyRepository.importJson(exportVersionInconnue)
        }
    }

    @Test
    fun `un JSON malforme est refuse sans ecraser la base existante`() = runTest {
        populateTestData()
        val before = repository.exportJson()

        assertFailsWith<IllegalArgumentException> {
            repository.importJson("this is not JSON")
        }

        assertEquals(before, repository.exportJson())
        assertEquals(tfsaProfile, repository.profile())
        assertEquals(2, repository.transactions().size)
    }

    @Test
    fun `un export de version 1 reste importable, son annee d'admissibilite ignoree`() = runTest {
        val exportV1 = """
            {"version":1,
             "profil":{"anneeAdmissibiliteCeli":1999,"anneeNaissance":2002,"dateOuvertureCeliapp":"2023-06-01"},
             "plafonds":[],"transactions":[],"snapshotsArc":[],
             "reglages":{"urlPageArc":"https://arc.gc.ca","dateDerniereVerification":null}}
        """.trimIndent()

        repository.importJson(exportV1)

        // 2002 + 18, labelStep le 1999 ecrit dans le file.
        assertEquals(2020, repository.profile()?.tfsaEligibilityYear)
    }

    @Test
    fun `un echec en cours d'ecriture ne laisse pas la base a moitie videe`() = runTest {
        populateTestData()
        val before = repository.exportJson()

        // Version et JSON valides, mais deux transactions partagent le meme id :
        // la deuxieme insertion viole la cle primaire after que les tables aient
        // deja ete videes et qu'une part (profile, limits) ait deja ete ecrite.
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
            repository.importJson(exportEcritureEchoue)
        }

        assertEquals(before, repository.exportJson())
        assertEquals(tfsaProfile, repository.profile())
        assertEquals(3, repository.limits().size)
        assertEquals(2, repository.transactions().size)
        assertEquals(1, repository.craSnapshots().size)
        assertEquals(
            Settings(urlPageArc = "https://arc.gc.ca", lastCheckDate = Instant.parse("2026-09-08T12:00:00Z")),
            repository.settings(),
        )
    }

    @Test
    fun `l'export est deterministe`() = runTest {
        populateTestData()

        val firstExport = repository.exportJson()
        val secondExport = repository.exportJson()

        assertEquals(firstExport, secondExport)
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
        emptyRepository.importJson(exportVersion2)

        assertEquals(Profile(birthYear = 2002, fhsaOpeningDate = LocalDate.of(2023, 6, 1)), emptyRepository.profile())
        assertEquals(
            listOf(
                Transaction(Account.TFSA, LocalDate.of(2026, 1, 15), TransactionType.DEPOSIT, BigDecimal("1234.56"), id = 1),
                Transaction(Account.FHSA, LocalDate.of(2025, 3, 1), TransactionType.WITHDRAWAL, BigDecimal("500.00"), id = 2),
            ),
            emptyRepository.transactions().sortedBy { it.id },
        )
        assertEquals(exportVersion2, emptyRepository.exportJson())
    }
}
