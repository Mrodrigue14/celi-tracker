package dev.celitracker.data

import dev.celitracker.engine.Account
import dev.celitracker.engine.AnnualLimit
import dev.celitracker.engine.CraSnapshot
import dev.celitracker.engine.Profile
import dev.celitracker.engine.Settings
import dev.celitracker.engine.Transaction
import dev.celitracker.engine.TransactionType
import kotlinx.coroutines.test.runTest
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ExportJsonTest {

    private val database = TestDatabase()
    private val repository = database.repository

    private val emptyDatabase = TestDatabase()
    private val emptyRepository = emptyDatabase.repository

    @AfterTest
    fun close() {
        database.close()
        emptyDatabase.close()
    }

    private val tfsaProfile = Profile(
        birthYear = 2002,
        fhsaOpeningDate = LocalDate.of(2023, 6, 1),
    )

    private suspend fun populateTestData() {
        repository.saveProfile(tfsaProfile)
        // Shuffled on purpose: the export must sort.
        repository.saveLimit(AnnualLimit(Account.FHSA, 2026, BigDecimal("8000.00"), confirmed = true))
        repository.saveLimit(AnnualLimit(Account.TFSA, 2026, BigDecimal("7000.00"), confirmed = true))
        repository.saveLimit(AnnualLimit(Account.TFSA, 2020, BigDecimal("6000.00"), confirmed = false))
        repository.addTransaction(Transaction(Account.TFSA, LocalDate.of(2026, 1, 15), TransactionType.DEPOSIT, BigDecimal("1234.56")))
        repository.addTransaction(Transaction(Account.FHSA, LocalDate.of(2023, 7, 1), TransactionType.DEPOSIT, BigDecimal("1000.00")))
        repository.saveCraSnapshot(CraSnapshot(0, Account.TFSA, LocalDate.of(2026, 1, 1), BigDecimal("1234.56")))
        repository.saveSettings(Settings(craPageUrl = "https://arc.gc.ca", lastCheckDate = Instant.parse("2026-09-08T12:00:00Z")))
    }

    @Test
    fun `round trip without loss - reexporting after import gives an identical string`() = runTest {
        populateTestData()
        val exportOriginal = repository.exportJson()

        emptyRepository.importJson(exportOriginal)
        val reimportedExport = emptyRepository.exportJson()

        assertEquals(exportOriginal, reimportedExport)
    }

    @Test
    fun `an amount with two decimals survives export and import`() = runTest {
        repository.saveProfile(tfsaProfile)
        repository.addTransaction(Transaction(Account.TFSA, LocalDate.of(2026, 1, 15), TransactionType.DEPOSIT, BigDecimal("1234.56")))

        val export = repository.exportJson()

        assertTrue(export.contains("\"montant\":\"1234.56\""))

        emptyRepository.importJson(export)
        val readBack = emptyRepository.transactions().single()
        assertEquals("1234.56", readBack.amount.toPlainString())
    }

    @Test
    fun `an unknown version is rejected`() = runTest {
        val unknownVersionExport = """
            {"version":99,"profil":null,"plafonds":[],"transactions":[],"snapshotsArc":[],
             "reglages":{"urlPageArc":"","dateDerniereVerification":null}}
        """.trimIndent()

        assertFailsWith<IllegalArgumentException> {
            emptyRepository.importJson(unknownVersionExport)
        }
    }

    @Test
    fun `malformed JSON is rejected without overwriting the existing database`() = runTest {
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
    fun `a version 1 export stays importable, its eligibility year ignored`() = runTest {
        val exportV1 = """
            {"version":1,
             "profil":{"anneeAdmissibiliteCeli":1999,"anneeNaissance":2002,"dateOuvertureCeliapp":"2023-06-01"},
             "plafonds":[],"transactions":[],"snapshotsArc":[],
             "reglages":{"urlPageArc":"https://arc.gc.ca","dateDerniereVerification":null}}
        """.trimIndent()

        repository.importJson(exportV1)

        // 2002 + 18, not the 1999 written in the file.
        assertEquals(2020, repository.profile()?.tfsaEligibilityYear)
    }

    @Test
    fun `a failure mid write does not leave the database half emptied`() = runTest {
        populateTestData()
        val before = repository.exportJson()

        // Duplicate transaction id: the insert fails after the tables are cleared and profile and limits written.
        val exportFailingWrite = """
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
            repository.importJson(exportFailingWrite)
        }

        assertEquals(before, repository.exportJson())
        assertEquals(tfsaProfile, repository.profile())
        assertEquals(3, repository.limits().size)
        assertEquals(2, repository.transactions().size)
        assertEquals(1, repository.craSnapshots().size)
        assertEquals(
            Settings(craPageUrl = "https://arc.gc.ca", lastCheckDate = Instant.parse("2026-09-08T12:00:00Z")),
            repository.settings(),
        )
    }

    @Test
    fun `the export is deterministic`() = runTest {
        populateTestData()

        val firstExport = repository.exportJson()
        val secondExport = repository.exportJson()

        assertEquals(firstExport, secondExport)
    }

    /** Users keep such files: field names and stored values must survive any renaming in code. */
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
