package dev.celitracker.app.ui.detail

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.celitracker.app.CeliTrackerApplication
import dev.celitracker.app.ui.format.formatMontant
import dev.celitracker.engine.DroitsAnneeCeliapp
import java.math.BigDecimal

private val LARGEUR_COLONNE = 130.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailCeliappScreen(onRetour: () -> Unit) {
    val application = LocalContext.current.applicationContext as CeliTrackerApplication
    val viewModel: DetailCeliappViewModel = viewModel(factory = application.viewModelFactory)
    LaunchedEffect(Unit) { viewModel.charger() }
    val etat by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CELIAPP — détail") },
                navigationIcon = {
                    IconButton(onClick = onRetour) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
    ) { innerPadding ->
        DetailCeliappContenu(etat = etat, modifier = Modifier.padding(innerPadding))
    }
}

@Composable
fun DetailCeliappContenu(etat: DetailCeliappUiState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            LigneEnTeteCeliapp()
            etat.lignes.forEach { ligne -> LigneCeliapp(ligne) }
        }
    }
}

@Composable
private fun LigneEnTeteCeliapp() {
    Row {
        listOf("Année", "Report entrant", "Droits de l'année", "Dépôts", "Report sortant", "Plafond à vie restant")
            .forEach { texte ->
                Text(texte, modifier = Modifier.width(LARGEUR_COLONNE).padding(8.dp), fontWeight = FontWeight.Bold)
            }
    }
}

@Composable
private fun LigneCeliapp(ligne: DroitsAnneeCeliapp) {
    Row {
        Text(ligne.annee.toString(), modifier = Modifier.width(LARGEUR_COLONNE).padding(8.dp))
        Text(ligne.reportEntrant.formatMontant(), modifier = Modifier.width(LARGEUR_COLONNE).padding(8.dp))
        Text(ligne.droitsAnnee.formatMontant(), modifier = Modifier.width(LARGEUR_COLONNE).padding(8.dp))
        Text(ligne.depots.formatMontant(), modifier = Modifier.width(LARGEUR_COLONNE).padding(8.dp))
        Text(ligne.reportSortant.formatMontant(), modifier = Modifier.width(LARGEUR_COLONNE).padding(8.dp))
        Text(ligne.plafondVieRestant.formatMontant(), modifier = Modifier.width(LARGEUR_COLONNE).padding(8.dp))
    }
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
                )
            )
        ),
    )
}
