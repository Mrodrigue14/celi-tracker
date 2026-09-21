# TFSA / FHSA calculation engine, implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the TFSA and FHSA contribution room calculation engine as a pure Kotlin/JVM module, fully tested, and turn CI from red to green.

**Architecture:** A single Gradle module `:engine`, with no Android dependency. Room is computed by pure functions `(profile, limits, transactions) -> room by year`, never persisted. Two separate engines, `TfsaEngine` and `FhsaEngine`, plus an overcontribution calculator. No external dependency other than `kotlin("test")`.

**Tech Stack:** Kotlin 2.2.0 (JVM), Gradle 9.7.1, JDK 21 (toolchain), `kotlin("test")` on JUnit Platform, `java.math.BigDecimal`, `java.time`.

**Spec:** `docs/superpowers/specs/2026-09-07-tfsa-fhsa-tracker-design.md`

## Global Constraints

- **No computed value is stored.** The engine persists nothing. No memoized field, no cache, no `roomByYear` structure kept between two calls.
- **`BigDecimal` everywhere, never `Double` or `Float`** for an amount.
- **Scale normalized to 2 decimal places on every output of the engine.** `BigDecimal.equals` also compares the scale: `BigDecimal("6000") != BigDecimal("6000.00")`. Every monetary value returned goes through `.toMoney()`. Every monetary literal in a test is written with two decimal places.
- **`TfsaEngine` and `FhsaEngine` stay two separate objects.** Merging them behind an `Account` parameter is not allowed.
- **`endRoom` is never floored to zero.** A negative balance *is* the over-contribution and must carry over to the next year. The original spreadsheet used `MAX(J36-K36, 0)`, which silently erased any over-contribution: this is precisely the bug not to reproduce.
- **Root package:** `dev.celitracker.engine`.
- **No named financial data in the repository.** Fixtures are anonymous scenarios: no institution name, no first-person wording.
- **All versions are pinned.** Dependabot proposes minor and patch upgrades; majors are blocked by `.github/dependabot.yml`.

## File structure

| File | Responsibility |
|---|---|
| `settings.gradle.kts` | Declares the `:engine` module |
| `build.gradle.kts` | Root, declares the Kotlin plugin without applying it |
| `engine/build.gradle.kts` | Toolchain 21, `kotlin("test")`, JUnit Platform |
| `engine/src/main/kotlin/dev/celitracker/engine/Model.kt` | Input types: `Account`, `TransactionType`, `Transaction`, `AnnualLimit`, `Profile`, `toMoney()` extension |
| `engine/src/main/kotlin/dev/celitracker/engine/TfsaEngine.kt` | `TfsaYear` + TFSA room, year by year |
| `engine/src/main/kotlin/dev/celitracker/engine/FhsaEngine.kt` | `FhsaYear` + FHSA room, non-cumulative carry-forward, end of participation period |
| `engine/src/main/kotlin/dev/celitracker/engine/Overcontribution.kt` | `MonthlyExcess` + maximum monthly excess and penalty |
| `engine/src/test/kotlin/dev/celitracker/engine/ModelTest.kt` | Decimal precision |
| `engine/src/test/kotlin/dev/celitracker/engine/TfsaEngineTest.kt` | Acceptance fixture, withdrawals, missing limits |
| `engine/src/test/kotlin/dev/celitracker/engine/FhsaEngineTest.kt` | Acceptance fixture, capped carry-forward, deadline |
| `engine/src/test/kotlin/dev/celitracker/engine/OvercontributionTest.kt` | Monthly excess, the re-contribution trap |

---

### Task 1: Gradle toolchain + data model

Turns CI from red (`chmod: cannot access 'gradlew'`) to green, and lays down the input types everything else builds on.

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `engine/build.gradle.kts`
- Create: `engine/src/main/kotlin/dev/celitracker/engine/Model.kt`
- Test: `engine/src/test/kotlin/dev/celitracker/engine/ModelTest.kt`
- Generated: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`

**Interfaces:**
- Consumes: nothing.
- Produces: `enum class Account { TFSA, FHSA }` · `enum class TransactionType { DEPOSIT, WITHDRAWAL }` · `data class Transaction(account: Account, date: LocalDate, type: TransactionType, amount: BigDecimal)` · `data class AnnualLimit(account: Account, year: Int, amount: BigDecimal, confirmed: Boolean = true)` · `data class Profile(tfsaEligibilityYear: Int, birthYear: Int, fhsaOpeningDate: LocalDate?)` · `fun BigDecimal.toMoney(): BigDecimal`

- [ ] **Step 1: Generate the Gradle wrapper**

Gradle is not installed on the machine. Download the distribution once, use it to generate the wrapper, then discard it: the wrapper is what gets versioned, not the distribution.

```bash
cd /c/Dev/celi-tracker
SCRATCH="$HOME/AppData/Local/Temp/celi-gradle"
mkdir -p "$SCRATCH"
curl -L -o "$SCRATCH/gradle.zip" https://services.gradle.org/distributions/gradle-9.7.1-bin.zip
unzip -q -o "$SCRATCH/gradle.zip" -d "$SCRATCH"
"$SCRATCH/gradle-9.7.1/bin/gradle" wrapper --gradle-version 9.7.1
```

Verify: `cat gradle/wrapper/gradle-wrapper.properties` must contain `gradle-9.7.1-bin.zip`.

- [ ] **Step 2: Write the build files**

`settings.gradle.kts`:

```kotlin
rootProject.name = "celi-tracker"

include(":engine")
```

`build.gradle.kts` (root):

```kotlin
// The Kotlin plugin is declared here to pin its version in a single place,
// but applied only in the modules that need it.
plugins {
    kotlin("jvm") version "2.2.0" apply false
}
```

`engine/build.gradle.kts`:

```kotlin
plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)
}

repositories {
    // Required: without a declared repository, Gradle can resolve neither
    // kotlin-stdlib nor kotlin-test, and the build fails with "no repositories
    // are defined".
    mavenCentral()
}

dependencies {
    // Only dependency of the engine. It knows nothing about Android, Room, or
    // networking.
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
```

- [ ] **Step 3: Write the failing test**

`engine/src/test/kotlin/dev/celitracker/engine/ModelTest.kt`:

```kotlin
package dev.celitracker.engine

import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class ModelTest {

    @Test
    fun `an amount keeps its exact decimal precision`() {
        val tx = Transaction(
            account = Account.TFSA,
            date = LocalDate.of(2026, 4, 6),
            type = TransactionType.DEPOSIT,
            amount = BigDecimal("1234.56"),
        )

        // If someone replaces BigDecimal with Double, this string equality
        // breaks (1234.5600000000001) and the test turns red.
        assertEquals("1234.56", tx.amount.toPlainString())
    }

    @Test
    fun `toMoney normalizes the scale to two decimal places`() {
        // BigDecimal.equals compares the scale: without normalization,
        // BigDecimal("6000") != BigDecimal("6000.00").
        assertEquals(BigDecimal("6000.00"), BigDecimal("6000").toMoney())
        assertEquals(BigDecimal("6000.00"), BigDecimal("6000.000").toMoney())
    }

    @Test
    fun `a limit is confirmed by default`() {
        val limit = AnnualLimit(Account.TFSA, 2019, BigDecimal("6000.00"))
        assertEquals(true, limit.confirmed)
    }

    @Test
    fun `a profile accepts the absence of an FHSA account`() {
        val profile = Profile(
            tfsaEligibilityYear = 2019,
            birthYear = 2001,
            fhsaOpeningDate = null,
        )
        assertEquals(null, profile.fhsaOpeningDate)
    }
}
```

- [ ] **Step 4: Run the test to confirm it fails**

Run: `./gradlew :engine:test`
Expected: compile FAILURE, `Unresolved reference: Transaction`, `Unresolved reference: toMoney`.

- [ ] **Step 5: Write the minimal implementation**

`engine/src/main/kotlin/dev/celitracker/engine/Model.kt`:

```kotlin
package dev.celitracker.engine

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * INPUT types for the engine. Nothing here is computed: the profile, the
 * limits table and the transaction log are the only three inputs from which
 * contribution room is derived.
 */

enum class Account { TFSA, FHSA }

enum class TransactionType { DEPOSIT, WITHDRAWAL }

/** The amount is ALWAYS positive; [type] is what carries the sign. */
data class Transaction(
    val account: Account,
    val date: LocalDate,
    val type: TransactionType,
    val amount: BigDecimal,
)

/**
 * [confirmed] false means the limit was proposed by automatically reading
 * the CRA site, not yet validated by the user. An unconfirmed limit never
 * enters the room calculation.
 */
data class AnnualLimit(
    val account: Account,
    val year: Int,
    val amount: BigDecimal,
    val confirmed: Boolean = true,
)

data class Profile(
    /** Year of turning 18 AND becoming a Canadian resident. */
    val tfsaEligibilityYear: Int,
    /** For the age-71 branch of the FHSA participation period. */
    val birthYear: Int,
    /** Starts both the FHSA room accumulation and the 15-year clock. */
    val fhsaOpeningDate: LocalDate?,
)

/**
 * Normalizes an amount to 2 decimal places.
 *
 * BigDecimal.equals compares both the value AND the scale, so
 * BigDecimal("6000") is not equal to BigDecimal("6000.00"). Every monetary
 * value produced by the engine goes through this function, otherwise test
 * assertions fail on amounts that are actually identical.
 */
fun BigDecimal.toMoney(): BigDecimal = setScale(2, RoundingMode.HALF_UP)
```

- [ ] **Step 6: Run the tests to confirm they pass**

Run: `./gradlew :engine:test`
Expected: PASS, 4 tests.

- [ ] **Step 7: Commit**

```bash
cd /c/Dev/celi-tracker
git add settings.gradle.kts build.gradle.kts engine gradle gradlew gradlew.bat
git commit -m "$(cat <<'EOF'
Add the Gradle wrapper, the engine module and the data model

The engine is a pure Kotlin/JVM module: no Android SDK is required to build
or test it, which turns CI green without a heavy dependency.

Model.kt contains only INPUT types. The toMoney() function normalizes the
scale to 2 decimal places because BigDecimal.equals also compares the scale.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
git push origin main
```

- [ ] **Step 8: Verify that CI is green**

Run: `gh run list --limit 1`
Expected: the `Build` workflow is in `success`. This was the plan's first objective.

---

### Task 2: TFSA engine, acceptance fixture

The heart of the project. The fixture is a synthetic scenario derived from the annual limits published by the CRA: cumulative limits $51,500 minus deposits $9,700 = $41,800, across its ten values.

**Files:**
- Create: `engine/src/main/kotlin/dev/celitracker/engine/TfsaEngine.kt`
- Test: `engine/src/test/kotlin/dev/celitracker/engine/TfsaEngineTest.kt`

**Interfaces:**
- Consumes: `Account`, `TransactionType`, `Transaction`, `AnnualLimit`, `Profile`, `BigDecimal.toMoney()` (Task 1).
- Produces: `data class TfsaYear(year: Int, limit: BigDecimal, startRoom: BigDecimal, deposits: BigDecimal, withdrawals: BigDecimal, endRoom: BigDecimal, limitMissing: Boolean)` · `object TfsaEngine { fun roomByYear(profile: Profile, limits: List<AnnualLimit>, transactions: List<Transaction>, upTo: Int): List<TfsaYear> }`

- [ ] **Step 1: Write the failing test**

`engine/src/test/kotlin/dev/celitracker/engine/TfsaEngineTest.kt`:

```kotlin
package dev.celitracker.engine

import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** Shortcut: every monetary literal in a test is written with 2 decimal places. */
private fun toMoney(value: String): BigDecimal = BigDecimal(value).toMoney()

private fun tfsaLimits(vararg pairs: Pair<Int, String>): List<AnnualLimit> =
    pairs.map { (year, amount) -> AnnualLimit(Account.TFSA, year, toMoney(amount)) }

private fun deposit(date: String, amount: String) =
    Transaction(Account.TFSA, LocalDate.parse(date), TransactionType.DEPOSIT, toMoney(amount))

class TfsaEngineTest {

    /**
     * scenario_2019_eligible_three_deposits
     *
     * Person who became TFSA-eligible in 2019, no withdrawal, three deposits.
     * Synthetic scenario, derived from the annual limits published by the CRA.
     * Check: cumulative limits 51,500 minus deposits 9,700 = 41,800.
     */
    @Test
    fun `scenario 2019 eligible three deposits`() {
        val profile = Profile(
            tfsaEligibilityYear = 2019,
            birthYear = 2001,
            fhsaOpeningDate = null,
        )
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
        val transactions = listOf(
            deposit("2021-03-10", "5000.00"),
            deposit("2023-06-15", "3500.00"),
            deposit("2024-11-02", "1200.00"),
        )

        val room = TfsaEngine.roomByYear(profile, limits, transactions, upTo = 2026)
            .associateBy { it.year }

        assertEquals(toMoney("6000.00"), room.getValue(2019).startRoom)
        assertEquals(toMoney("6000.00"), room.getValue(2019).endRoom)

        assertEquals(toMoney("12000.00"), room.getValue(2020).startRoom)
        assertEquals(toMoney("12000.00"), room.getValue(2020).endRoom)

        assertEquals(toMoney("18000.00"), room.getValue(2021).startRoom)
        assertEquals(toMoney("5000.00"), room.getValue(2021).deposits)
        assertEquals(toMoney("13000.00"), room.getValue(2021).endRoom)

        assertEquals(toMoney("19000.00"), room.getValue(2022).startRoom)
        assertEquals(toMoney("19000.00"), room.getValue(2022).endRoom)

        assertEquals(toMoney("25500.00"), room.getValue(2023).startRoom)
        assertEquals(toMoney("3500.00"), room.getValue(2023).deposits)
        assertEquals(toMoney("22000.00"), room.getValue(2023).endRoom)

        assertEquals(toMoney("29000.00"), room.getValue(2024).startRoom)
        assertEquals(toMoney("1200.00"), room.getValue(2024).deposits)
        assertEquals(toMoney("27800.00"), room.getValue(2024).endRoom)

        assertEquals(toMoney("34800.00"), room.getValue(2025).startRoom)
        assertEquals(toMoney("34800.00"), room.getValue(2025).endRoom)

        assertEquals(toMoney("41800.00"), room.getValue(2026).startRoom)
        assertEquals(toMoney("41800.00"), room.getValue(2026).endRoom)
    }

    @Test
    fun `the years span from eligibility to the requested year`() {
        val profile = Profile(2019, 2001, null)
        val room = TfsaEngine.roomByYear(
            profile,
            tfsaLimits(2019 to "6000.00", 2020 to "6000.00"),
            emptyList(),
            upTo = 2020,
        )

        assertEquals(listOf(2019, 2020), room.map { it.year })
    }

    @Test
    fun `FHSA transactions are ignored by the TFSA engine`() {
        val profile = Profile(2019, 2001, LocalDate.of(2023, 4, 1))
        val transactions = listOf(
            Transaction(Account.FHSA, LocalDate.of(2019, 5, 1), TransactionType.DEPOSIT, toMoney("5000.00")),
        )

        val room = TfsaEngine.roomByYear(
            profile, tfsaLimits(2019 to "6000.00"), transactions, upTo = 2019,
        )

        assertEquals(toMoney("0.00"), room.single().deposits)
        assertEquals(toMoney("6000.00"), room.single().endRoom)
    }
}
```

- [ ] **Step 2: Run the tests to confirm they fail**

Run: `./gradlew :engine:test --tests "dev.celitracker.engine.TfsaEngineTest"`
Expected: compile FAILURE, `Unresolved reference: TfsaEngine`.

- [ ] **Step 3: Write the minimal implementation**

`engine/src/main/kotlin/dev/celitracker/engine/TfsaEngine.kt`:

```kotlin
package dev.celitracker.engine

import java.math.BigDecimal

/**
 * TFSA contribution room for a year.
 *
 * [limitMissing] flags a year whose limit is absent from the table or not
 * yet confirmed. Its limit is then treated as zero: the engine never
 * invents room, and the error leans on the safe side (room underestimated
 * rather than overestimated, so there is never an incentive to
 * over-contribute).
 */
data class TfsaYear(
    val year: Int,
    val limit: BigDecimal,
    val startRoom: BigDecimal,
    val deposits: BigDecimal,
    val withdrawals: BigDecimal,
    val endRoom: BigDecimal,
    val limitMissing: Boolean,
)

/**
 * TFSA engine. Pure function: same inputs, same outputs, no state kept
 * between two calls.
 *
 * DO NOT merge with [FhsaEngine]. The two regimes diverge on every axis,
 * starting with the fact that a TFSA withdrawal restores room while an
 * FHSA withdrawal never does.
 */
object TfsaEngine {

    fun roomByYear(
        profile: Profile,
        limits: List<AnnualLimit>,
        transactions: List<Transaction>,
        upTo: Int,
    ): List<TfsaYear> {
        val byYear = limits
            .filter { it.account == Account.TFSA && it.confirmed }
            .associateBy { it.year }
        val tfsaTransactions = transactions.filter { it.account == Account.TFSA }

        val result = mutableListOf<TfsaYear>()
        var previousEndRoom = BigDecimal.ZERO
        var previousWithdrawals = BigDecimal.ZERO

        for (year in profile.tfsaEligibilityYear..upTo) {
            val yearLimit = byYear[year]
            val limit = yearLimit?.amount ?: BigDecimal.ZERO
            val deposits = sum(tfsaTransactions, year, TransactionType.DEPOSIT)
            val withdrawals = sum(tfsaTransactions, year, TransactionType.WITHDRAWAL)

            // Withdrawals from the PREVIOUS year come back on January 1st;
            // those from the current year do not count yet.
            val startRoom = previousEndRoom + limit + previousWithdrawals

            // No coerceAtLeast(ZERO) here: a negative balance IS the
            // over-contribution and must carry over to the next year. The
            // original spreadsheet used MAX(..., 0), which erased it.
            val endRoom = startRoom - deposits

            result += TfsaYear(
                year = year,
                limit = limit.toMoney(),
                startRoom = startRoom.toMoney(),
                deposits = deposits.toMoney(),
                withdrawals = withdrawals.toMoney(),
                endRoom = endRoom.toMoney(),
                limitMissing = yearLimit == null,
            )

            previousEndRoom = endRoom
            previousWithdrawals = withdrawals
        }
        return result
    }

    private fun sum(transactions: List<Transaction>, year: Int, type: TransactionType): BigDecimal =
        transactions
            .filter { it.date.year == year && it.type == type }
            .fold(BigDecimal.ZERO) { total, tx -> total + tx.amount }
}
```

- [ ] **Step 4: Run the tests to confirm they pass**

Run: `./gradlew :engine:test`
Expected: PASS, 7 tests.

- [ ] **Step 5: Commit**

```bash
cd /c/Dev/celi-tracker
git add engine
git commit -m "$(cat <<'EOF'
Add the TFSA engine and its acceptance fixture

startRoom(Y) = endRoom(Y-1) + limit(Y) + withdrawals(Y-1)
endRoom(Y)   = startRoom(Y) - deposits(Y)

endRoom is deliberately not floored to zero: a negative balance is the
over-contribution and must carry over. The replaced spreadsheet used
MAX(..., 0), which silently erased any over-contribution.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: TFSA withdrawals, restoration on the following January 1st

The acceptance scenario contains **no withdrawal**: this path is not covered by any of its fixtures and must be tested explicitly.

**Files:**
- Modify: `engine/src/test/kotlin/dev/celitracker/engine/TfsaEngineTest.kt` (add tests)

**Interfaces:**
- Consumes: `TfsaEngine.roomByYear`, `TfsaYear` (Task 2).
- Produces: nothing new. This task validates already-implemented behavior; if a test fails, `TfsaEngine` gets fixed, not the test.

- [ ] **Step 1: Write the tests that must pass**

Add to `TfsaEngineTest.kt`, with this helper near the others at the top of the file:

```kotlin
private fun withdrawal(date: String, amount: String) =
    Transaction(Account.TFSA, LocalDate.parse(date), TransactionType.WITHDRAWAL, toMoney(amount))
```

Then, in the class:

```kotlin
    @Test
    fun `a withdrawal does not restore room in the year of the withdrawal`() {
        val profile = Profile(2019, 2001, null)
        val limits = tfsaLimits(2019 to "6000.00", 2020 to "6000.00")
        val transactions = listOf(
            deposit("2020-03-01", "6000.00"),
            withdrawal("2020-08-01", "6000.00"),
        )

        val room = TfsaEngine.roomByYear(profile, limits, transactions, upTo = 2020)
            .associateBy { it.year }

        // 6000 (end 2019) + 6000 (limit 2020) + 0 (withdrawals 2019) = 12000.
        // The 2020 withdrawal adds NOTHING to 2020's room.
        assertEquals(toMoney("12000.00"), room.getValue(2020).startRoom)
        assertEquals(toMoney("6000.00"), room.getValue(2020).withdrawals)
        assertEquals(toMoney("6000.00"), room.getValue(2020).endRoom)
    }

    @Test
    fun `a withdrawal restores room on the following January 1st`() {
        val profile = Profile(2019, 2001, null)
        val limits = tfsaLimits(
            2019 to "6000.00", 2020 to "6000.00", 2021 to "6000.00",
        )
        val transactions = listOf(
            deposit("2020-03-01", "6000.00"),
            withdrawal("2020-08-01", "6000.00"),
        )

        val room = TfsaEngine.roomByYear(profile, limits, transactions, upTo = 2021)
            .associateBy { it.year }

        // 6000 (end 2020) + 6000 (limit 2021) + 6000 (withdrawals 2020) = 18000.
        assertEquals(toMoney("18000.00"), room.getValue(2021).startRoom)
        assertEquals(toMoney("18000.00"), room.getValue(2021).endRoom)
    }

    @Test
    fun `an over-contribution carries over to the next year without being erased`() {
        val profile = Profile(2019, 2001, null)
        val limits = tfsaLimits(2019 to "6000.00", 2020 to "6000.00")
        val transactions = listOf(deposit("2019-05-01", "10000.00"))

        val room = TfsaEngine.roomByYear(profile, limits, transactions, upTo = 2020)
            .associateBy { it.year }

        // 6000 - 10000 = -4000. A MAX(..., 0) here would give 0 and would hide
        // the over-contribution, exactly the replaced spreadsheet's bug.
        assertEquals(toMoney("-4000.00"), room.getValue(2019).endRoom)
        // -4000 + 6000 = 2000: the excess is absorbed by the next limit.
        assertEquals(toMoney("2000.00"), room.getValue(2020).startRoom)
    }
```

- [ ] **Step 2: Run the tests**

Run: `./gradlew :engine:test --tests "dev.celitracker.engine.TfsaEngineTest"`
Expected: PASS, 10 tests. If one fails, fix `TfsaEngine.kt`, not the test.

- [ ] **Step 3: Commit**

```bash
cd /c/Dev/celi-tracker
git add engine
git commit -m "$(cat <<'EOF'
Cover TFSA room restoration after a withdrawal

The replaced spreadsheet contained no withdrawal: this path had no
validation. Three cases: no restoration in the year of the withdrawal,
restoration on the following January 1st, and propagation of an
over-contribution without erasing it.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: FHSA engine, non-cumulative carry-forward

The regime's trap: the carry-forward is capped at $8,000 **per year of arrival** and does not accumulate. Writing `carryForward += remainder` instead of `carryForward = min(remainder, 8000)` produces a wrong and perfectly plausible number.

**Files:**
- Create: `engine/src/main/kotlin/dev/celitracker/engine/FhsaEngine.kt`
- Test: `engine/src/test/kotlin/dev/celitracker/engine/FhsaEngineTest.kt`

**Interfaces:**
- Consumes: `Account`, `TransactionType`, `Transaction`, `Profile`, `BigDecimal.toMoney()` (Task 1).
- Produces: `data class FhsaYear(year: Int, carryForwardIn: BigDecimal, yearRoom: BigDecimal, deposits: BigDecimal, withdrawals: BigDecimal, carryForwardOut: BigDecimal, lifetimeLimitLeft: BigDecimal)` · `object FhsaEngine { val ANNUAL_LIMIT; val MAX_CARRY_FORWARD; val LIFETIME_LIMIT; fun roomByYear(profile: Profile, transactions: List<Transaction>, upTo: Int): List<FhsaYear> }`

- [ ] **Step 1: Write the failing test**

`engine/src/test/kotlin/dev/celitracker/engine/FhsaEngineTest.kt`:

```kotlin
package dev.celitracker.engine

import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun toMoney(value: String): BigDecimal = BigDecimal(value).toMoney()

private fun fhsaDeposit(date: String, amount: String) =
    Transaction(Account.FHSA, LocalDate.parse(date), TransactionType.DEPOSIT, toMoney(amount))

private fun fhsaWithdrawal(date: String, amount: String) =
    Transaction(Account.FHSA, LocalDate.parse(date), TransactionType.WITHDRAWAL, toMoney(amount))

class FhsaEngineTest {

    private val profileOpened2023 = Profile(
        tfsaEligibilityYear = 2019,
        birthYear = 2001,
        fhsaOpeningDate = LocalDate.of(2023, 4, 1),
    )

    /**
     * scenario_fhsa_opened_2023_no_contributions
     *
     * Guard against the regime's most tempting mistake: believing that three
     * years without contributing accumulate $32,000. The carry-forward is
     * capped at $8,000 PER YEAR OF ARRIVAL, so the annual limit stabilizes at
     * $16,000.
     */
    @Test
    fun `scenario fhsa opened 2023 no contributions`() {
        val room = FhsaEngine
            .roomByYear(profileOpened2023, emptyList(), upTo = 2026)
            .associateBy { it.year }

        assertEquals(toMoney("0.00"), room.getValue(2023).carryForwardIn)
        assertEquals(toMoney("8000.00"), room.getValue(2023).yearRoom)
        assertEquals(toMoney("8000.00"), room.getValue(2023).carryForwardOut)

        assertEquals(toMoney("8000.00"), room.getValue(2024).carryForwardIn)
        assertEquals(toMoney("16000.00"), room.getValue(2024).yearRoom)
        // The critical point: 8000, NOT 16000. The carry-forward does not
        // accumulate.
        assertEquals(toMoney("8000.00"), room.getValue(2024).carryForwardOut)

        assertEquals(toMoney("16000.00"), room.getValue(2025).yearRoom)
        assertEquals(toMoney("8000.00"), room.getValue(2025).carryForwardOut)

        assertEquals(toMoney("16000.00"), room.getValue(2026).yearRoom)
        assertEquals(toMoney("40000.00"), room.getValue(2026).lifetimeLimitLeft)
    }

    @Test
    fun `accumulation starts in the year the account is opened`() {
        val room = FhsaEngine.roomByYear(profileOpened2023, emptyList(), upTo = 2026)

        assertEquals(2023, room.first().year)
    }

    @Test
    fun `no room without an open account`() {
        val profileWithoutFhsa = Profile(2019, 2001, fhsaOpeningDate = null)

        assertTrue(FhsaEngine.roomByYear(profileWithoutFhsa, emptyList(), 2026).isEmpty())
    }

    @Test
    fun `a withdrawal never restores room`() {
        val transactions = listOf(
            fhsaDeposit("2023-10-01", "8000.00"),
            fhsaWithdrawal("2023-11-01", "8000.00"),
        )

        val room = FhsaEngine
            .roomByYear(profileOpened2023, transactions, upTo = 2024)
            .associateBy { it.year }

        // The withdrawal is recorded...
        assertEquals(toMoney("8000.00"), room.getValue(2023).withdrawals)
        // ...but the 8000 contributed remain used up for life: 40000 - 8000.
        assertEquals(toMoney("32000.00"), room.getValue(2023).lifetimeLimitLeft)
        // 2023 fully used -> no carry-forward into 2024.
        assertEquals(toMoney("0.00"), room.getValue(2024).carryForwardIn)
        assertEquals(toMoney("8000.00"), room.getValue(2024).yearRoom)
    }

    @Test
    fun `a partial contribution carries forward the unused balance`() {
        val transactions = listOf(fhsaDeposit("2023-10-01", "3000.00"))

        val room = FhsaEngine
            .roomByYear(profileOpened2023, transactions, upTo = 2024)
            .associateBy { it.year }

        // 8000 - 3000 = 5000 unused, below the carry-forward cap.
        assertEquals(toMoney("5000.00"), room.getValue(2023).carryForwardOut)
        assertEquals(toMoney("13000.00"), room.getValue(2024).yearRoom)
    }

    @Test
    fun `the 40000 lifetime limit caps the annual room`() {
        val transactions = listOf(
            fhsaDeposit("2023-10-01", "8000.00"),
            fhsaDeposit("2024-10-01", "16000.00"),
            fhsaDeposit("2025-10-01", "16000.00"),
        )

        val room = FhsaEngine
            .roomByYear(profileOpened2023, transactions, upTo = 2026)
            .associateBy { it.year }

        // 8000 + 16000 + 16000 = 40000 contributed: the lifetime limit is reached.
        assertEquals(toMoney("0.00"), room.getValue(2025).lifetimeLimitLeft)
        // Even though the carry-forward would allow more, nothing is left for life.
        assertEquals(toMoney("0.00"), room.getValue(2026).yearRoom)
    }
}
```

- [ ] **Step 2: Run the tests to confirm they fail**

Run: `./gradlew :engine:test --tests "dev.celitracker.engine.FhsaEngineTest"`
Expected: compile FAILURE, `Unresolved reference: FhsaEngine`.

- [ ] **Step 3: Write the minimal implementation**

`engine/src/main/kotlin/dev/celitracker/engine/FhsaEngine.kt`:

```kotlin
package dev.celitracker.engine

import java.math.BigDecimal

data class FhsaYear(
    val year: Int,
    val carryForwardIn: BigDecimal,
    val yearRoom: BigDecimal,
    val deposits: BigDecimal,
    val withdrawals: BigDecimal,
    val carryForwardOut: BigDecimal,
    val lifetimeLimitLeft: BigDecimal,
)

/**
 * FHSA engine. Deliberately separate from [TfsaEngine]: the two regimes
 * diverge on every axis, and reusing the TFSA's restoration path for the
 * FHSA is the most likely correctness bug in this project.
 *
 * The three limits are fixed by law and are NOT indexed: unlike the TFSA,
 * there is nothing to fetch from the CRA site.
 */
object FhsaEngine {

    val ANNUAL_LIMIT: BigDecimal = BigDecimal("8000").toMoney()

    /**
     * Carry-forward cap, PER YEAR OF ARRIVAL. The carry-forward does not
     * accumulate: someone who never contributes sees their annual limit
     * stabilize at 16000 (8000 + 8000), not grow by 8000 every year.
     */
    val MAX_CARRY_FORWARD: BigDecimal = BigDecimal("8000").toMoney()

    val LIFETIME_LIMIT: BigDecimal = BigDecimal("40000").toMoney()

    fun roomByYear(
        profile: Profile,
        transactions: List<Transaction>,
        upTo: Int,
    ): List<FhsaYear> {
        // Accumulation starts when the account is OPENED, not at age 18.
        val opening = profile.fhsaOpeningDate ?: return emptyList()
        val fhsaTransactions = transactions.filter { it.account == Account.FHSA }

        val result = mutableListOf<FhsaYear>()
        var carryForwardIn = BigDecimal.ZERO
        var cumulativeContributions = BigDecimal.ZERO

        for (year in opening.year..upTo) {
            val deposits = sum(fhsaTransactions, year, TransactionType.DEPOSIT)
            val withdrawals = sum(fhsaTransactions, year, TransactionType.WITHDRAWAL)

            val lifetimeLeftBefore = (LIFETIME_LIMIT - cumulativeContributions)
                .coerceAtLeast(BigDecimal.ZERO)
            val yearRoom = minOf(ANNUAL_LIMIT + carryForwardIn, lifetimeLeftBefore)

            // min(..., MAX_CARRY_FORWARD), NOT an accumulation: this is the
            // whole difference with the TFSA and the RRSP.
            val carryForwardOut = minOf(
                (yearRoom - deposits).coerceAtLeast(BigDecimal.ZERO),
                MAX_CARRY_FORWARD,
            )

            cumulativeContributions += deposits

            result += FhsaYear(
                year = year,
                carryForwardIn = carryForwardIn.toMoney(),
                yearRoom = yearRoom.toMoney(),
                deposits = deposits.toMoney(),
                // Recorded for balance display, but never enters ANY room
                // calculation: an FHSA withdrawal never restores anything.
                withdrawals = withdrawals.toMoney(),
                carryForwardOut = carryForwardOut.toMoney(),
                lifetimeLimitLeft = (LIFETIME_LIMIT - cumulativeContributions)
                    .coerceAtLeast(BigDecimal.ZERO).toMoney(),
            )

            carryForwardIn = carryForwardOut
        }
        return result
    }

    private fun sum(transactions: List<Transaction>, year: Int, type: TransactionType): BigDecimal =
        transactions
            .filter { it.date.year == year && it.type == type }
            .fold(BigDecimal.ZERO) { total, tx -> total + tx.amount }
}
```

- [ ] **Step 4: Run the tests to confirm they pass**

Run: `./gradlew :engine:test`
Expected: PASS, 16 tests.

- [ ] **Step 5: Commit**

```bash
cd /c/Dev/celi-tracker
git add engine
git commit -m "$(cat <<'EOF'
Add the FHSA engine with non-cumulative carry-forward

The carry-forward is capped at 8000$ per year of arrival and does not
accumulate: an account opened with no contribution stabilizes at 16000$ of
annual limit, not 8000$ more each year. A withdrawal never restores room
there.

Engine deliberately kept separate from TfsaEngine.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: End of the FHSA participation period

**Files:**
- Modify: `engine/src/main/kotlin/dev/celitracker/engine/FhsaEngine.kt` (add a function)
- Modify: `engine/src/test/kotlin/dev/celitracker/engine/FhsaEngineTest.kt` (add tests)

**Interfaces:**
- Consumes: `Profile` (Task 1), `FhsaEngine` (Task 4).
- Produces: `fun FhsaEngine.participationPeriodEnd(profile: Profile): LocalDate?`

- [ ] **Step 1: Write the failing tests**

Add to `FhsaEngineTest.kt` (the `java.time.LocalDate` import is already present):

```kotlin
    @Test
    fun `the deadline is December 31 of the year of the 15th anniversary`() {
        // Opened in April 2023 -> 15th anniversary in April 2038.
        // The period ends on December 31 of THAT year.
        assertEquals(
            LocalDate.of(2038, 12, 31),
            FhsaEngine.participationPeriodEnd(profileOpened2023),
        )
    }

    @Test
    fun `the age-71 branch wins when it comes sooner`() {
        val olderProfile = Profile(
            tfsaEligibilityYear = 1975,
            birthYear = 1960, // turns 71 in 2031
            fhsaOpeningDate = LocalDate.of(2023, 4, 1), // 15 years -> 2038
        )

        assertEquals(
            LocalDate.of(2031, 12, 31),
            FhsaEngine.participationPeriodEnd(olderProfile),
        )
    }

    @Test
    fun `no deadline without an open account`() {
        val profileWithoutFhsa = Profile(2019, 2001, fhsaOpeningDate = null)

        assertEquals(null, FhsaEngine.participationPeriodEnd(profileWithoutFhsa))
    }
```

- [ ] **Step 2: Run the tests to confirm they fail**

Run: `./gradlew :engine:test --tests "dev.celitracker.engine.FhsaEngineTest"`
Expected: compile FAILURE, `Unresolved reference: participationPeriodEnd`.

- [ ] **Step 3: Write the minimal implementation**

Add to `object FhsaEngine`, after `roomByYear`, and add `import java.time.LocalDate` at the top of the file:

```kotlin
    /**
     * End of the maximum participation period: December 31 of the year in
     * which the FIRST of the following three events occurs.
     *
     *   1. the 15th anniversary of the opening of the first FHSA
     *   2. the holder's 71st birthday
     *   3. the year following the first qualifying withdrawal
     *
     * Branch 3 is NOT implemented: it requires distinguishing a qualifying
     * withdrawal (purchase of a first home) from an ordinary withdrawal,
     * which the model does not track. The real deadline may therefore be
     * sooner than the one returned here. Exclusion accepted, documented in
     * the spec.
     */
    fun participationPeriodEnd(profile: Profile): LocalDate? {
        val opening = profile.fhsaOpeningDate ?: return null
        val fifteenthYear = opening.year + 15
        val age71Year = profile.birthYear + 71
        return LocalDate.of(minOf(fifteenthYear, age71Year), 12, 31)
    }
```

- [ ] **Step 4: Run the tests to confirm they pass**

Run: `./gradlew :engine:test`
Expected: PASS, 19 tests.

- [ ] **Step 5: Commit**

```bash
cd /c/Dev/celi-tracker
git add engine
git commit -m "$(cat <<'EOF'
Compute the end of the FHSA participation period

December 31 of the year of the first event between the 15th anniversary of
the opening and age 71. The third branch (year following the first
qualifying withdrawal) remains unimplemented, for lack of withdrawal
classification: the real deadline can be sooner than the one shown.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: TFSA over-contribution, monthly excess and penalty

Something the spreadsheet could not do. The CRA penalty is 1% per month applied to the **highest excess of the month**, which requires a balance that can be replayed in chronological order.

**Files:**
- Create: `engine/src/main/kotlin/dev/celitracker/engine/Overcontribution.kt`
- Test: `engine/src/test/kotlin/dev/celitracker/engine/OvercontributionTest.kt`

**Interfaces:**
- Consumes: `Account`, `TransactionType`, `Transaction`, `AnnualLimit`, `Profile`, `toMoney()` (Task 1) ; `TfsaEngine.roomByYear`, `TfsaYear` (Task 2).
- Produces: `data class MonthlyExcess(year: Int, month: Int, maxExcess: BigDecimal, penalty: BigDecimal)` · `object Overcontribution { fun tfsaExcesses(profile: Profile, limits: List<AnnualLimit>, transactions: List<Transaction>, upTo: YearMonth): List<MonthlyExcess> }`

- [ ] **Step 1: Write the failing test**

`engine/src/test/kotlin/dev/celitracker/engine/OvercontributionTest.kt`:

```kotlin
package dev.celitracker.engine

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun toMoney(value: String): BigDecimal = BigDecimal(value).toMoney()

class OvercontributionTest {

    private val profile = Profile(2019, 2001, null)
    private val limits = listOf(
        AnnualLimit(Account.TFSA, 2019, toMoney("6000.00")),
        AnnualLimit(Account.TFSA, 2020, toMoney("6000.00")),
    )

    private fun tx(date: String, type: TransactionType, amount: String) =
        Transaction(Account.TFSA, LocalDate.parse(date), type, toMoney(amount))

    @Test
    fun `no excess when contributions respect the room`() {
        val transactions = listOf(tx("2019-03-15", TransactionType.DEPOSIT, "6000.00"))

        val excesses = Overcontribution.tfsaExcesses(
            profile, limits, transactions, upTo = YearMonth.of(2019, 12),
        )

        assertTrue(excesses.isEmpty())
    }

    @Test
    fun `an excess persists every month through the end of the year`() {
        // 2019 room = 6000, deposit of 10000 -> excess of 4000.
        val transactions = listOf(tx("2019-03-15", TransactionType.DEPOSIT, "10000.00"))

        val excesses = Overcontribution.tfsaExcesses(
            profile, limits, transactions, upTo = YearMonth.of(2019, 12),
        )

        // March through December inclusive = 10 months.
        assertEquals(10, excesses.size)
        assertEquals(3, excesses.first().month)
        assertEquals(toMoney("4000.00"), excesses.first().maxExcess)
        assertEquals(toMoney("40.00"), excesses.first().penalty)
        assertEquals(12, excesses.last().month)
        assertEquals(toMoney("4000.00"), excesses.last().maxExcess)
    }

    @Test
    fun `re-contributing a withdrawn amount the same year recreates the excess`() {
        // THE regime's trap. A withdrawal cancels an existing excess, but
        // restores NO room before the following January 1st. Re-contributing
        // the same amount the same year therefore creates a full excess.
        val transactions = listOf(
            tx("2019-02-01", TransactionType.DEPOSIT, "6000.00"),   // room exhausted, 0 excess
            tx("2019-04-01", TransactionType.WITHDRAWAL, "6000.00"), // no room restored
            tx("2019-06-01", TransactionType.DEPOSIT, "6000.00"),   // re-contribution -> excess
        )

        val excesses = Overcontribution.tfsaExcesses(
            profile, limits, transactions, upTo = YearMonth.of(2019, 12),
        ).associateBy { it.month }

        // February to May: room covers the contributions, no excess.
        assertTrue(excesses[2] == null)
        assertTrue(excesses[5] == null)
        // June: room was already exhausted, the withdrawal did not restore any.
        assertEquals(toMoney("6000.00"), excesses.getValue(6).maxExcess)
        assertEquals(toMoney("60.00"), excesses.getValue(6).penalty)
        // The excess persists through the end of the year: June to December.
        assertEquals(7, excesses.size)
        assertEquals(toMoney("6000.00"), excesses.getValue(12).maxExcess)
    }

    @Test
    fun `a withdrawal cancels the excess but the month is still charged`() {
        val transactions = listOf(
            tx("2019-02-01", TransactionType.DEPOSIT, "6000.00"),
            tx("2019-03-01", TransactionType.DEPOSIT, "1000.00"),   // 1000 over
            tx("2019-04-15", TransactionType.WITHDRAWAL, "1000.00"), // fixed in April
        )

        val excesses = Overcontribution.tfsaExcesses(
            profile, limits, transactions, upTo = YearMonth.of(2019, 12),
        ).associateBy { it.month }

        assertEquals(toMoney("1000.00"), excesses.getValue(3).maxExcess)
        assertEquals(toMoney("10.00"), excesses.getValue(3).penalty)
        // April is still charged: the penalty is based on the HIGHEST excess
        // of the month, and it was 1000 until the 15th.
        assertEquals(toMoney("1000.00"), excesses.getValue(4).maxExcess)
        // May is clean: the excess was cancelled by the withdrawal.
        assertTrue(excesses[5] == null)
    }

    @Test
    fun `the excess is absorbed by the following year's room`() {
        // 2019 room = 6000, deposit of 10000 -> excess of 4000 through
        // December. On January 1, 2020, the 6000 limit absorbs the excess
        // (startRoom 2020 = -4000 + 6000 = 2000 > 0).
        val transactions = listOf(tx("2019-03-15", TransactionType.DEPOSIT, "10000.00"))

        val excesses = Overcontribution.tfsaExcesses(
            profile, limits, transactions, upTo = YearMonth.of(2020, 12),
        )

        assertEquals(2019, excesses.last().year)
        assertEquals(12, excesses.last().month)
        assertTrue(excesses.none { it.year == 2020 })
    }

    @Test
    fun `no excess without a transaction`() {
        val excesses = Overcontribution.tfsaExcesses(
            profile, limits, emptyList(), upTo = YearMonth.of(2019, 12),
        )

        assertTrue(excesses.isEmpty())
    }
}
```

- [ ] **Step 2: Run the tests to confirm they fail**

Run: `./gradlew :engine:test --tests "dev.celitracker.engine.OvercontributionTest"`
Expected: compile FAILURE, `Unresolved reference: Overcontribution`.

- [ ] **Step 3: Write the minimal implementation**

`engine/src/main/kotlin/dev/celitracker/engine/Overcontribution.kt`:

```kotlin
package dev.celitracker.engine

import java.math.BigDecimal
import java.time.YearMonth

/**
 * A month's excess and its corresponding penalty.
 *
 * [maxExcess] is the HIGHEST excess reached during the month, not the one
 * at month end: this is what the CRA bases the penalty on.
 */
data class MonthlyExcess(
    val year: Int,
    val month: Int,
    val maxExcess: BigDecimal,
    val penalty: BigDecimal,
)

object Overcontribution {

    /** 1% per month of the month's highest excess. */
    private val MONTHLY_PENALTY_RATE = BigDecimal("0.01")

    fun tfsaExcesses(
        profile: Profile,
        limits: List<AnnualLimit>,
        transactions: List<Transaction>,
        upTo: YearMonth,
    ): List<MonthlyExcess> {
        val tfsaTransactions = transactions
            .filter { it.account == Account.TFSA }
            .sortedBy { it.date }
        val earliest = tfsaTransactions.firstOrNull() ?: return emptyList()

        val startRoomByYear = TfsaEngine
            .roomByYear(profile, limits, transactions, upTo.year)
            .associate { it.year to it.startRoom }

        val result = mutableListOf<MonthlyExcess>()
        var month = YearMonth.from(earliest.date)

        // TWO variables, not a single net balance. A net balance would make
        // re-contribution free, whereas that is exactly the regime's trap:
        //   - a DEPOSIT first consumes the remaining room, the rest becomes
        //     excess;
        //   - a WITHDRAWAL cancels the existing excess, but restores NO
        //     room: room only comes back on the following January 1st, via
        //     the next year's startRoom.
        var currentYear = Int.MIN_VALUE
        var remainingRoom = BigDecimal.ZERO
        var excess = BigDecimal.ZERO

        while (!month.isAfter(upTo)) {
            if (month.year != currentYear) {
                currentYear = month.year
                // startRoom already includes the carry-forward, the year's
                // limit and the previous year's withdrawals. If it is
                // negative, the over-contribution has not been absorbed and
                // continues.
                val yearStart = startRoomByYear[currentYear] ?: BigDecimal.ZERO
                if (yearStart.signum() < 0) {
                    excess = yearStart.negate()
                    remainingRoom = BigDecimal.ZERO
                } else {
                    excess = BigDecimal.ZERO
                    remainingRoom = yearStart
                }
            }

            // The excess carried from the previous month is already chargeable.
            var maxExcess = excess

            for (tx in tfsaTransactions.filter { YearMonth.from(it.date) == month }) {
                if (tx.type == TransactionType.DEPOSIT) {
                    val absorbed = minOf(remainingRoom, tx.amount)
                    remainingRoom -= absorbed
                    excess += tx.amount - absorbed
                } else {
                    excess = (excess - tx.amount).coerceAtLeast(BigDecimal.ZERO)
                    // remainingRoom is deliberately left unchanged.
                }
                maxExcess = maxOf(maxExcess, excess)
            }

            if (maxExcess.signum() > 0) {
                result += MonthlyExcess(
                    year = month.year,
                    month = month.monthValue,
                    maxExcess = maxExcess.toMoney(),
                    penalty = (maxExcess * MONTHLY_PENALTY_RATE).toMoney(),
                )
            }
            month = month.plusMonths(1)
        }
        return result
    }
}
```

- [ ] **Step 4: Run the tests to confirm they pass**

Run: `./gradlew :engine:test`
Expected: PASS, 25 tests.

- [ ] **Step 5: Commit and push**

```bash
cd /c/Dev/celi-tracker
git add engine
git commit -m "$(cat <<'EOF'
Detect TFSA over-contribution and estimate the penalty

The CRA penalty is 1% per month on the month's highest excess, which
requires a balance that can be replayed in chronological order -- possible
only because no computed value is stored.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
git push origin main
```

---

### Task 7: Missing or unconfirmed limits

A limit that is absent, or proposed by the automatic reading without confirmation, must **never** create room. The error leans on the safe side.

**Files:**
- Modify: `engine/src/test/kotlin/dev/celitracker/engine/TfsaEngineTest.kt` (add tests)

**Interfaces:**
- Consumes: `TfsaEngine.roomByYear`, `TfsaYear` (Task 2), `AnnualLimit.confirmed` (Task 1).
- Produces: nothing new. Validates already-implemented behavior.

- [ ] **Step 1: Write the tests that must pass**

Add to `TfsaEngineTest.kt`:

```kotlin
    @Test
    fun `a year without a limit is flagged and creates no room`() {
        val profile = Profile(2019, 2001, null)
        // 2020 absent from the table.
        val limits = tfsaLimits(2019 to "6000.00")

        val room = TfsaEngine.roomByYear(profile, limits, emptyList(), upTo = 2020)
            .associateBy { it.year }

        assertEquals(false, room.getValue(2019).limitMissing)
        assertEquals(true, room.getValue(2020).limitMissing)
        assertEquals(toMoney("0.00"), room.getValue(2020).limit)
        // Room stagnates: the engine does not invent a limit.
        assertEquals(toMoney("6000.00"), room.getValue(2020).endRoom)
    }

    @Test
    fun `an unconfirmed limit is treated as absent`() {
        val profile = Profile(2019, 2001, null)
        val limits = listOf(
            AnnualLimit(Account.TFSA, 2019, toMoney("6000.00")),
            // Proposed by the automatic reading of the CRA site, not yet
            // validated by the user: it must not enter the calculation.
            AnnualLimit(Account.TFSA, 2020, toMoney("6000.00"), confirmed = false),
        )

        val room = TfsaEngine.roomByYear(profile, limits, emptyList(), upTo = 2020)
            .associateBy { it.year }

        assertEquals(true, room.getValue(2020).limitMissing)
        assertEquals(toMoney("6000.00"), room.getValue(2020).endRoom)
    }
```

- [ ] **Step 2: Run the tests**

Run: `./gradlew :engine:test`
Expected: PASS, 27 tests. If one fails, fix `TfsaEngine.kt`, not the test.

- [ ] **Step 3: Commit**

```bash
cd /c/Dev/celi-tracker
git add engine
git commit -m "$(cat <<'EOF'
Lock down handling of missing or unconfirmed limits

A limit absent from the table, or proposed by the automatic reading without
user confirmation, creates no room. The error leans on the safe side: room
is underestimated, never an incentive to over-contribute.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 8: Branch protection and Dependabot auto-merge

The CI gate deferred from the repository's bootstrap. It could not be put in place earlier: a required check that cannot pass makes `main` impossible to merge into. Now that `Build & test` is green, it makes sense.

**Files:**
- Create: `.github/workflows/dependabot-auto-merge.yml`
- Modify: `CLAUDE.md` (Git section)

**Interfaces:**
- Consumes: the `Build & test` check produced by `.github/workflows/build.yml`.
- Produces: nothing in code.

- [ ] **Step 1: Enable protection on `main`**

```bash
cd /c/Dev/celi-tracker
gh api -X PUT repos/Mrodrigue14/celi-tracker/branches/main/protection --input - <<'JSON'
{
  "required_status_checks": { "strict": true, "contexts": ["Build & test"] },
  "enforce_admins": false,
  "required_pull_request_reviews": null,
  "restrictions": null,
  "allow_force_pushes": false,
  "allow_deletions": false
}
JSON
```

Verify: `gh api repos/Mrodrigue14/celi-tracker/branches/main/protection --jq '.required_status_checks.contexts'`
Expected: `["Build & test"]`

- [ ] **Step 2: Write the auto-merge workflow**

`.github/workflows/dependabot-auto-merge.yml`:

```yaml
name: Dependabot auto-merge

# Auto-merges Dependabot PRs once CI is green.
#
# SECURITY, this workflow follows the official GitHub pattern:
#
#   1. `pull_request` trigger (NEVER `pull_request_target`): the job has no
#      access to the repository's secrets.
#   2. NO `checkout` and no execution of the PR's code.
#   3. Author check via `github.event.pull_request.user.login` (the
#      non-forgeable field) and NOT `github.actor` (forgeable on synchronize
#      via `@dependabot recreate`).
#   4. No interpolation of a controllable value in a `run:` step, only
#      `html_url`, generated by GitHub, is passed through an environment
#      variable.
#   5. Minimal permissions.
#
# The merge is delegated to GitHub's native auto-merge (`--auto`), which
# succeeds ONLY if the checks required by branch protection pass. The `main`
# protection requiring "Build & test" is therefore the real gate: without
# it, this workflow would merge with no safeguard.
#
# SCOPE: patch and minor only, all ecosystems. No major upgrade is
# auto-merged. No list of "uncovered" workflows to maintain -- such a list
# silently goes stale and ends up letting through exactly what it was meant
# to stop.
on: pull_request

permissions:
  contents: write
  pull-requests: write

jobs:
  auto-merge:
    runs-on: ubuntu-latest
    timeout-minutes: 5
    if: ${{ github.event.pull_request.user.login == 'dependabot[bot]' }}
    steps:
      - name: Fetch Dependabot metadata
        id: meta
        uses: dependabot/fetch-metadata@v3
        with:
          github-token: ${{ secrets.GITHUB_TOKEN }}

      - name: Enable auto-merge
        if: >
          steps.meta.outputs.update-type == 'version-update:semver-patch' ||
          steps.meta.outputs.update-type == 'version-update:semver-minor'
        run: gh pr merge --auto --merge "$PR_URL"
        env:
          PR_URL: ${{ github.event.pull_request.html_url }}
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

- [ ] **Step 3: Update `CLAUDE.md`**

Replace the `## Git` section with:

```markdown
## Git

`main` is protected: the "Build & test" check must pass, and the branch must
be up to date with `main` before merging. Work on a branch, open a PR, wait
for CI to go green before merging.

Dependabot patch and minor PRs are auto-merged once CI is green. Major
upgrades are blocked by `.github/dependabot.yml` and stay under manual
review.
```

- [ ] **Step 4: Commit and push through a PR**

`main` is now protected: a direct push is rejected. This is the first real exercise of the gate.

```bash
cd /c/Dev/celi-tracker
git checkout -b ci/auto-merge-dependabot
git add .github/workflows/dependabot-auto-merge.yml CLAUDE.md
git commit -m "$(cat <<'EOF'
Enable Dependabot auto-merge behind branch protection

The main protection requiring "Build & test" is the real gate: --auto only
succeeds if the required checks pass. Scope limited to patch and minor, with
no list of "uncovered" workflows to maintain -- such a list silently goes
stale.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
git push -u origin ci/auto-merge-dependabot
gh pr create --title "Enable Dependabot auto-merge" --body "$(cat <<'EOF'
Puts in place the CI gate deferred from the repository's bootstrap: `main`
protection now requires the "Build & test" check.

Dependabot auto-merge is limited to patch and minor upgrades. Majors stay
under manual review, and `.github/dependabot.yml` already blocks Gradle
majors.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 5: Verify the gate works, then merge**

Run: `gh pr checks --watch`
Expected: `Build & test` at `pass`.

```bash
gh pr merge --merge --delete-branch
git checkout main && git pull
```

---

## Self-review

**1. Spec coverage.**

| Spec requirement | Task |
|---|---|
| Data model (input types) | 1 |
| `BigDecimal`, not `Double` | 1 (dedicated test) |
| TFSA formula + $41,800.00 fixture | 2 |
| Withdrawals restored on the following January 1st | 3 |
| Over-contribution carried over without erasing | 3 |
| FHSA formula + non-cumulative carry-forward + $16,000 fixture | 4 |
| FHSA withdrawal restores no room | 4 |
| Lifetime limit $40,000 | 4 |
| Participation period (15-year / age-71 branches) | 5 |
| Monthly excess + 1%/month penalty | 6 |
| Missing or unconfirmed limit | 7 |
| TFSA and FHSA engines kept separate | 2 and 4, global constraint |

**Out of scope for this plan, to be covered by later plans:** Room persistence, JSON export/import, Compose screens, reading limits from the CRA site (multiple-of-500 validation, editable URL, visible failure), automatic Android backup, CRA snapshot, Android SDK setup, APK.

**2. Placeholders.** No "TBD", no "similar to task N", no step without code.

**3. Type consistency.** `toMoney()` is defined once (Task 1) and used everywhere. `TfsaYear` (Task 2) is consumed as-is by tasks 3, 6 and 7. `FhsaYear` and `FhsaEngine` (Task 4) are extended by task 5. `Overcontribution.tfsaExcesses` consumes `TfsaEngine.roomByYear` with the exact signature from task 2. The private `toMoney(String)` helper is redefined in each test file: this is deliberate, test files stay independent and readable in isolation.

**Execution note.** The cumulative test counts (4, 7, 10, 16, 19, 25, 27) assume the tasks run in order.
