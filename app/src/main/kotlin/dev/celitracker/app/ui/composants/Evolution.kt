package dev.celitracker.app.ui.composants

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.celitracker.app.ui.format.formatMontant
import dev.celitracker.app.ui.theme.chiffres
import java.math.BigDecimal

/** Au-dela, une etiquette par barre deborderait: on n'en garde qu'une sur deux. */
private const val BARRES_TOUTES_ETIQUETEES = 8

/**
 * Une barre par annee, la plus recente accentuee. Les montants ne deviennent
 * des Float que pour la hauteur des barres, jamais pour un calcul.
 */
@Composable
fun GraphiqueAnnees(
    valeurs: List<Pair<Int, BigDecimal>>,
    couleur: Color,
    modifier: Modifier = Modifier,
    onClicAnnee: ((Int) -> Unit)? = null,
) {
    if (valeurs.isEmpty()) return
    val maximum = valeurs.maxOf { it.second }.takeIf { it.signum() > 0 } ?: BigDecimal.ONE
    val attenuee = couleur.copy(alpha = 0.35f)
    val pas = if (valeurs.size <= BARRES_TOUTES_ETIQUETEES) 1 else 2
    val resume = valeurs.joinToString { (annee, montant) -> "$annee : ${montant.formatMontant()}" }

    Column(modifier = modifier.semantics { contentDescription = resume }) {
        Box {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
            ) {
                val largeurCase = size.width / valeurs.size
                val largeurBarre = largeurCase * 0.6f
                valeurs.forEachIndexed { index, (_, montant) ->
                    val proportion = montant.toFloat() / maximum.toFloat()
                    val hauteur = (size.height * proportion.coerceIn(0f, 1f)).coerceAtLeast(2.dp.toPx())
                    drawRoundRect(
                        color = if (index == valeurs.lastIndex) couleur else attenuee,
                        topLeft = Offset(index * largeurCase + (largeurCase - largeurBarre) / 2, size.height - hauteur),
                        size = Size(largeurBarre, hauteur),
                        cornerRadius = CornerRadius(6.dp.toPx()),
                    )
                }
            }
            // Une zone touchable par barre, sur toute la hauteur: une petite barre
            // doit rester aussi facile a toucher qu'une grande.
            if (onClicAnnee != null) {
                Row(modifier = Modifier.matchParentSize()) {
                    valeurs.forEach { (annee, montant) ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(onClickLabel = "Aller à $annee") { onClicAnnee(annee) }
                                .semantics { contentDescription = "$annee : ${montant.formatMontant()}" },
                        )
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
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
                    if (enCours) "$annee, en cours" else annee.toString(),
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
                Text("Voir les transactions", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
