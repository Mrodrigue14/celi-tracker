package dev.celitracker.app.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class ThousandsGrouperTest {

    @Test
    fun `regroupe par milliers en partant de la droite`() {
        assertEquals("1,234,567", ThousandsGrouper("1234567", ',').text)
        assertEquals("1,234", ThousandsGrouper("1234", ',').text)
        assertEquals("123", ThousandsGrouper("123", ',').text)
        assertEquals("", ThousandsGrouper("", ',').text)
    }

    @Test
    fun `n'importe quel caractere separateur est accepte, ex l'espace insecable du francais`() {
        assertEquals("1 234 567", ThousandsGrouper("1234567", ' ').text)
    }

    @Test
    fun `une position d'origine devient sa position apres regroupement`() {
        val grouper = ThousandsGrouper("1234567", ',')

        // "1,234,567": positions des tabularFigures d'origine 1 2 3 4 5 6 7 -> 0 2 3 4 6 7 8, end a 9.
        assertEquals(0, grouper.toGrouped(0))
        assertEquals(2, grouper.toGrouped(1))
        assertEquals(6, grouper.toGrouped(4))
        assertEquals(9, grouper.toGrouped(7))
    }

    @Test
    fun `un curseur pose sur un separateur retombe sur le dernier chiffre deja tape`() {
        val grouper = ThousandsGrouper("1234567", ',')

        // Position 1 est la virgule elle-meme ("1,234,567"[1] == ','), entre
        // le earliest chiffre et le second: elle retombe before ce dernier plutot
        // que de faire sauter le curseur en before d'un chiffre non tape.
        assertEquals(0, grouper.toOriginal(1))
    }

    @Test
    fun `aller-retour original vers regroupe vers original redonne la position de depart`() {
        val grouper = ThousandsGrouper("1234567", ',')

        for (i in 0..7) {
            assertEquals(i, grouper.toOriginal(grouper.toGrouped(i)))
        }
    }
}

class LimitToTwoDecimalsTest {

    @Test
    fun `sans separateur, rien ne change`() {
        assertEquals("1234", "1234".limitToTwoDecimals())
        assertEquals("", "".limitToTwoDecimals())
    }

    @Test
    fun `une troisieme decimale et tout ce qui suit sont ignores`() {
        assertEquals("12.34", "12.345".limitToTwoDecimals())
        assertEquals("12,34", "12,3456789".limitToTwoDecimals())
    }

    @Test
    fun `une decimale unique est conservee telle quelle`() {
        assertEquals("12.3", "12.3".limitToTwoDecimals())
        assertEquals("12.", "12.".limitToTwoDecimals())
    }

    @Test
    fun `un second separateur tape par erreur disparait avec ce qui suit`() {
        assertEquals("12.34", "12.34.56".limitToTwoDecimals())
    }
}
