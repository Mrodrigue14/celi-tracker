package dev.celitracker.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.celitracker.app.ui.text.label
import dev.celitracker.engine.Account

/** Each account keeps its color, as on the home screen. */
@Composable
fun AccountChoice(account: Account, onChange: (Account) -> Unit, modifier: Modifier = Modifier) {
    SegmentedChoice(
        options = Account.entries,
        selection = account,
        onChoose = onChange,
        label = { it.label() },
        modifier = modifier,
        activeColor = {
            if (it == Account.TFSA) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
        },
    )
}
