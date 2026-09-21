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

@Composable
fun DateField(
    date: String,
    onDate: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    clearable: Boolean = false,
) {
    var calendarOpen by remember { mutableStateOf(false) }
    val showClearIcon = clearable && date.isNotEmpty()
    val validDate = remember(date) { runCatching { LocalDate.parse(date) }.getOrNull() }

    // Material's calendar clips its buttons below 360 dp, so narrow windows fall back to typing.
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
        // Overlay because a read-only field swallows clicks; end padding keeps the clear icon reachable.
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
            // The picker returns a UTC instant: read it in UTC or the date shifts a day in some time zones.
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
