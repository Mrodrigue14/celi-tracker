package dev.celitracker.app.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.North
import androidx.compose.material.icons.filled.South
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.celitracker.app.CeliTrackerApplication
import dev.celitracker.app.R
import dev.celitracker.app.ui.composants.BandeauAlerte
import dev.celitracker.app.ui.composants.ChampDate
import dev.celitracker.app.ui.format.formatDate
import dev.celitracker.app.ui.format.formatMontant
import dev.celitracker.app.ui.texte.libelle
import dev.celitracker.app.ui.texte.resoudre
import dev.celitracker.app.ui.theme.chiffres
import dev.celitracker.engine.Compte
import dev.celitracker.engine.Transaction
import dev.celitracker.engine.TypeTx
import java.math.BigDecimal
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen() {
    val application = LocalContext.current.applicationContext as CeliTrackerApplication
    val viewModel: JournalViewModel = viewModel(factory = application.viewModelFactory)
    val etat by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val liste = rememberLazyListState()

    // Le message est un imperatif joue une fois, pas un etat durable: il part
    // dans un snackbar et le ViewModel l'oublie ensuite.
    val contexte = LocalContext.current
    LaunchedEffect(etat.message) {
        val message = etat.message ?: return@LaunchedEffect
        snackbar.showSnackbar(message.resoudre(contexte))
        viewModel.messageAffiche()
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = { Text(stringResource(R.string.journal_titre)) })
                ChoixCompte(
                    compte = etat.compte,
                    onChanger = viewModel::changerCompte,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            // Journal vide: l'etat vide porte deja son propre bouton, un second
            // ferait doublon. Le bouton flottant revient des la premiere transaction.
            if (etat.transactions.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.action_ajouter)) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    onClick = viewModel::ouvrirNouvelle,
                )
            }
        },
    ) { innerPadding ->
        JournalContenu(
            etat = etat,
            onOuvrirTransaction = viewModel::ouvrirModification,
            onAjouter = viewModel::ouvrirNouvelle,
            modifier = Modifier.padding(innerPadding),
            liste = liste,
        )
    }

    // Arrivee depuis le detail: on amene l'annee demandee en haut de la liste,
    // une seule fois, des que ses transactions sont chargees.
    LaunchedEffect(etat.anneeCiblee, etat.transactions) {
        val annee = etat.anneeCiblee ?: return@LaunchedEffect
        if (etat.transactions.isEmpty()) return@LaunchedEffect
        val position = positionEnTete(etat.transactions, annee)
        if (position >= 0) liste.scrollToItem(position)
        viewModel.anneeCibleeAtteinte()
    }

    etat.formulaire?.let { formulaire ->
        FeuilleTransaction(
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

/** Les deux comptes a portee de pouce, au lieu d'un journal par ecran de detail. */
@Composable
private fun ChoixCompte(compte: Compte, onChanger: (Compte) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        Compte.entries.forEachIndexed { index, choix ->
            SegmentedButton(
                selected = compte == choix,
                onClick = { if (choix != compte) onChanger(choix) },
                shape = SegmentedButtonDefaults.itemShape(index, Compte.entries.size),
                // Chaque compte garde sa couleur, ici comme sur l'accueil.
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = if (choix == Compte.CELI) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                ),
            ) {
                Text(choix.libelle())
            }
        }
    }
}

@Composable
fun JournalContenu(
    etat: JournalUiState,
    onOuvrirTransaction: (Transaction) -> Unit,
    onAjouter: () -> Unit,
    modifier: Modifier = Modifier,
    liste: LazyListState = rememberLazyListState(),
) {
    if (etat.transactions.isEmpty()) {
        JournalVide(onAjouter = onAjouter, modifier = modifier)
        return
    }
    LazyColumn(modifier = modifier.fillMaxSize(), state = liste) {
        etat.transactions.groupBy { it.date.year }.forEach { (annee, transactions) ->
            item(key = "annee-$annee") { EnTeteAnnee(annee) }
            items(transactions, key = { it.id }) { transaction ->
                LigneTransaction(transaction, onClick = { onOuvrirTransaction(transaction) })
            }
        }
    }
}

/**
 * Position de l'en-tete de [annee] dans la liste: chaque annee occupe une ligne
 * d'en-tete puis une ligne par transaction, dans l'ordre d'affichage.
 */
internal fun positionEnTete(transactions: List<Transaction>, annee: Int): Int {
    var position = 0
    transactions.groupBy { it.date.year }.forEach { (groupe, lignes) ->
        if (groupe == annee) return position
        position += 1 + lignes.size
    }
    return -1
}

@Composable
private fun JournalVide(onAjouter: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Pastille(
            icone = Icons.AutoMirrored.Filled.List,
            fond = MaterialTheme.colorScheme.surfaceVariant,
            teinte = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.journal_vide_titre),
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.journal_vide_texte),
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onAjouter, modifier = Modifier.padding(top = 24.dp)) {
            Text(stringResource(R.string.journal_vide_action))
        }
    }
}

@Composable
private fun EnTeteAnnee(annee: Int) {
    Text(
        annee.toString(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun LigneTransaction(transaction: Transaction, onClick: () -> Unit) {
    val depot = transaction.type == TypeTx.DEPOT
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Pastille(
                icone = if (depot) Icons.Filled.South else Icons.Filled.North,
                fond = if (depot) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.tertiaryContainer
                },
                teinte = if (depot) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onTertiaryContainer
                },
                description = if (depot) stringResource(R.string.type_depot) else stringResource(R.string.type_retrait),
            )
            Column(modifier = Modifier.weight(1f)) {
                // Chiffres a chasse fixe: les montants s'alignent d'une ligne a
                // l'autre, ce qui rend la colonne lisible d'un coup d'oeil.
                Text(
                    transaction.montant.formatMontant(),
                    style = MaterialTheme.typography.titleMedium.chiffres(),
                )
                Text(
                    if (depot) stringResource(R.string.type_depot) else stringResource(R.string.type_retrait),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                transaction.date.formatDate(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
    }
}

@Composable
private fun Pastille(icone: ImageVector, fond: Color, teinte: Color, description: String? = null) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(fond, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icone, contentDescription = description, tint = teinte)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeuilleTransaction(
    formulaire: FormulaireTransaction,
    onDate: (String) -> Unit,
    onType: (TypeTx) -> Unit,
    onMontant: (String) -> Unit,
    onEnregistrer: () -> Unit,
    onSupprimer: () -> Unit,
    onFermer: () -> Unit,
) {
    // Ouverte a pleine hauteur: a moitie deployee, le bouton d'enregistrement
    // tombait sous le bord de l'ecran.
    ModalBottomSheet(
        onDismissRequest = onFermer,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                if (formulaire.estNouvelle) stringResource(R.string.journal_nouvelle) else stringResource(R.string.journal_modifier),
                style = MaterialTheme.typography.titleLarge,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                TypeTx.entries.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = formulaire.type == type,
                        onClick = { onType(type) },
                        shape = SegmentedButtonDefaults.itemShape(index, TypeTx.entries.size),
                    ) {
                        Text(if (type == TypeTx.DEPOT) stringResource(R.string.type_depot) else stringResource(R.string.type_retrait))
                    }
                }
            }
            OutlinedTextField(
                value = formulaire.montant,
                onValueChange = onMontant,
                label = { Text(stringResource(R.string.journal_montant)) },
                suffix = { Text("$") },
                isError = formulaire.montant.isNotEmpty() && formulaire.montantValide == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall.chiffres(),
                modifier = Modifier.fillMaxWidth(),
            )
            ChampDate(date = formulaire.date, onDate = onDate, etiquette = stringResource(R.string.journal_date))
            formulaire.avertissement?.let { BandeauAlerte(it.resoudre(), Icons.Filled.Warning) }
            Button(
                onClick = onEnregistrer,
                enabled = formulaire.valide,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (formulaire.avertissement == null) stringResource(R.string.action_enregistrer) else stringResource(R.string.action_enregistrer_quand_meme))
            }
            formulaire.erreur?.let {
                Text(it.resoudre(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            if (!formulaire.estNouvelle) {
                TextButton(onClick = onSupprimer, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_supprimer), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun JournalContenuApercu() {
    JournalContenu(
        etat = JournalUiState(
            transactions = listOf(
                Transaction(Compte.CELI, LocalDate.of(2026, 3, 1), TypeTx.RETRAIT, BigDecimal("500.00"), id = 2),
                Transaction(Compte.CELI, LocalDate.of(2026, 1, 15), TypeTx.DEPOT, BigDecimal("2000.00"), id = 1),
                Transaction(Compte.CELI, LocalDate.of(2025, 11, 3), TypeTx.DEPOT, BigDecimal("1500.00"), id = 3),
            ),
        ),
        onOuvrirTransaction = {},
        onAjouter = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun JournalVideApercu() {
    JournalVide(onAjouter = {})
}
