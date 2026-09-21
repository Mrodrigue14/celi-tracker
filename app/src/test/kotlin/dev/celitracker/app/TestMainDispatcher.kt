@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package dev.celitracker.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain

/**
 * Fixe `Dispatchers.Main` une fois pour toute la JVM de test, sans jamais le
 * reinitialiser. Chaque classe le remettait a zero a la end, pendant que des
 * coroutines d'une other classe tournaient encore sur le vrai dispatcher IO de
 * Room: les tests echouaient alors au hasard, selon l'ordre d'execution.
 */
object TestMainDispatcher {
    private val dispatcher = UnconfinedTestDispatcher()

    fun install() = Dispatchers.setMain(dispatcher)
}
