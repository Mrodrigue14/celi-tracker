package dev.celitracker.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.celitracker.app.CeliTrackerApplication
import dev.celitracker.app.R
import dev.celitracker.app.ui.components.AmountField
import dev.celitracker.app.ui.components.DateField
import dev.celitracker.app.ui.components.IconBadge
import dev.celitracker.app.ui.components.SectionTitle
import dev.celitracker.app.ui.components.SegmentedChoice
import dev.celitracker.app.ui.components.TILE_SHAPE
import dev.celitracker.app.ui.components.TwoPanes
import dev.celitracker.app.ui.components.WidthLimitedContent
import dev.celitracker.app.ui.components.isWideScreen
import dev.celitracker.app.ui.format.formatAmount
import dev.celitracker.app.ui.format.formatDate
import dev.celitracker.app.ui.text.resolve
import dev.celitracker.app.ui.theme.ThemeMode
import dev.celitracker.app.ui.theme.tabularFigures
import dev.celitracker.engine.Account
import dev.celitracker.engine.AnnualLimit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val application = LocalContext.current.applicationContext as CeliTrackerApplication
    val themeMode by application.themePreference.mode.collectAsStateWithLifecycle()
    val viewModel: SettingsViewModel = viewModel(factory = application.viewModelFactory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(message.resolve(context))
        viewModel.messageShown()
    }

    var importToConfirm by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(CreateDocument(JSON_MIME_TYPE)) { uri ->
        if (uri != null) viewModel.exportData { content -> writeFile(context, uri, content) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        importToConfirm = uri
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.settings_title)) })
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innerPadding ->
        WidthLimitedContent(modifier = Modifier.padding(innerPadding)) {
            SettingsContent(
                state = state,
                modifier = Modifier.fillMaxSize(),
                onBirthYearChange = viewModel::updateBirthYear,
                onOpeningDateChange = viewModel::updateFhsaOpeningDate,
                onSaveProfile = viewModel::saveProfile,
                onNewLimitYearChange = viewModel::updateNewLimitYear,
                onNewLimitAmountChange = viewModel::updateNewLimitAmount,
                onAddLimit = viewModel::addLimit,
                onCraPageUrlChange = viewModel::updateCraPageUrl,
                onSaveCraPageUrl = viewModel::saveCraPageUrl,
                onRestoreCraPageUrl = viewModel::restoreCraPageUrl,
                onCheckCra = { viewModel.checkCra(explicitRequest = true) },
                onConfirmProposal = viewModel::confirmProposal,
                onRejectProposal = viewModel::rejectProposal,
                onExport = { exportLauncher.launch(EXPORT_FILE_NAME) },
                themeMode = themeMode,
                onThemeMode = application.themePreference::choose,
                onImport = { importLauncher.launch(arrayOf(JSON_MIME_TYPE)) },
            )
        }
    }

    importToConfirm?.let { uri ->
        ImportConfirmation(
            onConfirm = {
                importToConfirm = null
                viewModel.importData { readFile(context, uri) }
            },
            onCancel = { importToConfirm = null },
        )
    }
}

private const val JSON_MIME_TYPE = "application/json"

private const val EXPORT_FILE_NAME = "celi-tracker.json"

private suspend fun writeFile(context: Context, uri: Uri, content: String) = withContext(Dispatchers.IO) {
    val stream = context.contentResolver.openOutputStream(uri) ?: error("output stream unavailable")
    stream.use { it.write(content.toByteArray()) }
}

private suspend fun readFile(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    val stream = context.contentResolver.openInputStream(uri) ?: error("input stream unavailable")
    stream.use { it.reader().readText() }
}

@Composable
private fun ImportConfirmation(onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.settings_import_title)) },
        text = {
            Text(stringResource(R.string.settings_import_text))
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.settings_import_replace)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun SettingsContent(
    state: SettingsUiState,
    onBirthYearChange: (String) -> Unit,
    onOpeningDateChange: (String) -> Unit,
    onSaveProfile: () -> Unit,
    onNewLimitYearChange: (String) -> Unit,
    onNewLimitAmountChange: (String) -> Unit,
    onAddLimit: () -> Unit,
    onCraPageUrlChange: (String) -> Unit,
    onSaveCraPageUrl: () -> Unit,
    onCheckCra: () -> Unit,
    onRestoreCraPageUrl: () -> Unit,
    onConfirmProposal: (AnnualLimit) -> Unit,
    onRejectProposal: (AnnualLimit) -> Unit,
    onExport: () -> Unit,
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profileAndAppearance: @Composable (Modifier) -> Unit = { groupModifier ->
        Column(modifier = groupModifier.padding(horizontal = 16.dp)) {
            SectionTitle(stringResource(R.string.settings_profile), Modifier.padding(top = 24.dp, bottom = 12.dp))
            OutlinedTextField(
                value = state.birthYear,
                onValueChange = onBirthYearChange,
                label = { Text(stringResource(R.string.settings_birth_year)) },
                supportingText = { Text(stringResource(R.string.settings_birth_year_help)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = state.birthYear.isNotBlank() && state.validBirthYear == null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TfsaEligibility(year = state.tfsaEligibilityYear, modifier = Modifier.padding(top = 8.dp))
            DateField(
                date = state.fhsaOpeningDate,
                onDate = onOpeningDateChange,
                label = stringResource(R.string.settings_fhsa_opening),
                isError = state.invalidOpeningDate,
                clearable = true,
                modifier = Modifier.padding(top = 16.dp),
            )
            Button(
                onClick = onSaveProfile,
                enabled = state.isProfileValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            ) {
                Text(stringResource(R.string.settings_save_profile))
            }

            HorizontalDivider(modifier = Modifier.padding(top = 32.dp))

            Appearance(mode = themeMode, onChoose = onThemeMode)
        }
    }

    val limits: @Composable (Modifier) -> Unit = { groupModifier ->
        Column(modifier = groupModifier.padding(horizontal = 16.dp)) {
            CraProposals(
                unconfirmedLimits = state.unconfirmedLimits,
                onConfirm = onConfirmProposal,
                onReject = onRejectProposal,
            )
            state.craError?.let { CraReadFailure(it.resolve()) }

            SectionTitle(stringResource(R.string.settings_limits_source), Modifier.padding(top = 24.dp, bottom = 12.dp))
            OutlinedTextField(
                value = state.craPageUrl,
                onValueChange = onCraPageUrlChange,
                label = { Text(stringResource(R.string.settings_cra_page)) },
                supportingText = {
                    Text(
                        state.lastCraCheck
                            ?.let { stringResource(R.string.settings_last_read, it.atZone(ZoneId.systemDefault()).toLocalDate().formatDate()) }
                            ?: stringResource(R.string.settings_never_read),
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onSaveCraPageUrl,
                    enabled = !state.checkInProgress,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.settings_test_and_save))
                }
                Button(
                    onClick = onCheckCra,
                    enabled = !state.checkInProgress,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (state.checkInProgress) stringResource(R.string.settings_reading) else stringResource(R.string.settings_check))
                }
            }
            TextButton(
                onClick = onRestoreCraPageUrl,
                enabled = !state.checkInProgress,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_restore_address))
            }

            SectionTitle(stringResource(R.string.settings_tfsa_limits), Modifier.padding(top = 24.dp, bottom = 12.dp))
            if (state.confirmedLimits.isEmpty()) {
                Text(
                    stringResource(R.string.settings_no_limit),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.relevantLimits.forEach { limit -> LimitRow(limit) }
            EarlierLimits(state.earlierLimits)

            Row(
                modifier = Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = state.newLimitYear,
                    onValueChange = onNewLimitYearChange,
                    label = { Text(stringResource(R.string.settings_year)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                AmountField(
                    value = state.newLimitAmount,
                    onValue = onNewLimitAmountChange,
                    label = stringResource(R.string.settings_amount),
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedButton(
                onClick = onAddLimit,
                enabled = state.isNewLimitValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                Text(stringResource(R.string.settings_add_limit))
            }
        }
    }

    val backup: @Composable (Modifier) -> Unit = { groupModifier ->
        Column(modifier = groupModifier.padding(horizontal = 16.dp)) {
            HorizontalDivider(modifier = Modifier.padding(top = 32.dp))
            BackupAndRestore(onExport = onExport, onImport = onImport)
        }
    }

    if (isWideScreen()) {
        TwoPanes(
            left = {
                Column(modifier = it.verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
                    profileAndAppearance(Modifier)
                    backup(Modifier)
                }
            },
            right = { limits(it.verticalScroll(rememberScrollState()).padding(top = 24.dp, bottom = 32.dp)) },
            modifier = modifier,
        )
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            profileAndAppearance(Modifier)
            HorizontalDivider(modifier = Modifier.padding(top = 32.dp, start = 16.dp, end = 16.dp))
            limits(Modifier)
            backup(Modifier)
        }
    }
}

@Composable
private fun Appearance(mode: ThemeMode, onChoose: (ThemeMode) -> Unit) {
    SectionTitle(stringResource(R.string.settings_appearance), Modifier.padding(top = 24.dp, bottom = 12.dp))
    SegmentedChoice(
        options = ThemeMode.entries,
        selection = mode,
        onChoose = onChoose,
        label = {
            stringResource(
                when (it) {
                    ThemeMode.SYSTEM -> R.string.theme_system
                    ThemeMode.LIGHT -> R.string.theme_light
                    ThemeMode.DARK -> R.string.theme_dark
                },
            )
        },
        activeColor = { MaterialTheme.colorScheme.primaryContainer },
    )
}

/** Android backup can miss APK installs and cannot be inspected: the JSON file can be checked beforehand. */
@Composable
private fun BackupAndRestore(onExport: () -> Unit, onImport: () -> Unit) {
    SectionTitle(stringResource(R.string.settings_backup), Modifier.padding(top = 24.dp, bottom = 12.dp))
    Text(
        stringResource(R.string.settings_backup_text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(
        modifier = Modifier.padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(onClick = onExport, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.settings_export)) }
        OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.settings_import)) }
    }
}

/** CRA limits stay out of the calculation until the user confirms them. */
@Composable
private fun CraProposals(
    unconfirmedLimits: List<AnnualLimit>,
    onConfirm: (AnnualLimit) -> Unit,
    onReject: (AnnualLimit) -> Unit,
) {
    if (unconfirmedLimits.isEmpty()) return

    SectionTitle(stringResource(R.string.settings_cra_proposed), Modifier.padding(top = 24.dp, bottom = 12.dp))
    unconfirmedLimits.forEach { limit ->
        Column(modifier = Modifier.padding(bottom = 12.dp)) {
            Text(
                stringResource(R.string.settings_proposed_limit_row, limit.year, limit.amount.formatAmount()),
                style = MaterialTheme.typography.bodyLarge.tabularFigures(),
            )
            Text(
                stringResource(R.string.settings_cra_proposed_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(onClick = { onConfirm(limit) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_confirm))
                }
                OutlinedButton(onClick = { onReject(limit) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_reject))
                }
            }
        }
    }
}

@Composable
private fun CraReadFailure(reason: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .background(MaterialTheme.colorScheme.errorContainer, TILE_SHAPE)
            .padding(16.dp),
    ) {
        Text(
            stringResource(R.string.settings_read_failed),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
        Text(
            reason,
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
        Text(
            stringResource(R.string.settings_manual_entry),
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

/** The expanded state is visual only, so it stays out of the ViewModel. */
@Composable
private fun EarlierLimits(limits: List<AnnualLimit>) {
    if (limits.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(false) }

    TextButton(onClick = { expanded = !expanded }) {
        Text(
            if (expanded) {
                stringResource(R.string.settings_hide_earlier)
            } else {
                pluralStringResource(R.plurals.settings_show_earlier, limits.size, limits.size, limits.first().year, limits.last().year)
            },
        )
    }
    if (expanded) {
        limits.forEach { limit -> LimitRow(limit, dimmed = true) }
    }
}

@Composable
private fun TfsaEligibility(year: Int?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBadge(
            icon = Icons.Filled.Check,
            backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Column {
            Text(
                if (year != null) stringResource(R.string.settings_room_since, year) else stringResource(R.string.settings_enter_birth_year),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                stringResource(R.string.settings_eligibility_rule),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LimitRow(limit: AnnualLimit, dimmed: Boolean = false) {
    val color = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(limit.year.toString(), style = MaterialTheme.typography.bodyLarge, color = color)
        Text(
            limit.amount.formatAmount(),
            style = MaterialTheme.typography.bodyLarge.tabularFigures(),
            color = color,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsContentPreview() {
    SettingsContent(
        state = SettingsUiState(
            birthYear = "1995",
            fhsaOpeningDate = "2023-04-01",
            limits = listOf(
                AnnualLimit(Account.TFSA, 2025, BigDecimal("7000.00"), confirmed = true),
                AnnualLimit(Account.TFSA, 2026, BigDecimal("7000.00"), confirmed = true),
                AnnualLimit(Account.TFSA, 2027, BigDecimal("7500.00"), confirmed = false),
            ),
            craPageUrl = "https://www.canada.ca/...",
        ),
        onBirthYearChange = {},
        onOpeningDateChange = {},
        onSaveProfile = {},
        onNewLimitYearChange = {},
        onNewLimitAmountChange = {},
        onAddLimit = {},
        onCraPageUrlChange = {},
        onSaveCraPageUrl = {},
        onRestoreCraPageUrl = {},
        onCheckCra = {},
        onConfirmProposal = {},
        onRejectProposal = {},
        onExport = {},
        onImport = {},
        themeMode = ThemeMode.SYSTEM,
        onThemeMode = {},
    )
}
