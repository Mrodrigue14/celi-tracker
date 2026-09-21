package dev.celitracker.app.ui.detail

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.celitracker.app.CeliTrackerApplication
import dev.celitracker.app.R
import dev.celitracker.app.ui.components.WidthLimitedContent
import dev.celitracker.app.ui.components.YearCard
import dev.celitracker.app.ui.components.YearDetailList
import dev.celitracker.app.ui.format.formatAmount
import dev.celitracker.engine.TfsaYear
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TfsaDetailScreen(onBack: () -> Unit, onSeeTransactions: (Int) -> Unit) {
    val application = LocalContext.current.applicationContext as CeliTrackerApplication
    val viewModel: TfsaDetailViewModel = viewModel(factory = application.viewModelFactory)
    LaunchedEffect(Unit) { viewModel.load() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.detail_title_tfsa)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        WidthLimitedContent(modifier = Modifier.padding(innerPadding)) {
            TfsaDetailContent(state = state, onSeeTransactions = onSeeTransactions, modifier = Modifier.fillMaxSize())
        }
    }
}

/** Du plus general au plus detaille: l'evolution, puis chaque year, la plus recente d'abord. */
@Composable
fun TfsaDetailContent(state: TfsaDetailUiState, onSeeTransactions: (Int) -> Unit, modifier: Modifier = Modifier) {
    YearDetailList(
        rows = state.rows,
        year = { it.year },
        chartTitle = stringResource(R.string.detail_room_year_end),
        chartValue = { it.endRoom },
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    ) { row, inProgress ->
        TfsaYearCard(
            row,
            inProgress = inProgress,
            onSeeTransactions = row.year.takeIf { it in state.yearsWithTransactions }?.let { year -> { onSeeTransactions(year) } },
        )
    }
}

@Composable
private fun TfsaYearCard(row: TfsaYear, inProgress: Boolean, onSeeTransactions: (() -> Unit)?) {
    YearCard(
        year = row.year,
        amount = row.endRoom.formatAmount(),
        amountLabel = if (inProgress) stringResource(R.string.detail_room_left) else stringResource(R.string.detail_room_year_end),
        inProgress = inProgress,
        onSeeTransactions = onSeeTransactions,
        tiles = listOf(
            row.limit.formatAmount() to stringResource(R.string.detail_year_limit),
            row.startRoom.formatAmount() to stringResource(R.string.detail_room_january_first),
            row.deposits.formatAmount() to stringResource(R.string.detail_deposits),
            row.withdrawals.formatAmount() to stringResource(R.string.detail_withdrawals),
        ),
        // Un limit absent est account a zero: les room sont sous-estimes, labelStep inventes.
        note = if (row.limitMissing) stringResource(R.string.detail_limit_unconfirmed, row.year) else null,
    )
}

@Preview(showBackground = true)
@Composable
private fun TfsaDetailContentPreview() {
    fun row(year: Int, yearStart: String, deposits: String, end: String) = TfsaYear(
        year = year,
        limit = BigDecimal("7000.00"),
        startRoom = BigDecimal(yearStart),
        deposits = BigDecimal(deposits),
        withdrawals = BigDecimal.ZERO,
        endRoom = BigDecimal(end),
        limitMissing = false,
    )
    TfsaDetailContent(
        state = TfsaDetailUiState(
            rows = listOf(
                row(2024, "7000.00", "2000.00", "5000.00"),
                row(2025, "12000.00", "0.00", "12000.00"),
                row(2026, "19000.00", "3000.00", "16000.00"),
            ),
        ),
        onSeeTransactions = {},
    )
}
