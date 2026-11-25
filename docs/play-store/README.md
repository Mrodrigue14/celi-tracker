# Publishing on Google Play

The plan: an internal test first, to install the app on the owner's phone
through Play, then a closed test, then the public listing.

Everything here was checked against the Play Console help pages on
2026-10-14. Play rules change often, so when a Console screen disagrees with
this file, follow the Console.

## What is ready in the repository

| Item | Where |
|---|---|
| Signed app bundle | `./gradlew :app:bundleRelease`, then `app/build/outputs/bundle/release/app-release.aab` |
| Privacy policy | [`PRIVACY.md`](../../PRIVACY.md), French in [`PRIVACY.fr.md`](../../PRIVACY.fr.md) |
| Store text, English and French | [`listing.md`](listing.md) |
| App icon, 512 × 512 | [`graphics/icon-512.png`](graphics/icon-512.png) |
| Feature graphic, 1024 × 500 | [`graphics/feature-en.png`](graphics/feature-en.png), [`graphics/feature-fr.png`](graphics/feature-fr.png) |
| Phone screenshots, 1080 × 1920 | [`graphics/screenshots/`](graphics/screenshots/), `en-*` and `fr-*` |

The bundle targets API 36, the minimum for new apps since 2026-08-31. Its two
native libraries (`libsqliteJni.so`, `libandroidx.graphics.path.so`) are
16 KB aligned. The screenshots come from the emulator, with the synthetic data
from `CLAUDE.md` plus two made-up 2026 deposits. Never take them on a phone that
holds real figures.

## 1. Developer account

Create a **personal** account at [play.google.com/console](https://play.google.com/console):
a one-time fee of US$25 and an identity check.

A personal account created after 2023-11-13 cannot publish to production right
away. It first needs a closed test with at least 12 testers opted in for 14 days
in a row, then an application for production access (step 5). Internal
testing has no such requirement.

## 2. Create the app

- App name: `TFSA Tracker`. Default language: English (Canada).
- App, not game. Free.
- App signing: let Google generate and protect the app signing key.
  `local-data/celi-tracker-release.jks` then becomes only the *upload key*. If
  it is lost, Google support can reset it. Play-installed builds are therefore
  signed by Google's key, not this keystore: an APK installed by hand can't be
  updated by Play. Export the JSON, uninstall it, install from Play, import.

## 3. App content (Policy and programs > App content)

| Form | Answer |
|---|---|
| Privacy policy | `https://github.com/Mrodrigue14/celi-tracker/blob/main/PRIVACY.md` |
| Ads | No ads |
| App access | All functionality is available without special access |
| Content rating | The category closest to a utility or reference app. Every content question: No |
| Target audience | 18 and over only. TFSA room starts at 18, and staying out of the children's age groups keeps the app out of the Families policy |
| Data safety | No user data collected, none shared. See below |
| Financial features | "My app doesn't provide any financial features" |
| Government app | No. The app is not affiliated with the CRA |
| News app, health app | No |
| Advertising ID | The app does not use it |

### Data safety

Play defines collection as data sent off the device. The app
sends nothing to the developer. Two things leave the phone, and neither is
collection by the developer:

- the canada.ca page load, which carries no user data;
- the Android backup, which goes to the user's own Google account and that the
  developer can't read. Play's help gives the closest case in its FAQ: files a
  user sends to their own Drive, which the app never accesses, need no
  declaration.

The privacy policy states both, so form and policy agree. This answer follows
the help page, which says nothing about Android backup itself. If the form's
wording points the other way,
declare "Financial info > Other financial info" and "Personal info > Other
info" (the year of birth) as collected for backup only, encrypted in transit,
and deletable.

## 4. Store listing (Grow > Store presence)

- Text: [`listing.md`](listing.md). Add French (Canada) as a translation.
- Graphics: the `graphics/` folder. Use the matching language for the feature
  graphic and screenshots.
- Category: Finance. Contact email: required and shown publicly. Website: the
  GitHub repository.
- Do not use the CRA or Government of Canada logos, or wording that suggests
  they endorse the app.

## 5. Releases

### Internal testing

Testing > Internal testing: add your own Google account
as a tester, create a release, upload the `.aab`, roll it out. Open the
opt-in link on the phone and install from Play.

### Closed testing

Testing > Closed testing: a list of at least 12 Google
accounts. Each must accept the invitation and stay opted in for 14 days. The
Console shows when the requirement is met.

### Production

Apply for production access: Play asks how the closed test
went, who the app is for and what changed after the test. Then create the
production release. Countries: Canada only.

## Every new version

1. Raise `versionCode` in `app/build.gradle.kts` by one. Play refuses a
   version code it has already received, on any track, even a test one.
2. `./gradlew :app:bundleRelease` with the signing keys in `local.properties`.
3. Upload the `.aab` to a track, test it, then promote it.
