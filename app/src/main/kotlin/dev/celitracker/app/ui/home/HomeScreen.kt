package dev.celitracker.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.celitracker.app.CeliTrackerApplication
import dev.celitracker.app.R
import dev.celitracker.app.ui.components.AlertBanner
import dev.celitracker.app.ui.components.CARD_SHAPE
import dev.celitracker.app.ui.components.EmptyState
import dev.celitracker.app.ui.components.IconBadge
import dev.celitracker.app.ui.components.RoomRing
import dev.celitracker.app.ui.components.TileGrid
import dev.celitracker.app.ui.components.WidthLimitedContent
import dev.celitracker.app.ui.components.isWideScreen
import dev.celitracker.app.ui.format.formatAmount
import dev.celitracker.app.ui.format.formatDate
import dev.celitracker.app.ui.theme.tabularFigures
import dev.celitracker.engine.Account
import dev.celitracker.engine.FhsaYear
import dev.celitracker.engine.MonthlyExcess
import dev.celitracker.engine.Profile
import dev.celitracker.engine.TfsaYear
import dev.celitracker.engine.Usage
import dev.celitracker.engine.UsageLevel
import java.math.BigDecimal
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenDetail: (Account) -> Unit,
    onAdd: (Account) -> Unit,
    onOpenJournal: (Account) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val application = LocalContext.current.applicationContext as CeliTrackerApplication
    val viewModel: HomeViewModel = viewModel(factory = application.viewModelFactory)
    LaunchedEffect(Unit) { viewModel.load() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.app_name)) })
        },
    ) { innerPadding ->
        WidthLimitedContent(modifier = Modifier.padding(innerPadding)) {
            HomeContent(
                state = state,
                onOpenDetail = onOpenDetail,
                onAdd = onAdd,
                onOpenJournal = onOpenJournal,
                onOpenSettings = onOpenSettings,
            )
        }
    }
}

/**
 * Pur: recoit l'state et des lambdas, jamais le ViewModel. Previewable sans
 * dependance a Android.
 */
@Composable
fun HomeContent(
    state: HomeUiState,
    onOpenDetail: (Account) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onAdd: (Account) -> Unit,
    onOpenJournal: (Account) -> Unit,
) {
    if (!state.loaded) return
    if (!state.hasProfile) {
        EmptyState(
            icon = Icons.Filled.Person,
            title = stringResource(R.string.home_no_profile_title),
            text = stringResource(R.string.home_no_profile_text),
            actionLabel = stringResource(R.string.home_no_profile_action),
            onAction = onOpenSettings,
            backgroundColor = MaterialTheme.colorScheme.primaryContainer,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = modifier,
        )
        return
    }

    val tfsaCard: @Composable (Modifier) -> Unit = { cardModifier ->
        AccountCard(
            modifier = cardModifier,
            name = stringResource(R.string.account_tfsa),
            icon = Icons.Filled.Savings,
            color = MaterialTheme.colorScheme.primary,
            container = MaterialTheme.colorScheme.primaryContainer,
            onContainer = MaterialTheme.colorScheme.onPrimaryContainer,
            remainingRoom = state.tfsaCurrentYear?.endRoom,
            fraction = state.tfsaUsedFraction,
            tiles = listOfNotNull(
                state.tfsaCurrentYear?.let { it.deposits.formatAmount() to stringResource(R.string.home_contributed_in, state.currentYear) },
                state.tfsaCurrentYear?.let { it.limit.formatAmount() to stringResource(R.string.home_year_limit, state.currentYear) },
            ),
            onClick = { onOpenDetail(Account.TFSA) },
            onAdd = { onAdd(Account.TFSA) },
            onOpenJournal = { onOpenJournal(Account.TFSA) },
        ) {
            if (state.tfsaCurrentYear?.limitMissing == true) {
                AlertBanner(stringResource(R.string.alert_limit_unconfirmed), Icons.Filled.Info)
            }
            // La penalty calculee au month pres dit deja whole d'une sur-cotisation:
            // le bandeau d'usage ne s'ajoute que s'il n'y en a labelStep.
            val excess = state.currentTfsaExcess
            if (excess != null) {
                AlertBanner(stringResource(R.string.alert_overcontribution, excess.penalty.formatAmount()), Icons.Filled.Warning)
            } else {
                UsageAlert(state.tfsaUsage, state.currentYear)
            }
        }
    }

    val fhsaCard: @Composable (Modifier) -> Unit = { cardModifier ->
        if (state.profile?.fhsaOpeningDate == null) {
            NoFhsa(onOpenSettings, modifier = cardModifier)
        } else {
            AccountCard(
                modifier = cardModifier,
                name = stringResource(R.string.account_fhsa),
                icon = Icons.Filled.Home,
                color = MaterialTheme.colorScheme.secondary,
                container = MaterialTheme.colorScheme.secondaryContainer,
                onContainer = MaterialTheme.colorScheme.onSecondaryContainer,
                remainingRoom = state.fhsaRemainingRoom,
                fraction = state.fhsaUsedFraction,
                tiles = listOfNotNull(
                    state.fhsaCurrentYear?.let { it.deposits.formatAmount() to stringResource(R.string.home_contributed_in, state.currentYear) },
                    state.fhsaCurrentYear?.let { it.lifetimeLimitLeft.formatAmount() to stringResource(R.string.home_lifetime_limit_left) },
                    state.fhsaCurrentYear?.let { it.carryForwardIn.formatAmount() to stringResource(R.string.home_carry_forward_received) },
                    state.fhsaParticipationDeadline?.let { it.formatDate() to stringResource(R.string.home_deadline) },
                ),
                onClick = { onOpenDetail(Account.FHSA) },
                onAdd = { onAdd(Account.FHSA) },
                onOpenJournal = { onOpenJournal(Account.FHSA) },
            ) {
                UsageAlert(state.fhsaUsage, state.currentYear)
            }
        }
    }

    // Sur un ecran large, les deux comptes cote a cote profitent de la largeur
    // plutot que d'empiler deux cartes etroites au-dessus d'un grand vide.
    if (isWideScreen()) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp)
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            tfsaCard(Modifier.weight(1f).fillMaxHeight())
            fhsaCard(Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            tfsaCard(Modifier.fillMaxWidth())
            fhsaCard(Modifier.fillMaxWidth())
        }
    }
}

/** 80 % previent sur backgroundColor neutre; 95 % et plus passe a la color d'error. */
@Composable
private fun UsageAlert(usage: Usage?, year: Int) {
    val u = usage ?: return
    when (u.level) {
        UsageLevel.NORMAL -> Unit

        UsageLevel.WARNING -> AlertBanner(
            stringResource(R.string.alert_usage_warning, u.percent ?: 0, year, u.remaining.formatAmount()),
            Icons.Filled.Info,
            severe = false,
        )

        UsageLevel.CRITICAL -> AlertBanner(
            stringResource(R.string.alert_usage_critical, u.percent ?: 0, year, u.remaining.formatAmount()),
            Icons.Filled.Warning,
        )

        UsageLevel.EXCEEDED -> AlertBanner(
            stringResource(R.string.alert_usage_exceeded, year, u.excess.formatAmount()),
            Icons.Filled.Warning,
        )
    }
}

/** Pas de account, labelStep de card pleine de dashes: une invitation a l'add. */
@Composable
private fun NoFhsa(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CARD_SHAPE)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onOpenSettings)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBadge(
            icon = Icons.Filled.Home,
            backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.account_fhsa), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.home_no_fhsa),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = stringResource(R.string.home_open_settings),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Une card par account: bande de sa color en top, le amount qui account en
 * grand, l'anneau d'usage a cote, les values secondaires en tiles.
 * [alerts] vient en dernier: elle n'apparait que s'il y a quelque chose a dire.
 */
@Composable
private fun AccountCard(
    name: String,
    icon: ImageVector,
    color: Color,
    container: Color,
    onContainer: Color,
    remainingRoom: BigDecimal?,
    fraction: Float?,
    tiles: List<Pair<String, String>>,
    onClick: () -> Unit,
    onAdd: () -> Unit,
    onOpenJournal: () -> Unit,
    modifier: Modifier = Modifier,
    alerts: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CARD_SHAPE)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(color),
        )
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBadge(icon = icon, backgroundColor = container, tint = onContainer)
                Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.action_see_detail),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        (remainingRoom ?: BigDecimal.ZERO).formatAmount(),
                        style = MaterialTheme.typography.headlineMedium.tabularFigures(),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(stringResource(R.string.home_room_left), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (fraction != null) RoomRing(fraction = fraction, color = color)
            }
            TileGrid(tiles)
            alerts()
            // Les deux gestes les plus frequents, visibles sur la card plutot
            // que caches derriere l'ecran de detail.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(
                    onClick = onAdd,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = container, contentColor = onContainer),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.action_add), modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(onClick = onOpenJournal, modifier = Modifier.weight(1f)) {
                    Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.action_journal), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeContentPreview() {
    HomeContent(
        state = HomeUiState(
            profile = Profile(1995, LocalDate.of(2023, 4, 1)),
            currentYear = 2026,
            currentMonth = 9,
            tfsaRoom = listOf(
                TfsaYear(
                    year = 2026,
                    limit = BigDecimal("7000.00"),
                    startRoom = BigDecimal("12000.00"),
                    deposits = BigDecimal("3000.00"),
                    withdrawals = BigDecimal.ZERO,
                    endRoom = BigDecimal("9000.00"),
                    limitMissing = false,
                ),
            ),
            fhsaRoom = listOf(
                FhsaYear(
                    year = 2026,
                    carryForwardIn = BigDecimal("1000.00"),
                    yearRoom = BigDecimal("8000.00"),
                    deposits = BigDecimal("4000.00"),
                    withdrawals = BigDecimal.ZERO,
                    carryForwardOut = BigDecimal("4000.00"),
                    lifetimeLimitLeft = BigDecimal("28000.00"),
                ),
            ),
        ),
        onOpenDetail = {},
        onOpenSettings = {},
        onAdd = {},
        onOpenJournal = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun HomeContentEmptyPreview() {
    HomeContent(
        state = HomeUiState(profile = null, currentYear = 2026, currentMonth = 9),
        onOpenDetail = {},
        onOpenSettings = {},
        onAdd = {},
        onOpenJournal = {},
    )
}
