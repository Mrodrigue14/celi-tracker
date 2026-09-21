package dev.celitracker.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.celitracker.app.R
import dev.celitracker.app.ui.components.AccountChoice
import dev.celitracker.app.ui.components.AmountField
import dev.celitracker.app.ui.components.DateField
import dev.celitracker.app.ui.components.SectionTitle
import dev.celitracker.app.ui.format.formatAmount
import dev.celitracker.app.ui.format.formatDate
import dev.celitracker.app.ui.text.label
import dev.celitracker.app.ui.theme.tabularFigures
import dev.celitracker.engine.Account
import dev.celitracker.engine.CraSnapshot

class CraSnapshotCallbacks(
    val onAccountChange: (Account) -> Unit,
    val onDateChange: (String) -> Unit,
    val onAmountChange: (String) -> Unit,
    val onSave: () -> Unit,
    val onDelete: (CraSnapshot) -> Unit,
)

@Composable
fun CraSnapshotSection(state: SettingsUiState, callbacks: CraSnapshotCallbacks, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        SectionTitle(stringResource(R.string.settings_cra_snapshot), Modifier.padding(top = 24.dp, bottom = 8.dp))
        Text(
            stringResource(R.string.settings_cra_snapshot_help),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AccountChoice(
            account = state.snapshotAccount,
            onChange = callbacks.onAccountChange,
            modifier = Modifier.padding(top = 12.dp),
        )
        DateField(
            date = state.snapshotDate,
            onDate = callbacks.onDateChange,
            label = stringResource(R.string.settings_cra_snapshot_date),
            isError = state.invalidSnapshotDate,
            modifier = Modifier.padding(top = 12.dp),
        )
        AmountField(
            value = state.snapshotAmount,
            onValue = callbacks.onAmountChange,
            label = stringResource(R.string.settings_cra_snapshot_amount),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        )
        OutlinedButton(
            onClick = callbacks.onSave,
            enabled = state.isNewSnapshotValid,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        ) {
            Text(stringResource(R.string.settings_cra_snapshot_save))
        }
        state.snapshots.forEach { snapshot ->
            SnapshotRow(snapshot, onDelete = { callbacks.onDelete(snapshot) })
        }
    }
}

@Composable
private fun SnapshotRow(snapshot: CraSnapshot, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(snapshot.account.label(), style = MaterialTheme.typography.bodyLarge)
            Text(
                snapshot.referenceDate.formatDate(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(snapshot.declaredRoom.formatAmount(), style = MaterialTheme.typography.bodyLarge.tabularFigures())
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
        }
    }
}
