package dev.celitracker.data

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class DepotTest {

    private val fichier = File.createTempFile("celi-tracker-test", ".db")
    private val base = ouvrirBase(fichier.absolutePath)
    private val depot = Depot(base)

    @AfterTest
    fun fermer() {
        base.close()
        fichier.delete()
    }

    private val profilCeli = Profil(
        anneeAdmissibiliteCeli = 2020,
        anneeNaissance = 2000,
        dateOuvertureCeliapp = LocalDate.of(2023, 6, 1),
    )

    @Test
    fun `profil fait l'aller-retour sur une base reelle`() = runTest {
        assertNull(depot.profil())

        depot.enregistrerProfil(profilCeli)

        assertEquals(profilCeli, depot.profil())
    }

    @Test
    fun `plafond fait l'aller-retour`() = runTest {
        val plafond = PlafondAnnuel(Compte.CELI, 2026, BigDecimal("7000.00"), confirme = true)

        depot.enregistrerPlafond(plafond)

        assertEquals(listOf(plafond), depot.plafonds())
    }

    @Test
    fun `snapshot arc fait l'aller-retour`() = runTest {
        depot.enregistrerSnapshotArc(SnapshotArc(0, Compte.CELI, LocalDate.of(2026, 1, 1), BigDecimal("1234.56")))

        val snapshots = depot.snapshotsArc()

        assertEquals(1, snapshots.size)
        assertEquals(Compte.CELI, snapshots[0].compte)
        assertEquals(BigDecimal("1234.56"), snapshots[0].droitsDeclares)
    }

    @Test
    fun `reglages par defaut puis aller-retour`() = runTest {
        assertEquals(Reglages(urlPageArc = "", dateDerniereVerification = null), depot.reglages())

        val reglages = Reglages(urlPageArc = "https://arc.gc.ca", dateDerniereVerification = Instant.parse("2026-09-08T12:00:00Z"))
        depot.enregistrerReglages(reglages)

        assertEquals(reglages, depot.reglages())
    }

    @Test
    fun `transaction fait l'aller-retour et un montant a deux decimales reste exact`() = runTest {
        depot.enregistrerProfil(profilCeli)
        val transaction = Transaction(Compte.CELI, LocalDate.of(2026, 1, 15), TypeTx.DEPOT, BigDecimal("4321.28"))

        depot.ajouterTransaction(transaction)

        val relue = depot.transactions().single()
        assertEquals("4321.28", relue.montant.toPlainString())
        assertEquals(transaction.compte, relue.compte)
        assertEquals(transaction.date, relue.date)
        assertEquals(transaction.type, relue.type)
    }

    @Test
    fun `la colonne montant de transactions est de type TEXT`() = runTest {
        depot.enregistrerProfil(profilCeli)
        depot.ajouterTransaction(Transaction(Compte.CELI, LocalDate.of(2026, 1, 15), TypeTx.DEPOT, BigDecimal("100.00")))
        base.close()

        val connection = BundledSQLiteDriver().open(fichier.absolutePath)
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
    fun `suppression d'une transaction`() = runTest {
        depot.enregistrerProfil(profilCeli)
        depot.ajouterTransaction(Transaction(Compte.CELI, LocalDate.of(2026, 1, 15), TypeTx.DEPOT, BigDecimal("50.00")))
        val id = depot.transactions().single().id

        depot.supprimerTransaction(id)

        assertTrue(depot.transactions().isEmpty())
    }

    @Test
    fun `ajouterTransaction rejette un montant nul ou negatif`() = runTest {
        depot.enregistrerProfil(profilCeli)

        assertFailsWith<IllegalArgumentException> {
            depot.ajouterTransaction(Transaction(Compte.CELI, LocalDate.of(2026, 1, 15), TypeTx.DEPOT, BigDecimal.ZERO))
        }
        assertFailsWith<IllegalArgumentException> {
            depot.ajouterTransaction(Transaction(Compte.CELI, LocalDate.of(2026, 1, 15), TypeTx.DEPOT, BigDecimal("-1.00")))
        }
    }

    @Test
    fun `ajouterTransaction rejette une transaction CELI anterieure a l'annee d'admissibilite`() = runTest {
        depot.enregistrerProfil(profilCeli)

        assertFailsWith<IllegalArgumentException> {
            depot.ajouterTransaction(Transaction(Compte.CELI, LocalDate.of(2019, 12, 31), TypeTx.DEPOT, BigDecimal("100.00")))
        }
    }

    @Test
    fun `ajouterTransaction rejette une transaction CELIAPP anterieure a l'ouverture du compte`() = runTest {
        depot.enregistrerProfil(profilCeli)

        assertFailsWith<IllegalArgumentException> {
            depot.ajouterTransaction(Transaction(Compte.CELIAPP, LocalDate.of(2023, 5, 31), TypeTx.DEPOT, BigDecimal("100.00")))
        }
    }

    @Test
    fun `ajouterTransaction rejette une transaction CELIAPP sans date d'ouverture enregistree`() = runTest {
        depot.enregistrerProfil(profilCeli.copy(dateOuvertureCeliapp = null))

        assertFailsWith<IllegalArgumentException> {
            depot.ajouterTransaction(Transaction(Compte.CELIAPP, LocalDate.of(2026, 1, 1), TypeTx.DEPOT, BigDecimal("100.00")))
        }
    }
}
