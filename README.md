# celi-tracker

[![English](https://img.shields.io/badge/lang-English-1E4FB8)](README.md)
[![Français](https://img.shields.io/badge/lang-Fran%C3%A7ais-7A8295)](README.fr.md)

Personal Android app that tracks contribution room for a TFSA (tax-free
savings account) and an FHSA (first home savings account). In French these are
the CELI and the CELIAPP, hence the project name.

It replaces an Excel workbook where each year's room was typed in by hand
instead of being calculated. Fixing an amount in the transaction log put
everything else out of sync, with no warning.

## How it works

The database stores no calculated result. It only holds what the user enters:
the profile, the table of annual limits, and the log of deposits and
withdrawals. The app recalculates contribution room from those three inputs
every time it displays it. There is no table of room per year.

The TFSA and the FHSA each have their own calculation engine. Their rules
differ on when room starts to build up, whether a withdrawal gives room back,
the lifetime limit and how unused room carries forward, so a single engine
switched by a flag would not hold up.

## Status

The design is in
[`docs/superpowers/specs/`](docs/superpowers/specs/2026-09-07-tfsa-fhsa-tracker-design.md).

Done:

0. Command-line Android SDK setup and Gradle wrapper
1. Calculation engine (pure Kotlin module) and its tests
2. Room persistence, JSON export and import
3. Compose interface: home, account detail, settings
4. Transaction log: add, edit, delete
5. TFSA limits already filled in, plus the current year's limit read from the
   CRA website, proposed and then confirmed by hand
6. Backup: automatic copy to the Google account and device-to-device transfer,
   plus JSON file export and import from the settings
7. English and French interface, light and dark themes, two-pane layout on
   tablets
8. CRA figure: enter the room your CRA account shows and its date, and the
   home screen sets it beside the calculated room without replacing it

Next:

9. APK

## Disclaimer

This project gives no tax or financial advice. The amounts shown are
calculated from the data the user enters and have no official value. To know
your contribution room, rely on My Account at the Canada Revenue Agency.

## Privacy

The repository is public and holds no named financial data. The test
scenarios are made up, and `.gitignore` excludes databases and JSON exports.

## License

[PolyForm Shield License 1.0.0](LICENSE). Use, modification and redistribution
are free, except to build a product that competes with the licensor's. This
restriction does not expire.
