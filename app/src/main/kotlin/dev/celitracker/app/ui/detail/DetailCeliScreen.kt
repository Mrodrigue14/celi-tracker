package dev.celitracker.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.celitracker.app.CeliTrackerApplication
import dev.celitracker.app.ui.format.formatMontant
import dev.celitracker.engine.DroitsAnnee
import java.math.BigDecimal

private val LARGEUR_COLONNE = 110.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailCeliScreen(onRetour: () -> Unit) {
    val application = LocalContext.current.applicationContext as CeliTrackerApplication
    val viewModel: DetailCeliViewModel = viewModel(factory = application.viewModelFactory)
    LaunchedEffect(Unit) { viewModel.charger() }
    val etat by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CELI — détail") },
                navigationIcon = {
                    IconButton(onClick = onRetour) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
    ) { innerPadding ->
        DetailCeliContenu(etat = etat, modifier = Modifier.padding(innerPadding))
    }
}

@Composable
fun DetailCeliContenu(etat: DetailCeliUiState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            LigneEnTete("Année", "Plafond", "Droits début", "Dépôts", "Retraits", "Droits fin")
            etat.lignes.forEach { ligne -> LigneCeli(ligne) }
        }
        if (etat.lignes.any { it.plafondManquant }) {
            Text(
                "* Plafond de l'année non confirmé — droits sous-estimés, pas inventés.",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun LigneEnTete(vararg colonnes: String) {
    Row {
        colonnes.forEach { texte ->
            Text(texte, modifier = Modifier.width(LARGEUR_COLONNE).padding(8.dp), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LigneCeli(ligne: DroitsAnnee) {
    val fondAlerte = if (ligne.plafondManquant) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface
    Row(modifier = Modifier.background(fondAlerte)) {
        Cellule(ligne.annee.toString())
        Cellule(
            texte = if (ligne.plafondManquant) "${ligne.plafond.formatMontant()} *" else ligne.plafond.formatMontant(),
            // Complete le texte, ne le remplace pas: un lecteur d'ecran doit
            // entendre le montant ET l'alerte, pas l'un ou l'autre.
            description = if (ligne.plafondManquant) {
                "${ligne.plafond.formatMontant()}, plafond non confirmé pour l'année ${ligne.annee}"
            } else null,
        )
        Cellule(ligne.droitsDebut.formatMontant())
        Cellule(ligne.depots.formatMontant())
        Cellule(ligne.retraits.formatMontant())
        Cellule(ligne.droitsFin.formatMontant())
    }
}

@Composable
private fun Cellule(texte: String, description: String? = null) {
    Text(
        texte,
        modifier = Modifier
            .width(LARGEUR_COLONNE)
            .padding(8.dp)
            .let { if (description != null) it.semantics { contentDescription = description } else it },
    )
}

@Preview(showBackground = true)
@Composable
private fun DetailCeliContenuApercu() {
    DetailCeliContenu(
        etat = DetailCeliUiState(
            lignes = listOf(
                DroitsAnnee(
                    annee = 2025,
                    plafond = BigDecimal("7000.00"),
                    droitsDebut = BigDecimal("10000.00"),
                    depots = BigDecimal("2000.00"),
                    retraits = BigDecimal.ZERO,
                    droitsFin = BigDecimal("8000.00"),
                    plafondManquant = false,
                ),
                DroitsAnnee(
                    annee = 2026,
                    plafond = BigDecimal.ZERO,
                    droitsDebut = BigDecimal("8000.00"),
                    depots = BigDecimal.ZERO,
                    retraits = BigDecimal.ZERO,
                    droitsFin = BigDecimal("8000.00"),
                    plafondManquant = true,
                ),
            )
        ),
    )
}
