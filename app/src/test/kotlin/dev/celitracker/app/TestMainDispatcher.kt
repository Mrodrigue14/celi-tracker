@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package dev.celitracker.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain

/** Never reset: a per-class reset raced with coroutines from other classes still running on Room's IO dispatcher. */
object TestMainDispatcher {
    private val dispatcher = UnconfinedTestDispatcher()

    fun install() = Dispatchers.setMain(dispatcher)
}
