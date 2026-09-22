package dev.celitracker.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.celitracker.app.ui.text.label
import dev.celitracker.app.ui.theme.colors
import dev.celitracker.engine.Account

@Composable
fun AccountChoice(account: Account, onChange: (Account) -> Unit, modifier: Modifier = Modifier) {
    SegmentedChoice(
        options = Account.entries,
        selection = account,
        onChoose = onChange,
        label = { it.label() },
        modifier = modifier,
        activeColor = { it.colors().container },
    )
}
