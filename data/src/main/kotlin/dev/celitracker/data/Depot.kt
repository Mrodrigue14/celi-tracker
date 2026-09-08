package dev.celitracker.data

import dev.celitracker.engine.Compte
import dev.celitracker.engine.PlafondAnnuel
import dev.celitracker.engine.Profil
import dev.celitracker.engine.Reglages
import dev.celitracker.engine.SnapshotArc
import dev.celitracker.engine.Transaction
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Expose uniquement les types de `:engine`, jamais les entites Room. Aucune
 * valeur calculee n'est persistee: les droits sont recalcules a la lecture
 * par `:engine`.
 */
class Depot(private val base: CeliTrackerBase) {
    private val dao get() = base.dao()

    suspend fun profil(): Profil? = dao.profil()?.let {
        Profil(
            anneeAdmissibiliteCeli = it.anneeAdmissibiliteCeli,
            anneeNaissance = it.anneeNaissance,
            dateOuvertureCeliapp = it.dateOuvertureCeliapp,
        )
    }

    suspend fun enregistrerProfil(profil: Profil) {
        dao.enregistrerProfil(
            ProfilEntity(
                anneeAdmissibiliteCeli = profil.anneeAdmissibiliteCeli,
                anneeNaissance = profil.anneeNaissance,
                dateOuvertureCeliapp = profil.dateOuvertureCeliapp,
            )
        )
    }

    suspend fun plafonds(): List<PlafondAnnuel> =
        dao.plafonds().map { PlafondAnnuel(it.compte, it.annee, it.montant, it.confirme) }

    suspend fun enregistrerPlafond(plafond: PlafondAnnuel) {
        dao.enregistrerPlafond(PlafondEntity(plafond.compte, plafond.annee, plafond.montant, plafond.confirme))
    }

    suspend fun transactions(): List<Transaction> =
        dao.transactions().map { Transaction(it.compte, it.date, it.type, it.montant, it.id) }

    /**
     * Rejette les saisies incoherentes ici, pas dans le moteur: une
     * transaction anterieure a l'annee d'admissibilite produirait des
     * resultats differents selon le moteur consulte.
     */
    suspend fun ajouterTransaction(transaction: Transaction) {
        require(transaction.montant > BigDecimal.ZERO) { "Le montant doit etre positif." }
        val profil = requireNotNull(profil()) { "Aucun profil enregistre." }
        when (transaction.compte) {
            Compte.CELI -> require(!transaction.date.isBefore(LocalDate.of(profil.anneeAdmissibiliteCeli, 1, 1))) {
                "Transaction CELI anterieure a l'annee d'admissibilite."
            }
            Compte.CELIAPP -> {
                val ouverture = requireNotNull(profil.dateOuvertureCeliapp) {
                    "Aucune date d'ouverture CELIAPP enregistree."
                }
                require(!transaction.date.isBefore(ouverture)) {
                    "Transaction CELIAPP anterieure a l'ouverture du compte."
                }
            }
        }
        dao.ajouterTransaction(
            TransactionEntity(
                compte = transaction.compte,
                date = transaction.date,
                type = transaction.type,
                montant = transaction.montant,
            )
        )
    }

    suspend fun supprimerTransaction(id: Long) = dao.supprimerTransaction(id)

    suspend fun snapshotsArc(): List<SnapshotArc> =
        dao.snapshotsArc().map { SnapshotArc(it.id, it.compte, it.dateReference, it.droitsDeclares) }

    suspend fun enregistrerSnapshotArc(snapshot: SnapshotArc) {
        dao.enregistrerSnapshotArc(
            SnapshotArcEntity(
                compte = snapshot.compte,
                dateReference = snapshot.dateReference,
                droitsDeclares = snapshot.droitsDeclares,
            )
        )
    }

    suspend fun reglages(): Reglages =
        dao.reglages()?.let { Reglages(it.urlPageArc, it.dateDerniereVerification) }
            ?: Reglages(urlPageArc = "", dateDerniereVerification = null)

    suspend fun enregistrerReglages(reglages: Reglages) {
        dao.enregistrerReglages(ReglagesEntity(urlPageArc = reglages.urlPageArc, dateDerniereVerification = reglages.dateDerniereVerification))
    }
}
