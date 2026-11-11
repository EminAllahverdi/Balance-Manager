# Cibim

An Android app for logging every expense and income in one household, quickly enough that nothing gets missed, and showing clearly where the money went and who it was for. It speaks English and Azerbaijani, and keeps everything on the phone, with a nightly backup to the user's own Google Drive. There's no server.

What it does:

- Add an expense, income, transfer or debt in a few taps, from the app, a home-screen widget, a launcher shortcut, the Quick Settings tile, or by voice.
- Every expense has a category and a person it was for, so months can be seen by category or by person.
- Bank notifications from the apps you pick become ready-made entries to confirm. Nothing is added without you.
- Accounts and balances, recurring payments with reminders, bank loans, debts with people, and bills paid for others.
- Reports: spending by week, month or year, and a groceries report built from receipts read off their QR code or a photo.
- App lock, a balance that hides on the widget, CSV export and import.

## Building

Android Studio, or from the command line:

```
./gradlew :app:assembleDevDebug      # Cibim Dev, beside the real app on one phone
./gradlew testDebugUnitTest :core:model:test
```

Releases are built by CI and published on GitHub Releases. `CLAUDE.md` describes the modules, the rules the app follows, and how releases and signing work.

## Design

Every screen is in [`docs/design/screens`](docs/design/screens) (open `index.html` in a browser), and the design system (colours, type, spacing, patterns) is in [`docs/design`](docs/design).

## Privacy

[`docs/privacy.md`](docs/privacy.md)
