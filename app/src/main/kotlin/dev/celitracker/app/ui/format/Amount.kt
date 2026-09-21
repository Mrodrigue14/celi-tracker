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
 * Language sets separators and symbol placement, never the currency: US English would otherwise show US dollars.
 * NumberFormat formats a BigDecimal directly, so the amount never becomes a Double.
 */
fun BigDecimal.formatAmount(locale: Locale = Locale.getDefault()): String = NumberFormat.getCurrencyInstance(locale).apply { currency = CANADIAN_DOLLAR }.format(this)

fun LocalDate.formatDate(locale: Locale = Locale.getDefault()): String = format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale))

fun BigDecimal.formatSignedAmount(locale: Locale = Locale.getDefault()): String = if (signum() > 0) "+" + formatAmount(locale) else formatAmount(locale)

private fun String.toDecimalOrNull(): BigDecimal? = replace(',', '.').toBigDecimalOrNull()

fun String.toEnteredAmount(): BigDecimal? = toDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }

/** Zero is valid: fully used room is a real figure. */
fun String.toEnteredRoom(): BigDecimal? = toDecimalOrNull()?.takeIf { it >= BigDecimal.ZERO }
