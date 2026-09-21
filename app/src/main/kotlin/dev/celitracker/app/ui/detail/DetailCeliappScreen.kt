package dev.celitracker.app.ui.detail

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.celitracker.app.CeliTrackerApplication
import dev.celitracker.app.R
import dev.celitracker.app.ui.composants.CarteAnnee
import dev.celitracker.app.ui.composants.ContenuLargeurLimitee
import dev.celitracker.app.ui.composants.ListeDetailAnnees
import dev.celitracker.app.ui.format.formatMontant
import dev.celitracker.engine.DroitsAnneeCeliapp
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailCeliappScreen(onRetour: () -> Unit, onVoirTransactions: (Int) -> Unit) {
    val application = LocalContext.current.applicationContext as CeliTrackerApplication
    val viewModel: DetailCeliappViewModel = viewModel(factory = application.viewModelFactory)
    LaunchedEffect(Unit) { viewModel.charger() }
    val etat by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.detail_titre_celiapp)) },
                navigationIcon = {
                    IconButton(onClick = onRetour) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_retour))
                    }
                },
            )
        },
    ) { innerPadding ->
        ContenuLargeurLimitee(modifier = Modifier.padding(innerPadding)) {
            DetailCeliappContenu(etat = etat, onVoirTransactions = onVoirTransactions, modifier = Modifier.fillMaxSize())
        }
    }
}

/** Du plus general au plus detaille: l'evolution, puis chaque annee, la plus recente d'abord. */
@Composable
fun DetailCeliappContenu(etat: DetailCeliappUiState, onVoirTransactions: (Int) -> Unit, modifier: Modifier = Modifier) {
    ListeDetailAnnees(
        lignes = etat.lignes,
        annee = { it.annee },
        titreGraphique = stringResource(R.string.detail_plafond_vie_restant),
        valeurGraphique = { it.plafondVieRestant },
        couleur = MaterialTheme.colorScheme.secondary,
        modifier = modifier,
    ) { ligne, enCours ->
        CarteAnneeCeliapp(
            ligne,
            enCours = enCours,
            onVoirTransactions = ligne.annee.takeIf { it in etat.anneesAvecTransactions }?.let { annee -> { onVoirTransactions(annee) } },
        )
    }
}

@Composable
private fun CarteAnneeCeliapp(ligne: DroitsAnneeCeliapp, enCours: Boolean, onVoirTransactions: (() -> Unit)?) {
    CarteAnnee(
        annee = ligne.annee,
        montant = (ligne.droitsAnnee - ligne.depots).formatMontant(),
        libelleMontant = if (enCours) stringResource(R.string.detail_droits_restants) else stringResource(R.string.detail_droits_non_utilises),
        enCours = enCours,
        onVoirTransactions = onVoirTransactions,
        tuiles = listOf(
            ligne.reportEntrant.formatMontant() to stringResource(R.string.detail_report_recu),
            ligne.droitsAnnee.formatMontant() to stringResource(R.string.detail_droits_annee),
            ligne.depots.formatMontant() to stringResource(R.string.detail_depots),
            ligne.reportSortant.formatMontant() to stringResource(R.string.detail_report_transmis),
            ligne.plafondVieRestant.formatMontant() to stringResource(R.string.detail_plafond_vie_restant),
        ),
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
        onVoirTransactions = {},
    )
}
