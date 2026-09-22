package dev.celitracker.app.ui.detail

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.celitracker.app.R
import dev.celitracker.app.ui.components.WidthLimitedContent
import dev.celitracker.engine.Account
import dev.celitracker.engine.Transaction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DetailScaffold(title: String, onBack: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        WidthLimitedContent(modifier = Modifier.padding(innerPadding), content = content)
    }
}

internal fun List<Transaction>.yearsWith(account: Account): Set<Int> = filter { it.account == account }.map { it.date.year }.toSet()
