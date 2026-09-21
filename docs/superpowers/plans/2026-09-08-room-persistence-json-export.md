# Room persistence and JSON export: implementation plan

**Goal:** Deliver the `:data` module (Room persistence of user input and JSON
export/import) without introducing an Android dependency.

**Architecture:** `:data` is a **pure Kotlin/JVM module** that depends on
`:engine`. Room runs there through its JVM artifact and the bundled SQLite
driver, so tests run on the JVM, with no Android SDK or emulator, and CI stays
unchanged. `:engine` never depends on `:data`.

**Tech Stack:** Room 2.8.4 (`room-runtime`, `room-compiler` via KSP 2.3.11,
the `androidx.room` Gradle plugin for schema export), `androidx.sqlite:sqlite-bundled`
2.7.0, `kotlinx-coroutines-core` 1.10.2, `kotlinx-serialization-json` for the
export. Kotlin 2.4.10, Gradle 9.7.1, JDK 21.

**Spec:** `docs/superpowers/specs/2026-09-07-tfsa-fhsa-tracker-design.md`

## Constraints found by a spike, not to be rediscovered

A throwaway spike validated the setup. Four points made it fail before it
passed:

1. **No `@ConstructedBy` and no `expect object`.** That is the multiplatform
   pattern; in a plain `kotlin("jvm")` module it produces `'expect' and
   'actual' declarations can be used only in multiplatform projects`. On the
   JVM, Room generates the implementation directly.
2. **`pluginManagement.repositories` must include `google()`** in
   `settings.gradle.kts`: the `androidx.room` Gradle plugin is not published
   on the Gradle Plugin Portal.
3. **The `androidx.room` plugin with `room { schemaDirectory(...) }` is
   required.** Without it, Room emits a warning about the schema export, and
   CI runs with `-PwarningsAsErrors=true`, so the build would fail.
4. **Building the database:**
   `Room.databaseBuilder<T>(name = <absolute path>).setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()`.

## Global constraints

- **`:engine` never depends on `:data`.** The dependency runs in one
  direction only.
- **No calculated value is persisted.** The database holds the profile, the
  limit table, the transaction log, the CRA snapshots, and the settings. **No
  room table**, no memoized balance.
- **Amounts are stored as `TEXT`**, never `REAL`. SQLite has no exact decimal
  type; a `REAL` would reintroduce the floating-point drift that `BigDecimal`
  eliminates. Converted via `BigDecimal.toPlainString()`.
- **Dates are stored as ISO-8601 `TEXT`** (`LocalDate.toString()`).
- No Android dependency, no Android SDK, no Robolectric.
- Root package: `dev.celitracker.data`.
- No personally identifiable financial data: fixtures stay synthetic.
- The Kover coverage threshold also applies to `:data`.

(Table and column names keep their original French spelling, for example
`profil`, `plafonds`, `compte`, `annee`, `montant`, `confirme`, `dateReference`,
`droitsDeclares`, and the account and transaction-type values are stored as
`CELI`, `CELIAPP`, `DEPOT`, `RETRAIT`, so existing exported data stays
compatible.)

## File structure

| File | Responsibility |
|---|---|
| `settings.gradle.kts` | adds `include(":data")` and `google()` in `pluginManagement` |
| `build.gradle.kts` (root) | declares the KSP and `androidx.room` plugins, without applying them |
| `data/build.gradle.kts` | applies kotlin/ksp/room/kover, dependencies, `schemaDirectory` |
| `data/src/main/kotlin/.../Entities.kt` | the 5 Room entities plus the type converters |
| `data/src/main/kotlin/.../CeliTrackerDao.kt` | queries |
| `data/src/main/kotlin/.../CeliTrackerDatabase.kt` | `@Database`, and the opening factory |
| `data/src/main/kotlin/.../Repository.kt` | exposes the `:engine` types, never the entities; validates input |
| `data/src/main/kotlin/.../ExportJson.kt` | serialization, versioned |

---

### Task 1: `:data` module and persisting input

**Files:** `settings.gradle.kts`, `build.gradle.kts`, `data/build.gradle.kts`,
`data/src/main/kotlin/dev/celitracker/data/{Entities,CeliTrackerDao,CeliTrackerDatabase,Repository}.kt`,
`data/src/test/kotlin/dev/celitracker/data/RepositoryTest.kt`

**Interfaces produced:**

```kotlin
class Repository(private val database: CeliTrackerDatabase) {
    suspend fun profile(): Profile?
    suspend fun saveProfile(profile: Profile)
    suspend fun limits(): List<AnnualLimit>
    suspend fun saveLimit(limit: AnnualLimit)
    suspend fun transactions(): List<Transaction>
    suspend fun addTransaction(transaction: Transaction)
    suspend fun deleteTransaction(id: Long)
    suspend fun craSnapshots(): List<CraSnapshot>
    suspend fun saveCraSnapshot(snapshot: CraSnapshot)
    suspend fun settings(): Settings
    suspend fun saveSettings(settings: Settings)
}

fun openDatabase(path: String): CeliTrackerDatabase
```

`CraSnapshot` and `Settings` do not yet exist in `:engine`: add them to
`Model.kt` (`CraSnapshot(id: Long, account: Account, referenceDate: LocalDate,
declaredRoom: BigDecimal)` and `Settings(urlPageArc: String, lastCheckDate:
Instant?)`), with the same discipline as the existing types. `Transaction`
gains an `id: Long = 0` to allow deletion.

**Input validation.** This is where, not in the engine, inconsistent input is
rejected: a code review had found that a transaction earlier than the
eligibility year produced two different results depending on which engine was
consulted. `addTransaction` throws `IllegalArgumentException` if:

- the amount is zero or negative (the sign is carried by `type`);
- the transaction is a TFSA one and predates January 1st of
  `profile.tfsaEligibilityYear`;
- the transaction is an FHSA one and predates `profile.fhsaOpeningDate`, or no
  opening date is on record.

**Required tests:** a round trip of each type on a real database in a
temporary file; a two-decimal amount read back exactly (`1234.56` stays
`1234.56`, never `1234.5600000000002`); the amount column is indeed of type
`TEXT`; each of the validation rules above; deleting a transaction.

---

### Task 2: JSON export and import

**Files:** `data/src/main/kotlin/dev/celitracker/data/ExportJson.kt`,
`data/src/test/kotlin/dev/celitracker/data/ExportJsonTest.kt`

**Interfaces produced:**

```kotlin
suspend fun Repository.exportJson(): String
suspend fun Repository.importJson(json: String)
```

**Rules:**

- The export carries an integer `version` field, valued 1. `importJson`
  refuses an unknown version with an explicit message rather than making a
  best-effort guess.
- Amounts are serialized as **strings**, never as JSON numbers: a JSON number
  passes through a `double` in most readers and would lose the precision that
  is `BigDecimal`'s whole reason for being.
- **Import replaces everything, atomically**: within a single database
  transaction, every table is cleared and then refilled. An import that fails
  partway through must never leave a half-overwritten database. Document this
  choice: it is not a merge.
- The export is **deterministic**: for the same content, two successive
  exports produce an identical string (sort collections by a stable key).

**Required tests:** a lossless round trip (export, then import into an empty
database, then re-export produces an identical string); a two-decimal amount
survives; an unknown version is refused; malformed JSON is refused without
overwriting the existing database; the export is deterministic.

---

## Out of scope

Compose interface, reading limits from the CRA site, automatic Android
backup, the Android application module, and the APK. These will come once the
Android SDK is installed; this plan does not need it.
