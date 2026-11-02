# Cibim — notes for Claude Code

Personal Android app for tracking every expense and income for one household, called Cibim. It replaces the Money Manager app. The repo, the code and the package name `com.eminallahverdi.balance` stay Balance: the package can't change now that real data and the Google sign-in depend on it. Owner: Emin, a senior Android developer. The repo is **public**: never commit personal data, backups (`*.mmbackup`, `*.db`), real names of family members, or signing keys.

## Where the design lives
- Design system (tokens, type, guidelines): https://claude.ai/artifact/4ifuwdoahAni7s3raTtnZJ. A copy is in `docs/design/` (`tokens.json`, `README.md`).
- Screens (approved for the MVP): https://claude.ai/artifact/SxvUVfe8cVcaBCsfdG8jhW
- Keep the design current: once a UI change is confirmed on a device, add anything the artboards don't show to the screens file, and to the design system if it's a new pattern. Features not built yet stay in the design as they are.
- Compose implementation: `core/designsystem`. `BtTheme.colors` and `BtTheme.type` mirror `docs/design/tokens.json` one to one. When a token changes, update both.
- Picking one thing always opens a bottom sheet (`ChoiceSheet` and `OptionRow`), never a dropdown menu or spinner, even for a short list.

## Stack
Kotlin and Jetpack Compose (Material 3 under our own design system), Hilt, Room, DataStore, Coroutines/Flow, type-safe Navigation Compose, Glance widgets, WorkManager. No backend. Backup goes to the hidden app-data folder in the user's own Google Drive.

Background work must stay cheap on battery:
- No polling, exact alarms, foreground services or wake locks of our own. Prefer events (Room flows, the notification listener) to checking on a timer.
- At most one daily WorkManager job per feature, doing its work and ending in seconds. Reminders accept arriving late under Doze, which is what makes them cheap.
- Drive backup runs once a night at 03:00. It waits for Wi-Fi, or any connection if the user allows mobile data, and doesn't wait for charging: the owner wants it every night, and it takes seconds. With no network at night it runs as soon as there is one.
- Glance keeps a job awake for about 45 s after every widget redraw, so redraw only when something a widget shows has changed.
- A plain daily period slips later by however late each run was. Work tied to a time of day books its next run with `bookDailyWork`, as the evening reminder, payment reminders and the widgets' midnight refresh do.

## Modules
```
build-logic/convention   Gradle convention plugins: bm.android.application | library | compose | feature, bm.hilt, bm.room, bm.jvm.library
app                      MainActivity, QuickAddActivity (translucent, opens over the home screen), navigation, bottom bar, launcher shortcuts, Quick Settings tile (add expense), EveningReminder, AppLock (locks after 30 s away; quick add stays outside it, since it shows no amounts and must stay fast)
core:model               Pure Kotlin. Money (minor units), Person, Category (parentId = sub-category), Account, Transaction(Kind), SpendingCalculator, pieSlices, transactionsToCsv
core:common              Dispatchers, MoneyFormat (₼), DateLabels, DeviceUnlock (fingerprint with the phone's PIN as backup, for the app lock and the widget), bookDailyWork
core:designsystem        Theme, bundled fonts (Manrope, Space Grotesk), BtIcons (string key -> ImageVector), components, DonutChart, MonthPicker. `tileGlyph` darkens pale category icons on Light
core:database            Room entities and DAOs (table `txn`; amounts in qəpik; dates as epochDay)
core:datastore           UserPreferences (theme, default expense account, widget balance mode, app lock …)
core:data                CatalogRepository, TransactionRepository (merchant memory + suggestions), BankAlertRepository (bank notifications into pending entries), StarterTemplate
core:backup              Nightly backup to Drive and restore: BackupArchive (zip of a checked database copy, settings and photos), DriveAuthorization (Google Identity), DriveFiles (Drive REST), NightlyBackup (WorkManager). A restore is staged, then swapped in by applyPendingRestore at the next start, before Hilt opens anything
feature:home             Swipeable month summary, by category / by person, split bars. Rows open that month's transactions. The month name sits on the page and opens a month sheet. The card's icon swaps the balance for a pie chart of expenses or income, and stays as left (UserPreferences)
feature:transaction      Quick add: step 1 amount + category + keypad, step 2 details (sub-category, merchant, for whom, 7-day date strip + calendar, note)
feature:catalog          Categories, sub-categories and people: add, edit, drag to reorder, archive and restore, photo avatars (AvatarStore in core:data)
feature:history          Search, month/person/category/account filters in bottom sheets, day groups. Tapping a row opens quick add to edit or delete it. The History tab, and from Home the same screen pre-filtered with a back button (TransactionsDestination)
feature:insights         Week/month/year: stacked bars by person (tap a bar to select it), daily average, saved, biggest category changes. Numbers come from InsightsCalculator in core:model. A period is always compared with the whole previous one, even while it's still running: the owner's choice
feature:settings         Default expense account, theme, app lock, widget balance, hide amounts, bank notifications and their apps, evening reminder switch and time, Drive backup (switch, mobile data, back up now, restore), Export CSV, Money Manager import, way into the catalog and recurring payments. Rows are added as the features behind them land
feature:confirm          BankNotificationListener (a NotificationListenerService) and the To confirm page: ready-made entries with Edit, Skip and Confirm, and the auto-confirm switch
feature:recurring        Reminders for payments that repeat. Due ones show here, on Home and in a daily notification until logged or skipped. Add opens quick add filled in. PaymentReminders clears a reminder once its entry is in the list
feature:accounts         Balances, default account, in-total and widget switches, add/edit/archive, a card's last four digits, adjust balance, recent transfers, way into Loans & debts. Transfers themselves are added in quick add
feature:loans            Bank loans (recurring payments with LoanTerms, so they remind the same way), credit cards used this month, and debts with people, grouped by person. Pay one, part of one, or all. DebtRepository in core:data
widget                   Glance 2x1 balance (always show, always hide, tap or fingerprint to reveal, with a + that always adds an expense), 2x1 to confirm, 4x1 actions, 4x2 today + one-tap categories. WidgetRefresher redraws them when data changes and at midnight
importer:moneymanager    Reads a Money Manager backup on the device (a zip behind a few stray bytes, holding SQLite MyFinance.db), plans the import with `planImport`, then replaces accounts, entries, loans and reminders in one database transaction. People and categories stay, and missing ones are added, so re-running corrects the last run. An account keeps its saved card digits if its name is unchanged. Settings > Data > Import from Money Manager
```

## Domain rules
- Money is always `Long` minor units. Never use floating point.
- Amounts always show two decimals (₼4.40, ₼3,435.00), even where the design mock-ups show whole numbers. In a tight space the text shrinks; it's never cut off or rounded. The one exception is the small label above the selected chart bar, where 1,000 and up reads 3.4k; the card's heading has the exact figure.
- Every expense has a category (or a sub-category, whose parent is the reporting category) and a **person** ("For whom"). Each category has a default person.
- People and categories are fully user-editable: add, remove, reorder, colour, and photo avatars for people. Removing something that's in use **archives** it instead of deleting it.
- Balances are always today's, so Home shows Total balance and the account chips only on the current month. Past months show Spent and Income.
- The eye (Settings: "Hide balance and income") hides balances, account chips, income, saved, transfers, adjustments and money borrowed or lent. Spending stays visible: the owner finds it less sensitive.
- Transfers move money between accounts and never count as spending or income. `ADJUSTMENT`, `INTERNAL` and `DEBT` carry a signed amount and are left out of charts.
- Bank loan payments are normal spending everywhere. A loan finishes when its payments run out, and logging a payment counts towards it; skipping one doesn't.
- A debt is money between the owner and someone outside the household, so its name is plain text, not a `Person`. With a category it's something they paid for the owner, like a share of lunch: no money moves until it's paid back, and then it's an ordinary expense in that category. Without one it's a plain loan: `DEBT` entries change a balance when it's made and when it's repaid, and are never spending or income. What's left is worked out from a debt's entries, so editing or deleting one keeps it right.
- Nothing adds a transaction by itself: the owner logs every entry so they see what they spent. Automation stops at a ready-made entry they save, as recurring reminders do. The one exception is Auto-confirm known merchants on To confirm: it's off by default, turning it on always asks first, and it only adds merchants the user has confirmed themselves. An entry counts as a reminder's when it has the same name, or the same category and amount, from 10 days before its date (2 for weekly), because billing dates move.
- Bank notifications stay on the device, and only the apps the user picks in Settings are read. For other apps, only the package name is kept, so they can be picked. Nothing depends on a particular bank, and app package names are deliberately not hardcoded, because the app is for anyone. `parseBankNotification` in core:model looks for a sum in manats with a word for money moving. A foreign card payment is read from the manat figure sent with it, which includes commission; a separate commission line, an exchange rate and a balance are never the amount. Login codes and offers are ignored. A plus sign before the sum, or a word for money arriving, makes it income. Taking cash out (Nağdlaşdırma, an ATM and the like) is a `TRANSFER`, whichever shop's machine it came from. A shop's name loses what card statements add: the processor before a star, a billing word after, and a payment reference that changes with every charge, so ANTHROPIC* CLAUDE SUB reads as Claude. Payments and withdrawals were checked against the wording of the owner's main bank, posted on the emulator. It hasn't run on a real notification yet and money arriving hasn't been seen, so expect to adjust its wording lists.
- `guessFor` in core:model fills in a pending entry, in order: a merchant the user confirmed, another spelling of a merchant they've used, a recurring payment or loan by name or by its amount within 10 days of its date, then category names and `CATEGORY_HINTS` in the notification's merchant and title. Anything but a confirmed merchant is marked Suggested, and auto-confirm never acts on it. When the real backup is imported, check the hints still point at the owner's categories. Anything personal goes in the on-device mapping file, not the hints, because this repo is public.
- A notification's card decides its account, ahead of every other guess, when an account has that card's last four digits saved (Accounts, edit an account). Those digits are the only card detail kept, and they stay on the device and in its backup. A withdrawal goes from that account to the first cash account, with no merchant, category or person, so it can't count as spending and auto-confirm never acts on it. With no cash account it has no Confirm, and Edit opens quick add as a transfer.
- The evening reminder goes off at the time set in Settings, only if nothing is dated today, and opens quick add. Adding an entry clears it. A run so late it's the next morning stays quiet.
- Every Room schema change needs a migration, because the owner's real data lives in this database. From the import on, treat the data on the phone as real: never wipe it to test something.
- A backup keeps the last 7 in the app-data folder, which only this app can see. A night with no backup shows on Home from about 30 hours after the last one (`isBackupOverdue`), and it taps through to Settings. With backups off, nothing nags: that was the user's choice. The debug and release apps share backups as long as both are registered in the same Google Cloud project.
- The import is told how to map things by a JSON file on the device (`ImportMapping` documents it), because it names the owner's family. It never goes in this repo, and `*-mapping.json` is gitignored. Test data for the importer uses invented names only. In the old app, tags did three jobs (people, sub-categories, merchants), and the categories named after people really meant "for whom", so both become the For whom field. Entries with no category were balance corrections and import as `ADJUSTMENT`. Taksit income was an offset for paying by card, and the bank loan paid out was not earnings, so both import as `INTERNAL`: the balance stays right and they aren't income.
- The app must work for anyone the owner shares it with. First run shows onboarding: name, people, a category template, and optionally an import or a Drive restore.

## Commands
```
./gradlew testDebugUnitTest :core:model:test   # unit tests, all modules (core:model is pure JVM, so it has `test`, not `testDebugUnitTest`)
./gradlew :app:assembleDebug         # debug APK (applicationId com.eminallahverdi.balance.debug)
./gradlew :app:installDebug          # to a connected phone
```
CI (`.github/workflows/android.yml`) runs both on every push and uploads the debug APK.

The backup round trip is an instrumented test, which CI doesn't run. Always aim it at the emulator, or it installs to the phone too:
```
ANDROID_SERIAL=emulator-5554 ./gradlew :core:backup:connectedDebugAndroidTest
```

Drive runs through the owner's Google Cloud project, Cibim: Drive API on, published to production (the `drive.appdata` scope needs no review, but testing mode ends consent after 7 days), and an Android OAuth client for each package name and signing SHA-1. The debug app's client exists. The consent screen links to this repo and `docs/privacy.md`, so moving that file means updating Branding. A build with no client says Drive isn't set up.

The AVD `Pixel_3a_API_30` (Android 11) boots with `emulator -avd Pixel_3a_API_30`. `installDebug` installs to everything connected, so while the phone is plugged in, install to the emulator alone with `adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk`. Test bank notifications with `adb -s emulator-5554 shell "cmd notification post -S bigtext -t 'Bank' tag 'Payment 12.40 AZN, SHOP'"`, which arrive from the app called Shell. Rounded widgets need Android 12, so they're square there.

## Status (branch feat/mvp-foundation)
The build is green locally. `app` has navigation (Home, History, Insights, Accounts, Settings) with the bottom bar and centre + button, `QuickAddActivity` hosting `QuickAddRoute`, and `FirstRunSeeder` applying `StarterTemplate.household()` until onboarding exists. `feature:catalog` is reached from Settings. Every tab is a real screen. The home-screen widgets and the launcher shortcuts (add expense, add income, transfer) work. Settings has only rows that work. The owner's real Money Manager history is imported on their phone (debug app) and checked against the backup: every balance and month matched.

Next, in order:
1. Finish proving Drive on the phone. Sign-in and a real upload work, and the first night's run is booked for 03:00. Still to see: that night's run, and a restore from Drive, which has only been proven by the round-trip test.
2. Finalizing, starting with the signed release build. Real data should live in a real install. The debug and release apps have separate data, so the import or a Drive restore is run again in the release app, and the release key's SHA-1 needs its own OAuth client in the same Google Cloud project.
3. First-run onboarding, which also asks for the notification permission and offers a Drive restore: the evening reminder is on by default, so nothing else asks on a fresh install.
4. Wear OS, very last.

Leave quick add's input screen alone: the owner is planning to change it, and merchant chips stay as they are until then.

Not seen on a real device yet: the widget picker previews, which need Android 12, and the Home backup notice, which only shows with Drive working.
