package dev.celitracker.app.ui.journal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import dev.celitracker.engine.Transaction
import dev.celitracker.engine.TypeTx
import java.math.BigDecimal
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(onRetour: () -> Unit) {
    val application = LocalContext.current.applicationContext as CeliTrackerApplication
    val viewModel: JournalViewModel = viewModel(factory = application.viewModelFactory)
    val etat by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Journal ${viewModel.compte.name}") },
                navigationIcon = {
                    IconButton(onClick = onRetour) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::ouvrirNouvelle) {
                Icon(Icons.Filled.Add, contentDescription = "Ajouter une transaction")
            }
        },
    ) { innerPadding ->
        JournalContenu(
            etat = etat,
            onOuvrirTransaction = viewModel::ouvrirModification,
            modifier = Modifier.padding(innerPadding),
        )
    }

    etat.formulaire?.let { formulaire ->
        DialogueTransaction(
            formulaire = formulaire,
            onDate = viewModel::modifierDate,
            onType = viewModel::modifierType,
            onMontant = viewModel::modifierMontant,
            onEnregistrer = viewModel::enregistrer,
            onSupprimer = viewModel::supprimer,
            onFermer = viewModel::fermerFormulaire,
        )
    }
}

@Composable
fun JournalContenu(
    etat: JournalUiState,
    onOuvrirTransaction: (Transaction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        etat.message?.let { Text(it, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall) }
        if (etat.transactions.isEmpty()) {
            Text("Aucune transaction.", modifier = Modifier.padding(16.dp))
        }
        LazyColumn {
            items(etat.transactions, key = { it.id }) { transaction ->
                LigneTransaction(transaction, onClick = { onOuvrirTransaction(transaction) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun LigneTransaction(transaction: Transaction, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(transaction.montant.formatMontant()) },
        supportingContent = { Text(transaction.type.libelle()) },
        trailingContent = { Text(transaction.date.toString()) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun DialogueTransaction(
    formulaire: FormulaireTransaction,
    onDate: (String) -> Unit,
    onType: (TypeTx) -> Unit,
    onMontant: (String) -> Unit,
    onEnregistrer: () -> Unit,
    onSupprimer: () -> Unit,
    onFermer: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(if (formulaire.estNouvelle) "Nouvelle transaction" else "Modifier la transaction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = formulaire.date,
                    onValueChange = onDate,
                    label = { Text("Date (AAAA-MM-JJ)") },
                    isError = formulaire.dateValide == null,
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TypeTx.entries.forEach { type ->
                        FilterChip(
                            selected = formulaire.type == type,
                            onClick = { onType(type) },
                            label = { Text(type.libelle()) },
                        )
                    }
                }
                OutlinedTextField(
                    value = formulaire.montant,
                    onValueChange = onMontant,
                    label = { Text("Montant") },
                    isError = formulaire.montant.isNotEmpty() && formulaire.montantValide == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                formulaire.erreur?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = onEnregistrer, enabled = formulaire.valide) { Text("Enregistrer") }
        },
        dismissButton = {
            Row {
                if (!formulaire.estNouvelle) {
                    TextButton(onClick = onSupprimer) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onFermer) { Text("Annuler") }
            }
        },
    )
}

private fun TypeTx.libelle(): String = when (this) {
    TypeTx.DEPOT -> "Dépôt"
    TypeTx.RETRAIT -> "Retrait"
}

@Preview(showBackground = true)
@Composable
private fun JournalContenuApercu() {
    JournalContenu(
        etat = JournalUiState(
            transactions = listOf(
                Transaction(Compte.CELI, LocalDate.of(2026, 3, 1), TypeTx.RETRAIT, BigDecimal("500.00"), id = 2),
                Transaction(Compte.CELI, LocalDate.of(2026, 1, 15), TypeTx.DEPOT, BigDecimal("2000.00"), id = 1),
            ),
        ),
        onOuvrirTransaction = {},
    )
}
