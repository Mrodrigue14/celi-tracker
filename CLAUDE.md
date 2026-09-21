# CLAUDE.md

Guidance for Claude Code (claude.ai/code) in this repository.

## Project

Personal Android app (Kotlin, Jetpack Compose, Room) that tracks TFSA and FHSA
contribution room (CELI and CELIAPP in French). It replaces an Excel workbook.
Single user, no backend, no authentication.

The reference design is
`docs/superpowers/specs/2026-09-07-tfsa-fhsa-tracker-design.md`. Read it before
changing the calculation engine.

Development happens without Android Studio: JDK 21 plus the Android SDK
installed with `sdkmanager` (`cmdline-tools`).

## Language

Code, comments, test names, docs and commit messages are in English. The app
ships English (`app/src/main/res/values/strings.xml`, the default) and French
(`values-fr/strings.xml`). The README and PR descriptions are bilingual,
English first.

## Invariants: do not break

**1. Nothing computed is stored.** The database holds the profile, the table of
annual limits and the transaction journal. Nothing else. Contribution room is a
pure function `(profile, limits, transactions sorted by date) -> room`,
recomputed on read. Adding a `room_by_year` table or caching a balance in the
database reintroduces exactly the Excel workbook bug this project fixes.

**1a. The TFSA eligibility year is computed, never entered.**
`Profile.tfsaEligibilityYear` is a derived property: the year the user turns
18, never before 2009. It assumes Canadian residence since that age, which is
true for the only user. Storing it or asking for it again in the UI would give
two sources for the same fact.

**1b. No hardcoded user-facing text.** Every displayed text lives in
`app/src/main/res/values*/strings.xml`. `:engine` and `:data` never return a
sentence: they return typed reasons (`InputRejectionReason`,
`ImportFailureReason`, `CraFailureReason`, `AddressRejectionReason`) that `:app`
translates. ViewModels produce `UiText` (resource plus arguments), resolved in
the device language at display time. An amount is always formatted in Canadian
dollars whatever the language: only the separators change.

**1c. Stored names stay French.** Room table and column names, JSON backup
field names, stored enum values (`CELI`, `CELIAPP`, `DEPOT`, `RETRAIT`) and the
theme preference values (`SYSTEME`, `CLAIR`, `SOMBRE`) predate the move to
English and are already on users' devices. They are spelled out explicitly
(`@ColumnInfo`, `@SerialName`, `storedValue()`), so renaming Kotlin code never
changes them. `SchemaV2CompatibilityTest`, `ExportJsonTest` and
`ThemePreferenceTest` fail if they drift. Changing one of them needs a Room
migration or an export version bump, never a silent rename.

**2. Two separate engines, TFSA and FHSA.** Do not merge them into one engine
driven by an account-type flag. The rules differ everywhere:

| | TFSA | FHSA |
|---|---|---|
| Room starts accruing | at 18 with residence, even without an account | when the account is opened |
| Withdrawal | gives room back on the next January 1 | **never** gives room back |
| Lifetime limit | none | $40,000 |
| Unused room carry-forward | unlimited, cumulative | capped at $8,000, **not cumulative** |

The FHSA carry-forward is the trap of the plan: it is capped at $8,000 *per
year of arrival*, so it does not accumulate. Three years without contributing
do not give $32,000 of room: the annual limit levels off at $16,000. Writing
`carryForward += remaining` instead of `carryForward = min(remaining, 8000)`
produces a wrong but plausible number.

Reusing the TFSA room-restoration path for the FHSA is the most likely
correctness bug in this project.

**3. `BigDecimal`, never `Double`.** These are exact sums of money, and a
floating-point drift on an overcontribution comparison is unacceptable.

**4. An unconfirmed limit is left out of the calculation.** A limit read
automatically from the CRA website is saved with `confirmed = false` and stays
inert until the user confirms it. No silent change to contribution room.

**5. No named financial data in the repository.** Check it mechanically before
committing: `bash tools/check-privacy.sh`. The private patterns live in
`local-data/private-patterns.txt`, which is gitignored (the script still reads
the older name `local-data/motifs-prives.txt`). Writing them in a tracked file
would publish them, which is precisely the problem. Without that file the
script checks nothing and says so. The repository is public. Test fixtures are
anonymous scenarios, with no institution name and no first-person wording.
Databases and JSON exports are excluded by `.gitignore`.

## Acceptance test

`scenario_2019_eligible_three_deposits`: eligible in 2019, no withdrawal,
deposits of $5,000.00 (2021-03-10), $3,500.00 (2023-06-15) and $1,200.00
(2024-11-02), limits 6,000 / 6,000 / 6,000 / 6,000 / 6,500 / 7,000 / 7,000 /
7,000 (2019 to 2026).

Expected room at the end of 2026: **$41,800.00**. Synthetic scenario, derived
from the annual limits published by the CRA. Check: sum of limits $51,500 minus
deposits $9,700 = $41,800.

`scenario_fhsa_opened_2023_no_contributions`: FHSA opened in April 2023, no
contribution. Expected 2026 room: **$16,000**, not $32,000. Lifetime limit left
$40,000. End of the participation period: **2038-12-31**.

This scenario has **no withdrawal**, so the room-restoration path has no
external validation: only synthetic fixtures cover it, and they deserve the
same rigour.

## Git

`main` is protected: the "Build & test", "Analyze (java-kotlin)" and "Revue des
dépendances" checks must pass, and the branch must be up to date with `main`
before merging. The last check keeps its French name because branch protection
refers to it by name. Work on a branch, open a PR, let CI go green before
merging.

Dependabot patch and minor PRs are auto-merged once CI is green. Major bumps
are never auto-merged: `.github/dependabot.yml` stops Dependabot from opening
them for Gradle, and the `if:` of the auto-merge workflow excludes any major
bump for the other ecosystems. They stay in manual review.

## Style

Run `./gradlew ktlintFormat` before committing; CI runs `ktlintCheck` before
the tests. The style lives in `.editorconfig` (`intellij_idea`, chosen by
measuring the existing code: it asked for the fewest corrections). Code
generated by KSP under `build/` is excluded, and `@Composable` functions keep
their PascalCase.

## Version constraints

**Kotlin is capped by CodeQL, not by Gradle.** The CodeQL Kotlin extractor
rejects any version it does not know yet (`KotlinVersionTooRecentError`), and
`Analyze (java-kotlin)` is a required check on `main`. A Kotlin bump that runs
too far ahead therefore fails in CI. That is intended, and that check has the
last word. No cap is pinned in `dependabot.yml`: the PR simply stays blocked
until CodeQL catches up, which resolves itself without maintenance.

Observed on 2026-09-08: Kotlin 2.4.20 rejected, 2.4.10 accepted.

## License

PolyForm Shield License 1.0.0: free to use, modify and redistribute, except to
build a competing product. Do not relicense or remove the copyright notice
(`Required Notice`) at the top of `LICENSE`.

No third-party code is copied into this repository, so there is no `NOTICE` and
no `third_party/`. If code under another license is added later, they will be
needed.
