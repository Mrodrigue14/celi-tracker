package dev.celitracker.app.ui.detail

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.celitracker.app.R
import dev.celitracker.app.ui.appViewModel
import dev.celitracker.app.ui.components.YearCard
import dev.celitracker.app.ui.components.YearDetailList
import dev.celitracker.app.ui.format.formatAmount
import dev.celitracker.app.ui.theme.colors
import dev.celitracker.engine.Account
import dev.celitracker.engine.FhsaYear
import java.math.BigDecimal

@Composable
fun FhsaDetailScreen(onBack: () -> Unit, onSeeTransactions: (Int) -> Unit) {
    val viewModel: FhsaDetailViewModel = appViewModel()
    LaunchedEffect(Unit) { viewModel.load() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    DetailScaffold(title = stringResource(R.string.detail_title_fhsa), onBack = onBack) {
        FhsaDetailContent(state = state, onSeeTransactions = onSeeTransactions, modifier = Modifier.fillMaxSize())
    }
}

@Composable
fun FhsaDetailContent(state: FhsaDetailUiState, onSeeTransactions: (Int) -> Unit, modifier: Modifier = Modifier) {
    YearDetailList(
        rows = state.rows,
        year = { it.year },
        chartTitle = stringResource(R.string.detail_lifetime_limit_left),
        chartValue = { it.lifetimeLimitLeft },
        color = Account.FHSA.colors().accent,
        yearsWithTransactions = state.yearsWithTransactions,
        onSeeTransactions = onSeeTransactions,
        modifier = modifier,
    ) { row, inProgress, onSeeYearTransactions ->
        FhsaYearCard(row, inProgress = inProgress, onSeeTransactions = onSeeYearTransactions)
    }
}

@Composable
private fun FhsaYearCard(row: FhsaYear, inProgress: Boolean, onSeeTransactions: (() -> Unit)?) {
    YearCard(
        year = row.year,
        amount = row.remainingRoom.formatAmount(),
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
