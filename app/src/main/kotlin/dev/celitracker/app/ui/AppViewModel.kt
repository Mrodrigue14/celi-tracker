package dev.celitracker.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.celitracker.app.CeliTrackerApplication

/** Every screen's ViewModel comes from the one factory wired in [CeliTrackerApplication]. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(): VM {
    val application = LocalContext.current.applicationContext as CeliTrackerApplication
    return viewModel(factory = application.viewModelFactory)
}
