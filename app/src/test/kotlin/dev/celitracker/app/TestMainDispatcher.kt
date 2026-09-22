@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package dev.celitracker.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext

private val dispatcher = UnconfinedTestDispatcher()

/** Never reset: a per-class reset raced with coroutines from other classes still running on Room's IO dispatcher. */
class TestMainDispatcher : BeforeAllCallback {
    override fun beforeAll(context: ExtensionContext) = Dispatchers.setMain(dispatcher)
}
