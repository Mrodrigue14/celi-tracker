package dev.celitracker.app.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.North
import androidx.compose.material.icons.filled.South
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.celitracker.app.CeliTrackerApplication
import dev.celitracker.app.R
import dev.celitracker.app.ui.components.AlertBanner
import dev.celitracker.app.ui.components.AmountField
import dev.celitracker.app.ui.components.DateField
import dev.celitracker.app.ui.components.EmptyState
import dev.celitracker.app.ui.components.IconBadge
import dev.celitracker.app.ui.components.SegmentedChoice
import dev.celitracker.app.ui.components.TwoPanes
import dev.celitracker.app.ui.components.WidthLimitedContent
import dev.celitracker.app.ui.components.isWideScreen
import dev.celitracker.app.ui.format.formatAmount
import dev.celitracker.app.ui.format.formatDate
import dev.celitracker.app.ui.text.label
import dev.celitracker.app.ui.text.resolve
import dev.celitracker.app.ui.theme.tabularFigures
import dev.celitracker.engine.Account
import dev.celitracker.engine.Transaction
import dev.celitracker.engine.TransactionType
import java.math.BigDecimal
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen() {
    val application = LocalContext.current.applicationContext as CeliTrackerApplication
    val viewModel: JournalViewModel = viewModel(factory = application.viewModelFactory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val list = rememberLazyListState()
    val twoPanes = isWideScreen()

    // One-shot message: shown in a snackbar, then the ViewModel forgets it.
    val context = LocalContext.current
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(message.resolve(context))
        viewModel.messageShown()
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = { Text(stringResource(R.string.journal_title)) })
                AccountChoice(
                    account = state.account,
                    onChange = viewModel::changeAccount,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            // The empty state has its own button.
            if (state.transactions.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.action_add)) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    onClick = viewModel::openNew,
                )
            }
        },
    ) { innerPadding ->
        WidthLimitedContent(modifier = Modifier.padding(innerPadding)) {
            val journal: @Composable (Modifier) -> Unit = { journalModifier ->
                JournalContent(
                    state = state,
                    onOpenTransaction = viewModel::openEdit,
                    onAdd = viewModel::openNew,
                    modifier = journalModifier,
                    list = list,
                )
            }
            if (twoPanes) {
                TwoPanes(
                    left = journal,
                    right = {
                        TransactionPane(
                            form = state.form,
                            onDate = viewModel::updateDate,
                            onType = viewModel::updateType,
                            onAmount = viewModel::updateAmount,
                            onSave = viewModel::save,
                            onDelete = viewModel::delete,
                            onClose = viewModel::closeForm,
                            modifier = it,
                        )
                    },
                )
            } else {
                journal(Modifier.fillMaxSize())
            }
        }
    }

    // Waits for the transactions: the year's header does not exist before.
    LaunchedEffect(state.scrollToYear, state.transactions) {
        val year = state.scrollToYear ?: return@LaunchedEffect
        if (state.transactions.isEmpty()) return@LaunchedEffect
        val position = headerPosition(state.transactions, year)
        if (position >= 0) list.scrollToItem(position)
        viewModel.scrollToYearDone()
    }

    // Two-pane mode already shows the form on the right.
    if (!twoPanes) {
        state.form?.let { form ->
            TransactionSheet(
                form = form,
                onDate = viewModel::updateDate,
                onType = viewModel::updateType,
                onAmount = viewModel::updateAmount,
                onSave = viewModel::save,
                onDelete = viewModel::delete,
                onClose = viewModel::closeForm,
            )
        }
    }
}

@Composable
private fun AccountChoice(account: Account, onChange: (Account) -> Unit, modifier: Modifier = Modifier) {
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

@Composable
fun JournalContent(
    state: JournalUiState,
    onOpenTransaction: (Transaction) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    list: LazyListState = rememberLazyListState(),
) {
    if (state.transactions.isEmpty()) {
        EmptyState(
            icon = Icons.AutoMirrored.Filled.List,
            title = stringResource(R.string.journal_empty_title),
            text = stringResource(R.string.journal_empty_text),
            actionLabel = stringResource(R.string.journal_empty_action),
            onAction = onAdd,
            modifier = modifier,
        )
        return
    }
    val byYear = remember(state.transactions) { state.transactions.groupBy { it.date.year } }
    LazyColumn(modifier = modifier.fillMaxSize(), state = list) {
        byYear.forEach { (year, transactions) ->
            item(key = "year-$year") { YearHeader(year) }
            items(transactions, key = { it.id }) { transaction ->
                TransactionRow(transaction, onClick = { onOpenTransaction(transaction) })
            }
        }
    }
}

/** Each year is one header row followed by one row per transaction. */
internal fun headerPosition(transactions: List<Transaction>, year: Int): Int {
    var position = 0
    transactions.groupBy { it.date.year }.forEach { (group, rows) ->
        if (group == year) return position
        position += 1 + rows.size
    }
    return -1
}

@Composable
private fun YearHeader(year: Int) {
    Text(
        year.toString(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun TransactionRow(transaction: Transaction, onClick: () -> Unit) {
    val isDeposit = transaction.type == TransactionType.DEPOSIT
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            IconBadge(
                icon = if (isDeposit) Icons.Filled.South else Icons.Filled.North,
                backgroundColor = if (isDeposit) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.tertiaryContainer
                },
                tint = if (isDeposit) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onTertiaryContainer
                },
                description = if (isDeposit) stringResource(R.string.type_deposit) else stringResource(R.string.type_withdrawal),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    transaction.amount.formatAmount(),
                    style = MaterialTheme.typography.titleMedium.tabularFigures(),
                )
                Text(
                    if (isDeposit) stringResource(R.string.type_deposit) else stringResource(R.string.type_withdrawal),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                transaction.date.formatDate(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionSheet(
    form: TransactionForm,
    onDate: (String) -> Unit,
    onType: (TransactionType) -> Unit,
    onAmount: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    // Full height: half expanded, the save button fell below the screen edge.
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                if (form.isNew) stringResource(R.string.journal_new) else stringResource(R.string.journal_edit),
                style = MaterialTheme.typography.titleLarge,
            )
            TransactionFields(
                form = form,
                onDate = onDate,
                onType = onType,
                onAmount = onAmount,
                onSave = onSave,
                onDelete = onDelete,
            )
        }
    }
}

@Composable
private fun TransactionPane(
    form: TransactionForm?,
    onDate: (String) -> Unit,
    onType: (TransactionType) -> Unit,
    onAmount: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (form == null) {
        EmptyState(icon = Icons.AutoMirrored.Filled.List, text = stringResource(R.string.journal_pane_empty), modifier = modifier)
        return
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 16.dp)
            // Lifts the last button above the floating Add button.
            .padding(bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (form.isNew) stringResource(R.string.journal_new) else stringResource(R.string.journal_edit),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_cancel))
            }
        }
        TransactionFields(
            form = form,
            onDate = onDate,
            onType = onType,
            onAmount = onAmount,
            onSave = onSave,
            onDelete = onDelete,
        )
    }
}

@Composable
private fun TransactionFields(
    form: TransactionForm,
    onDate: (String) -> Unit,
    onType: (TransactionType) -> Unit,
    onAmount: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    SegmentedChoice(
        options = TransactionType.entries,
        selection = form.type,
        onChoose = onType,
        label = { if (it == TransactionType.DEPOSIT) stringResource(R.string.type_deposit) else stringResource(R.string.type_withdrawal) },
    )
    AmountField(
        value = form.amount,
        onValue = onAmount,
        label = stringResource(R.string.journal_amount),
        isError = form.amount.isNotEmpty() && form.validAmount == null,
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.fillMaxWidth(),
    )
    DateField(date = form.date, onDate = onDate, label = stringResource(R.string.journal_date))
    form.warning?.let { AlertBanner(it.resolve(), Icons.Filled.Warning) }
    Button(
        onClick = onSave,
        enabled = form.valid,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (form.warning == null) stringResource(R.string.action_save) else stringResource(R.string.action_save_anyway))
    }
    form.error?.let {
        Text(it.resolve(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
    if (!form.isNew) {
        TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun JournalContentPreview() {
    JournalContent(
        state = JournalUiState(
            transactions = listOf(
                Transaction(Account.TFSA, LocalDate.of(2026, 3, 1), TransactionType.WITHDRAWAL, BigDecimal("500.00"), id = 2),
                Transaction(Account.TFSA, LocalDate.of(2026, 1, 15), TransactionType.DEPOSIT, BigDecimal("2000.00"), id = 1),
                Transaction(Account.TFSA, LocalDate.of(2025, 11, 3), TransactionType.DEPOSIT, BigDecimal("1500.00"), id = 3),
            ),
        ),
        onOpenTransaction = {},
        onAdd = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun JournalEmptyPreview() {
    JournalContent(state = JournalUiState(), onOpenTransaction = {}, onAdd = {})
}
