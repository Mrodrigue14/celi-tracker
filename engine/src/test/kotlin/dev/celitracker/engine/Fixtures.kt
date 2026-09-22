package dev.celitracker.engine

import java.math.BigDecimal
import java.time.LocalDate

internal fun money(value: String): BigDecimal = BigDecimal(value).toMoney()

internal fun tfsaLimits(vararg pairs: Pair<Int, String>): List<AnnualLimit> = pairs.map { (year, amount) -> AnnualLimit(Account.TFSA, year, money(amount)) }

internal fun deposit(date: String, amount: String, account: Account = Account.TFSA) = Transaction(account, LocalDate.parse(date), TransactionType.DEPOSIT, money(amount))

internal fun withdrawal(date: String, amount: String, account: Account = Account.TFSA) = Transaction(account, LocalDate.parse(date), TransactionType.WITHDRAWAL, money(amount))

/** `scenario_2019_eligible_three_deposits` from CLAUDE.md: room at the end of 2026 is 41,800. */
internal object AcceptanceScenario {
    val profile = Profile(birthYear = 2001, fhsaOpeningDate = null)

    val limits = tfsaLimits(
        2019 to "6000.00",
        2020 to "6000.00",
        2021 to "6000.00",
        2022 to "6000.00",
        2023 to "6500.00",
        2024 to "7000.00",
        2025 to "7000.00",
        2026 to "7000.00",
    )

    val deposits = listOf(
        deposit("2021-03-10", "5000.00"),
        deposit("2023-06-15", "3500.00"),
        deposit("2024-11-02", "1200.00"),
    )
}
