package dev.celitracker.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
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

/**
 * Pins the on-disk format that installed copies of the app already hold: table
 * and column names, stored enum values and the schema identity hash of version
 * 2. Renaming a Kotlin property or enum constant without keeping its stored
 * name fails here instead of on a user's phone.
 */
class SchemaV2CompatibilityTest {

    private val file = File.createTempFile("celi-tracker-schema-v2", ".db").apply { delete() }

    @AfterTest
    fun cleanUp() {
        file.delete()
    }

    /** The exact tables of `data/schemas/.../2.json`, filled the way version 2 wrote them. */
    private fun writeVersion2Database() {
        val connection = BundledSQLiteDriver().open(file.absolutePath)
        listOf(
            "CREATE TABLE `profil` (`id` INTEGER NOT NULL, `anneeNaissance` INTEGER NOT NULL, `dateOuvertureCeliapp` TEXT, PRIMARY KEY(`id`))",
            "CREATE TABLE `plafonds` (`compte` TEXT NOT NULL, `annee` INTEGER NOT NULL, `montant` TEXT NOT NULL, `confirme` INTEGER NOT NULL, PRIMARY KEY(`compte`, `annee`))",
            "CREATE TABLE `transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `compte` TEXT NOT NULL, `date` TEXT NOT NULL, `type` TEXT NOT NULL, `montant` TEXT NOT NULL)",
            "CREATE TABLE `snapshots_arc` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `compte` TEXT NOT NULL, `dateReference` TEXT NOT NULL, `droitsDeclares` TEXT NOT NULL)",
            "CREATE TABLE `reglages` (`id` INTEGER NOT NULL, `urlPageArc` TEXT NOT NULL, `dateDerniereVerification` TEXT, PRIMARY KEY(`id`))",
            "CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
            "INSERT INTO room_master_table (id, identity_hash) VALUES (42, '5ed078fc4f57631f12fd1270ea635faf')",
            "INSERT INTO profil (id, anneeNaissance, dateOuvertureCeliapp) VALUES (0, 2002, '2023-06-01')",
            "INSERT INTO plafonds (compte, annee, montant, confirme) VALUES ('CELI', 2026, '7000.00', 1)",
            "INSERT INTO plafonds (compte, annee, montant, confirme) VALUES ('CELIAPP', 2026, '8000.00', 0)",
            "INSERT INTO transactions (id, compte, date, type, montant) VALUES (1, 'CELI', '2026-01-15', 'DEPOT', '1234.56')",
            "INSERT INTO transactions (id, compte, date, type, montant) VALUES (2, 'CELIAPP', '2025-03-01', 'RETRAIT', '500.00')",
            "INSERT INTO snapshots_arc (id, compte, dateReference, droitsDeclares) VALUES (1, 'CELI', '2026-01-01', '41800.00')",
            "INSERT INTO reglages (id, urlPageArc, dateDerniereVerification) VALUES (0, 'https://example.org/arc', '2026-09-08T12:00:00Z')",
            "PRAGMA user_version = 2",
        ).forEach(connection::execSQL)
        connection.close()
    }

    private fun openRepository() = configureDatabase(Room.databaseBuilder<CeliTrackerDatabase>(name = file.absolutePath))

    @Test
    fun `a database written by schema version 2 opens and reads unchanged`() = runTest {
        writeVersion2Database()
        val database = openRepository()
        val repository = Repository(database)

        assertEquals(Profile(birthYear = 2002, fhsaOpeningDate = LocalDate.of(2023, 6, 1)), repository.profile())
        assertEquals(
            setOf(
                AnnualLimit(Account.TFSA, 2026, BigDecimal("7000.00"), confirmed = true),
                AnnualLimit(Account.FHSA, 2026, BigDecimal("8000.00"), confirmed = false),
            ),
            repository.limits().toSet(),
        )
        assertEquals(
            listOf(
                Transaction(Account.TFSA, LocalDate.of(2026, 1, 15), TransactionType.DEPOSIT, BigDecimal("1234.56"), id = 1),
                Transaction(Account.FHSA, LocalDate.of(2025, 3, 1), TransactionType.WITHDRAWAL, BigDecimal("500.00"), id = 2),
            ),
            repository.transactions().sortedBy { it.id },
        )
        assertEquals(listOf(CraSnapshot(1, Account.TFSA, LocalDate.of(2026, 1, 1), BigDecimal("41800.00"))), repository.craSnapshots())
        assertEquals(Settings("https://example.org/arc", Instant.parse("2026-09-08T12:00:00Z")), repository.settings())
        database.close()
    }

    @Test
    fun `new rows are stored under the version 2 names and values`() = runTest {
        writeVersion2Database()
        val database = openRepository()
        Repository(database).addTransaction(Transaction(Account.FHSA, LocalDate.of(2026, 2, 1), TransactionType.WITHDRAWAL, BigDecimal("12.30")))
        database.close()

        val connection = BundledSQLiteDriver().open(file.absolutePath)
        val statement = connection.prepare("SELECT compte, date, type, montant FROM transactions WHERE id > 2")
        statement.step()
        val stored = List(4) { statement.getText(it) }
        statement.close()
        connection.close()

        assertEquals(listOf("CELIAPP", "2026-02-01", "RETRAIT", "12.30"), stored)
    }
}
