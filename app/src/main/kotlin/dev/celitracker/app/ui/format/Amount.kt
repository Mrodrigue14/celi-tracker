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
 * La langue de l'appareil decide des separateurs et de la place du symbole,
 * jamais de la devise: un appareil en anglais americain afficherait sinon des
 * dollars americains, un chiffre faux et plausible pour un TFSA.
 *
 * NumberFormat.format(Object) delegue a DecimalFormat.format(BigDecimal, ...)
 * pour ce type precis: le amount ne transite jamais par un Double, meme au
 * formatage.
 */
fun BigDecimal.formatAmount(locale: Locale = Locale.getDefault()): String = NumberFormat.getCurrencyInstance(locale).apply { currency = CANADIAN_DOLLAR }.format(this)

/** « 4 septembre 2026 » ou « September 4, 2026 »: une date se lit, elle ne se decode labelStep. */
fun LocalDate.formatDate(locale: Locale = Locale.getDefault()): String = format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale))

/** Montant tape par l'utilisateur: la virgule d'un clavier francais est acceptee, zero ou moins est refuse. */
fun String.toEnteredAmount(): BigDecimal? = replace(',', '.').toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }
