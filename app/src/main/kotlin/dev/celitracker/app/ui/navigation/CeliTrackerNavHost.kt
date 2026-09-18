package dev.celitracker.app.ui.navigation

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.celitracker.app.ui.accueil.AccueilScreen
import dev.celitracker.app.ui.detail.DetailCeliScreen
import dev.celitracker.app.ui.detail.DetailCeliappScreen
import dev.celitracker.app.ui.journal.JournalScreen
import dev.celitracker.app.ui.reglages.ReglagesScreen
import dev.celitracker.engine.Compte

private const val ROUTE_ACCUEIL = "accueil"
private const val ROUTE_DETAIL_CELI = "detailCeli"
private const val ROUTE_DETAIL_CELIAPP = "detailCeliapp"
private const val ROUTE_REGLAGES = "reglages"
private const val ROUTE_JOURNAL = "journal"

/** Lus par la fabrique de JournalViewModel via SavedStateHandle. */
const val ARG_COMPTE = "compte"
const val ARG_AJOUTER = "ajouter"
const val ARG_ANNEE = "annee"

/** Valeur d'ARG_ANNEE quand aucune annee n'est demandee: un argument entier ne peut pas etre nul. */
const val AUCUNE_ANNEE = -1

private fun routeJournal(compte: Compte, ajouter: Boolean = false, annee: Int = AUCUNE_ANNEE) = "$ROUTE_JOURNAL?$ARG_COMPTE=$compte&$ARG_AJOUTER=$ajouter&$ARG_ANNEE=$annee"

/**
 * Les trois destinations de premier niveau. Le detail d'un compte n'en est
 * pas une: il se rattache a l'accueil, d'ou il s'ouvre.
 */
private enum class Onglet(val route: String, val libelle: String, val icone: ImageVector) {
    ACCUEIL(ROUTE_ACCUEIL, "Accueil", Icons.Filled.SpaceDashboard),
    JOURNAL(ROUTE_JOURNAL, "Journal", Icons.AutoMirrored.Filled.ReceiptLong),
    REGLAGES(ROUTE_REGLAGES, "Réglages", Icons.Filled.Settings),
}

private fun ongletDe(route: String?): Onglet = when {
    route == null -> Onglet.ACCUEIL
    route.startsWith(ROUTE_JOURNAL) -> Onglet.JOURNAL
    route == ROUTE_REGLAGES -> Onglet.REGLAGES
    else -> Onglet.ACCUEIL
}

@Composable
fun CeliTrackerNavHost(navController: NavHostController = rememberNavController()) {
    val entree by navController.currentBackStackEntryAsState()
    val ongletActif = ongletDe(entree?.destination?.route)

    // Changer d'onglet ne s'empile pas: chaque onglet garde son etat et le
    // bouton retour ramene a l'accueil plutot que de rejouer l'historique.
    fun allerA(route: String) = navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    fun revenirAccueil() {
        navController.popBackStack(navController.graph.findStartDestination().id, inclusive = false)
    }

    // Une intention precise (ce compte, feuille d'ajout ouverte) ne doit pas
    // etre remplacee par l'etat restaure d'une visite precedente du journal.
    fun ouvrirJournal(compte: Compte, ajouter: Boolean = false, annee: Int = AUCUNE_ANNEE) = navController.navigate(routeJournal(compte, ajouter, annee)) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Onglet.entries.forEach { onglet ->
                    NavigationBarItem(
                        selected = onglet == ongletActif,
                        onClick = {
                            when {
                                // Depuis un detail, « Accueil » ramene a l'accueil lui-meme,
                                // sans restaurer le detail qu'on vient de quitter.
                                onglet == Onglet.ACCUEIL -> revenirAccueil()

                                onglet != ongletActif -> allerA(onglet.route)
                            }
                        },
                        icon = { Icon(onglet.icone, contentDescription = null) },
                        label = { Text(onglet.libelle) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
        },
    ) { marges ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_ACCUEIL,
            // Les ecrans ont leur propre Scaffold: les marges deja appliquees
            // ici sont consommees pour qu'ils ne les ajoutent pas une seconde fois.
            modifier = Modifier.padding(marges).consumeWindowInsets(marges),
        ) {
            composable(ROUTE_ACCUEIL) {
                AccueilScreen(
                    onOuvrirDetail = { compte ->
                        navController.navigate(if (compte == Compte.CELI) ROUTE_DETAIL_CELI else ROUTE_DETAIL_CELIAPP)
                    },
                    onAjouter = { compte -> ouvrirJournal(compte, ajouter = true) },
                    onOuvrirJournal = { compte -> ouvrirJournal(compte) },
                    onOuvrirReglages = { allerA(ROUTE_REGLAGES) },
                )
            }
            composable(ROUTE_DETAIL_CELI) {
                DetailCeliScreen(
                    onRetour = navController::popBackStack,
                    onOuvrirJournal = { ouvrirJournal(Compte.CELI) },
                    onVoirTransactions = { annee -> ouvrirJournal(Compte.CELI, annee = annee) },
                )
            }
            composable(ROUTE_DETAIL_CELIAPP) {
                DetailCeliappScreen(
                    onRetour = navController::popBackStack,
                    onOuvrirJournal = { ouvrirJournal(Compte.CELIAPP) },
                    onVoirTransactions = { annee -> ouvrirJournal(Compte.CELIAPP, annee = annee) },
                )
            }
            composable(ROUTE_REGLAGES) {
                ReglagesScreen()
            }
            composable(
                route = "$ROUTE_JOURNAL?$ARG_COMPTE={$ARG_COMPTE}&$ARG_AJOUTER={$ARG_AJOUTER}&$ARG_ANNEE={$ARG_ANNEE}",
                arguments = listOf(
                    navArgument(ARG_COMPTE) {
                        type = NavType.StringType
                        defaultValue = Compte.CELI.name
                    },
                    navArgument(ARG_AJOUTER) {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                    navArgument(ARG_ANNEE) {
                        type = NavType.IntType
                        defaultValue = AUCUNE_ANNEE
                    },
                ),
            ) {
                JournalScreen()
            }
        }
    }
}
