package dev.celitracker.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.celitracker.app.R
import kotlinx.coroutines.launch
import java.math.BigDecimal

private const val CHART_PANE_SHARE = 0.42f

private const val YEARS_TITLE_ITEM_COUNT = 1

@Composable
fun <T> YearDetailList(
    rows: List<T>,
    year: (T) -> Int,
    chartTitle: String,
    chartValue: (T) -> BigDecimal,
    color: Color,
    modifier: Modifier = Modifier,
    card: @Composable (row: T, inProgress: Boolean) -> Unit,
) {
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val yearInProgress = rows.lastOrNull()?.let(year)
    val displayed = rows.reversed()
    val twoPanes = isWideScreen() && rows.isNotEmpty()
    val chartItemCount = if (twoPanes) 0 else 1
    val itemsBeforeYears = chartItemCount + YEARS_TITLE_ITEM_COUNT

    fun goTo(target: Int) {
        val position = displayed.indexOfFirst { year(it) == target }
        if (position >= 0) scope.launch { list.animateScrollToItem(itemsBeforeYears + position) }
    }

    val chart: @Composable (Modifier) -> Unit = { chartModifier ->
        Column(modifier = chartModifier) {
            SectionTitle(chartTitle, Modifier.padding(top = 12.dp), color)
            YearChart(
                values = rows.map { year(it) to chartValue(it) },
                color = color,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                onYearClick = ::goTo,
            )
        }
    }

    val years: @Composable (Modifier) -> Unit = { yearsModifier ->
        LazyColumn(
            modifier = yearsModifier,
            state = list,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (rows.isNotEmpty()) {
                if (!twoPanes) item(key = "chart") { chart(Modifier) }
                item(key = "years-title") {
                    SectionTitle(stringResource(R.string.detail_year_by_year), Modifier.padding(top = 12.dp), color)
                }
            }
            items(displayed, key = { year(it) }) { row ->
                card(row, year(row) == yearInProgress)
            }
        }
    }

    if (twoPanes) {
        TwoPanes(
            left = { chart(it.padding(horizontal = 16.dp)) },
            right = years,
            modifier = modifier,
            leftShare = CHART_PANE_SHARE,
        )
    } else {
        years(modifier)
    }
}
