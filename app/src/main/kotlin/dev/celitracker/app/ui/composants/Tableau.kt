package dev.celitracker.app.ui.composants

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.celitracker.app.R
import dev.celitracker.app.ui.theme.chiffres
import kotlin.math.roundToInt

/** Rayon unique des cartes et des tuiles, pour une forme coherente partout. */
val FORME_CARTE = RoundedCornerShape(20.dp)

val FORME_TUILE = RoundedCornerShape(14.dp)

/**
 * Anneau de progression des droits de l'annee. Au-dela de 100 %, il passe a
 * la couleur d'erreur: une sur-cotisation se voit avant de se lire.
 */
@Composable
fun AnneauDroits(fraction: Float, couleur: Color, modifier: Modifier = Modifier) {
    val cible = fraction.coerceIn(0f, 1f)
    // L'animation dit « voici ce qui a change » a l'ouverture de l'ecran.
    val affichee by animateFloatAsState(cible, animationSpec = tween(700), label = "anneau")
    val depasse = fraction > 1f
    val trait = if (depasse) MaterialTheme.colorScheme.error else couleur
    val piste = MaterialTheme.colorScheme.surfaceVariant
    val pourcentage = (fraction * 100).roundToInt()
    val description = stringResource(R.string.anneau_description, pourcentage)

    Box(
        modifier = modifier
            .size(84.dp)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(84.dp)) {
            val epaisseur = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round)
            drawArc(piste, startAngle = -90f, sweepAngle = 360f, useCenter = false, style = epaisseur)
            drawArc(trait, startAngle = -90f, sweepAngle = 360f * affichee, useCenter = false, style = epaisseur)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.anneau_pourcentage, pourcentage),
                style = MaterialTheme.typography.titleSmall.chiffres(),
                color = if (depasse) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Text(stringResource(R.string.anneau_utilise), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Pastille ronde teintee, qui porte une icone. */
@Composable
fun PastilleCompte(icone: ImageVector, fond: Color, teinte: Color, description: String? = null) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(fond, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icone, contentDescription = description, tint = teinte)
    }
}

/** Titre de section, dans la couleur d'accent de l'ecran. */
@Composable
fun TitreSection(texte: String, modifier: Modifier = Modifier, couleur: Color = MaterialTheme.colorScheme.primary) {
    Text(texte, modifier = modifier, style = MaterialTheme.typography.labelLarge, color = couleur)
}

/**
 * Une valeur secondaire de la carte: le chiffre d'abord, son etiquette dessous.
 * `fillMaxHeight` sur la Column: un appelant qui l'etire (GrilleTuiles, pour
 * qu'une etiquette sur deux lignes n'ecrase pas sa voisine) doit voir le fond
 * suivre, pas seulement le texte.
 */
@Composable
fun Tuile(valeur: String, etiquette: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainer, FORME_TUILE)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(valeur, style = MaterialTheme.typography.titleMedium.chiffres())
        Text(etiquette, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Grille de tuiles sur deux colonnes; une tuile seule sur sa ligne garde sa
 * demi-largeur. `IntrinsicSize.Min` sur chaque ligne: une etiquette qui
 * deborde sur deux lignes (une date longue en anglais, par exemple) agrandit
 * les DEUX tuiles de la ligne a la meme hauteur plutot que de rendre l'une
 * plus haute que l'autre.
 */
@Composable
fun GrilleTuiles(tuiles: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tuiles.chunked(2).forEach { ligne ->
            Row(modifier = Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ligne.forEach { (valeur, etiquette) ->
                    Tuile(valeur, etiquette, modifier = Modifier.weight(1f))
                }
                if (ligne.size == 1) Box(modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Au-dela, une tablette etirerait les listes et les formulaires jusqu'a nuire a leur lecture. */
val LARGEUR_MAX_CONTENU = 720.dp

/** Pour un ecran a deux colonnes (les deux comptes cote a cote): profiter de bien plus de largeur. */
val LARGEUR_MAX_CONTENU_DEUX_COLONNES = 1100.dp

/**
 * Plafonne la largeur du contenu principal d'un ecran et le centre. Sur un
 * telephone, la limite ne joue jamais (aucun telephone n'atteint 720 dp de
 * large). Sur une tablette, elle evite des lignes de texte interminables et
 * des tuiles etirees plutot que d'etoffer la mise en page.
 */
@Composable
fun ContenuLargeurLimitee(modifier: Modifier = Modifier, largeurMax: Dp = LARGEUR_MAX_CONTENU, contenu: @Composable BoxScope.() -> Unit) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(modifier = Modifier.widthIn(max = largeurMax).fillMaxSize(), content = contenu)
    }
}

/**
 * Bandeau d'alerte. [grave] a vrai prend la couleur d'erreur, reservee a ce qui
 * coute de l'argent; a faux, un simple avertissement sur fond neutre.
 */
@Composable
fun BandeauAlerte(texte: String, icone: ImageVector, modifier: Modifier = Modifier, grave: Boolean = true) {
    val fond = if (grave) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer
    val encre = if (grave) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(fond, FORME_TUILE)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icone, contentDescription = null, tint = encre)
        Text(texte, style = MaterialTheme.typography.bodyMedium, color = encre)
    }
}
