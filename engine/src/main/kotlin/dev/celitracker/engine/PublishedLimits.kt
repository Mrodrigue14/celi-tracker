package dev.celitracker.engine

import java.math.BigDecimal

/** Transcribed from the CRA page "Avant de cotiser à un CELI" (retrieved 2026-09-17), never extrapolated from the indexing rule. */
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
