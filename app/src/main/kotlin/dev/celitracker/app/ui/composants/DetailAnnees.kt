package dev.celitracker.app.ui.composants

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.celitracker.app.R
import kotlinx.coroutines.launch
import java.math.BigDecimal

/** Le graphique se lit d'un coup d'oeil; les cartes, elles, portent le detail. */
private const val PART_VOLET_GRAPHIQUE = 0.42f

/**
 * Structure commune aux details du CELI et du CELIAPP: le graphique, puis une
 * carte par annee, la plus recente d'abord; toucher une barre amene a sa carte.
 * Seul le contenu change d'un compte a l'autre: les regles restent chacune dans
 * son moteur.
 *
 * Sur un ecran large, le graphique prend son propre volet a gauche: il reste
 * sous les yeux pendant que les annees defilent a droite, au lieu de s'en aller
 * des la premiere carte.
 */
@Composable
fun <T> ListeDetailAnnees(
    lignes: List<T>,
    annee: (T) -> Int,
    titreGraphique: String,
    valeurGraphique: (T) -> BigDecimal,
    couleur: Color,
    modifier: Modifier = Modifier,
    carte: @Composable (ligne: T, enCours: Boolean) -> Unit,
) {
    val liste = rememberLazyListState()
    val portee = rememberCoroutineScope()
    val anneeEnCours = lignes.lastOrNull()?.let(annee)
    val affichees = lignes.reversed()
    val deuxVolets = ecranLarge() && lignes.isNotEmpty()
    // Le titre « Année par année » precede toujours la premiere carte; le
    // graphique ne compte que s'il est reste dans la liste.
    val elementsAvantAnnees = if (deuxVolets) 1 else 2

    fun allerA(cible: Int) {
        val position = affichees.indexOfFirst { annee(it) == cible }
        if (position >= 0) portee.launch { liste.animateScrollToItem(elementsAvantAnnees + position) }
    }

    val graphique: @Composable (Modifier) -> Unit = { modifierGraphique ->
        Column(modifier = modifierGraphique) {
            TitreSection(titreGraphique, Modifier.padding(top = 12.dp), couleur)
            GraphiqueAnnees(
                valeurs = lignes.map { annee(it) to valeurGraphique(it) },
                couleur = couleur,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                onClicAnnee = ::allerA,
            )
        }
    }

    val annees: @Composable (Modifier) -> Unit = { modifierAnnees ->
        LazyColumn(
            modifier = modifierAnnees,
            state = liste,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (lignes.isNotEmpty()) {
                if (!deuxVolets) item(key = "evolution") { graphique(Modifier) }
                item(key = "titre-annees") {
                    TitreSection(stringResource(R.string.detail_annee_par_annee), Modifier.padding(top = 12.dp), couleur)
                }
            }
            items(affichees, key = { annee(it) }) { ligne ->
                carte(ligne, annee(ligne) == anneeEnCours)
            }
        }
    }

    if (deuxVolets) {
        Row(modifier = modifier) {
            graphique(Modifier.weight(PART_VOLET_GRAPHIQUE).padding(start = 16.dp, end = 8.dp))
            annees(Modifier.weight(1f - PART_VOLET_GRAPHIQUE))
        }
    } else {
        annees(modifier)
    }
}
