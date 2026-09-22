package dev.celitracker.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.celitracker.app.R
import dev.celitracker.app.ui.theme.tabularFigures
import kotlin.math.roundToInt

val CARD_SHAPE = RoundedCornerShape(20.dp)

val TILE_SHAPE = RoundedCornerShape(14.dp)

/** Turns to the error color past 100%: an over-contribution must show at a glance. */
@Composable
fun RoomRing(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val target = fraction.coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(target, animationSpec = tween(700), label = "ring")
    val exceeded = fraction > 1f
    val stroke = if (exceeded) MaterialTheme.colorScheme.error else color
    val track = MaterialTheme.colorScheme.surfaceVariant
    val percent = (fraction * 100).roundToInt()
    val description = stringResource(R.string.ring_description, percent)

    Box(
        modifier = modifier
            .size(84.dp)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(84.dp)) {
            val thickness = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round)
            drawArc(track, startAngle = -90f, sweepAngle = 360f, useCenter = false, style = thickness)
            drawArc(stroke, startAngle = -90f, sweepAngle = 360f * animatedFraction, useCenter = false, style = thickness)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.ring_percent, percent),
                style = MaterialTheme.typography.titleSmall.tabularFigures(),
                color = if (exceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Text(stringResource(R.string.ring_used), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun IconBadge(icon: ImageVector, backgroundColor: Color, tint: Color, description: String? = null) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(backgroundColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.labelLarge, color = color)
}

/** `fillMaxHeight` keeps the background full height when a taller neighbor stretches the tile. */
@Composable
fun Tile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainer, TILE_SHAPE)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium.tabularFigures())
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** `IntrinsicSize.Min` so a label wrapping to two lines grows both tiles of its row to the same height. */
@Composable
fun TileGrid(tiles: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(modifier = Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (value, label) ->
                    Tile(value, label, modifier = Modifier.weight(1f))
                }
                if (row.size == 1) Box(modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Material 3 "expanded" width class: below it, two panes would each be narrower than a phone. */
private const val TWO_PANE_THRESHOLD_DP = 840

@Composable
fun isWideScreen(): Boolean = LocalConfiguration.current.screenWidthDp >= TWO_PANE_THRESHOLD_DP

private val MAX_WIDTH_ONE_PANE = 720.dp

private val MAX_WIDTH_TWO_PANES = 1100.dp

@Composable
fun WidthLimitedContent(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val maxWidth = if (isWideScreen()) MAX_WIDTH_TWO_PANES else MAX_WIDTH_ONE_PANE
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(modifier = Modifier.widthIn(max = maxWidth).fillMaxSize(), content = content)
    }
}

@Composable
fun TwoPanes(
    left: @Composable (Modifier) -> Unit,
    right: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    leftShare: Float = 0.5f,
) {
    Row(modifier = modifier.fillMaxSize()) {
        left(Modifier.weight(leftShare))
        VerticalDivider()
        right(Modifier.weight(1f - leftShare))
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBadge(icon = icon, backgroundColor = backgroundColor, tint = tint)
        title?.let { Text(it, style = MaterialTheme.typography.titleLarge) }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        actionLabel?.let { Button(onClick = onAction, modifier = Modifier.padding(top = 12.dp)) { Text(it) } }
    }
}

@Composable
fun <T> SegmentedChoice(
    options: List<T>,
    selection: T,
    onChoose: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    activeColor: @Composable (T) -> Color = { MaterialTheme.colorScheme.secondaryContainer },
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selection,
                onClick = { if (option != selection) onChoose(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                colors = SegmentedButtonDefaults.colors(activeContainerColor = activeColor(option)),
            ) {
                Text(label(option))
            }
        }
    }
}

/** The error color is reserved for what costs money; anything else is a [NoticeBanner]. */
@Composable
fun AlertBanner(text: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Banner(text, icon, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, modifier)
}

@Composable
fun NoticeBanner(text: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Banner(text, icon, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, modifier)
}

@Composable
private fun Banner(text: String, icon: ImageVector, backgroundColor: Color, ink: Color, modifier: Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor, TILE_SHAPE)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = ink)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = ink)
    }
}
