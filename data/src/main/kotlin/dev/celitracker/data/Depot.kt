package dev.celitracker.data

import dev.celitracker.engine.Compte
import dev.celitracker.engine.PlafondAnnuel
import dev.celitracker.engine.Profil
import dev.celitracker.engine.Reglages
import dev.celitracker.engine.SnapshotArc
import dev.celitracker.engine.Transaction
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Page de l'ARC qui enonce le plafond CELI de l'annee en cours. Une donnee,
 * pas une constante figee dans le code appelant: une reorganisation du site se
 * corrige dans les reglages, sans nouvelle version de l'application.
 */
const val URL_PAGE_ARC_PAR_DEFAUT =
    "https://www.canada.ca/fr/agence-revenu/services/impot/particuliers/sujets/" +
        "compte-epargne-libre-impot/cotiser/calculer-droits.html"

/**
 * Expose uniquement les types de `:engine`, jamais les entites Room. Aucune
 * valeur calculee n'est persistee: les droits sont recalcules a la lecture
 * par `:engine`.
 */
class Depot(private val base: CeliTrackerBase) {
    private val dao get() = base.dao()

    suspend fun profil(): Profil? = dao.profil()?.let {
        Profil(
            anneeNaissance = it.anneeNaissance,
            dateOuvertureCeliapp = it.dateOuvertureCeliapp,
        )
    }

    suspend fun enregistrerProfil(profil: Profil) {
        dao.enregistrerProfil(
            ProfilEntity(
                anneeNaissance = profil.anneeNaissance,
                dateOuvertureCeliapp = profil.dateOuvertureCeliapp,
            ),
        )
    }

    suspend fun plafonds(): List<PlafondAnnuel> = dao.plafonds().map { PlafondAnnuel(it.compte, it.annee, it.montant, it.confirme) }

    suspend fun enregistrerPlafond(plafond: PlafondAnnuel) {
        dao.enregistrerPlafond(PlafondEntity(plafond.compte, plafond.annee, plafond.montant, plafond.confirme))
    }

    suspend fun supprimerPlafond(compte: Compte, annee: Int) = dao.supprimerPlafond(compte, annee)

    suspend fun transactions(): List<Transaction> = dao.transactions().map { Transaction(it.compte, it.date, it.type, it.montant, it.id) }

    suspend fun ajouterTransaction(transaction: Transaction) {
        valider(transaction)
        dao.ajouterTransaction(transaction.versEntite())
    }

    suspend fun modifierTransaction(transaction: Transaction) {
        valider(transaction)
        val lignesModifiees = dao.modifierTransaction(transaction.versEntite())
        if (lignesModifiees != 1) refuserSaisie(RaisonSaisie.TRANSACTION_INTROUVABLE)
    }

    /**
     * Rejette les saisies incoherentes ici, pas dans le moteur: une
     * transaction anterieure a l'annee d'admissibilite produirait des
     * resultats differents selon le moteur consulte.
     */
    private suspend fun valider(transaction: Transaction) {
        if (transaction.montant <= BigDecimal.ZERO) refuserSaisie(RaisonSaisie.MONTANT_NON_POSITIF)
        val profil = profil() ?: refuserSaisie(RaisonSaisie.PROFIL_ABSENT)
        when (transaction.compte) {
            Compte.CELI -> if (transaction.date.isBefore(LocalDate.of(profil.anneeAdmissibiliteCeli, 1, 1))) {
                refuserSaisie(RaisonSaisie.CELI_AVANT_ADMISSIBILITE)
            }

            Compte.CELIAPP -> {
                val ouverture = profil.dateOuvertureCeliapp ?: refuserSaisie(RaisonSaisie.CELIAPP_NON_OUVERT)
                if (transaction.date.isBefore(ouverture)) refuserSaisie(RaisonSaisie.CELIAPP_AVANT_OUVERTURE)
            }
        }
    }

    private fun Transaction.versEntite() = TransactionEntity(id, compte, date, type, montant)

    suspend fun supprimerTransaction(id: Long) = dao.supprimerTransaction(id)

    suspend fun snapshotsArc(): List<SnapshotArc> = dao.snapshotsArc().map { SnapshotArc(it.id, it.compte, it.dateReference, it.droitsDeclares) }

    suspend fun enregistrerSnapshotArc(snapshot: SnapshotArc) {
        dao.enregistrerSnapshotArc(
            SnapshotArcEntity(
                compte = snapshot.compte,
                dateReference = snapshot.dateReference,
                droitsDeclares = snapshot.droitsDeclares,
            ),
        )
    }

    suspend fun reglages(): Reglages = dao.reglages()?.let { Reglages(it.urlPageArc, it.dateDerniereVerification) }
        ?: Reglages(urlPageArc = URL_PAGE_ARC_PAR_DEFAUT, dateDerniereVerification = null)

    suspend fun enregistrerReglages(reglages: Reglages) {
        dao.enregistrerReglages(ReglagesEntity(urlPageArc = reglages.urlPageArc, dateDerniereVerification = reglages.dateDerniereVerification))
    }

    /**
     * Ecrit la seule date de verification, sans relire ni reecrire l'adresse:
     * une lecture ARC en cours ne doit pas ecraser une adresse que
     * l'utilisateur vient de changer.
     */
    suspend fun noterVerificationArc(date: Instant) {
        if (dao.noterVerificationArc(date) == 0) {
            dao.enregistrerReglages(ReglagesEntity(urlPageArc = reglages().urlPageArc, dateDerniereVerification = date))
        }
    }

    /** Reserve a `importerJson` : remplace tout le contenu en une transaction. */
    internal suspend fun remplacerTout(
        profil: ProfilEntity?,
        plafonds: List<PlafondEntity>,
        transactions: List<TransactionEntity>,
        snapshots: List<SnapshotArcEntity>,
        reglages: ReglagesEntity,
    ) = dao.remplacerTout(profil, plafonds, transactions, snapshots, reglages)
}
