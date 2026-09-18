package dev.celitracker.app.ui.composants

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
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private const val LARGEUR_MINIMALE_CALENDRIER = 360

/**
 * Champ de date partage par les ecrans. Il est en lecture seule et ouvre le
 * calendrier: une date se choisit, elle ne se tape pas caractere par caractere.
 */
@Composable
fun ChampDate(
    date: String,
    onDate: (String) -> Unit,
    etiquette: String,
    modifier: Modifier = Modifier,
    estErreur: Boolean = false,
    /** Pour une date facultative: une croix permet de la retirer une fois choisie. */
    effacable: Boolean = false,
) {
    var calendrierOuvert by remember { mutableStateOf(false) }
    val croix = effacable && date.isNotEmpty()
    val dateValide = remember(date) { runCatching { LocalDate.parse(date) }.getOrNull() }

    // Le calendrier de Material occupe une largeur fixe de 360 dp et rogne ses
    // propres boutons en dessous. Dans une fenetre plus etroite (ecran ancien,
    // mode ecran partage), la date se tape donc au clavier.
    if (LocalConfiguration.current.screenWidthDp < LARGEUR_MINIMALE_CALENDRIER) {
        OutlinedTextField(
            value = date,
            onValueChange = onDate,
            label = { Text("$etiquette (AAAA-MM-JJ)") },
            isError = estErreur,
            singleLine = true,
            modifier = modifier.fillMaxWidth(),
        )
        return
    }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = date,
            onValueChange = {},
            label = { Text(etiquette) },
            readOnly = true,
            isError = estErreur,
            singleLine = true,
            trailingIcon = if (croix) {
                {
                    IconButton(onClick = { onDate("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Retirer la date")
                    }
                }
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth(),
        )
        // La zone qui ouvre le calendrier laisse la croix cliquable.
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(end = if (croix) 56.dp else 0.dp)
                .clickable { calendrierOuvert = true },
        )
    }

    if (calendrierOuvert) {
        Calendrier(
            dateInitiale = dateValide,
            onChoisie = {
                onDate(it.toString())
                calendrierOuvert = false
            },
            onFermer = { calendrierOuvert = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Calendrier(dateInitiale: LocalDate?, onChoisie: (LocalDate) -> Unit, onFermer: () -> Unit) {
    val etat = rememberDatePickerState(
        initialSelectedDateMillis = dateInitiale?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onFermer,
        confirmButton = {
            // Le selecteur rend un instant UTC: le relire en UTC evite de
            // reculer d'un jour selon le fuseau de l'appareil.
            TextButton(
                onClick = {
                    val millis = etat.selectedDateMillis ?: return@TextButton
                    onChoisie(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                },
                enabled = etat.selectedDateMillis != null,
            ) {
                Text("Choisir")
            }
        },
        dismissButton = { TextButton(onClick = onFermer) { Text("Annuler") } },
    ) {
        DatePicker(state = etat)
    }
}
