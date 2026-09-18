package dev.celitracker.app.ui.reglages

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.ZoneId

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

    val contexte = LocalContext.current
    var importAConfirmer by remember { mutableStateOf<Uri?>(null) }

    val lanceurExport = rememberLauncherForActivityResult(CreateDocument(TYPE_JSON)) { uri ->
        if (uri != null) viewModel.exporter { contenu -> ecrireFichier(contexte, uri, contenu) }
    }
    val lanceurImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        importAConfirmer = uri
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
            onUrlPageArcChange = viewModel::modifierUrlPageArc,
            onEnregistrerUrlPageArc = viewModel::enregistrerUrlPageArc,
            onVerifierArc = { viewModel.verifierArc(demandeExplicite = true) },
            onConfirmerProposition = viewModel::confirmerProposition,
            onRejeterProposition = viewModel::rejeterProposition,
            onExporter = { lanceurExport.launch(NOM_FICHIER_EXPORT) },
            onImporter = { lanceurImport.launch(arrayOf(TYPE_JSON)) },
        )
    }

    importAConfirmer?.let { uri ->
        ConfirmationImport(
            onConfirmer = {
                importAConfirmer = null
                viewModel.importer { lireFichier(contexte, uri) }
            },
            onAnnuler = { importAConfirmer = null },
        )
    }
}

private const val TYPE_JSON = "application/json"

private const val NOM_FICHIER_EXPORT = "celi-tracker.json"

private suspend fun ecrireFichier(contexte: Context, uri: Uri, contenu: String) = withContext(Dispatchers.IO) {
    val flux = contexte.contentResolver.openOutputStream(uri) ?: error("fichier inaccessible")
    flux.use { it.write(contenu.toByteArray()) }
}

private suspend fun lireFichier(contexte: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    val flux = contexte.contentResolver.openInputStream(uri) ?: error("fichier illisible")
    flux.use { it.reader().readText() }
}

/** L'import remplace tout: il se confirme, il ne se declenche pas d'un doigt qui glisse. */
@Composable
private fun ConfirmationImport(onConfirmer: () -> Unit, onAnnuler: () -> Unit) {
    AlertDialog(
        onDismissRequest = onAnnuler,
        title = { Text("Remplacer toutes les données ?") },
        text = {
            Text(
                "L'import écrase le profil, les plafonds et le journal des transactions " +
                    "par le contenu du fichier. Ce n'est pas une fusion.",
            )
        },
        confirmButton = { TextButton(onClick = onConfirmer) { Text("Remplacer") } },
        dismissButton = { TextButton(onClick = onAnnuler) { Text("Annuler") } },
    )
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
    onUrlPageArcChange: (String) -> Unit,
    onEnregistrerUrlPageArc: () -> Unit,
    onVerifierArc: () -> Unit,
    onConfirmerProposition: (PlafondAnnuel) -> Unit,
    onRejeterProposition: (PlafondAnnuel) -> Unit,
    onExporter: () -> Unit,
    onImporter: () -> Unit,
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

        PropositionsArc(
            propositions = etat.propositions,
            onConfirmer = onConfirmerProposition,
            onRejeter = onRejeterProposition,
        )
        etat.erreurArc?.let { EchecLectureArc(it) }

        TitreSection("Source des plafonds")
        OutlinedTextField(
            value = etat.urlPageArc,
            onValueChange = onUrlPageArcChange,
            label = { Text("Page de l'ARC") },
            supportingText = {
                Text(
                    etat.derniereVerificationArc
                        ?.let { "Dernière lecture : ${it.atZone(ZoneId.systemDefault()).toLocalDate()}" }
                        ?: "Jamais lue. L'application vérifie une fois par mois, et seulement s'il manque un plafond.",
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onEnregistrerUrlPageArc, modifier = Modifier.weight(1f)) {
                Text("Enregistrer l'adresse")
            }
            Button(
                onClick = onVerifierArc,
                enabled = !etat.verificationEnCours,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (etat.verificationEnCours) "Lecture..." else "Vérifier")
            }
        }

        TitreSection("Plafonds CELI")
        if (etat.plafondsConfirmes.isEmpty()) {
            Text(
                "Aucun plafond enregistré. Sans plafond confirmé, les droits de l'année restent à zéro plutôt que d'être devinés.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        etat.plafondsPertinents.forEach { plafond -> LignePlafond(plafond) }
        PlafondsAnterieurs(etat.plafondsAnterieurs)

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

        HorizontalDivider(modifier = Modifier.padding(top = 32.dp))

        SauvegardeEtRecuperation(onExporter = onExporter, onImporter = onImporter)
    }
}

/**
 * La sauvegarde Android copie deja la base vers le compte Google et la reprend
 * lors d'un transfert d'appareil. Elle a deux angles morts: elle ne se declenche
 * pas toujours quand l'application est installee par APK, et son contenu n'est
 * pas inspectable. Le fichier JSON, lui, se verifie AVANT d'en avoir besoin.
 */
@Composable
private fun SauvegardeEtRecuperation(onExporter: () -> Unit, onImporter: () -> Unit) {
    TitreSection("Sauvegarde")
    Text(
        "Tes données sont copiées automatiquement vers ton compte Google et reprises " +
            "lors d'un transfert vers un nouveau téléphone. Le fichier JSON reste la copie " +
            "que tu peux vérifier et ranger où tu veux.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(
        modifier = Modifier.padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(onClick = onExporter, modifier = Modifier.weight(1f)) { Text("Exporter") }
        OutlinedButton(onClick = onImporter, modifier = Modifier.weight(1f)) { Text("Importer") }
    }
}

/**
 * Les plafonds lus sur le site de l'ARC restent inertes tant qu'ils ne sont pas
 * confirmes: c'est une proposition, pas une modification des droits.
 */
@Composable
private fun PropositionsArc(
    propositions: List<PlafondAnnuel>,
    onConfirmer: (PlafondAnnuel) -> Unit,
    onRejeter: (PlafondAnnuel) -> Unit,
) {
    if (propositions.isEmpty()) return

    TitreSection("Proposé par l'ARC")
    propositions.forEach { plafond ->
        Column(modifier = Modifier.padding(bottom = 12.dp)) {
            Text(
                "${plafond.annee} : ${plafond.montant.formatMontant()}",
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            )
            Text(
                "Lu sur le site de l'ARC. Il n'entre dans le calcul qu'une fois confirmé.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(onClick = { onConfirmer(plafond) }, modifier = Modifier.weight(1f)) {
                    Text("Confirmer")
                }
                OutlinedButton(onClick = { onRejeter(plafond) }, modifier = Modifier.weight(1f)) {
                    Text("Rejeter")
                }
            }
        }
    }
}

/** Echec visible, pas de mode degrade silencieux: la saisie manuelle reste juste dessous. */
@Composable
private fun EchecLectureArc(raison: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Text(
            "Lecture automatique impossible",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
        Text(
            raison,
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
        Text(
            "Saisis le plafond à la main plus bas.",
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

/**
 * Replies par defaut: ils ne comptent pas dans les droits et repoussaient le
 * reste des reglages loin sous le pouce. L'etat ouvert/ferme est purement
 * visuel, donc il vit dans l'ecran et non dans le ViewModel.
 */
@Composable
private fun PlafondsAnterieurs(plafonds: List<PlafondAnnuel>) {
    if (plafonds.isEmpty()) return
    var ouverts by rememberSaveable { mutableStateOf(false) }

    TextButton(onClick = { ouverts = !ouverts }) {
        Text(
            if (ouverts) {
                "Masquer les années antérieures"
            } else {
                "Afficher ${plafonds.size} années antérieures (${plafonds.first().annee} à ${plafonds.last().annee})"
            },
        )
    }
    if (ouverts) {
        plafonds.forEach { plafond -> LignePlafond(plafond, attenue = true) }
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
private fun LignePlafond(plafond: PlafondAnnuel, attenue: Boolean = false) {
    val couleur = if (attenue) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(plafond.annee.toString(), style = MaterialTheme.typography.bodyLarge, color = couleur)
        Text(
            plafond.montant.formatMontant(),
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            color = couleur,
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
                PlafondAnnuel(Compte.CELI, 2027, BigDecimal("7500.00"), confirme = false),
            ),
            urlPageArc = "https://www.canada.ca/...",
        ),
        onAnneeNaissanceChange = {},
        onDateOuvertureChange = {},
        onEnregistrerProfil = {},
        onNouveauPlafondAnneeChange = {},
        onNouveauPlafondMontantChange = {},
        onAjouterPlafond = {},
        onUrlPageArcChange = {},
        onEnregistrerUrlPageArc = {},
        onVerifierArc = {},
        onConfirmerProposition = {},
        onRejeterProposition = {},
        onExporter = {},
        onImporter = {},
    )
}
