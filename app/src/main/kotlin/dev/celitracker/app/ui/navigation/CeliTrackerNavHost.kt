package dev.celitracker.app.ui.navigation

import androidx.annotation.StringRes
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
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.celitracker.app.R
import dev.celitracker.app.ui.detail.FhsaDetailScreen
import dev.celitracker.app.ui.detail.TfsaDetailScreen
import dev.celitracker.app.ui.home.HomeScreen
import dev.celitracker.app.ui.journal.JournalScreen
import dev.celitracker.app.ui.settings.SettingsScreen
import dev.celitracker.engine.Account

private const val ROUTE_HOME = "home"
private const val ROUTE_TFSA_DETAIL = "tfsaDetail"
private const val ROUTE_FHSA_DETAIL = "fhsaDetail"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_JOURNAL = "journal"

/** Read by the JournalViewModel factory through SavedStateHandle. */
const val ARG_ACCOUNT = "account"
const val ARG_ADD = "add"
const val ARG_YEAR = "year"

/** Value of ARG_YEAR when no year is requested: an int argument cannot be null. */
const val NO_YEAR = -1

private fun journalRoute(account: Account, add: Boolean = false, year: Int = NO_YEAR) = "$ROUTE_JOURNAL?$ARG_ACCOUNT=$account&$ARG_ADD=$add&$ARG_YEAR=$year"

/**
 * The three top-level destinations. An account's detail screen is not
 * one of them: it attaches to home, from which it opens.
 */
private enum class Tab(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    HOME(ROUTE_HOME, R.string.tab_home, Icons.Filled.SpaceDashboard),
    JOURNAL(ROUTE_JOURNAL, R.string.tab_journal, Icons.AutoMirrored.Filled.ReceiptLong),
    SETTINGS(ROUTE_SETTINGS, R.string.tab_settings, Icons.Filled.Settings),
}

private fun tabOf(route: String?): Tab = when {
    route == null -> Tab.HOME
    route.startsWith(ROUTE_JOURNAL) -> Tab.JOURNAL
    route == ROUTE_SETTINGS -> Tab.SETTINGS
    else -> Tab.HOME
}

@Composable
fun CeliTrackerNavHost(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val activeTab = tabOf(backStackEntry?.destination?.route)

    // Switching tabs does not stack: each tab keeps its state and the
    // back button returns to home rather than replaying the history.
    fun goTo(route: String) = navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    fun backToHome() {
        navController.popBackStack(navController.graph.findStartDestination().id, inclusive = false)
    }

    // A precise intent (this account, add sheet open) must not
    // be replaced by the restored state of a previous visit to the journal.
    fun openJournal(account: Account, add: Boolean = false, year: Int = NO_YEAR) = navController.navigate(journalRoute(account, add, year)) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == activeTab,
                        onClick = {
                            when {
                                // From a detail screen, "Home" returns to home itself,
                                // without restoring the detail screen just left.
                                tab == Tab.HOME -> backToHome()

                                tab != activeTab -> goTo(tab.route)
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.label)) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
        },
    ) { margins ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_HOME,
            // Screens have their own Scaffold: the margins already applied
            // here are consumed so they do not add them a second time.
            modifier = Modifier.padding(margins).consumeWindowInsets(margins),
        ) {
            composable(ROUTE_HOME) {
                HomeScreen(
                    onOpenDetail = { account ->
                        navController.navigate(if (account == Account.TFSA) ROUTE_TFSA_DETAIL else ROUTE_FHSA_DETAIL)
                    },
                    onAdd = { account -> openJournal(account, add = true) },
                    onOpenJournal = { account -> openJournal(account) },
                    onOpenSettings = { goTo(ROUTE_SETTINGS) },
                )
            }
            composable(ROUTE_TFSA_DETAIL) {
                TfsaDetailScreen(
                    onBack = navController::popBackStack,
                    onSeeTransactions = { year -> openJournal(Account.TFSA, year = year) },
                )
            }
            composable(ROUTE_FHSA_DETAIL) {
                FhsaDetailScreen(
                    onBack = navController::popBackStack,
                    onSeeTransactions = { year -> openJournal(Account.FHSA, year = year) },
                )
            }
            composable(ROUTE_SETTINGS) {
                SettingsScreen()
            }
            composable(
                route = "$ROUTE_JOURNAL?$ARG_ACCOUNT={$ARG_ACCOUNT}&$ARG_ADD={$ARG_ADD}&$ARG_YEAR={$ARG_YEAR}",
                arguments = listOf(
                    navArgument(ARG_ACCOUNT) {
                        type = NavType.StringType
                        defaultValue = Account.TFSA.name
                    },
                    navArgument(ARG_ADD) {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                    navArgument(ARG_YEAR) {
                        type = NavType.IntType
                        defaultValue = NO_YEAR
                    },
                ),
            ) {
                JournalScreen()
            }
        }
    }
}
