package dev.celitracker.app.ui.components

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import dev.celitracker.app.ui.text.UiText
import dev.celitracker.app.ui.text.resolve

/** One-shot message: shown in the snackbar, then [onShown] lets the ViewModel forget it. */
@Composable
fun MessageSnackbarEffect(message: UiText?, snackbar: SnackbarHostState, onShown: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(message) {
        if (message == null) return@LaunchedEffect
        snackbar.showSnackbar(message.resolve(context))
        onShown()
    }
}
