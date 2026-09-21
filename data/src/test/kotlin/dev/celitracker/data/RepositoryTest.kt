package dev.celitracker.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RepositoryTest {

    private val file = File.createTempFile("celi-tracker-test", ".db")
    private val database = configureDatabase(Room.databaseBuilder<CeliTrackerDatabase>(name = file.absolutePath))
    private val repository = Repository(database)

    @AfterTest
    fun close() {
        database.close()
        file.delete()
    }

    private val tfsaProfile = Profile(
        birthYear = 2002, // eligible for the TFSA in 2020
        fhsaOpeningDate = LocalDate.of(2023, 6, 1),
    )

    @Test
    fun `profile round trips on a real database`() = runTest {
        assertNull(repository.profile())

        repository.saveProfile(tfsaProfile)

        assertEquals(tfsaProfile, repository.profile())
    }

    @Test
    fun `limit round trips`() = runTest {
        val limit = AnnualLimit(Account.TFSA, 2026, BigDecimal("7000.00"), confirmed = true)

        repository.saveLimit(limit)

        assertEquals(listOf(limit), repository.limits())
    }

    @Test
    fun `cra snapshot round trips`() = runTest {
        repository.saveCraSnapshot(CraSnapshot(0, Account.TFSA, LocalDate.of(2026, 1, 1), BigDecimal("1234.56")))

        val snapshots = repository.craSnapshots()

        assertEquals(1, snapshots.size)
        assertEquals(Account.TFSA, snapshots[0].account)
        assertEquals(BigDecimal("1234.56"), snapshots[0].declaredRoom)
    }

    @Test
    fun `default settings then round trip`() = runTest {
        assertEquals(Settings(craPageUrl = DEFAULT_CRA_PAGE_URL, lastCheckDate = null), repository.settings())

        val settings = Settings(craPageUrl = "https://arc.gc.ca", lastCheckDate = Instant.parse("2026-09-08T12:00:00Z"))
        repository.saveSettings(settings)

        assertEquals(settings, repository.settings())
    }

    @Test
    fun `transaction round trips and an amount with two decimals stays exact`() = runTest {
        repository.saveProfile(tfsaProfile)
        val transaction = Transaction(Account.TFSA, LocalDate.of(2026, 1, 15), TransactionType.DEPOSIT, BigDecimal("1234.56"))

        repository.addTransaction(transaction)

        val readBack = repository.transactions().single()
        assertEquals("1234.56", readBack.amount.toPlainString())
        assertEquals(transaction.account, readBack.account)
        assertEquals(transaction.date, readBack.date)
        assertEquals(transaction.type, readBack.type)
    }

    @Test
    fun `the transactions amount column is of type TEXT`() = runTest {
        repository.saveProfile(tfsaProfile)
        repository.addTransaction(Transaction(Account.TFSA, LocalDate.of(2026, 1, 15), TransactionType.DEPOSIT, BigDecimal("100.00")))
        database.close()

        val connection = BundledSQLiteDriver().open(file.absolutePath)
        val types = mutableMapOf<String, String>()
        connection.prepare("PRAGMA table_info(transactions)").use { stmt ->
            while (stmt.step()) {
                types[stmt.getText(1)] = stmt.getText(2)
            }
        }
        connection.close()

        assertEquals("TEXT", types["montant"])
    }

    @Test
    fun `deleting a transaction`() = runTest {
        repository.saveProfile(tfsaProfile)
        repository.addTransaction(Transaction(Account.TFSA, LocalDate.of(2026, 1, 15), TransactionType.DEPOSIT, BigDecimal("50.00")))
        val id = repository.transactions().single().id

        repository.deleteTransaction(id)

        assertTrue(repository.transactions().isEmpty())
    }

    @Test
    fun `updating a transaction`() = runTest {
        repository.saveProfile(tfsaProfile)
        repository.addTransaction(Transaction(Account.TFSA, LocalDate.of(2026, 1, 15), TransactionType.DEPOSIT, BigDecimal("50.00")))
        val updated = repository.transactions().single().copy(type = TransactionType.WITHDRAWAL, amount = BigDecimal("75.25"))

        repository.updateTransaction(updated)

        assertEquals(listOf(updated), repository.transactions())
    }

    @Test
    fun `updateTransaction applies the same validation as adding`() = runTest {
        repository.saveProfile(tfsaProfile)
        repository.addTransaction(Transaction(Account.TFSA, LocalDate.of(2026, 1, 15), TransactionType.DEPOSIT, BigDecimal("50.00")))
        val earlier = repository.transactions().single().copy(date = LocalDate.of(2019, 12, 31))

        assertFailsWith<IllegalArgumentException> { repository.updateTransaction(earlier) }
    }

    @Test
    fun `updateTransaction rejects a nonexistent transaction`() = runTest {
        repository.saveProfile(tfsaProfile)

        assertFailsWith<IllegalArgumentException> {
            repository.updateTransaction(Transaction(Account.TFSA, LocalDate.of(2026, 1, 15), TransactionType.DEPOSIT, BigDecimal("50.00"), id = 42))
        }
    }

    @Test
    fun `addTransaction rejects a zero or negative amount`() = runTest {
        repository.saveProfile(tfsaProfile)

        assertFailsWith<IllegalArgumentException> {
            repository.addTransaction(Transaction(Account.TFSA, LocalDate.of(2026, 1, 15), TransactionType.DEPOSIT, BigDecimal.ZERO))
        }
        assertFailsWith<IllegalArgumentException> {
            repository.addTransaction(Transaction(Account.TFSA, LocalDate.of(2026, 1, 15), TransactionType.DEPOSIT, BigDecimal("-1.00")))
        }
    }

    @Test
    fun `addTransaction rejects a TFSA transaction before the eligibility year`() = runTest {
        repository.saveProfile(tfsaProfile)

        assertFailsWith<IllegalArgumentException> {
            repository.addTransaction(Transaction(Account.TFSA, LocalDate.of(2019, 12, 31), TransactionType.DEPOSIT, BigDecimal("100.00")))
        }
    }

    @Test
    fun `addTransaction rejects an FHSA transaction before the account is opened`() = runTest {
        repository.saveProfile(tfsaProfile)

        assertFailsWith<IllegalArgumentException> {
            repository.addTransaction(Transaction(Account.FHSA, LocalDate.of(2023, 5, 31), TransactionType.DEPOSIT, BigDecimal("100.00")))
        }
    }

    @Test
    fun `addTransaction rejects an FHSA transaction with no opening date on record`() = runTest {
        repository.saveProfile(tfsaProfile.copy(fhsaOpeningDate = null))

        assertFailsWith<IllegalArgumentException> {
            repository.addTransaction(Transaction(Account.FHSA, LocalDate.of(2026, 1, 1), TransactionType.DEPOSIT, BigDecimal("100.00")))
        }
    }
}
