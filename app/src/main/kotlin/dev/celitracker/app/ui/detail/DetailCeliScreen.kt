package dev.celitracker.app.ui.detail

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.celitracker.app.CeliTrackerApplication
import dev.celitracker.app.R
import dev.celitracker.app.ui.composants.CarteAnnee
import dev.celitracker.app.ui.composants.ListeDetailAnnees
import dev.celitracker.app.ui.format.formatMontant
import dev.celitracker.engine.DroitsAnnee
import java.math.BigDecimal

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
        DetailCeliContenu(etat = etat, onVoirTransactions = onVoirTransactions, modifier = Modifier.padding(innerPadding))
    }
}

/** Du plus general au plus detaille: l'evolution, puis chaque annee, la plus recente d'abord. */
@Composable
fun DetailCeliContenu(etat: DetailCeliUiState, onVoirTransactions: (Int) -> Unit, modifier: Modifier = Modifier) {
    ListeDetailAnnees(
        lignes = etat.lignes,
        annee = { it.annee },
        titreGraphique = stringResource(R.string.detail_droits_fin_annee),
        valeurGraphique = { it.droitsFin },
        couleur = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    ) { ligne, enCours ->
        CarteAnneeCeli(
            ligne,
            enCours = enCours,
            onVoirTransactions = ligne.annee.takeIf { it in etat.anneesAvecTransactions }?.let { annee -> { onVoirTransactions(annee) } },
        )
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
        onVoirTransactions = {},
    )
}
