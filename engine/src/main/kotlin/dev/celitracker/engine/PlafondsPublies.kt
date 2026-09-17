package dev.celitracker.engine

import java.math.BigDecimal

/**
 * Plafonds annuels du CELI publies par l'ARC, recopies de la page
 * « Avant de cotiser a un CELI » (canada.ca, consultee le 2026-09-17) :
 *
 * ```
 * De 2009 a 2012  5 000 $     2019 a 2022  6 000 $
 * 2013 et 2014    5 500 $     2023         6 500 $
 * 2015           10 000 $     2024 a 2026  7 000 $
 * De 2016 a 2018  5 500 $
 * ```
 *
 * C'est une SAISIE, pas un calcul: les montants sont transcrits d'une source
 * officielle. L'ARC les indexe a l'inflation et les arrondit au 500 $ pres,
 * mais deviner une annee future a partir de cette regle produirait un chiffre
 * faux et plausible, exactement ce que ce projet evite.
 */
val PLAFONDS_CELI_PUBLIES: Map<Int, BigDecimal> = buildMap {
    fun inscrire(annees: IntRange, montant: String) = annees.forEach { put(it, BigDecimal(montant).argent()) }

    inscrire(2009..2012, "5000")
    inscrire(2013..2014, "5500")
    inscrire(2015..2015, "10000")
    inscrire(2016..2018, "5500")
    inscrire(2019..2022, "6000")
    inscrire(2023..2023, "6500")
    inscrire(2024..2026, "7000")
}
