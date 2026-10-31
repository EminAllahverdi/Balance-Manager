# Balance Manager — notes for Claude Code

Personal Android app for tracking every expense and income for one household. It replaces the Money Manager app. Owner: Emin, a senior Android developer. The repo is **public**: never commit personal data, backups (`*.mmbackup`, `*.db`), real names of family members, or signing keys.

## Where the design lives
- Design system (tokens, type, guidelines): https://claude.ai/artifact/4ifuwdoahAni7s3raTtnZJ. A copy is in `docs/design/` (`tokens.json`, `README.md`).
- Screens (approved for the MVP): https://claude.ai/artifact/SxvUVfe8cVcaBCsfdG8jhW
- Compose implementation: `core/designsystem`. `BtTheme.colors` and `BtTheme.type` mirror `docs/design/tokens.json` one to one. When a token changes, update both.

## Stack
Kotlin and Jetpack Compose (Material 3 under our own design system), Hilt, Room, DataStore, Coroutines/Flow, type-safe Navigation Compose, Glance widgets, WorkManager. No backend. Backup goes to the Google Drive app-data folder (planned).

## Modules
```
build-logic/convention   Gradle convention plugins: bm.android.application | library | compose | feature, bm.hilt, bm.room, bm.jvm.library
app                      MainActivity, QuickAddActivity (translucent, opens over the home screen), navigation, bottom bar
core:model               Pure Kotlin. Money (minor units), Person, Category (parentId = sub-category), Account, Transaction(Kind), SpendingCalculator
core:common              Dispatchers, MoneyFormat (₼), DateLabels
core:designsystem        Theme, bundled fonts (Manrope, Space Grotesk), BtIcons (string key -> ImageVector), components
core:database            Room entities and DAOs (table `txn`; amounts in qəpik; dates as epochDay)
core:datastore           UserPreferences (theme, default expense account, widget balance mode, app lock …)
core:data                CatalogRepository, TransactionRepository (merchant memory + suggestions), StarterTemplate
feature:home             Month summary, by category / by person, split bars
feature:transaction      Quick add: step 1 amount + category + keypad, step 2 details (sub-category, merchant, for whom, 7-day date strip + calendar, note)
feature:catalog          Categories, sub-categories and people: add, edit, drag to reorder, archive and restore, photo avatars (AvatarStore in core:data)
feature:history | accounts | settings   to do
widget                   to do: Glance 2x1 balance (privacy modes), 4x1 actions, 4x2 today + categories
importer:moneymanager    to do: reads .mmbackup (zip -> SQLite MyFinance.db) on the device
```

## Domain rules
- Money is always `Long` minor units. Never use floating point.
- Amounts always show two decimals (₼4.40, ₼3,435.00), even where the design mock-ups show whole numbers. In a tight space the text shrinks; it's never cut off or rounded.
- Every expense has a category (or a sub-category, whose parent is the reporting category) and a **person** ("For whom"). Each category has a default person.
- People and categories are fully user-editable: add, remove, reorder, colour, and photo avatars for people. Removing something that's in use **archives** it instead of deleting it.
- Transfers move money between accounts and never count as spending or income. `ADJUSTMENT` and `INTERNAL` carry a signed amount and are left out of charts.
- The app must work for anyone the owner shares it with. First run shows onboarding: name, people, a category template, and optionally an import or a Drive restore.

## Commands
```
./gradlew testDebugUnitTest :core:model:test   # unit tests, all modules (core:model is pure JVM, so it has `test`, not `testDebugUnitTest`)
./gradlew :app:assembleDebug         # debug APK (applicationId com.eminallahverdi.balance.debug)
./gradlew :app:installDebug          # to a connected phone
```
CI (`.github/workflows/android.yml`) runs both on every push and uploads the debug APK.

## Status (branch feat/mvp-foundation)
The build is green locally. `app` has navigation (Home, History, Insights, Accounts, Settings) with the bottom bar and centre + button, `QuickAddActivity` hosting `QuickAddRoute`, and `FirstRunSeeder` applying `StarterTemplate.household()` until onboarding exists. `feature:catalog` is reached from Settings. History, Insights and Accounts are placeholders in `BalanceApp.kt`, and Settings is a stand-in with only the "Categories & people" row.

Next, in order:
1. `feature:history` (search and filters), `feature:accounts` (include-in-total and widget switches, transfer, adjust balance), `feature:settings`.
2. `widget` (Glance) and app shortcuts, then the Money Manager importer, which uses a mapping file kept on the device, never in this repo.
3. Evening reminder, recurring payments and Drive backup (WorkManager).
