package dev.celitracker.app.ui.composants

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
import dev.celitracker.app.ui.format.formatMontant
import dev.celitracker.app.ui.theme.chiffres
import java.math.BigDecimal
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** Au-dela, une etiquette par barre deborderait: on n'en garde qu'une sur deux. */
private const val BARRES_TOUTES_ETIQUETEES = 8

private val HAUTEUR_GRAPHIQUE = 150.dp

private val LARGEUR_AXE = 44.dp

/** Place laissee au-dessus de la plus haute barre pour y ecrire sa valeur. */
private val MARGE_HAUTE = 20.dp

/**
 * Une barre par annee, la plus recente accentuee et chiffree. Trois lignes de
 * repere (zero, moitie, sommet) et leurs montants arrondis situent l'ordre de
 * grandeur sans charger le graphique: les montants exacts sont dans les cartes.
 *
 * Les montants ne deviennent des nombres a virgule que pour placer les barres
 * et les reperes, jamais pour un calcul de droits.
 */
@Composable
fun GraphiqueAnnees(
    valeurs: List<Pair<Int, BigDecimal>>,
    couleur: Color,
    modifier: Modifier = Modifier,
    onClicAnnee: ((Int) -> Unit)? = null,
) {
    if (valeurs.isEmpty()) return
    val sommet = sommetArrondi(valeurs.maxOf { it.second }.toDouble())
    val attenuee = couleur.copy(alpha = 0.35f)
    val ligne = MaterialTheme.colorScheme.outlineVariant
    val styleAxe = MaterialTheme.typography.labelSmall.chiffres().copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val styleValeur = MaterialTheme.typography.labelMedium.chiffres().copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
    val mesureur = rememberTextMeasurer()
    val locale = LocalConfiguration.current.locales[0]
    val compact = remember(locale) { CompactDecimalFormat.getInstance(locale, CompactDecimalFormat.CompactStyle.SHORT) }
    val derniere = valeurs.last().second
    val valeurDerniere = compact.format(derniere.toDouble())
    val pas = if (valeurs.size <= BARRES_TOUTES_ETIQUETEES) 1 else 2
    val resume = valeurs.map { (annee, montant) -> stringResource(R.string.graphique_barre, annee, montant.formatMontant()) }.joinToString()

    Column(modifier = modifier.semantics { contentDescription = resume }) {
        Row {
            // Axe: les montants des reperes, alignes sur leurs lignes.
            Canvas(modifier = Modifier.width(LARGEUR_AXE).height(HAUTEUR_GRAPHIQUE)) {
                val haut = MARGE_HAUTE.toPx()
                listOf(1.0, 0.5, 0.0).forEach { part ->
                    val y = haut + (size.height - haut) * (1 - part).toFloat()
                    val texte = mesureur.measure(compact.format(sommet * part), styleAxe)
                    drawText(texte, topLeft = Offset(0f, y - texte.size.height / 2f))
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                Canvas(modifier = Modifier.fillMaxWidth().height(HAUTEUR_GRAPHIQUE)) {
                    val haut = MARGE_HAUTE.toPx()
                    val zone = size.height - haut
                    val tirets = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
                    listOf(1.0, 0.5, 0.0).forEach { part ->
                        val y = haut + zone * (1 - part).toFloat()
                        drawLine(
                            color = ligne,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = if (part == 0.0) null else tirets,
                        )
                    }
                    val largeurCase = size.width / valeurs.size
                    val largeurBarre = largeurCase * 0.6f
                    valeurs.forEachIndexed { index, (_, montant) ->
                        val proportion = (montant.toDouble() / sommet).coerceIn(0.0, 1.0).toFloat()
                        val hauteur = (zone * proportion).coerceAtLeast(2.dp.toPx())
                        val gauche = index * largeurCase + (largeurCase - largeurBarre) / 2
                        drawRoundRect(
                            color = if (index == valeurs.lastIndex) couleur else attenuee,
                            topLeft = Offset(gauche, size.height - hauteur),
                            size = Size(largeurBarre, hauteur),
                            cornerRadius = CornerRadius(6.dp.toPx()),
                        )
                        if (index == valeurs.lastIndex) {
                            val texte = mesureur.measure(valeurDerniere, styleValeur)
                            drawText(
                                texte,
                                topLeft = Offset(
                                    (gauche + largeurBarre / 2 - texte.size.width / 2f).coerceIn(0f, size.width - texte.size.width),
                                    size.height - hauteur - texte.size.height - 2.dp.toPx(),
                                ),
                            )
                        }
                    }
                }
                // Une zone touchable par barre, sur toute la hauteur: une petite barre
                // doit rester aussi facile a toucher qu'une grande.
                if (onClicAnnee != null) {
                    Row(modifier = Modifier.matchParentSize()) {
                        valeurs.forEach { (annee, montant) ->
                            val allerA = stringResource(R.string.action_aller_a_annee, annee)
                            val description = stringResource(R.string.graphique_barre, annee, montant.formatMontant())
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable(onClickLabel = allerA) { onClicAnnee(annee) }
                                    .semantics { contentDescription = description },
                            )
                        }
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(start = LARGEUR_AXE, top = 6.dp)) {
            valeurs.forEachIndexed { index, (annee, _) ->
                val affichee = index % pas == (valeurs.lastIndex % pas)
                Text(
                    if (affichee) annee.toString() else "",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall.chiffres(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Sommet de l'axe: le plus haut montant arrondi vers le haut au palier suivant
 * d'une puissance de dix. Des paliers serres evitent un grand vide au-dessus des
 * barres, et chacun se divise en deux pour que le repere du milieu reste rond.
 */
private val PALIERS = listOf(1.0, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0)

internal fun sommetArrondi(maximum: Double): Double {
    if (maximum <= 0) return 1.0
    val puissance = 10.0.pow(floor(log10(maximum)))
    val facteur = PALIERS.first { it * puissance >= maximum }
    return facteur * puissance
}

/**
 * Une annee du detail: le resultat de l'annee en evidence, ce qui l'a produit
 * en tuiles dessous.
 */
@Composable
fun CarteAnnee(
    annee: Int,
    montant: String,
    libelleMontant: String,
    tuiles: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    enCours: Boolean = false,
    note: String? = null,
    /** `null` quand l'annee n'a aucune transaction: pas de lien vers une liste vide. */
    onVoirTransactions: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(FORME_CARTE)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (enCours) stringResource(R.string.detail_annee_en_cours, annee) else annee.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(libelleMontant, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(montant, style = MaterialTheme.typography.titleLarge.chiffres(), fontWeight = FontWeight.SemiBold)
        }
        GrilleTuiles(tuiles)
        note?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        onVoirTransactions?.let { voir ->
            TextButton(onClick = voir, modifier = Modifier.align(Alignment.End)) {
                Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.action_voir_transactions), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
