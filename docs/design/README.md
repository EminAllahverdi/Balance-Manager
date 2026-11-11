# Balance Tracker Design System

A personal money app for one household. You log a payment in a few seconds, and everything else exists to show clearly where the money went and who it was for. The look is "ink & lime": a near-black base, quiet greys, and one lime accent saved for the action that matters on each screen.

## Principles

- **Fast to log beats rich to look at.** The quick-add panel opens over the home screen from the widget, with every category in view, or on the amount when the category is already known. Every screen has exactly one `accent-fill` action.
- **Numbers carry the screen.** Amounts use the numeric family (Space Grotesk) and are the largest thing on any card. Labels stay small and calm (`text-secondary`, `caption`).
- **Every amount has a person.** Me, Wife, Daughter, Mom and Family each own one colour (`person-*`). It's used for dots, split bars and the person picker, and nowhere else.
- **Dark first, light mirrored.** Every colour token has a dark and a light value under the same name. Dark is the default theme.

## Colour

| Role | Token | Use |
|---|---|---|
| Ground | `bg-canvas` | Behind everything |
| Cards and rows | `bg-surface` | List rows, cards, bottom bar, sheets |
| Controls | `bg-surface-raised`, `bg-surface-high` | Chips, inputs, icon buttons, selected segment |
| Text | `text-primary`, `text-secondary`, `text-muted` | Title or amount, meta line, caption |
| Action | `accent-fill` + `accent-on` | Save, the add button, a selected chip. One per screen. |
| Money | `money-expense`, `money-income` | Only where the sign matters: deltas, insights, income rows. Ordinary expense rows stay `text-primary`, so the list doesn't turn red. |
| People | `person-me` `person-wife` `person-daughter` `person-mom` `person-family` | Split bars, dots, person picker |

**Category hues** are data, not tokens. Each category stores its own hue. Its icon tile is that hue at 16 % opacity behind a glyph in the full hue: Groceries teal `#5DCAA5`, Health coral `#FF7A66`, Car blue `#8AB4F8`, Subscriptions violet `#B39DFF`, Work amber `#F5B544`, Transport yellow `#FFD166`, Cafe `#FF9F6E`, Shopping `#7FD1F7`, Kids & Education `#FFB547`, Utilities `#9AD0A8`, Grooming `#F49AC2`, Entertainment `#C79BF2`, Travel `#6FC7D9`, Gifts `#F7A072`, Family support `#B8C0CC`, Other `#8A94A3`.

## Type

Body text uses **Manrope** and amounts use **Space Grotesk**, both from Google Fonts and bundled in the APK.
- Money: `amount-display` (balance), `amount-large` (card totals), `amount-medium` (row amounts), `amount-small` (chips).
- Text: `title-large` (screen title), `title-medium` (section), `body-strong` (row title), `body`, `label` (chips, buttons), `caption` (meta), `overline` (card labels, uppercase).
- Amounts always show the ₼ sign and two decimals (₼4.40, ₼3,435.00). In a tight space the text shrinks. Only the small label above a chart bar may shorten, to 3.4k. Expenses have no minus sign in lists, but get one in history and transaction detail.

## Shape and spacing

- **Spacing:** 4 / 8 / 12 / 16 / 20 / 24 / 32. The screen margin is `space-md`, and rows are 8 apart.
- **Radius:** `radius-icon` for category tiles, `radius-md` for rows, `radius-lg` for hero cards, sheets and widgets, `radius-full` for chips and buttons. No shadows: depth comes only from the surface steps.

## Iconography

Use **Material Symbols Rounded**, weight 400 and outlined, at 22 px in rows and the bar and 16–20 px in chips. These are the same glyphs Android ships, so Compose uses `Icons.Rounded.*` or the variable font directly. No emoji, and no filled icons except the active bottom-bar tab.

## App icon

Cibim means "my pocket", so the icon is a pocket with cash in it: a tapered curve like a smile, running from top left to bottom right, on `accent-fill`, with two notes tucked behind it. The fabric below the curve swells as if a hand were inside. It's the one place shading is allowed, because a launcher icon has to hold its own on a wallpaper.

- **Files:** `app-icon.svg` is the master and `app-icon-512.png` the same picture for stores. In the app it's an adaptive icon on Android's 108 canvas, of which a launcher shows the middle 72, so the curve and notes stay well inside the edge in every mask.
- **The smile:** `accent-on`, with rounded tips, stopping short of the edges.
- **The notes:** landscape, almost level, the front one tilted 12° and the back one 32°. They use the darker greens of `money-income`, `#11845B` and `#0E6E4C`, with a lime edge and a thin printed frame. The ₼ is drawn symmetrically, sits on the front note only, and tilts with it. There's no number, bank name or emblem: they suggest cash without copying a real note.
- **Themed icon:** `app-icon-monochrome.svg`. Android tints one colour, so the notes become outlines and only the smile stays solid.
- **Cibim Dev:** the same drawing on violet, so it can't be mistaken for the real app.

## Components (Compose names)

- `BalanceCard`: the total balance, account chips, and spent/income for the month. Past months show only spent and income, because balances are always today's.
- `PersonStrip`: one card per person with their monthly total.
- `CategoryRow`: icon tile, name, people line, amount, chevron, and a person split bar (6 px tall, 2 px gaps). Tapping opens that month's transactions, where each one can be edited.
- `TransactionRow`: icon tile, merchant or sub-category, the line "category · sub · account", amount, `PersonTag`.
- `Chip`: default (`bg-surface-raised` with a hairline) or selected (`accent-fill`). A filter chip ends in a small arrow and opens a `ChoiceSheet`. On a page whose one action is Save, a selected chip is `bg-surface-high` with a lime outline instead, so Save stays the only lime fill.
- `SegmentedToggle`: Category / Person, or Expense / Income / Transfer / Debt. Shared out evenly across a row, as quick add's four tabs are, each segment takes less padding and keeps its label on one line, ending in an ellipsis at worst, so a longer word never wraps. A compact one, with tighter segments, sits in the corner of a card beside its overline, as Spending's Week / Month / Year does in its chart card.
- `QuickAddSheet`: category first. The grid shows Expense, Income, Transfer and Debt, up to three Frequent merchants, and all categories, four across, so nothing scrolls sideways. While the four tabs show, the sheet keeps one height, 84 % of the screen, so switching tabs never moves it under the thumb. Tapping a Frequent merchant fills in what it was filed under last time. Then one page has the category (tap to change it), account, amount, sub-category, merchant, for whom, date, note, and a custom keypad with Save, so nothing needs a second page to check. An entry that arrives with a category skips the grid, and so does a transfer, which has From and To instead. Back steps from the keyboard to the grid to closed. On a short phone the keys shrink from 54 to 46 so the page still fits.
- What the bank sent: above Frequent, up to three payments waiting on To confirm, as rows with the bank's merchant, what it would be filed under and the time, each with an Add chip, and a caption saying a tap opens one to check first. Tapping one opens `QuickAddSheet` filled in from it.
- `DebtStep`, the Debt tab: New, four cells with a tile each (I paid for others, They paid for me, I lent, I borrowed), then Paid back, everyone owed or owing as a row with `I owe` or `Owes me`, what's left, and Pay or Received. A kind opens the keypad page with the kind under the category, Who as name chips, and a caption under the amount saying in plain words what saving will do with the money.
- Text bar: the pencil beside the merchants and the Note chip swap the keypad for the phone's keyboard, with a text field just above it and a small Save beside the field, so the last thing typed doesn't need Done and then Save. What's already there starts selected, so typing replaces it. Merchants from any category show as chips while typing, so a new spelling doesn't start a second one, and `Add “name”` keeps a new one, which is only created when the entry is saved. Done brings the keypad back with the merchant selected or the note on its chip, shortened if long. Tapping a selected merchant clears it.
- `RecurringPayment`: reached from Accounts. A reminder, never an entry. Nothing adds itself: when one is due and missing from the list it shows in the Due list, as a row on Home and in a daily notification, with Skip and Add. Add opens `QuickAddSheet` filled in, for the user to check and save. A reminder clears on its own once an entry with the same name, or the same category and amount, is in the list.
- `BankLoan`: a recurring payment with a set number of payments, so it reminds the same way. A card with the monthly amount, a progress bar, payments left with what's still to pay, and the next date, in `status-warning` when it's within a week. Its payments are normal spending everywhere. Its editor takes what the contract says, all of it optional: amount borrowed, interest a year, months in all, already paid and commission, labelled above the fields in pairs. A chip offers the monthly payment worked out from them, and a box on `bg-surface-raised` under the fields says what's left to pay, about how much of it is interest, and what the whole loan costs.
- `Debt`: money with someone outside the household. One card per person and direction, with `I owe` in `money-expense` or `Owes me` in `money-income`, a line per open debt with Pay or Received beside it, and Pay all with the total when there's more than one. Something they paid for you becomes an expense in its own category when it's paid back. Borrowed or lent money uses the handshake icon, a sign and a plain label (Borrowed from, Paid back to, Lent to, Paid back by), and is never income or spending. A loan with someone can be Already running: the switch swaps the account for what was paid back before, Payments left sets the monthly part that Pay starts from, and a caption says what's left and how much a month.
- `BillEditor`, I paid for others: Amount paid, Who owes you as one field ("Sam, Alex and Kamal") with a chip for each person met before, category, sub-category, merchant, Your share, For whom, From, Date and Note, with the amount on Save. Under Your share, a "Split evenly by N" chip, and a caption that says in plain words what saving will do with the money. The bill is one card under People for everyone named, showing what they still owe. Received opens the pay sheet with the rest filled in, and beside its button a "₼X is my share" chip makes what nobody sends back the user's own spending.
- `FloatingAddButton`: the lime add button at the bottom right of a list with no bottom bar, in reach of the thumb, on Categories and Loans & debts. It says what it adds, and the list leaves room for it to scroll clear.
- `BtTimePickerSheet`: a time of day on two endless wheels, hours and minutes, like a phone's alarm clock, in a panel that slides up from the bottom. Always 24-hour. The wheels settle on a highlighted middle row, the rows around it fade, tapping one turns to it, a soft tick marks each number, and each wheel takes a full half of the panel, so a drag anywhere on that side turns it. The panel has no drag handle and can't be dragged, so it never fights the wheels: it closes with Save, the X, Back or a tap on the dim area.
- `LockScreen`: an opaque page with a lock tile, Cibim is locked, one line saying how to open it, and Unlock. It covers the app whenever the app lock is on, so nothing shows behind it, and the system's own prompt opens over it by itself: fingerprint, with the phone's PIN as backup. It only shows on its own after that prompt is cancelled, and Back from it leaves the app.
- `MonthTitle` and `MonthPicker`: a month is its name and a small caret, on the page itself, so it moves with a swipe and a swipe that starts on it works. Tapping it opens a sheet of months, a year at a time, the same grid as History's month filter. Home changes months this way.
- `MonthButton`: a month that lives in a card's corner, as Groceries' does in its spending card: the month's name in `label` and a caret, in a small pill on `bg-surface-raised`. It opens the same sheet of months, and it sits on the page, so a swipe still changes the month as it does on Home.
- `ScrollingTabs`, Reports: the third tab. Its title, then a row of tabs, one per report, Spending and Groceries so far: the one showing in `text-primary` with a 3 px lime line under it, the rest `text-muted`, over a 1 px `border-default` rule across the width. The row scrolls once more reports join than fit, so a new one is just another tab. Each report keeps its period in the corner of its first card, so the top holds one title and one control: Spending's Week / Month / Year as a compact `SegmentedToggle`, and Groceries' month as a `MonthButton`.
- `DonutChart`: a ring of a month's money by category, swept in from the top, with the total in the middle and the top five categories and their percent beside it. Smaller ones join as The rest. It takes over Home's card from a small icon in the corner, which becomes a wallet to go back, and the card stays as it was left. With the eye on, Income shows an empty ring.
- Chart bars: rounded stacks by person. The selected bar is bright and carries the only figure above a bar, the rest fade back to 40 %, and the legend under the chart shows the selected bar's amount for each person.
- `tileGlyph`: on Light, a pale category colour darkens until its icon reaches 3:1 on its tile, keeping its hue. Dark uses the colour as it is.
- Paired actions: when two actions sit side by side and neither leads, as Export's Save and Share do, they're equal buttons with a lime icon. Lime fill next to a plain button reads as a selected tab.
- `TotalCard`: the accounts' total and what acts on all of them in one bordered `radius-lg` card. The overline counts the accounts, the total is large and shrinks to fit, and under a hairline Transfer and Adjust balance share the bottom edge, split by another hairline, each a lime icon and a label.
- `AccountCard`: tile, name, and a caption of type, •••• and the card's last digits, and "not in the total" when it's left out, with a small widget glyph when it's on the widget. Its amount is `text-muted` when it isn't in the total. At the end, a star: filled in `accent-text` on the default account, an outline in `text-muted` on the rest. Tapping an outline moves the default there with a small bounce, and a bar at the bottom says "New expenses start on Cash" with Undo for 4 seconds.
- Place row: a row that leads somewhere else, as Loans & debts and Recurring payments do from Accounts: a tile, the name, a caption that sums up what's there ("Spotify, Rent, Gym · 3"), and a chevron.
- Switch group: related switches share one box on `bg-surface-raised`, hairlines between them, each a row with an icon, a title, a caption saying what it does and a `BtSwitch`. The account editor's Default for expenses, Count in the total and Show on the widget sit together this way.
- Export sheet: Quick picks as chips (This month to Everything) that fill in From and To, which stay on show as date buttons, so a pick never hides what it means. Under them, how many entries fall between the dates in `body-strong`, or that none do in `text-muted`, then Save and Share as paired actions. Share goes to the phone's own share sheet.
- Widget loading: the widget's surface with a soft diagonal band sweeping across it, low contrast, with a pause between passes. Previews in the launcher's picker are layouts of the real widgets with sample figures.
- Backup: a section in Settings with the switch and the last backup's time, Use mobile data, Back up now, and Restore from Google Drive. Restoring is a sheet of the backups in the account, then a confirmation, because it replaces everything and restarts the app. A missed backup is a `HomeNotice` with a `status-warning` crossed-out cloud, like the other things waiting on Home, and it opens Settings.
- Version line: Settings ends with the app's name, version and build number, like Cibim 1.4.0 (58), centred in `caption` and `text-muted`, so it's plain which app and build is open. Settings holds nothing that's set somewhere else: the default account is on Accounts and the eye is on Home.
- Language: Settings' Language row, under Theme, opens a `ChoiceSheet` of Follow the phone, English and Azərbaycanca. Each language is named in itself, so someone who can't read the one showing can still find theirs. Welcome has the same two as chips at its top right. Picking one changes the words in place: nothing reloads, and the page stays where it was.
- `ToConfirm`: a card for a ready-made entry read from a bank notification, with the bank app and time, the bank's own text, what it would be saved as, and Edit, Skip and Confirm. A category worked out from words, the title or an amount shows Suggested in the caption. An entry with no category yet has no Confirm, and Edit goes to quick add. Cash taken out is a Cash withdrawal with the transfer tile and "Card → Cash" as its caption, never a shop's category. With the eye on, withdrawals and income hide their amount, and spending stays. Added entries stay for the day, faded, with a check. Auto-confirm is the only thing that adds an entry unasked: it's off by default, and turning it on always asks first.
- `UnreadCard`: Not read, at the end of To confirm. A bordered card for a notification from a picked bank app that wasn't read: the app and time, the reason in `body-strong`, the text in monospace with invisible characters written out like ⟨U+200E⟩, and Copy. They live in memory only, the last 10.
- `BtSwitch`: the one on/off control, `accent-fill` when on.
- `ChoiceSheet`: a bottom sheet for picking one thing, used instead of a menu or spinner everywhere: History filters and Settings. Long choices become grids that fit one screen: a year stepper over a 12-month grid, or a 4-column category grid. Short choices are `OptionRow`s with a check on the selected one, and a caption line when a choice needs explaining. In a filter, the first option always clears it.
- `EditorSheet`: a bottom sheet with a live preview, the title and Remove in the header, then the fields, then Save. Remove sits at the top so it's visible without scrolling, and it always asks first. A field that would hold a bare number gets a label above it, not a placeholder, which is gone once it's filled in. The account editor's Card's last 4 digits is one, with a line under it saying what it's for, on every account but cash.
- `BottomBar`: Home, History, Reports, Accounts, with a centre lime add button 64 px with 22 px corners. Its labels stay on one line.
- `PersonAvatar`: a person's initial on their colour, or their photo inside a ring of their colour, 8 % of its size, so the colour that marks them everywhere else still shows. In a picker, as quick add's For whom is, it's plain: the initial in `text-primary` on `bg-surface-high`, or the photo alone, so the lime ring is the only colour saying who's picked and a lime or yellow person never reads as a second selection.
- Rename: tapping a person's name on Loans & debts opens a sheet with the name as a field and a caption saying how many debts change with it and that the paid-back notes follow. A name someone already has says the two will join.
- Widgets: `BalanceWidget` 2×1, `BalanceVoiceWidget` 4×1, `QuickActionsWidget` 4×1, `TodayWidget` 4×2. `QuickActionsWidget` has four keys, Expense on lime and widest, then Income, Transfer and Debt. The + on `BalanceVoiceWidget` carries a small lime badge with how many bank payments wait, 9+ beyond nine. `BalanceWidget` has two tap areas: the + in its corner always adds an expense, and the rest of the card follows the Widget balance setting. The amount sits below the +, at 20, and shrinks further when it's long. `BalanceVoiceWidget` is the same balance with two keys on the right, a lime mic and a + on `bg-surface-high`, and revealing the balance never changes its layout. All use `radius-lg` and a translucent `bg-surface`. Text uses the system font, because home-screen widgets can't load the bundled ones. The four one-tap categories are the first four in the user's own order.
- `ColourPicker`: two rows, the 13 palette colours and a last place that opens a hue ring for any other colour. The ring keeps a colour to the strength and lightness that read on both themes.
- Voice: a sheet over the home screen with Say what you spent, or Listening…, EN and AZ chips, what's heard so far in the middle (an example in `text-muted` before anything is), a 72 px mic that turns lime with a stop glyph and a soft halo while it listens, the hint, or the problem in `money-expense`, and Type instead. It ends in `QuickAddSheet` filled in, with what was heard above it as a quoted line beside a lime mic, so the fields can be checked against the words.
- Swipe to delete: a History row slides left over a band of `money-expense` at 16 % with a bin and Delete. Letting go asks first, naming the entry as its row does (title · amount · day) and saying what changes. With the eye on, a hidden amount stays out of the name.
- Archived accounts: they fold behind one row with a count. Open, each has Restore in `accent-text` and Delete in `money-expense`. Delete opens a page, not a dialog: what uses the account, as History rows that open to change, then Move all to another account, the one lime action, and Delete account and N transactions as red text. Both ask first and say which balances change.
- Groceries report: a card with the month's spending and a row each for last month and the 3, 6 and 12-month averages. A change reads + in `money-expense` when it's more, − in `money-income` when it's less, same in `text-secondary`, and – with nothing to compare. Item groups get a 36 px tile from the category palette with their own glyph, and a bar in the same colour scaled to the largest. Most spent on is a numbered list, and receipts are rows with a basket tile.
- Receipt review: a sheet with the top of the picture, the shop as a field, the day and the receipt's own total, a `status-warning` caption when the items don't add up to it, every item with an X to leave it out, and Save receipt. Saving never adds an entry: when no expense matches, a question offers quick add filled in.

## Writing

Use short, plain labels in sentence case: "Spent this month", "Add expense", "For whom". Dates read "Today", "Yesterday", then "Tue 28 Oct". Don't use exclamation marks or the word "successfully".

### Azerbaijani

The app speaks English and Azerbaijani, with the same plain voice in both.

- **Translate by meaning, not word for word**, and in Azerbaijani words rather than Turkish ones: Yadda saxla, not Kaydet; Ləğv et, not İptal.
- **Buttons say the action**, in the plain form: Yadda saxla, Əlavə et, Təsdiqlə, Keç, Geri al. **Sentences speak to the user formally**: "Xərc əlavə etmək üçün + düyməsinə toxunun."
- **Avoid endings on a name the user typed**, since its sound decides them: "Card hesabından" puts the ending on a fixed word after the name, and "Borc qaytarıldı: Sam" leaves the name bare.
- **Dates** read "Bu gün", "Dünən", then "26 okt, B.e.". A month as a title starts with a capital: Noyabr.
- **Never translated**: names of categories, people, accounts and shops, which are the user's own. Only the starter template comes in both languages.
- **Words run longer than in English**, so a label stays on one line and ends in an ellipsis rather than wrapping. Where room is tight, a shorter word does the job: the Reports tab is Hesabat.

The words used throughout:

| English | Azərbaycanca |
|---|---|
| Expense, Income, Transfer, Debt | Xərc, Gəlir, Köçürmə, Borc |
| Account, Balance, Cash, Card | Hesab, Balans, Nağd, Kart |
| Category, Sub-category, For whom | Kateqoriya, Alt kateqoriya, Kimin üçün |
| Entry (a transaction) | Əməliyyat |
| Merchant, Note, Receipt | Mağaza, Qeyd, Çek |
| Home, History, Reports, Accounts | Əsas, Tarixçə, Hesabat (the tab) or Hesabatlar (the title), Hesablar |
| Spending, Groceries | Xərclər, Ərzaq |
| Loans & debts, Recurring payments, To confirm | Kreditlər və borclar, Müntəzəm ödənişlər, Təsdiq üçün |
| Settings, Language, Backup | Ayarlar, Dil, Ehtiyat nüsxə |
| Save, Cancel, Delete, Undo | Yadda saxla, Ləğv et, Sil, Geri al |
