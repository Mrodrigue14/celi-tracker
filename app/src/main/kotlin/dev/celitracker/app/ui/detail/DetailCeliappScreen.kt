package dev.celitracker.app.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.celitracker.app.CeliTrackerApplication
import dev.celitracker.app.ui.composants.CarteAnnee
import dev.celitracker.app.ui.composants.GraphiqueAnnees
import dev.celitracker.app.ui.format.formatMontant
import dev.celitracker.engine.DroitsAnneeCeliapp
import kotlinx.coroutines.launch
import java.math.BigDecimal

/** Le graphique et le titre « Année par année » precedent la premiere carte. */
private const val ELEMENTS_AVANT_ANNEES = 2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailCeliappScreen(onRetour: () -> Unit, onOuvrirJournal: () -> Unit, onVoirTransactions: (Int) -> Unit) {
    val application = LocalContext.current.applicationContext as CeliTrackerApplication
    val viewModel: DetailCeliappViewModel = viewModel(factory = application.viewModelFactory)
    LaunchedEffect(Unit) { viewModel.charger() }
    val etat by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Détail du CELIAPP") },
                navigationIcon = {
                    IconButton(onClick = onRetour) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = onOuvrirJournal) {
                        Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = "Journal du CELIAPP")
                    }
                },
            )
        },
    ) { innerPadding ->
        DetailCeliappContenu(etat = etat, modifier = Modifier.padding(innerPadding), onVoirTransactions = onVoirTransactions)
    }
}

/** Meme lecture que le detail du CELI, avec les notions propres au CELIAPP. */
@Composable
fun DetailCeliappContenu(etat: DetailCeliappUiState, modifier: Modifier = Modifier, onVoirTransactions: (Int) -> Unit = {}) {
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
                TitreSection("Plafond à vie restant")
                GraphiqueAnnees(
                    valeurs = etat.lignes.map { it.annee to it.plafondVieRestant },
                    couleur = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                    onClicAnnee = ::allerA,
                )
            }
            item(key = "titre-annees") { TitreSection("Année par année") }
        }
        items(anneesAffichees, key = { it.annee }) { ligne ->
            CarteAnneeCeliapp(
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
private fun CarteAnneeCeliapp(ligne: DroitsAnneeCeliapp, enCours: Boolean, onVoirTransactions: (() -> Unit)?) {
    CarteAnnee(
        annee = ligne.annee,
        montant = (ligne.droitsAnnee - ligne.depots).formatMontant(),
        libelleMontant = if (enCours) "Droits restants" else "Droits non utilisés",
        enCours = enCours,
        onVoirTransactions = onVoirTransactions,
        tuiles = listOf(
            ligne.reportEntrant.formatMontant() to "Report reçu",
            ligne.droitsAnnee.formatMontant() to "Droits de l'année",
            ligne.depots.formatMontant() to "Dépôts",
            ligne.reportSortant.formatMontant() to "Report transmis",
            ligne.plafondVieRestant.formatMontant() to "Plafond à vie restant",
        ),
    )
}

@Composable
private fun TitreSection(texte: String) {
    Text(
        texte,
        modifier = Modifier.padding(top = 12.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.secondary,
    )
}

@Preview(showBackground = true)
@Composable
private fun DetailCeliappContenuApercu() {
    DetailCeliappContenu(
        etat = DetailCeliappUiState(
            lignes = listOf(
                DroitsAnneeCeliapp(
                    annee = 2026,
                    reportEntrant = BigDecimal.ZERO,
                    droitsAnnee = BigDecimal("8000.00"),
                    depots = BigDecimal("3000.00"),
                    retraits = BigDecimal.ZERO,
                    reportSortant = BigDecimal("5000.00"),
                    plafondVieRestant = BigDecimal("37000.00"),
                ),
            ),
        ),
    )
}
