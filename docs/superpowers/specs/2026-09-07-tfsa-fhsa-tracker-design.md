# TFSA / FHSA contribution room tracker: design

Date: 2026-09-07
Status: awaiting review

## Problem

The current tracker is an Excel workbook (`gestion_CELI.xlsx`, 4 sheets). It
works, but it has a structural flaw: the "Start-of-year room" and "End-of-year
room" columns from 2023 to 2046 are **hand-entered values**, not formulas. The
workbook *documents* the withdrawal carry-forward rule ("Withdrawals in a year
are added back to your room on the following January 1st") but never
calculates it anywhere. A single correction in the transaction log silently
desynchronizes the entire room table.

Secondary flaws observed:

- Asymmetry between the accounts: the TFSA sheet keeps a contribution log but
  no investment tracking; the FHSA sheet keeps investment tracking but no
  contribution log ("Your total contributions to date" is frozen at 0, so
  "remaining room" always shows $40,000).
- No overcontribution detection, even though the CRA penalty (1% per month on
  the excess) is the real risk in this tracker.
- Nothing on the FHSA deadline: no opening date, no 15-year / age-71
  countdown, even though the rule is written in the sheet.
- Future annual limits are set to 0 and have to be updated by hand.

## Scope

**In scope.** Tracking TFSA and FHSA contribution room: a log of deposits and
withdrawals, exact year-by-year room calculation, withdrawal carry-forward,
overcontribution detection, FHSA deadline.

**Out of scope.** The stock portfolio tracking from the "Non-registered" and
"FHSA" sheets (buys, sells, gain/loss, taxable capital gain) is not carried
over. No market prices, no open positions, no market value.

**Single user.** No account, no authentication, no backend to build. One
person, one device.

**Platform.** Native Android app: Kotlin, Jetpack Compose, Room. The
development machine has JDK 21; the Android SDK is installed from the command
line (`cmdline-tools` / `sdkmanager`), without Android Studio.

## Guiding principle: store nothing that is calculated

This is the fix for the Excel workbook's structural flaw. The database holds
only **raw input**. Contribution room is a **pure function** of `(profile,
limit table, transactions sorted by date)`, recalculated every time it is
displayed.

There is therefore **no** `room_by_year` table. Creating one would
reintroduce exactly the bug this design fixes.

A useful consequence: since the balance can be replayed at any date,
overcontribution detection comes with no extra machinery. The CRA penalty
applies to each month's highest excess, which requires exactly a balance
that can be replayed in chronological order.

## Data model

```
Profile (singleton)
  birthYear         : Int        // derives TFSA eligibility (age 18, no earlier than 2009)
                                  //  and the FHSA age-71 rule
  fhsaOpeningDate    : LocalDate? // start of accumulation AND the 15-year clock

AnnualLimit
  account   : Account       // TFSA | FHSA
  year      : Int
  amount    : BigDecimal
  source    : Source        // MANUAL | CRA_AUTO
  confirmed : Boolean       // an unconfirmed limit does not enter the calculation

Transaction
  id          : Long
  account     : Account
  date        : LocalDate
  type        : TransactionType // DEPOSIT | WITHDRAWAL
  amount      : BigDecimal      // always positive
  institution : String?
  notes       : String?

CraSnapshot
  id            : Long
  account       : Account
  referenceDate : LocalDate
  declaredRoom  : BigDecimal

Settings (singleton)
  urlPageArc    : String   // editable, not a compiled constant
  lastCheckDate : Instant?
```

Amounts are stored as `BigDecimal`, never `Double`: contribution room is an
exact sum of money, and floating-point drift in an overcontribution
comparison is unacceptable.

(The database columns and the JSON export keep their original French names,
for example `anneeNaissance`, `dateOuvertureCeliapp`, `compte`, `annee`,
`montant`, `confirme`, `dateReference`, `droitsDeclares`, and the account and
transaction-type values are stored as `CELI`, `CELIAPP`, `DEPOT`, `RETRAIT`,
so existing exported data stays compatible.)

### The CRA snapshot is informational, never authoritative

The user periodically enters the room amount the CRA shows, along with its
reference date. The app displays the calculated figure and the declared
figure side by side, and highlights the difference.

The snapshot **never replaces** the calculation. CRA figures lag behind
return processing and typically do not include the current year's
contributions; letting them override the calculation would double-count or
erase recent contributions. The interface always shows the reference date so
the figure's freshness is visible.

## TFSA engine

```
endRoom(tfsaEligibilityYear - 1) = 0
startRoom(A) = endRoom(A - 1) + limit(A) + withdrawals(A - 1)
endRoom(A)   = startRoom(A) - deposits(A)
```

Withdrawals from a given year are added back to room on **January 1st of the
following year**, not immediately.

**Overcontribution.** For each month, the excess is `max(0, cumulative
contributions - room available on that date)`. The CRA penalty is 1% per
month, applied to the month's highest excess. The app flags the excess and
estimates the penalty; it does not produce any tax form.

## FHSA engine: separate, not a TFSA with a flag

The two regimes diverge on every axis. Sharing one engine parameterized by a
flag is the most likely way to silently corrupt one of the two accounts.

| | TFSA | FHSA |
|---|---|---|
| Start of accumulation | age 18 + residency, even without an open account | **when the account is opened** |
| Withdrawal | restores room the following January 1st | **never restores room** |
| Lifetime limit | none | $40,000 |
| Carry-forward of unused room | unlimited | capped at $8,000 |

```
yearRoom(A)        = min( 8000 + min(carryForwardIn(A), 8000),
                          40000 - cumulativeContributions(before A) )
carryForwardOut(A) = min( yearRoom(A) - deposits(A), 8000 )
```

The $8,000 annual limit and the $40,000 lifetime limit are set by law and
**are not indexed**: there is nothing to fetch automatically for the FHSA.

**The carry-forward does not accumulate.** This is the regime's trap, and the
reason for the `min(carryForwardIn, 8000)` above. Unlike the TFSA and the
RRSP, where unused room accumulates indefinitely, the FHSA carry-forward is
capped at $8,000 **per year of arrival**. Someone who opens an account and
never contributes does not gain another $8,000 every year: their annual limit
stabilizes at $16,000 and stops growing. The $40,000 lifetime limit, however,
stays intact, so it takes at least three years of contributions ($16,000 +
$16,000 + $8,000) to exhaust it.

**Maximum participation period.** It ends on December 31 of the year in which
the **first** of the following three events occurs:

1. the 15th anniversary of the first FHSA's opening;
2. the holder's 71st birthday;
3. the year following the first qualifying withdrawal.

The app calculates branches 1 and 2 and warns as the deadline approaches.
Branch 3 is not implemented: see the deliberate exclusions below.

### Deliberate exclusions

These are decisions, not oversights.

- **FHSA withdrawals reduce the balance and never affect room.** The
  distinction between a qualifying withdrawal (a first home purchase,
  non-taxable) and an ordinary withdrawal (taxable) is not tracked.
- **The "year following the first qualifying withdrawal" rule is not
  implemented** in the deadline calculation, since it depends on the
  withdrawal classification above. The actual deadline may therefore be
  earlier than the one shown.
- **The FHSA tax deduction is not tracked** (amount claimed vs. carried
  forward to a future year).

## Updating TFSA limits

There is no CRA API for the TFSA limit. The official page presents the
amounts as narrative text, not a structured table. The limit is $5,000
indexed to inflation and **rounded to the nearest $500**; it moves up a step
every two or three years ($7,000 for 2024, 2025 and 2026).

The app reads the CRA page, but it is designed to **fail visibly**:

1. **Strict validation.** A limit that is read is rejected if it is not a
   multiple of 500, if it is lower than the previous year's limit, or if it
   exceeds that limit by more than $2,000. These invariants catch most
   extraction errors.
2. **Proposal, never applied automatically.** A limit that is read is saved
   with `source = CRA_AUTO` and `confirmed = false`. An unconfirmed limit does
   not enter the room calculation. The user confirms it in Settings.
3. **Visible failure.** Network unavailable, page redesigned, or amount
   rejected: a banner reads "automatic reading failed", with a button to open
   the CRA page and a manual entry field. The manual fallback is a displayed
   behavior, not a silent degraded mode.
4. **Editable URL.** `Settings.urlPageArc` is data, not a compiled constant: a
   reorganization of the CRA site is fixed inside the app, without a new
   release.
5. **Frequency.** At most one check per month, and only if a limit is missing
   for the current or the following year.

## Backup and recovery

A tracker that does not survive the loss of the phone would be a regression
compared to a synced Excel workbook. Two mechanisms, with different failure
modes:

- **Automatic Android backup** (`allowBackup`). Android copies the app's
  database to the user's Google Drive and restores it on reinstall. Cost: one
  manifest attribute.
  *Documented caveat:* restoration is triggered by an install through the
  Play Store or the setup wizard. Since the app is installed from a
  manually-loaded APK, automatic restoration may not trigger. The backup's
  content cannot be inspected.
- **Manual JSON export / import.** An "Export" button produces a readable
  file that the user stores wherever they want; "Import" reads it back. This
  is the only mechanism whose correct operation can be verified **before** it
  is actually needed.

## Screens

1. **Home**: two cards (TFSA, FHSA): remaining room, contributed this year,
   overcontribution alert, difference from the CRA snapshot.
2. **Account detail**: the year-by-year table (limit, start room, deposits,
   withdrawals, end room), calculated and not stored. For the FHSA: carry
   forward, remaining lifetime limit, deadline.
3. **Journal**: the account's transactions: add, edit, delete.
4. **Settings**: profile, editable limit table, proposed limits to confirm,
   CRA snapshot, CRA page URL, export / import.

## Tests

The calculation engine is a pure Kotlin module, with no Android dependency,
testable from the command line. It is written and verified **before** any
interface.

**Acceptance fixture `scenario_2019_eligible_three_deposits`.** A person who
became TFSA-eligible in 2019, with no withdrawals, and three deposits:
$5,000.00 (2021-03-10), $3,500.00 (2023-06-15), $1,200.00 (2024-11-02).
Limits 2019 to 2026: 6,000, 6,000, 6,000, 6,000, 6,500, 7,000, 7,000, 7,000.

Expected results, year by year:

| Year | Start room | Deposits | End room |
|---|---|---|---|
| 2019 | 6,000.00 | 0 | 6,000.00 |
| 2020 | 12,000.00 | 0 | 12,000.00 |
| 2021 | 18,000.00 | 5,000.00 | 13,000.00 |
| 2022 | 19,000.00 | 0 | 19,000.00 |
| 2023 | 25,500.00 | 3,500.00 | 22,000.00 |
| 2024 | 29,000.00 | 1,200.00 | 27,800.00 |
| 2025 | 34,800.00 | 0 | 34,800.00 |
| 2026 | 41,800.00 | 0 | **41,800.00** |

Synthetic scenario, derived from the annual limits published by the CRA.
Check: cumulative limits $51,500 minus deposits $9,700 = $41,800.

**Acceptance fixture `scenario_fhsa_opened_2023_no_contributions`.** An FHSA
opened in April 2023, with no contributions or withdrawals since.

| Year | Available room | Carry forward to A+1 |
|---|---|---|
| 2023 | 8,000 | 8,000 |
| 2024 | 16,000 | 8,000 |
| 2025 | 16,000 | 8,000 |
| 2026 | **16,000** | n/a |

Remaining lifetime limit: $40,000. End of the participation period:
**2038-12-31** (15th anniversary in April 2038).

This scenario guards against the regime's most tempting mistake: assuming
that three years without contributing accumulate $32,000 of room. The
carry-forward is capped at $8,000 per year of arrival and does not
accumulate, so the annual limit stabilizes at $16,000. An engine that returns
$32,000 for 2026 is wrong.

**Synthetic fixtures.** The acceptance scenario contains no withdrawal: the
room-restoration path is not covered by any of its fixtures and must be
tested explicitly.

- Withdrawal in year A: room restored on January 1st of A+1, and **not
  before**.
- Withdrawal followed by a re-deposit in the same year: overcontribution
  detected.
- Overcontribution over several months: penalty calculated on each month's
  maximum excess.
- FHSA: partial contribution in year A: carry-forward capped at $8,000 in
  A+1, so $16,000 contributable at most.
- FHSA: withdrawal: the balance drops, room does not move.
- FHSA: $40,000 lifetime limit reached: annual room brought down to 0.
- A year's limit missing or unconfirmed: the year is flagged as incomplete,
  no room invented.
- CRA extraction: amount not a multiple of 500, lower than before, or more
  than $2,000 higher: rejected.

## Delivery order

0. Android SDK install script (`cmdline-tools` + `sdkmanager`), without
   Android Studio. Gradle wrapper.
1. **Calculation engine**: pure Kotlin module plus tests, including the
   acceptance fixture. Verifiable from the command line, without a phone.
2. Room persistence plus JSON export / import.
3. Compose interface.
4. Reading CRA limits plus automatic Android backup.
5. Installed APK.

## Rule sources

- Closing an FHSA and the maximum participation period (the deadline's three
  branches):
  <https://www.canada.ca/en/revenue-agency/services/tax/individuals/topics/first-home-savings-account/closing-your-fhsa.html>
- TFSA annual limit and room calculation:
  <https://www.canada.ca/en/revenue-agency/services/tax/individuals/topics/tax-free-savings-account/contributing/calculate-room.html>

The FHSA's non-cumulative $8,000 carry-forward limit, the $8,000 annual
limit, and the $40,000 lifetime limit are set by law and are not indexed.

## Privacy

The repository is public. No personally identifiable financial data goes
into it: the test fixtures are anonymous scenarios, and the real journal
(database, JSON exports) is excluded by `.gitignore` from the first commit.
