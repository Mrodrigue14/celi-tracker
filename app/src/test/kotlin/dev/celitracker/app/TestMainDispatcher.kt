@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package dev.celitracker.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain

/**
 * Sets `Dispatchers.Main` once for the whole test JVM, and never
 * resets it. Each class used to reset it at the end, while
 * coroutines from another class were still running on Room's real IO
 * dispatcher: tests then failed at random, depending on execution order.
 */
object TestMainDispatcher {
    private val dispatcher = UnconfinedTestDispatcher()

    fun install() = Dispatchers.setMain(dispatcher)
}
