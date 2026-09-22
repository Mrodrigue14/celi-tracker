package dev.celitracker.data

import dev.celitracker.engine.Account
import dev.celitracker.engine.AnnualLimit
import dev.celitracker.engine.PUBLISHED_TFSA_LIMITS
import kotlinx.coroutines.test.runTest
import java.math.BigDecimal
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultLimitsTest {

    private val database = TestDatabase()
    private val repository = database.repository

    @AfterTest
    fun close() = database.close()

    @Test
    fun `published limits are recorded and confirmed`() = runTest {
        repository.seedPublishedLimits()

        val saved = repository.limits().filter { it.account == Account.TFSA }
        assertEquals(PUBLISHED_TFSA_LIMITS.size, saved.size)
        assertTrue(saved.all { it.confirmed })
        assertEquals(BigDecimal("7000.00"), saved.single { it.year == 2026 }.amount)
        assertEquals(BigDecimal("10000.00"), saved.single { it.year == 2015 }.amount)
    }

    @Test
    fun `a limit already entered is not overwritten`() = runTest {
        repository.saveLimit(AnnualLimit(Account.TFSA, 2026, BigDecimal("6500.00"), confirmed = true))

        repository.seedPublishedLimits()

        assertEquals(BigDecimal("6500.00"), repository.limits().single { it.year == 2026 }.amount)
    }
}
