# Balance Manager — notes for Claude Code

Personal Android app for tracking every expense and income for one household. It replaces the Money Manager app. Owner: Emin, a senior Android developer. The repo is **public**: never commit personal data, backups (`*.mmbackup`, `*.db`), real names of family members, or signing keys.

## Where the design lives
- Design system (tokens, type, guidelines): https://claude.ai/artifact/4ifuwdoahAni7s3raTtnZJ. A copy is in `docs/design/` (`tokens.json`, `README.md`).
- Screens (approved for the MVP): https://claude.ai/artifact/SxvUVfe8cVcaBCsfdG8jhW
- Keep the design current: once a UI change is confirmed on a device, add anything the artboards don't show to the screens file, and to the design system if it's a new pattern. Features not built yet stay in the design as they are.
- Compose implementation: `core/designsystem`. `BtTheme.colors` and `BtTheme.type` mirror `docs/design/tokens.json` one to one. When a token changes, update both.
- Picking one thing always opens a bottom sheet (`ChoiceSheet` and `OptionRow`), never a dropdown menu or spinner, even for a short list.

## Stack
Kotlin and Jetpack Compose (Material 3 under our own design system), Hilt, Room, DataStore, Coroutines/Flow, type-safe Navigation Compose, Glance widgets, WorkManager. No backend. Backup goes to the Google Drive app-data folder (planned).

Background work must stay cheap on battery:
- No polling, exact alarms, foreground services or wake locks of our own. Prefer events (Room flows, the notification listener) to checking on a timer.
- At most one daily WorkManager job per feature, doing its work and ending in seconds. Reminders accept arriving late under Doze, which is what makes them cheap.
- Uploads such as Drive backup wait for Wi-Fi and charging.
- Glance keeps a job awake for about 45 s after every widget redraw, so redraw only when something a widget shows has changed.

## Modules
```
build-logic/convention   Gradle convention plugins: bm.android.application | library | compose | feature, bm.hilt, bm.room, bm.jvm.library
app                      MainActivity, QuickAddActivity (translucent, opens over the home screen), navigation, bottom bar, launcher shortcuts
core:model               Pure Kotlin. Money (minor units), Person, Category (parentId = sub-category), Account, Transaction(Kind), SpendingCalculator
core:common              Dispatchers, MoneyFormat (₼), DateLabels
core:designsystem        Theme, bundled fonts (Manrope, Space Grotesk), BtIcons (string key -> ImageVector), components
core:database            Room entities and DAOs (table `txn`; amounts in qəpik; dates as epochDay)
core:datastore           UserPreferences (theme, default expense account, widget balance mode, app lock …)
core:data                CatalogRepository, TransactionRepository (merchant memory + suggestions), StarterTemplate
feature:home             Swipeable month summary, by category / by person, split bars. Rows open that month's transactions
feature:transaction      Quick add: step 1 amount + category + keypad, step 2 details (sub-category, merchant, for whom, 7-day date strip + calendar, note)
feature:catalog          Categories, sub-categories and people: add, edit, drag to reorder, archive and restore, photo avatars (AvatarStore in core:data)
feature:history          Search, month/person/category/account filters in bottom sheets, day groups. Tapping a row opens quick add to edit or delete it. The History tab, and from Home the same screen pre-filtered with a back button (TransactionsDestination)
feature:insights         Week/month/year: stacked bars by person (tap a bar to select it), daily average, saved, biggest category changes. Numbers come from InsightsCalculator in core:model
feature:settings         Default expense account, theme, widget balance, hide amounts, way into the catalog and recurring payments. Rows are added as the features behind them land
feature:recurring        Reminders for payments that repeat. Due ones show here, on Home and in a daily notification until logged or skipped. Add opens quick add filled in. PaymentReminders clears a reminder once its entry is in the list
feature:accounts         Balances, default account, in-total and widget switches, add/edit/archive, adjust balance, recent transfers. Transfers themselves are added in quick add
widget                   Glance 2x1 balance (always show, always hide, tap or fingerprint to reveal, with a + that always adds an expense), 4x1 actions, 4x2 today + one-tap categories. WidgetRefresher redraws them when data changes and at midnight
importer:moneymanager    to do: reads .mmbackup (zip -> SQLite MyFinance.db) on the device
```

## Domain rules
- Money is always `Long` minor units. Never use floating point.
- Amounts always show two decimals (₼4.40, ₼3,435.00), even where the design mock-ups show whole numbers. In a tight space the text shrinks; it's never cut off or rounded. The one exception is the small label above a chart bar, where 1,000 and up reads 3.4k; tapping the bar shows the exact figure.
- Every expense has a category (or a sub-category, whose parent is the reporting category) and a **person** ("For whom"). Each category has a default person.
- People and categories are fully user-editable: add, remove, reorder, colour, and photo avatars for people. Removing something that's in use **archives** it instead of deleting it.
- Balances are always today's, so Home shows Total balance and the account chips only on the current month. Past months show Spent and Income.
- The eye (Settings: "Hide balance and income") hides balances, account chips, income, saved, transfers and adjustments. Spending stays visible: the owner finds it less sensitive.
- Transfers move money between accounts and never count as spending or income. `ADJUSTMENT` and `INTERNAL` carry a signed amount and are left out of charts.
- Nothing adds a transaction by itself: the owner logs every entry so they see what they spent. Automation stops at a ready-made entry they save, as recurring reminders do. An entry counts as a reminder's when it has the same name, or the same category and amount, from 10 days before its date (2 for weekly), because billing dates move.
- Every Room schema change needs a migration, because the owner's real backup will live in this database. The data on the phone today is dummy data.
- The app must work for anyone the owner shares it with. First run shows onboarding: name, people, a category template, and optionally an import or a Drive restore.

## Commands
```
./gradlew testDebugUnitTest :core:model:test   # unit tests, all modules (core:model is pure JVM, so it has `test`, not `testDebugUnitTest`)
./gradlew :app:assembleDebug         # debug APK (applicationId com.eminallahverdi.balance.debug)
./gradlew :app:installDebug          # to a connected phone
```
CI (`.github/workflows/android.yml`) runs both on every push and uploads the debug APK.

## Status (branch feat/mvp-foundation)
The build is green locally. `app` has navigation (Home, History, Insights, Accounts, Settings) with the bottom bar and centre + button, `QuickAddActivity` hosting `QuickAddRoute`, and `FirstRunSeeder` applying `StarterTemplate.household()` until onboarding exists. `feature:catalog` is reached from Settings. Every tab is a real screen. The home-screen widgets and the launcher shortcuts (add expense, add income, transfer) work. Settings has only rows that work: app lock, reminders, backup, import and export join it with their features.

Next, in order:
1. Loans & debts page, built on recurring payments.
2. Evening reminder (WorkManager).
3. Bank notifications: read them, and offer ready-made entries on a To confirm page, with its widget and launcher shortcut. The design's "Auto-confirm known merchants" switch conflicts with the rule that nothing adds itself, so settle that with the owner first.
4. App lock, then Export CSV.
5. Signed release build and the final app name. Real data should live in a real install, and Drive sign-in needs this signing key.
6. Drive backup and restore (WorkManager).
7. First-run onboarding.
8. The Money Manager importer, which uses a mapping file kept on the device, never in this repo. The owner imports their real backup once every feature is finished. That brings their real categories, sub-categories and people, replacing today's dummy data.
9. The polish list below, then the Quick Settings tile.
10. Wear OS, very last.

Polish:
- Home's month header: the `<` chevron beside the month reads as a back button. The owner wants it redesigned (`MonthSwitcher` in `HomeScreen.kt`). Swiping already changes the month, so the arrows may not need to stay.
- Home card chart view: an icon on the Home card swaps the card's content for a pie chart of that month, with a selector for expenses or income. While the chart shows, the icon becomes the normal-view icon and switches back. The choice is saved in `UserPreferences`, so the card stays in whichever view the user left it.
- Insights charts: review the chart design and make it cleaner, more beautiful and more modern. The approved artboard is the starting point, not the limit.
- Accounts actions: Transfer's lime fill beside the outlined Adjust balance reads like a selected tab, not an action. Show the fix in the design first (edit the current design system and screens) and get the owner's approval there before changing code.
- Widget picker: the launcher shows only the app icon as each widget's preview. Give them `android:previewLayout` layouts that look like the real widgets.
- Widget loading: replace the plain loading state with a glassy wave, like Apple and Meta use: a low-opacity layer sweeping across the widget, not an aggressive shimmer or a skeleton. Home-screen widgets can't run Compose animation, so it probably needs an indeterminate `ProgressBar` with a custom sweep drawable in the loading layout.
- Quick add's account picker is the last `DropdownMenu`. Move it to a `ChoiceSheet`.
- Light theme: pale category colours such as Transport's yellow are faint on their tinted tiles, in the app and on the Today widget.
