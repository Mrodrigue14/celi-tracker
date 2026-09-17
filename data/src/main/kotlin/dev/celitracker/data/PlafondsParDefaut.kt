package dev.celitracker.data

import dev.celitracker.engine.Compte
import dev.celitracker.engine.PLAFONDS_CELI_PUBLIES
import dev.celitracker.engine.PlafondAnnuel

/**
 * Inscrit les plafonds CELI publies par l'ARC qui manquent encore en base.
 * Une annee deja enregistree n'est jamais ecrasee: une correction faite a la
 * main reste la verite pour l'utilisateur.
 *
 * Ils sont enregistres confirmes: ils viennent d'une table officielle
 * transcrite dans le depot, pas d'une lecture automatique dont le resultat
 * reste a valider.
 */
suspend fun Depot.garnirPlafondsPublies() {
    val anneesConnues = plafonds().filter { it.compte == Compte.CELI }.map { it.annee }.toSet()
    PLAFONDS_CELI_PUBLIES
        .filterKeys { it !in anneesConnues }
        .forEach { (annee, montant) ->
            enregistrerPlafond(PlafondAnnuel(compte = Compte.CELI, annee = annee, montant = montant, confirme = true))
        }
}
