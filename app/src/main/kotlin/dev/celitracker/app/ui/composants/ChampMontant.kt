package dev.celitracker.app.ui.composants

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import dev.celitracker.app.ui.theme.chiffres
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Champ de montant partage: separateurs de milliers affiches en tapant, comme
 * dans un tableur, sans toucher a la valeur brute qui sert au calcul (elle
 * garde le separateur decimal tel que tape, `versMontantSaisi` accepte les deux).
 */
@Composable
fun ChampMontant(
    valeur: String,
    onValeur: (String) -> Unit,
    etiquette: String,
    modifier: Modifier = Modifier,
    estErreur: Boolean = false,
    style: TextStyle = LocalTextStyle.current,
) {
    val locale = LocalConfiguration.current.locales[0]
    OutlinedTextField(
        value = valeur,
        onValueChange = { onValeur(it.limiterADeuxDecimales()) },
        label = { Text(etiquette) },
        suffix = { Text("$") },
        isError = estErreur,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        textStyle = style.chiffres(),
        visualTransformation = remember(locale) { TransformationMontant(locale) },
        modifier = modifier,
    )
}

/** L'argent n'a que deux decimales: une troisieme frappee, ou toute frappee apres, est ignoree. */
internal fun String.limiterADeuxDecimales(): String {
    val indexSeparateur = indexOfFirst { it == ',' || it == '.' }
    if (indexSeparateur < 0) return this
    val partieDecimale = substring(indexSeparateur + 1).filter { it.isDigit() }.take(2)
    return substring(0, indexSeparateur + 1) + partieDecimale
}

/**
 * Regroupe une suite de chiffres par milliers en partant de la droite (« 1234567 »
 * -> « 1 234 567 »), et sait convertir une position de curseur d'un cote a
 * l'autre. Pure logique, sans dependance a Compose: c'est ce qui la rend
 * testable sans instrumentation.
 */
internal class RegroupeurMilliers(chiffres: String, separateur: Char) {
    /** Position, dans [texte], juste avant le chiffre d'origine numero i (0..chiffres.length). */
    private val positions = IntArray(chiffres.length + 1)
    val texte: String

    init {
        val regroupe = StringBuilder()
        chiffres.forEachIndexed { i, chiffre ->
            if (i > 0 && (chiffres.length - i) % 3 == 0) regroupe.append(separateur)
            positions[i] = regroupe.length
            regroupe.append(chiffre)
        }
        positions[chiffres.length] = regroupe.length
        texte = regroupe.toString()
    }

    fun versRegroupe(indexOriginal: Int): Int = positions[indexOriginal.coerceIn(0, positions.lastIndex)]

    /** Le curseur pose sur un separateur retombe sur le dernier chiffre deja tape. */
    fun versOriginal(indexRegroupe: Int): Int = positions.indexOfLast { it <= indexRegroupe }.coerceAtLeast(0)
}

/**
 * Regroupe la partie entiere par milliers. La partie decimale, avec son
 * separateur, suit sans y toucher: c'est elle qui porte les sous exacts.
 */
private class TransformationMontant(private val locale: Locale) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val original = text.text
        val indexSeparateur = original.indexOfFirst { it == ',' || it == '.' }
        val partieEntiere = if (indexSeparateur >= 0) original.substring(0, indexSeparateur) else original
        val partieDecimale = if (indexSeparateur >= 0) original.substring(indexSeparateur) else ""
        val separateurMilliers = DecimalFormatSymbols.getInstance(locale).groupingSeparator
        val regroupeur = RegroupeurMilliers(partieEntiere, separateurMilliers)

        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = if (offset <= partieEntiere.length) regroupeur.versRegroupe(offset) else regroupeur.texte.length + (offset - partieEntiere.length)

            override fun transformedToOriginal(offset: Int): Int = if (offset <= regroupeur.texte.length) regroupeur.versOriginal(offset) else partieEntiere.length + (offset - regroupeur.texte.length)
        }
        return TransformedText(AnnotatedString(regroupeur.texte + partieDecimale), mapping)
    }
}
