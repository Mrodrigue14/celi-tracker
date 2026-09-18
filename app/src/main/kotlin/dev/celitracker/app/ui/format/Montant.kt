package dev.celitracker.app.ui.format

import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

private val DOLLAR_CANADIEN: Currency = Currency.getInstance("CAD")

/**
 * La langue de l'appareil decide des separateurs et de la place du symbole,
 * jamais de la devise: un appareil en anglais americain afficherait sinon des
 * dollars americains, un chiffre faux et plausible pour un CELI.
 *
 * NumberFormat.format(Object) delegue a DecimalFormat.format(BigDecimal, ...)
 * pour ce type precis: le montant ne transite jamais par un Double, meme au
 * formatage.
 */
fun BigDecimal.formatMontant(locale: Locale = Locale.getDefault()): String = NumberFormat.getCurrencyInstance(locale).apply { currency = DOLLAR_CANADIEN }.format(this)

/** « 4 septembre 2026 » ou « September 4, 2026 »: une date se lit, elle ne se decode pas. */
fun LocalDate.formatDate(locale: Locale = Locale.getDefault()): String = format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale))
