package dev.celitracker.app.ui.reglages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.celitracker.app.CeliTrackerApplication
import dev.celitracker.app.ui.composants.ChampDate
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
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(etat.message) {
        val message = etat.message ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        viewModel.messageAffiche()
    }

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
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innerPadding ->
        ReglagesContenu(
            etat = etat,
            modifier = Modifier.padding(innerPadding),
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
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
    ) {
        TitreSection("Profil")
        OutlinedTextField(
            value = etat.anneeNaissance,
            onValueChange = onAnneeNaissanceChange,
            label = { Text("Année de naissance") },
            supportingText = { Text("Elle détermine l'année où tes droits CELI commencent.") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = etat.anneeNaissance.isNotBlank() && etat.anneeNaissanceValide == null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        AdmissibiliteCeli(annee = etat.anneeAdmissibiliteCeli, modifier = Modifier.padding(top = 8.dp))
        ChampDate(
            date = etat.dateOuvertureCeliapp,
            onDate = onDateOuvertureChange,
            etiquette = "Ouverture du CELIAPP (facultatif)",
            estErreur = etat.dateOuvertureInvalide,
            modifier = Modifier.padding(top = 16.dp),
        )
        Button(
            onClick = onEnregistrerProfil,
            enabled = etat.profilValide,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        ) {
            Text("Enregistrer le profil")
        }

        HorizontalDivider(modifier = Modifier.padding(top = 32.dp))

        TitreSection("Plafonds CELI")
        if (etat.plafonds.isEmpty()) {
            Text(
                "Aucun plafond enregistré. Sans plafond confirmé, les droits de l'année restent à zéro plutôt que d'être devinés.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        etat.plafonds.forEach { plafond -> LignePlafond(plafond) }

        Row(
            modifier = Modifier.padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = etat.nouveauPlafondAnnee,
                onValueChange = onNouveauPlafondAnneeChange,
                label = { Text("Année") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = etat.nouveauPlafondMontant,
                onValueChange = onNouveauPlafondMontantChange,
                label = { Text("Montant") },
                suffix = { Text("$") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedButton(
            onClick = onAjouterPlafond,
            enabled = etat.nouveauPlafondValide,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        ) {
            Text("Ajouter le plafond")
        }
    }
}

@Composable
private fun TitreSection(texte: String) {
    Text(
        texte,
        modifier = Modifier.padding(top = 24.dp, bottom = 12.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** Resultat d'un calcul, donc affiche et non saisi. */
@Composable
private fun AdmissibiliteCeli(annee: Int?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Column {
            Text(
                if (annee != null) "Droits CELI depuis $annee" else "Entre ton année de naissance",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "Calculé : l'année de tes 18 ans, au plus tôt 2009.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LignePlafond(plafond: PlafondAnnuel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(plafond.annee.toString(), style = MaterialTheme.typography.bodyLarge)
        Text(
            plafond.montant.formatMontant(),
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ReglagesContenuApercu() {
    ReglagesContenu(
        etat = ReglagesUiState(
            anneeNaissance = "1995",
            dateOuvertureCeliapp = "2023-04-01",
            plafonds = listOf(
                PlafondAnnuel(Compte.CELI, 2025, BigDecimal("7000.00"), confirme = true),
                PlafondAnnuel(Compte.CELI, 2026, BigDecimal("7000.00"), confirme = true),
            ),
        ),
        onAnneeNaissanceChange = {},
        onDateOuvertureChange = {},
        onEnregistrerProfil = {},
        onNouveauPlafondAnneeChange = {},
        onNouveauPlafondMontantChange = {},
        onAjouterPlafond = {},
    )
}
