package dev.celitracker.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/*
 * Palette de l'application. Le bleu (confiance) porte le CELI et l'identite;
 * le vert-bleu porte le CELIAPP, pour que chaque compte se reconnaisse a sa
 * couleur. Le rouge est reserve aux alertes: l'utiliser ailleurs lui ferait
 * perdre son sens.
 *
 * En sombre, la hauteur d'une surface se lit a sa clarte, pas a son ombre,
 * qui disparait sur un fond presque noir.
 */

private val Clair = lightColorScheme(
    primary = Color(0xFF1E4FB8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE6FF),
    onPrimaryContainer = Color(0xFF0A2A6B),
    secondary = Color(0xFF00796B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCDEDE8),
    onSecondaryContainer = Color(0xFF00382F),
    tertiary = Color(0xFF6B4FA0),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEADDFF),
    onTertiaryContainer = Color(0xFF26104F),
    error = Color(0xFFC62828),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFDE2E1),
    onErrorContainer = Color(0xFF5F0B0B),
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF151A23),
    surface = Color(0xFFF6F7FB),
    onSurface = Color(0xFF151A23),
    surfaceVariant = Color(0xFFE3E7F0),
    onSurfaceVariant = Color(0xFF4A5263),
    outline = Color(0xFF7A8295),
    outlineVariant = Color(0xFFC9CFDB),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFEFF1F7),
    surfaceContainerHigh = Color(0xFFE9ECF3),
    surfaceContainerHighest = Color(0xFFE3E7F0),
)

private val Sombre = darkColorScheme(
    primary = Color(0xFFA9C2FF),
    onPrimary = Color(0xFF0A2A6B),
    primaryContainer = Color(0xFF1C3A80),
    onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = Color(0xFF7FD8C8),
    onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF005146),
    onSecondaryContainer = Color(0xFFCDEDE8),
    tertiary = Color(0xFFD2BCFF),
    onTertiary = Color(0xFF3A2466),
    tertiaryContainer = Color(0xFF52397F),
    onTertiaryContainer = Color(0xFFEADDFF),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF5F0B0B),
    errorContainer = Color(0xFF7A1C1C),
    onErrorContainer = Color(0xFFFDE2E1),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE4E7EE),
    surface = Color(0xFF0E1116),
    onSurface = Color(0xFFE4E7EE),
    surfaceVariant = Color(0xFF2A303C),
    onSurfaceVariant = Color(0xFFB4BBC9),
    outline = Color(0xFF7E8697),
    outlineVariant = Color(0xFF3A4150),
    surfaceContainerLowest = Color(0xFF0A0C10),
    surfaceContainerLow = Color(0xFF151A22),
    surfaceContainer = Color(0xFF191E27),
    surfaceContainerHigh = Color(0xFF1F2530),
    surfaceContainerHighest = Color(0xFF262C38),
)

/** Suit le theme du telephone, clair ou sombre, sans reglage dans l'application. */
@Composable
fun CeliTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Sombre else Clair, content = content)
}

/**
 * Chiffres tabulaires de la police normale: les montants s'alignent d'une
 * ligne a l'autre sans l'aspect machine a ecrire d'une police a chasse fixe.
 */
fun TextStyle.chiffres(): TextStyle = copy(fontFeatureSettings = "tnum")
