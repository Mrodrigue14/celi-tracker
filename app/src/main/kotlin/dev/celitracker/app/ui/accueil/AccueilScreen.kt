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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.celitracker.app.CeliTrackerApplication
import dev.celitracker.app.R
import dev.celitracker.app.ui.composants.AnneauDroits
import dev.celitracker.app.ui.composants.BandeauAlerte
import dev.celitracker.app.ui.composants.FORME_CARTE
import dev.celitracker.app.ui.composants.GrilleTuiles
import dev.celitracker.app.ui.composants.PastilleCompte
import dev.celitracker.app.ui.format.formatDate
import dev.celitracker.app.ui.format.formatMontant
import dev.celitracker.app.ui.theme.chiffres
import dev.celitracker.engine.Compte
import dev.celitracker.engine.DroitsAnnee
import dev.celitracker.engine.DroitsAnneeCeliapp
import dev.celitracker.engine.ExcedentMensuel
import dev.celitracker.engine.NiveauUtilisation
import dev.celitracker.engine.Profil
import dev.celitracker.engine.Utilisation
import java.math.BigDecimal
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccueilScreen(
    onOuvrirDetail: (Compte) -> Unit,
    onAjouter: (Compte) -> Unit,
    onOuvrirJournal: (Compte) -> Unit,
    onOuvrirReglages: () -> Unit,
) {
    val application = LocalContext.current.applicationContext as CeliTrackerApplication
    val viewModel: AccueilViewModel = viewModel(factory = application.viewModelFactory)
    LaunchedEffect(Unit) { viewModel.charger() }
    val etat by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.app_name)) })
        },
    ) { innerPadding ->
        AccueilContenu(
            etat = etat,
            modifier = Modifier.padding(innerPadding),
            onOuvrirDetail = onOuvrirDetail,
            onAjouter = onAjouter,
            onOuvrirJournal = onOuvrirJournal,
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
    onAjouter: (Compte) -> Unit = {},
    onOuvrirJournal: (Compte) -> Unit = {},
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
            nom = stringResource(R.string.compte_celi),
            icone = Icons.Filled.Savings,
            couleur = MaterialTheme.colorScheme.primary,
            conteneur = MaterialTheme.colorScheme.primaryContainer,
            surConteneur = MaterialTheme.colorScheme.onPrimaryContainer,
            droitsRestants = etat.celiAnneeCourante?.droitsFin,
            fraction = etat.fractionUtiliseeCeli,
            tuiles = listOfNotNull(
                etat.celiAnneeCourante?.let { it.depots.formatMontant() to stringResource(R.string.accueil_cotise_en, etat.anneeCourante) },
                etat.celiAnneeCourante?.let { it.plafond.formatMontant() to stringResource(R.string.accueil_plafond_annee, etat.anneeCourante) },
            ),
            onClick = { onOuvrirDetail(Compte.CELI) },
            onAjouter = { onAjouter(Compte.CELI) },
            onOuvrirJournal = { onOuvrirJournal(Compte.CELI) },
        ) {
            if (etat.celiAnneeCourante?.plafondManquant == true) {
                BandeauAlerte(stringResource(R.string.alerte_plafond_non_confirme), Icons.Filled.Info)
            }
            // La penalite calculee au mois pres dit deja tout d'une sur-cotisation:
            // le bandeau d'utilisation ne s'ajoute que s'il n'y en a pas.
            val excedent = etat.excedentCeliCourant
            if (excedent != null) {
                BandeauAlerte(stringResource(R.string.alerte_sur_cotisation, excedent.penalite.formatMontant()), Icons.Filled.Warning)
            } else {
                AlerteUtilisation(etat.utilisationCeli, etat.anneeCourante)
            }
        }
        if (etat.profil?.dateOuvertureCeliapp == null) {
            SansCeliapp(onOuvrirReglages)
            return@Column
        }
        CarteCompte(
            nom = stringResource(R.string.compte_celiapp),
            icone = Icons.Filled.Home,
            couleur = MaterialTheme.colorScheme.secondary,
            conteneur = MaterialTheme.colorScheme.secondaryContainer,
            surConteneur = MaterialTheme.colorScheme.onSecondaryContainer,
            droitsRestants = etat.droitsRestantsCeliapp,
            fraction = etat.fractionUtiliseeCeliapp,
            tuiles = listOfNotNull(
                etat.celiappAnneeCourante?.let { it.depots.formatMontant() to stringResource(R.string.accueil_cotise_en, etat.anneeCourante) },
                etat.celiappAnneeCourante?.let { it.plafondVieRestant.formatMontant() to stringResource(R.string.accueil_plafond_vie_restant) },
                etat.celiappAnneeCourante?.let { it.reportEntrant.formatMontant() to stringResource(R.string.accueil_report_recu) },
                etat.echeanceParticipationCeliapp?.let { it.formatDate() to stringResource(R.string.accueil_echeance) },
            ),
            onClick = { onOuvrirDetail(Compte.CELIAPP) },
            onAjouter = { onAjouter(Compte.CELIAPP) },
            onOuvrirJournal = { onOuvrirJournal(Compte.CELIAPP) },
        ) {
            AlerteUtilisation(etat.utilisationCeliapp, etat.anneeCourante)
        }
    }
}

/** 80 % previent sur fond neutre; 95 % et plus passe a la couleur d'erreur. */
@Composable
private fun AlerteUtilisation(utilisation: Utilisation?, annee: Int) {
    val u = utilisation ?: return
    when (u.niveau) {
        NiveauUtilisation.NORMAL -> Unit

        NiveauUtilisation.ATTENTION -> BandeauAlerte(
            stringResource(R.string.alerte_utilisation_attention, u.pourcentage ?: 0, annee, u.restant.formatMontant()),
            Icons.Filled.Info,
            grave = false,
        )

        NiveauUtilisation.CRITIQUE -> BandeauAlerte(
            stringResource(R.string.alerte_utilisation_critique, u.pourcentage ?: 0, annee, u.restant.formatMontant()),
            Icons.Filled.Warning,
        )

        NiveauUtilisation.DEPASSE -> BandeauAlerte(
            stringResource(R.string.alerte_utilisation_depassee, annee, u.excedent.formatMontant()),
            Icons.Filled.Warning,
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
        Text(stringResource(R.string.accueil_sans_profil_titre), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.accueil_sans_profil_texte),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onOuvrirReglages, modifier = Modifier.padding(top = 12.dp)) { Text(stringResource(R.string.accueil_sans_profil_action)) }
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
            Text(stringResource(R.string.compte_celiapp), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.accueil_sans_celiapp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = stringResource(R.string.accueil_ouvrir_reglages),
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
    onAjouter: () -> Unit,
    onOuvrirJournal: () -> Unit,
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
                    contentDescription = stringResource(R.string.action_voir_detail),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        (droitsRestants ?: BigDecimal.ZERO).formatMontant(),
                        style = MaterialTheme.typography.headlineMedium.chiffres(),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(stringResource(R.string.accueil_droits_restants), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (fraction != null) AnneauDroits(fraction = fraction, couleur = couleur)
            }
            GrilleTuiles(tuiles)
            alertes()
            // Les deux gestes les plus frequents, visibles sur la carte plutot
            // que caches derriere l'ecran de detail.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(
                    onClick = onAjouter,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = conteneur, contentColor = surConteneur),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.action_ajouter), modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(onClick = onOuvrirJournal, modifier = Modifier.weight(1f)) {
                    Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.action_journal), modifier = Modifier.padding(start = 8.dp))
                }
            }
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
