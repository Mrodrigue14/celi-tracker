package dev.celitracker.app.ui.components

import android.icu.text.CompactDecimalFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.celitracker.app.R
import dev.celitracker.app.ui.format.formatAmount
import dev.celitracker.app.ui.theme.tabularFigures
import java.math.BigDecimal
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** Au-dela, une label par barre deborderait: on n'en garde qu'une sur deux. */
private const val MAX_BARS_ALL_LABELLED = 8

private val CHART_HEIGHT = 150.dp

private val AXIS_WIDTH = 44.dp

/** Place laissee au-dessus de la plus haute barre pour y write sa value. */
private val TOP_MARGIN = 20.dp

/**
 * Une barre par year, la plus recente accentuee et chiffree. Trois rows de
 * repere (zero, moitie, axisMax) et leurs montants arrondis situent l'ordre de
 * grandeur sans load le chart: les montants exacts sont dans les cartes.
 *
 * Les montants ne deviennent des nombres a virgule que pour placer les barres
 * et les reperes, jamais pour un calcul de room.
 */
@Composable
fun YearChart(
    values: List<Pair<Int, BigDecimal>>,
    color: Color,
    modifier: Modifier = Modifier,
    onYearClick: ((Int) -> Unit)? = null,
) {
    if (values.isEmpty()) return
    val axisMax = roundedAxisMax(values.maxOf { it.second }.toDouble())
    val dimmedColor = color.copy(alpha = 0.35f)
    val row = MaterialTheme.colorScheme.outlineVariant
    val axisStyle = MaterialTheme.typography.labelSmall.tabularFigures().copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val valueStyle = MaterialTheme.typography.labelMedium.tabularFigures().copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
    val measurer = rememberTextMeasurer()
    val locale = LocalConfiguration.current.locales[0]
    val compact = remember(locale) { CompactDecimalFormat.getInstance(locale, CompactDecimalFormat.CompactStyle.SHORT) }
    val latest = values.last().second
    val latestLabel = compact.format(latest.toDouble())
    val labelStep = if (values.size <= MAX_BARS_ALL_LABELLED) 1 else 2
    val summary = values.map { (year, amount) -> stringResource(R.string.chart_bar, year, amount.formatAmount()) }.joinToString()

    Column(modifier = modifier.semantics { contentDescription = summary }) {
        Row {
            // Axe: les montants des reperes, alignes sur leurs rows.
            Canvas(modifier = Modifier.width(AXIS_WIDTH).height(CHART_HEIGHT)) {
                val top = TOP_MARGIN.toPx()
                listOf(1.0, 0.5, 0.0).forEach { part ->
                    val y = top + (size.height - top) * (1 - part).toFloat()
                    val text = measurer.measure(compact.format(axisMax * part), axisStyle)
                    drawText(text, topLeft = Offset(0f, y - text.size.height / 2f))
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                Canvas(modifier = Modifier.fillMaxWidth().height(CHART_HEIGHT)) {
                    val top = TOP_MARGIN.toPx()
                    val zone = size.height - top
                    val dashes = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
                    listOf(1.0, 0.5, 0.0).forEach { part ->
                        val y = top + zone * (1 - part).toFloat()
                        drawLine(
                            color = row,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = if (part == 0.0) null else dashes,
                        )
                    }
                    val slotWidth = size.width / values.size
                    val barWidth = slotWidth * 0.6f
                    values.forEachIndexed { index, (_, amount) ->
                        val proportion = (amount.toDouble() / axisMax).coerceIn(0.0, 1.0).toFloat()
                        val barHeight = (zone * proportion).coerceAtLeast(2.dp.toPx())
                        val left = index * slotWidth + (slotWidth - barWidth) / 2
                        drawRoundRect(
                            color = if (index == values.lastIndex) color else dimmedColor,
                            topLeft = Offset(left, size.height - barHeight),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(6.dp.toPx()),
                        )
                        if (index == values.lastIndex) {
                            val text = measurer.measure(latestLabel, valueStyle)
                            drawText(
                                text,
                                topLeft = Offset(
                                    (left + barWidth / 2 - text.size.width / 2f).coerceIn(0f, size.width - text.size.width),
                                    size.height - barHeight - text.size.height - 2.dp.toPx(),
                                ),
                            )
                        }
                    }
                }
                // Une zone touchable par barre, sur toute la barHeight: une petite barre
                // doit rester aussi facile a toucher qu'une grande.
                if (onYearClick != null) {
                    Row(modifier = Modifier.matchParentSize()) {
                        values.forEach { (year, amount) ->
                            val goTo = stringResource(R.string.action_go_to_year, year)
                            val description = stringResource(R.string.chart_bar, year, amount.formatAmount())
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable(onClickLabel = goTo) { onYearClick(year) }
                                    .semantics { contentDescription = description },
                            )
                        }
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(start = AXIS_WIDTH, top = 6.dp)) {
            values.forEachIndexed { index, (year, _) ->
                val labelled = index % labelStep == (values.lastIndex % labelStep)
                Text(
                    if (labelled) year.toString() else "",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall.tabularFigures(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Sommet de l'axe: le plus top amount arrondi vers le top au palier suivant
 * d'une power de dix. Des paliers serres evitent un grand vide au-dessus des
 * barres, et chacun se divise en deux pour que le repere du milieu reste rond.
 */
private val STEPS = listOf(1.0, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0)

internal fun roundedAxisMax(maximum: Double): Double {
    if (maximum <= 0) return 1.0
    val power = 10.0.pow(floor(log10(maximum)))
    val factor = STEPS.first { it * power >= maximum }
    return factor * power
}

/**
 * Une year du detail: le result de l'year en evidence, ce qui l'a produit
 * en tiles dessous.
 */
@Composable
fun YearCard(
    year: Int,
    amount: String,
    amountLabel: String,
    tiles: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    inProgress: Boolean = false,
    note: String? = null,
    /** `null` quand l'year n'a aucune transaction: labelStep de lien vers une list vide. */
    onSeeTransactions: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CARD_SHAPE)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (inProgress) stringResource(R.string.detail_year_in_progress, year) else year.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(amountLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(amount, style = MaterialTheme.typography.titleLarge.tabularFigures(), fontWeight = FontWeight.SemiBold)
        }
        TileGrid(tiles)
        note?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        onSeeTransactions?.let { see ->
            TextButton(onClick = see, modifier = Modifier.align(Alignment.End)) {
                Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.action_see_transactions), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
