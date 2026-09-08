package dev.celitracker.app.ui.accueil

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.celitracker.app.CeliTrackerApplication
import dev.celitracker.app.ui.format.formatMontant
import dev.celitracker.engine.Compte
import dev.celitracker.engine.DroitsAnnee
import dev.celitracker.engine.DroitsAnneeCeliapp
import dev.celitracker.engine.ExcedentMensuel
import dev.celitracker.engine.Profil
import java.math.BigDecimal
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccueilScreen(
    onOuvrirDetail: (Compte) -> Unit,
    onOuvrirReglages: () -> Unit,
) {
    val application = LocalContext.current.applicationContext as CeliTrackerApplication
    val viewModel: AccueilViewModel = viewModel(factory = application.viewModelFactory)
    LaunchedEffect(Unit) { viewModel.charger() }
    val etat by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CELI Tracker") },
                actions = {
                    IconButton(onClick = onOuvrirReglages) {
                        Icon(Icons.Filled.Settings, contentDescription = "Réglages")
                    }
                },
            )
        },
    ) { innerPadding ->
        AccueilContenu(
            etat = etat,
            modifier = Modifier.padding(innerPadding),
            onOuvrirDetail = onOuvrirDetail,
            onOuvrirReglages = onOuvrirReglages,
        )
    }
}

/**
 * Pur: recoit l'etat et des lambdas, jamais le ViewModel. Previewable sans
 * dependance a Android.
 */
@Composable
fun AccueilContenu(
    etat: AccueilUiState,
    onOuvrirDetail: (Compte) -> Unit,
    onOuvrirReglages: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!etat.profilEnregistre) {
        EtatVide(onOuvrirReglages, modifier)
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CarteCeli(
            droits = etat.celiAnneeCourante,
            excedent = etat.excedentCeliCourant,
            onClick = { onOuvrirDetail(Compte.CELI) },
        )
        CarteCeliapp(
            droits = etat.celiappAnneeCourante,
            echeanceParticipation = etat.echeanceParticipationCeliapp,
            onClick = { onOuvrirDetail(Compte.CELIAPP) },
        )
    }
}

@Composable
private fun EtatVide(onOuvrirReglages: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Aucun profil enregistré", style = MaterialTheme.typography.titleLarge)
        Text(
            "Ouvrez les réglages pour saisir votre profil et commencer le suivi.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onOuvrirReglages) { Text("Ouvrir les réglages") }
    }
}

@Composable
private fun CarteCeli(droits: DroitsAnnee?, excedent: ExcedentMensuel?, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("CELI", style = MaterialTheme.typography.titleMedium)
            Text(
                droits?.droitsFin?.formatMontant() ?: "—",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text("Droits restants", style = MaterialTheme.typography.labelMedium)
            Text("Cotisé cette année : ${droits?.depots?.formatMontant() ?: "—"}")
            if (droits?.plafondManquant == true) {
                Text(
                    "⚠ Plafond de l'année non confirmé — droits sous-estimés",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { contentDescription = "Alerte : plafond de l'année non confirmé" },
                )
            }
            if (excedent != null) {
                Row {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = "Alerte de sur-cotisation",
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        "Sur-cotisation : pénalité estimée ${excedent.penalite.formatMontant()}",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun CarteCeliapp(
    droits: DroitsAnneeCeliapp?,
    echeanceParticipation: LocalDate?,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("CELIAPP", style = MaterialTheme.typography.titleMedium)
            Text(
                droits?.let { (it.droitsAnnee - it.depots).formatMontant() } ?: "—",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text("Droits restants", style = MaterialTheme.typography.labelMedium)
            Text("Cotisé cette année : ${droits?.depots?.formatMontant() ?: "—"}")
            Text("Plafond à vie restant : ${droits?.plafondVieRestant?.formatMontant() ?: "—"}")
            Text("Échéance de participation : ${echeanceParticipation?.toString() ?: "—"}")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AccueilContenuApercu() {
    AccueilContenu(
        etat = AccueilUiState(
            profil = Profil(2010, 1995, LocalDate.of(2023, 4, 1)),
            anneeCourante = 2026,
            moisCourant = 9,
            droitsCeli = listOf(
                DroitsAnnee(
                    annee = 2026,
                    plafond = BigDecimal("7000.00"),
                    droitsDebut = BigDecimal("12000.00"),
                    depots = BigDecimal("3000.00"),
                    retraits = BigDecimal.ZERO,
                    droitsFin = BigDecimal("9000.00"),
                    plafondManquant = false,
                )
            ),
            droitsCeliapp = listOf(
                DroitsAnneeCeliapp(
                    annee = 2026,
                    reportEntrant = BigDecimal("1000.00"),
                    droitsAnnee = BigDecimal("8000.00"),
                    depots = BigDecimal("4000.00"),
                    retraits = BigDecimal.ZERO,
                    reportSortant = BigDecimal("4000.00"),
                    plafondVieRestant = BigDecimal("28000.00"),
                )
            ),
        ),
        onOuvrirDetail = {},
        onOuvrirReglages = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun AccueilContenuApercuVide() {
    AccueilContenu(
        etat = AccueilUiState(profil = null, anneeCourante = 2026, moisCourant = 9),
        onOuvrirDetail = {},
        onOuvrirReglages = {},
    )
}
