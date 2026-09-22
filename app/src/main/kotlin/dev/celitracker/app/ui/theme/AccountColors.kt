package dev.celitracker.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.celitracker.engine.Account

data class AccountColors(val accent: Color, val container: Color, val onContainer: Color)

/** Each account keeps one color on every screen. */
@Composable
fun Account.colors(): AccountColors = with(MaterialTheme.colorScheme) {
    when (this@colors) {
        Account.TFSA -> AccountColors(primary, primaryContainer, onPrimaryContainer)
        Account.FHSA -> AccountColors(secondary, secondaryContainer, onSecondaryContainer)
    }
}
