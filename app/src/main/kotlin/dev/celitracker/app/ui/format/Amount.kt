package dev.celitracker.app.ui.format

import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

private val CANADIAN_DOLLAR: Currency = Currency.getInstance("CAD")

/**
 * The device's language decides the separators and the placement of the symbol,
 * never the currency: a device in American English would otherwise show
 * US dollars, a wrong and plausible figure for a TFSA.
 *
 * NumberFormat.format(Object) delegates to DecimalFormat.format(BigDecimal, ...)
 * for this exact type: the amount never passes through a Double, even for
 * formatting.
 */
fun BigDecimal.formatAmount(locale: Locale = Locale.getDefault()): String = NumberFormat.getCurrencyInstance(locale).apply { currency = CANADIAN_DOLLAR }.format(this)

/** "September 4, 2026" or "4 septembre 2026": a date is read, it is not decoded. */
fun LocalDate.formatDate(locale: Locale = Locale.getDefault()): String = format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale))

/** Amount typed by the user: a comma from a French keyboard is accepted, zero or less is rejected. */
fun String.toEnteredAmount(): BigDecimal? = replace(',', '.').toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }
