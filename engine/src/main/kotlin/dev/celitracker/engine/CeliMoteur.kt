package dev.celitracker.engine

import java.math.BigDecimal

/**
 * Droits de cotisation CELI pour une annee.
 *
 * [plafondManquant] signale une annee dont le plafond est absent de la table ou
 * pas encore confirme. Son plafond vaut alors zero: le moteur n'invente aucun
 * droit, et l'erreur penche du cote prudent (droits sous-estimes plutot que
 * sur-estimes, donc jamais d'incitation a sur-cotiser).
 */
data class DroitsAnnee(
    val annee: Int,
    val plafond: BigDecimal,
    val droitsDebut: BigDecimal,
    val depots: BigDecimal,
    val retraits: BigDecimal,
    val droitsFin: BigDecimal,
    val plafondManquant: Boolean,
)

/**
 * Moteur CELI. Fonction pure: memes entrees, memes sorties, aucun etat conserve
 * entre deux appels.
 *
 * NE PAS fusionner avec [CeliappMoteur]. Les deux regimes divergent sur chaque
 * axe, a commencer par le fait qu'un retrait CELI redonne des droits alors
 * qu'un retrait CELIAPP n'en redonne jamais.
 */
object CeliMoteur {

    fun droitsParAnnee(
        profil: Profil,
        plafonds: List<PlafondAnnuel>,
        transactions: List<Transaction>,
        jusqua: Int,
    ): List<DroitsAnnee> {
        val parAnnee = plafonds
            .filter { it.compte == Compte.CELI && it.confirme }
            .associateBy { it.annee }
        val txCeli = transactions.filter { it.compte == Compte.CELI }

        val resultat = mutableListOf<DroitsAnnee>()
        var droitsFinPrecedent = BigDecimal.ZERO
        var retraitsPrecedent = BigDecimal.ZERO

        for (annee in profil.anneeAdmissibiliteCeli..jusqua) {
            val plafondAnnee = parAnnee[annee]
            val plafond = plafondAnnee?.montant ?: BigDecimal.ZERO
            val depots = somme(txCeli, annee, TypeTx.DEPOT)
            val retraits = somme(txCeli, annee, TypeTx.RETRAIT)

            // Les retraits de l'annee PRECEDENTE reviennent le 1er janvier;
            // ceux de l'annee courante ne comptent pas encore.
            val droitsDebut = droitsFinPrecedent + plafond + retraitsPrecedent

            // Pas de coerceAtLeast(ZERO) ici: un solde negatif EST la
            // sur-cotisation et doit se propager a l'annee suivante. Le
            // classeur d'origine utilisait MAX(..., 0), ce qui l'effacait.
            val droitsFin = droitsDebut - depots

            resultat += DroitsAnnee(
                annee = annee,
                plafond = plafond.argent(),
                droitsDebut = droitsDebut.argent(),
                depots = depots.argent(),
                retraits = retraits.argent(),
                droitsFin = droitsFin.argent(),
                plafondManquant = plafondAnnee == null,
            )

            droitsFinPrecedent = droitsFin
            retraitsPrecedent = retraits
        }
        return resultat
    }

    private fun somme(transactions: List<Transaction>, annee: Int, type: TypeTx): BigDecimal =
        transactions
            .filter { it.date.year == annee && it.type == type }
            .fold(BigDecimal.ZERO) { total, tx -> total + tx.montant }
}
