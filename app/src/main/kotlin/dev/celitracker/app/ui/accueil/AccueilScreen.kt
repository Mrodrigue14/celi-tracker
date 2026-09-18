package dev.celitracker.app.ui.accueil

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.celitracker.app.CeliTrackerApplication
import dev.celitracker.app.ui.composants.AnneauDroits
import dev.celitracker.app.ui.composants.BandeauAlerte
import dev.celitracker.app.ui.composants.FORME_CARTE
import dev.celitracker.app.ui.composants.GrilleTuiles
import dev.celitracker.app.ui.composants.PastilleCompte
import dev.celitracker.app.ui.format.formatMontant
import dev.celitracker.app.ui.theme.chiffres
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
    if (!etat.chargementTermine) return
    if (!etat.profilEnregistre) {
        EtatVide(onOuvrirReglages, modifier)
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CarteCompte(
            nom = "CELI",
            icone = Icons.Filled.Lock,
            couleur = MaterialTheme.colorScheme.primary,
            conteneur = MaterialTheme.colorScheme.primaryContainer,
            surConteneur = MaterialTheme.colorScheme.onPrimaryContainer,
            droitsRestants = etat.celiAnneeCourante?.droitsFin,
            fraction = etat.fractionUtiliseeCeli,
            tuiles = listOf(
                (etat.celiAnneeCourante?.depots?.formatMontant() ?: "-") to "Cotisé en ${etat.anneeCourante}",
                (etat.celiAnneeCourante?.plafond?.formatMontant() ?: "-") to "Plafond ${etat.anneeCourante}",
            ),
            onClick = { onOuvrirDetail(Compte.CELI) },
        ) {
            if (etat.celiAnneeCourante?.plafondManquant == true) {
                BandeauAlerte("Plafond de l'année non confirmé : droits sous-estimés.", Icons.Filled.Info)
            }
            etat.excedentCeliCourant?.let {
                BandeauAlerte("Sur-cotisation : pénalité estimée ${it.penalite.formatMontant()}.", Icons.Filled.Warning)
            }
        }
        if (etat.profil?.dateOuvertureCeliapp == null) {
            SansCeliapp(onOuvrirReglages)
            return@Column
        }
        CarteCompte(
            nom = "CELIAPP",
            icone = Icons.Filled.Home,
            couleur = MaterialTheme.colorScheme.secondary,
            conteneur = MaterialTheme.colorScheme.secondaryContainer,
            surConteneur = MaterialTheme.colorScheme.onSecondaryContainer,
            droitsRestants = etat.droitsRestantsCeliapp,
            fraction = etat.fractionUtiliseeCeliapp,
            tuiles = listOf(
                (etat.celiappAnneeCourante?.depots?.formatMontant() ?: "-") to "Cotisé en ${etat.anneeCourante}",
                (etat.celiappAnneeCourante?.plafondVieRestant?.formatMontant() ?: "-") to "Plafond à vie restant",
                (etat.celiappAnneeCourante?.reportEntrant?.formatMontant() ?: "-") to "Report reçu",
                (etat.echeanceParticipationCeliapp?.toString() ?: "-") to "Échéance",
            ),
            onClick = { onOuvrirDetail(Compte.CELIAPP) },
        )
    }
}

@Composable
private fun EtatVide(onOuvrirReglages: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PastilleCompte(
            icone = Icons.Filled.Person,
            fond = MaterialTheme.colorScheme.primaryContainer,
            teinte = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text("Commence par ton profil", style = MaterialTheme.typography.titleLarge)
        Text(
            "Ton année de naissance suffit pour calculer tes droits CELI. Ajoute la date d'ouverture de ton CELIAPP si tu en as un.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onOuvrirReglages, modifier = Modifier.padding(top = 12.dp)) { Text("Saisir mon profil") }
    }
}

/** Pas de compte, pas de carte pleine de tirets: une invitation a l'ajouter. */
@Composable
private fun SansCeliapp(onOuvrirReglages: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FORME_CARTE)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onOuvrirReglages)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PastilleCompte(
            icone = Icons.Filled.Home,
            fond = MaterialTheme.colorScheme.secondaryContainer,
            teinte = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text("CELIAPP", style = MaterialTheme.typography.titleMedium)
            Text(
                "Aucun compte ouvert. Ajoute sa date d'ouverture dans les réglages pour suivre tes droits.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Ouvrir les réglages",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Une carte par compte: bande de sa couleur en haut, le montant qui compte en
 * grand, l'anneau d'utilisation a cote, les valeurs secondaires en tuiles.
 * [alertes] vient en dernier: elle n'apparait que s'il y a quelque chose a dire.
 */
@Composable
private fun CarteCompte(
    nom: String,
    icone: ImageVector,
    couleur: Color,
    conteneur: Color,
    surConteneur: Color,
    droitsRestants: BigDecimal?,
    fraction: Float?,
    tuiles: List<Pair<String, String>>,
    onClick: () -> Unit,
    alertes: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FORME_CARTE)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(couleur),
        )
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PastilleCompte(icone = icone, fond = conteneur, teinte = surConteneur)
                Text(nom, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Voir le détail",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        droitsRestants?.formatMontant() ?: "-",
                        style = MaterialTheme.typography.headlineMedium.chiffres(),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text("Droits restants", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AnneauDroits(fraction = fraction, couleur = couleur)
            }
            GrilleTuiles(tuiles)
            alertes()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AccueilContenuApercu() {
    AccueilContenu(
        etat = AccueilUiState(
            profil = Profil(1995, LocalDate.of(2023, 4, 1)),
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
                ),
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
                ),
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
