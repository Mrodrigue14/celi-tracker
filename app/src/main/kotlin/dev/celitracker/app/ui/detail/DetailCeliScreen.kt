package dev.celitracker.app.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.celitracker.app.CeliTrackerApplication
import dev.celitracker.app.R
import dev.celitracker.app.ui.composants.CarteAnnee
import dev.celitracker.app.ui.composants.GraphiqueAnnees
import dev.celitracker.app.ui.format.formatMontant
import dev.celitracker.engine.DroitsAnnee
import kotlinx.coroutines.launch
import java.math.BigDecimal

/** Le graphique et le titre « Année par année » precedent la premiere carte. */
private const val ELEMENTS_AVANT_ANNEES = 2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailCeliScreen(onRetour: () -> Unit, onVoirTransactions: (Int) -> Unit) {
    val application = LocalContext.current.applicationContext as CeliTrackerApplication
    val viewModel: DetailCeliViewModel = viewModel(factory = application.viewModelFactory)
    LaunchedEffect(Unit) { viewModel.charger() }
    val etat by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.detail_titre_celi)) },
                navigationIcon = {
                    IconButton(onClick = onRetour) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_retour))
                    }
                },
            )
        },
    ) { innerPadding ->
        DetailCeliContenu(etat = etat, modifier = Modifier.padding(innerPadding), onVoirTransactions = onVoirTransactions)
    }
}

/**
 * Du plus general au plus detaille: l'annee en cours, l'evolution des droits,
 * puis chaque annee, la plus recente d'abord.
 */
@Composable
fun DetailCeliContenu(etat: DetailCeliUiState, modifier: Modifier = Modifier, onVoirTransactions: (Int) -> Unit = {}) {
    val anneeEnCours = etat.lignes.lastOrNull()?.annee
    val liste = rememberLazyListState()
    val portee = rememberCoroutineScope()
    val anneesAffichees = etat.lignes.reversed()

    // Le graphique et le titre precedent les cartes: l'index d'une annee les compte.
    fun allerA(annee: Int) {
        val position = anneesAffichees.indexOfFirst { it.annee == annee }
        if (position >= 0) portee.launch { liste.animateScrollToItem(ELEMENTS_AVANT_ANNEES + position) }
    }

    LazyColumn(
        modifier = modifier,
        state = liste,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (etat.lignes.isNotEmpty()) {
            item(key = "evolution") {
                TitreSection(stringResource(R.string.detail_droits_fin_annee))
                GraphiqueAnnees(
                    valeurs = etat.lignes.map { it.annee to it.droitsFin },
                    couleur = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                    onClicAnnee = ::allerA,
                )
            }
            item(key = "titre-annees") { TitreSection(stringResource(R.string.detail_annee_par_annee)) }
        }
        items(anneesAffichees, key = { it.annee }) { ligne ->
            CarteAnneeCeli(
                ligne,
                enCours = ligne.annee == anneeEnCours,
                onVoirTransactions = if (ligne.annee in etat.anneesAvecTransactions) {
                    { onVoirTransactions(ligne.annee) }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun CarteAnneeCeli(ligne: DroitsAnnee, enCours: Boolean, onVoirTransactions: (() -> Unit)?) {
    CarteAnnee(
        annee = ligne.annee,
        montant = ligne.droitsFin.formatMontant(),
        libelleMontant = if (enCours) stringResource(R.string.detail_droits_restants) else stringResource(R.string.detail_droits_fin_annee),
        enCours = enCours,
        onVoirTransactions = onVoirTransactions,
        tuiles = listOf(
            ligne.plafond.formatMontant() to stringResource(R.string.detail_plafond_annee),
            ligne.droitsDebut.formatMontant() to stringResource(R.string.detail_droits_premier_janvier),
            ligne.depots.formatMontant() to stringResource(R.string.detail_depots),
            ligne.retraits.formatMontant() to stringResource(R.string.detail_retraits),
        ),
        // Un plafond absent est compte a zero: les droits sont sous-estimes, pas inventes.
        note = if (ligne.plafondManquant) stringResource(R.string.detail_plafond_non_confirme, ligne.annee) else null,
    )
}

@Composable
private fun TitreSection(texte: String) {
    Text(
        texte,
        modifier = Modifier.padding(top = 12.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Preview(showBackground = true)
@Composable
private fun DetailCeliContenuApercu() {
    fun ligne(annee: Int, debut: String, depots: String, fin: String) = DroitsAnnee(
        annee = annee,
        plafond = BigDecimal("7000.00"),
        droitsDebut = BigDecimal(debut),
        depots = BigDecimal(depots),
        retraits = BigDecimal.ZERO,
        droitsFin = BigDecimal(fin),
        plafondManquant = false,
    )
    DetailCeliContenu(
        etat = DetailCeliUiState(
            lignes = listOf(
                ligne(2024, "7000.00", "2000.00", "5000.00"),
                ligne(2025, "12000.00", "0.00", "12000.00"),
                ligne(2026, "19000.00", "3000.00", "16000.00"),
            ),
        ),
    )
}
