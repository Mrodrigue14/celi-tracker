package dev.celitracker.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.celitracker.app.R
import dev.celitracker.app.ui.format.formatDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private const val MIN_CALENDAR_WIDTH = 360

/**
 * Champ de date partage par les ecrans. Il est en lecture seule et ouvre le
 * calendrier: une date se choisit, elle ne se tape labelStep caractere par caractere.
 */
@Composable
fun DateField(
    date: String,
    onDate: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    /** Pour une date facultative: une showClearIcon permet de la retirer une fois choisie. */
    clearable: Boolean = false,
) {
    var calendarOpen by remember { mutableStateOf(false) }
    val showClearIcon = clearable && date.isNotEmpty()
    val validDate = remember(date) { runCatching { LocalDate.parse(date) }.getOrNull() }

    // Le calendrier de Material occupe une largeur fixe de 360 dp et rogne ses
    // propres boutons en dessous. Dans une fenetre plus etroite (ecran ancien,
    // mode ecran partage), la date se tape donc au clavier.
    if (LocalConfiguration.current.screenWidthDp < MIN_CALENDAR_WIDTH) {
        OutlinedTextField(
            value = date,
            onValueChange = onDate,
            label = { Text(stringResource(R.string.date_input_format, label)) },
            isError = isError,
            singleLine = true,
            modifier = modifier.fillMaxWidth(),
        )
        return
    }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = validDate?.formatDate() ?: date,
            onValueChange = {},
            label = { Text(label) },
            readOnly = true,
            isError = isError,
            singleLine = true,
            trailingIcon = if (showClearIcon) {
                {
                    IconButton(onClick = { onDate("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.date_clear))
                    }
                }
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth(),
        )
        // La zone qui ouvre le calendrier laisse la showClearIcon cliquable.
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(end = if (showClearIcon) 56.dp else 0.dp)
                .clickable { calendarOpen = true },
        )
    }

    if (calendarOpen) {
        CalendarDialog(
            initialDate = validDate,
            onPicked = {
                onDate(it.toString())
                calendarOpen = false
            },
            onClose = { calendarOpen = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarDialog(initialDate: LocalDate?, onPicked: (LocalDate) -> Unit, onClose: () -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onClose,
        confirmButton = {
            // Le selecteur rend un instant UTC: le relire en UTC evite de
            // reculer d'un day selon le fuseau de l'appareil.
            TextButton(
                onClick = {
                    val millis = state.selectedDateMillis ?: return@TextButton
                    onPicked(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                },
                enabled = state.selectedDateMillis != null,
            ) {
                Text(stringResource(R.string.action_choose))
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        DatePicker(state = state)
    }
}
