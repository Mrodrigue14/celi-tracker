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
import dev.celitracker.engine.FhsaYear
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FhsaDetailScreen(onBack: () -> Unit, onSeeTransactions: (Int) -> Unit) {
    val application = LocalContext.current.applicationContext as CeliTrackerApplication
    val viewModel: FhsaDetailViewModel = viewModel(factory = application.viewModelFactory)
    LaunchedEffect(Unit) { viewModel.load() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.detail_title_fhsa)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        WidthLimitedContent(modifier = Modifier.padding(innerPadding)) {
            FhsaDetailContent(state = state, onSeeTransactions = onSeeTransactions, modifier = Modifier.fillMaxSize())
        }
    }
}

/** From most general to most detailed: the trend, then each year, most recent first. */
@Composable
fun FhsaDetailContent(state: FhsaDetailUiState, onSeeTransactions: (Int) -> Unit, modifier: Modifier = Modifier) {
    YearDetailList(
        rows = state.rows,
        year = { it.year },
        chartTitle = stringResource(R.string.detail_lifetime_limit_left),
        chartValue = { it.lifetimeLimitLeft },
        color = MaterialTheme.colorScheme.secondary,
        modifier = modifier,
    ) { row, inProgress ->
        FhsaYearCard(
            row,
            inProgress = inProgress,
            onSeeTransactions = row.year.takeIf { it in state.yearsWithTransactions }?.let { year -> { onSeeTransactions(year) } },
        )
    }
}

@Composable
private fun FhsaYearCard(row: FhsaYear, inProgress: Boolean, onSeeTransactions: (() -> Unit)?) {
    YearCard(
        year = row.year,
        amount = (row.yearRoom - row.deposits).formatAmount(),
        amountLabel = if (inProgress) stringResource(R.string.detail_room_left) else stringResource(R.string.detail_unused_room),
        inProgress = inProgress,
        onSeeTransactions = onSeeTransactions,
        tiles = listOf(
            row.carryForwardIn.formatAmount() to stringResource(R.string.detail_carry_forward_received),
            row.yearRoom.formatAmount() to stringResource(R.string.detail_year_room),
            row.deposits.formatAmount() to stringResource(R.string.detail_deposits),
            row.carryForwardOut.formatAmount() to stringResource(R.string.detail_carry_forward_out),
            row.lifetimeLimitLeft.formatAmount() to stringResource(R.string.detail_lifetime_limit_left),
        ),
    )
}

@Preview(showBackground = true)
@Composable
private fun FhsaDetailContentPreview() {
    FhsaDetailContent(
        state = FhsaDetailUiState(
            rows = listOf(
                FhsaYear(
                    year = 2026,
                    carryForwardIn = BigDecimal.ZERO,
                    yearRoom = BigDecimal("8000.00"),
                    deposits = BigDecimal("3000.00"),
                    withdrawals = BigDecimal.ZERO,
                    carryForwardOut = BigDecimal("5000.00"),
                    lifetimeLimitLeft = BigDecimal("37000.00"),
                ),
            ),
        ),
        onSeeTransactions = {},
    )
}
