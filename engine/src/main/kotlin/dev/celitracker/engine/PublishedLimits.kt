package dev.celitracker.engine

import java.math.BigDecimal

/**
 * Annual TFSA limits published by the CRA, copied from the page
 * "Avant de cotiser à un CELI" (canada.ca, retrieved 2026-09-17):
 *
 * ```
 * 2009 to 2012   $5,000      2019 to 2022  $6,000
 * 2013 and 2014  $5,500      2023          $6,500
 * 2015          $10,000      2024 to 2026  $7,000
 * 2016 to 2018   $5,500
 * ```
 *
 * This is INPUT, not a calculation: the amounts are transcribed from an
 * official source. The CRA indexes them to inflation and rounds to the
 * nearest $500, but guessing a future year from that rule would produce
 * a number that is wrong and plausible, exactly what this project
 * avoids.
 */
val PUBLISHED_TFSA_LIMITS: Map<Int, BigDecimal> = buildMap {
    fun putRange(years: IntRange, amount: String) = years.forEach { put(it, BigDecimal(amount).toMoney()) }

    putRange(2009..2012, "5000")
    putRange(2013..2014, "5500")
    putRange(2015..2015, "10000")
    putRange(2016..2018, "5500")
    putRange(2019..2022, "6000")
    putRange(2023..2023, "6500")
    putRange(2024..2026, "7000")
}
