package dev.celitracker.app.ui.reglages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.celitracker.app.CeliTrackerApplication
import dev.celitracker.app.ui.format.formatMontant
import dev.celitracker.engine.Compte
import dev.celitracker.engine.PlafondAnnuel
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReglagesScreen(onRetour: () -> Unit) {
    val application = LocalContext.current.applicationContext as CeliTrackerApplication
    val viewModel: ReglagesViewModel = viewModel(factory = application.viewModelFactory)
    val etat by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Réglages") },
                navigationIcon = {
                    IconButton(onClick = onRetour) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
    ) { innerPadding ->
        ReglagesContenu(
            etat = etat,
            modifier = Modifier.padding(innerPadding),
            onAnneeAdmissibiliteChange = viewModel::modifierAnneeAdmissibiliteCeli,
            onAnneeNaissanceChange = viewModel::modifierAnneeNaissance,
            onDateOuvertureChange = viewModel::modifierDateOuvertureCeliapp,
            onEnregistrerProfil = viewModel::enregistrerProfil,
            onNouveauPlafondAnneeChange = viewModel::modifierNouveauPlafondAnnee,
            onNouveauPlafondMontantChange = viewModel::modifierNouveauPlafondMontant,
            onAjouterPlafond = viewModel::ajouterPlafond,
        )
    }
}

@Composable
fun ReglagesContenu(
    etat: ReglagesUiState,
    onAnneeAdmissibiliteChange: (String) -> Unit,
    onAnneeNaissanceChange: (String) -> Unit,
    onDateOuvertureChange: (String) -> Unit,
    onEnregistrerProfil: () -> Unit,
    onNouveauPlafondAnneeChange: (String) -> Unit,
    onNouveauPlafondMontantChange: (String) -> Unit,
    onAjouterPlafond: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Profil", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = etat.anneeAdmissibiliteCeli,
            onValueChange = onAnneeAdmissibiliteChange,
            label = { Text("Année d'admissibilité CELI") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = etat.anneeAdmissibiliteCeli.isNotBlank() && etat.anneeAdmissibiliteValide == null,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = etat.anneeNaissance,
            onValueChange = onAnneeNaissanceChange,
            label = { Text("Année de naissance") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = etat.anneeNaissance.isNotBlank() && etat.anneeNaissanceValide == null,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = etat.dateOuvertureCeliapp,
            onValueChange = onDateOuvertureChange,
            label = { Text("Date d'ouverture du CELIAPP (AAAA-MM-JJ, optionnel)") },
            isError = etat.dateOuvertureInvalide,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = onEnregistrerProfil, enabled = etat.profilValide) {
            Text("Enregistrer le profil")
        }

        HorizontalDivider()

        Text("Plafonds CELI", style = MaterialTheme.typography.titleMedium)
        etat.plafonds.forEach { plafond -> LignePlafond(plafond) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = etat.nouveauPlafondAnnee,
                onValueChange = onNouveauPlafondAnneeChange,
                label = { Text("Année") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = etat.nouveauPlafondMontant,
                onValueChange = onNouveauPlafondMontantChange,
                label = { Text("Montant") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }
        Button(onClick = onAjouterPlafond, enabled = etat.nouveauPlafondValide) {
            Text("Ajouter le plafond")
        }

        etat.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun LignePlafond(plafond: PlafondAnnuel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(plafond.annee.toString())
        Text(plafond.montant.formatMontant())
    }
}

@Preview(showBackground = true)
@Composable
private fun ReglagesContenuApercu() {
    ReglagesContenu(
        etat = ReglagesUiState(
            anneeAdmissibiliteCeli = "2010",
            anneeNaissance = "1995",
            dateOuvertureCeliapp = "2023-04-01",
            plafonds = listOf(
                PlafondAnnuel(Compte.CELI, 2025, BigDecimal("7000.00"), confirme = true),
                PlafondAnnuel(Compte.CELI, 2026, BigDecimal("7000.00"), confirme = true),
            ),
        ),
        onAnneeAdmissibiliteChange = {},
        onAnneeNaissanceChange = {},
        onDateOuvertureChange = {},
        onEnregistrerProfil = {},
        onNouveauPlafondAnneeChange = {},
        onNouveauPlafondMontantChange = {},
        onAjouterPlafond = {},
    )
}
