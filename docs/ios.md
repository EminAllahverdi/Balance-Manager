# Building Cibim for iOS

This repo is the Android app, in Kotlin and Jetpack Compose. This page is the map for an iOS version in Swift and SwiftUI: where the behaviour lives, what iOS does differently, and what to keep the same so the two apps stay compatible.

**The spec is the "Domain rules" section of [`CLAUDE.md`](../CLAUDE.md).** Every rule the app follows is written there in plain words, and is kept up to date as features land. The rest of CLAUDE.md (Gradle modules, flavors, signing, releases, the emulator) is about building the Android app, and is only background here.

## Where things are

| In this repo | What it holds | For iOS |
|---|---|---|
| `core/model` | Every rule, as pure Kotlin with no Android in it: money, spending sums, reports, loans, debts, the bank notification reader, the voice parser, the receipt reader, CSV | Port it file by file. Its tests (`core/model/src/test`) are worked examples with inputs and expected results, so port them first and make them pass |
| `core/data` | What saving does: repositories, merchant memory, pending bank entries, the starter template in English and Azerbaijani, first run | The behaviour behind each screen's Save |
| `core/database` | The Room schema, version 7, with its migrations | The data model. Copy it exactly if backups should move between the two apps |
| `core/backup` | The nightly backup: a zip of a checked database copy, settings and photos, the last 7 kept in the user's Google Drive app folder | The backup format |
| `feature/*` | One module per area of the app, matching the screens' pages | Each screen's behaviour. Its texts are in `res/values/strings.xml`, with Azerbaijani in `res/values-az/strings.xml` |
| `core/designsystem` | The Compose theme, fonts (`res/font`, licences in `docs/licenses`), components, and `BtIcons`, which maps icon names to icons | The look, in code |
| `docs/design` | The design system: principles, colours, type, spacing, patterns (`README.md`, `tokens.json`), and the app icon | The look, written down |
| `docs/design/screens` | Every screen, 96 boards by page and flow (`index.html`), Azerbaijani included | What each screen looks like |

The files in `core/model` that hold the trickiest rules:

- `BankAlert.kt`: reading a bank notification into an amount, shop, card and kind, and why one isn't read. Its word lists cover English, Azerbaijani and Russian, and know no bank by name.
- `EntryGuess.kt`: filling in a pending entry from what the user did before, recurring payments and category hints.
- `SpokenEntry.kt`: reading a spoken sentence in English or Azerbaijani against the user's own names, endings included (yanacağa, çörəyə).
- `Receipts.kt`: finding items in a receipt's recognised text, including digits text recognition tends to miss.
- `LoanMath.kt`, `Debts.kt`, `DebtEntry.kt`: loans as banks work them out, debts, bills paid for others, and paying back the oldest first.
- `Insights.kt`, `Reports.kt`, `GroceryReport.kt`: the reports' figures.

## What iOS does differently

- **Bank notifications.** iOS lets no app read another app's notifications, so the Android app's listener has no equivalent. What can feed the same reader instead, into To confirm:
  - iOS 27's Shortcuts automation that runs when a notification arrives from a chosen app, set up once per bank app, handing the notification's text to an App Intent. Whether the text reaches the intent needs proving on a device first.
  - The Wallet transaction automation (iOS 17 and up), for Apple Pay taps: amount, shop and card. It doesn't see online or physical-card payments.
  - Pasting a bank message into the app.
  
  Picking bank apps in Settings has no equivalent: an iPhone app can't see which apps are installed.
- **Background work.** There's no exact 03:00 job: a `BGProcessingTask` runs when iOS chooses, usually overnight while charging, so Back up now matters more. Reminders are local notifications scheduled for their time. The evening reminder is scheduled daily and cancelled for the day once something is dated today, which needs no background work at all.
- **Widgets.** WidgetKit sizes are small, medium, large and lock screen, not Android's 2×1 and 4×1. Tap to reveal the balance works with interactive widgets (iOS 17). Face ID can't run inside a widget, so the fingerprint mode becomes opening the app behind Face ID. The Quick Settings tile's equivalent is a Control Center control (iOS 18).
- **Quick add over the home screen.** iOS has no see-through overlay, so the app opens straight into the add sheet. Ways in: Home Screen quick actions, the Control Center control, the Action Button, a lock screen widget, and App Shortcuts through Siri, in English.
- **Voice.** Apple's Speech framework works on the device for free. Whether it supports Azerbaijani needs checking with `SFSpeechRecognizer.supportedLocales()` on a device; Siri doesn't speak it.
- **Receipts.** The QR code needs camera permission on iOS (VisionKit or AVFoundation). Apple's Vision doesn't list Azerbaijani for text recognition, so either test it on real receipts or use Google's ML Kit for iOS, the reader the Android app uses. e-kassa's public check works the same.
- **Google Drive backup.** It needs its own Google Cloud project with an iOS OAuth client and the `drive.appdata` scope. Nothing in this repo is needed for that, and no keys are in it. iCloud is the simpler choice if backups don't have to move between the two apps.
- **App lock** is LocalAuthentication, with the passcode as backup.
- **Language.** English and Azerbaijani, switched in the app as well as in iOS's per-app language setting. The texts are in each Android module's `strings.xml`, ready to move into a String Catalog. What the user typed is never translated; only the starter template comes in both languages.

## Keep the same, so the two apps stay compatible

- **Money** is a whole number of qəpik (`Int64`), never a `Double`, and always shown with two decimals.
- **CSV.** Export writes the columns `Date, Type, Amount, Account, To account, Category, Sub-category, For whom, Merchant, Note`, and import reads them by name in any order. This moves data between the two apps whatever each stores it in.
- **Backups**, only if they should restore on either phone: the same SQLite schema as Room (`core/database`, version 7, migrations included) and the same zip layout (`core/backup/BackupArchive.kt`). Otherwise SwiftData is fine.
- **Icons and colours** are stored by name and number: a category's icon is a Material Symbols name such as `shopping_cart`, and a colour is an ARGB number.
- **The starter template** keeps its English names as keys, whichever language it's written in.
- **The bank reader's word lists** know no bank by name. Words for another bank go in both apps' lists, so both read it.

## Following what changes here

- Watch the repo's Releases on GitHub. `dev-<n>` builds are tries; a `v1.x.y` tag is a release.
- Each commit message says what changed and why. A changed rule shows up in `core/model` and its tests, and in CLAUDE.md's Domain rules, so those diffs are what to port.
- New screens are drawn in `docs/design/screens` before or as they're built.

## As in the Android app

There's no server: nothing leaves the phone except the user's own backup. Real names, balances and card numbers never go in this repo.
