package dev.celitracker.app.ui.format

import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * NumberFormat.format(Object) delegue a DecimalFormat.format(BigDecimal, ...)
 * pour ce type precis: le montant ne transite jamais par un Double, meme au
 * formatage.
 */
fun BigDecimal.formatMontant(): String = NumberFormat.getCurrencyInstance(Locale.CANADA_FRENCH).format(this)

private val FORMAT_DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.CANADA_FRENCH)

/** « 4 septembre 2026 » plutot que 2026-09-04: une date se lit, elle ne se decode pas. */
fun LocalDate.formatDate(): String = format(FORMAT_DATE)
