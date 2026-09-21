package dev.celitracker.app.ui.components

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
import dev.celitracker.app.ui.theme.tabularFigures
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Groups thousands visually only: the raw value keeps the decimal separator as typed. */
@Composable
fun AmountField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    style: TextStyle = LocalTextStyle.current,
) {
    val locale = LocalConfiguration.current.locales[0]
    OutlinedTextField(
        value = value,
        onValueChange = { onValue(it.limitToTwoDecimals()) },
        label = { Text(label) },
        suffix = { Text("$") },
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        textStyle = style.tabularFigures(),
        visualTransformation = remember(locale) { AmountTransformation(locale) },
        modifier = modifier,
    )
}

internal fun String.limitToTwoDecimals(): String {
    val separatorIndex = indexOfFirst { it == ',' || it == '.' }
    if (separatorIndex < 0) return this
    val decimalPart = substring(separatorIndex + 1).filter { it.isDigit() }.take(2)
    return substring(0, separatorIndex + 1) + decimalPart
}

internal class ThousandsGrouper(digits: String, separator: Char) {
    /** positions[i] is the index in [text] just before original digit i. */
    private val positions = IntArray(digits.length + 1)
    val text: String

    init {
        val grouped = StringBuilder()
        digits.forEachIndexed { i, digit ->
            if (i > 0 && (digits.length - i) % 3 == 0) grouped.append(separator)
            positions[i] = grouped.length
            grouped.append(digit)
        }
        positions[digits.length] = grouped.length
        text = grouped.toString()
    }

    fun toGrouped(originalIndex: Int): Int = positions[originalIndex.coerceIn(0, positions.lastIndex)]

    /** A cursor on a separator maps to the digit before it. */
    fun toOriginal(groupedIndex: Int): Int = positions.indexOfLast { it <= groupedIndex }.coerceAtLeast(0)
}

private class AmountTransformation(private val locale: Locale) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val original = text.text
        val separatorIndex = original.indexOfFirst { it == ',' || it == '.' }
        val integerPart = if (separatorIndex >= 0) original.substring(0, separatorIndex) else original
        val decimalPart = if (separatorIndex >= 0) original.substring(separatorIndex) else ""
        val thousandsSeparator = DecimalFormatSymbols.getInstance(locale).groupingSeparator
        val grouper = ThousandsGrouper(integerPart, thousandsSeparator)

        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = if (offset <= integerPart.length) grouper.toGrouped(offset) else grouper.text.length + (offset - integerPart.length)

            override fun transformedToOriginal(offset: Int): Int = if (offset <= grouper.text.length) grouper.toOriginal(offset) else integerPart.length + (offset - grouper.text.length)
        }
        return TransformedText(AnnotatedString(grouper.text + decimalPart), mapping)
    }
}
