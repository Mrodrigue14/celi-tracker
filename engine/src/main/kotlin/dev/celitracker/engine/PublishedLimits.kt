package dev.celitracker.engine

import java.math.BigDecimal

/**
 * Plafonds annuels du TFSA publies par l'ARC, recopies de la page
 * « Avant de cotiser a un TFSA » (canada.ca, consultee le 2026-09-17) :
 *
 * ```
 * De 2009 a 2012  5 000 $     2019 a 2022  6 000 $
 * 2013 et 2014    5 500 $     2023         6 500 $
 * 2015           10 000 $     2024 a 2026  7 000 $
 * De 2016 a 2018  5 500 $
 * ```
 *
 * C'est une SAISIE, labelStep un calcul: les montants sont transcrits d'une source
 * officielle. L'ARC les indexe a l'inflation et les arrondit au 500 $ pres,
 * mais deviner une year future a partir de cette regle produirait un chiffre
 * faux et plausible, exactement ce que ce projet evite.
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
