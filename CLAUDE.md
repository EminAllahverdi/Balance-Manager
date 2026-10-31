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
feature:history | accounts | catalog | settings   to do
widget                   to do: Glance 2x1 balance (privacy modes), 4x1 actions, 4x2 today + categories
importer:moneymanager    to do: reads .mmbackup (zip -> SQLite MyFinance.db) on the device
```

## Domain rules
- Money is always `Long` minor units. Never use floating point.
- Every expense has a category (or a sub-category, whose parent is the reporting category) and a **person** ("For whom"). Each category has a default person.
- People and categories are fully user-editable: add, remove, reorder, colour, and photo avatars for people. Removing something that's in use **archives** it instead of deleting it.
- Transfers move money between accounts and never count as spending or income. `ADJUSTMENT` and `INTERNAL` carry a signed amount and are left out of charts.
- The app must work for anyone the owner shares it with. First run shows onboarding: name, people, a category template, and optionally an import or a Drive restore.

## Commands
```
./gradlew testDebugUnitTest          # unit tests, all modules
./gradlew :app:assembleDebug         # debug APK (applicationId com.eminallahverdi.balance.debug)
./gradlew :app:installDebug          # to a connected phone
```
CI (`.github/workflows/android.yml`) runs both on every push and uploads the debug APK.

## Status (branch feat/mvp-foundation)
The foundation and core modules are written. `feature:home` and `feature:transaction` are written but not yet wired into `app`. **None of this has compiled locally yet.** Every build so far ran in CI only, so the first job is a green `./gradlew testDebugUnitTest :app:assembleDebug`.

Next, in order:
1. Get the build green.
2. Wire the app: navigation (Home, History, Insights, Accounts) with a bottom bar and a centre lime + button. Add `QuickAddActivity` (Theme.Balance.Overlay) hosting `QuickAddRoute`. Seed `StarterTemplate.household()` on first run, until onboarding exists.
3. `feature:catalog`: people (photo via the Photo Picker, copied into app storage; colour; reorder; archive) and categories/sub-categories (icon, colour, default person).
4. `feature:history` (search and filters), `feature:accounts` (include-in-total and widget switches, transfer, adjust balance), `feature:settings`.
5. `widget` (Glance) and app shortcuts, then the Money Manager importer, which uses a mapping file kept on the device, never in this repo.
6. Evening reminder, recurring payments and Drive backup (WorkManager).
