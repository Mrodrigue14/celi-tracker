package dev.celitracker.app.ui.composants

import kotlin.test.Test
import kotlin.test.assertEquals

class RegroupeurMilliersTest {

    @Test
    fun `regroupe par milliers en partant de la droite`() {
        assertEquals("1,234,567", RegroupeurMilliers("1234567", ',').texte)
        assertEquals("1,234", RegroupeurMilliers("1234", ',').texte)
        assertEquals("123", RegroupeurMilliers("123", ',').texte)
        assertEquals("", RegroupeurMilliers("", ',').texte)
    }

    @Test
    fun `n'importe quel caractere separateur est accepte, ex l'espace insecable du francais`() {
        assertEquals("1 234 567", RegroupeurMilliers("1234567", ' ').texte)
    }

    @Test
    fun `une position d'origine devient sa position apres regroupement`() {
        val regroupeur = RegroupeurMilliers("1234567", ',')

        // "1,234,567": positions des chiffres d'origine 1 2 3 4 5 6 7 -> 0 2 3 4 6 7 8, fin a 9.
        assertEquals(0, regroupeur.versRegroupe(0))
        assertEquals(2, regroupeur.versRegroupe(1))
        assertEquals(6, regroupeur.versRegroupe(4))
        assertEquals(9, regroupeur.versRegroupe(7))
    }

    @Test
    fun `un curseur pose sur un separateur retombe sur le dernier chiffre deja tape`() {
        val regroupeur = RegroupeurMilliers("1234567", ',')

        // Position 1 est la virgule elle-meme ("1,234,567"[1] == ','), entre
        // le premier chiffre et le second: elle retombe avant ce dernier plutot
        // que de faire sauter le curseur en avant d'un chiffre non tape.
        assertEquals(0, regroupeur.versOriginal(1))
    }

    @Test
    fun `aller-retour original vers regroupe vers original redonne la position de depart`() {
        val regroupeur = RegroupeurMilliers("1234567", ',')

        for (i in 0..7) {
            assertEquals(i, regroupeur.versOriginal(regroupeur.versRegroupe(i)))
        }
    }
}
