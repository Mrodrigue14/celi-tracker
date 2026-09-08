package dev.celitracker.app.ui.format

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/**
 * NumberFormat.format(Object) delegue a DecimalFormat.format(BigDecimal, ...)
 * pour ce type precis: le montant ne transite jamais par un Double, meme au
 * formatage.
 */
fun BigDecimal.formatMontant(): String =
    NumberFormat.getCurrencyInstance(Locale.CANADA_FRENCH).format(this)
