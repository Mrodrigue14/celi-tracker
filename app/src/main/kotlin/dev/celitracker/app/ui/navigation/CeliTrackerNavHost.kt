package dev.celitracker.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
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

/** Lu par la fabrique de JournalViewModel via SavedStateHandle. */
const val ARG_COMPTE = "compte"

@Composable
fun CeliTrackerNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = ROUTE_ACCUEIL) {
        composable(ROUTE_ACCUEIL) {
            AccueilScreen(
                onOuvrirDetail = { compte ->
                    val route = if (compte == Compte.CELI) ROUTE_DETAIL_CELI else ROUTE_DETAIL_CELIAPP
                    navController.navigate(route)
                },
                onOuvrirReglages = { navController.navigate(ROUTE_REGLAGES) },
            )
        }
        composable(ROUTE_DETAIL_CELI) {
            DetailCeliScreen(
                onRetour = navController::popBackStack,
                onOuvrirJournal = { navController.navigate("$ROUTE_JOURNAL/${Compte.CELI}") },
            )
        }
        composable(ROUTE_DETAIL_CELIAPP) {
            DetailCeliappScreen(
                onRetour = navController::popBackStack,
                onOuvrirJournal = { navController.navigate("$ROUTE_JOURNAL/${Compte.CELIAPP}") },
            )
        }
        composable(ROUTE_REGLAGES) {
            ReglagesScreen(onRetour = navController::popBackStack)
        }
        composable(
            route = "$ROUTE_JOURNAL/{$ARG_COMPTE}",
            arguments = listOf(navArgument(ARG_COMPTE) { type = NavType.StringType }),
        ) {
            JournalScreen(onRetour = navController::popBackStack)
        }
    }
}
