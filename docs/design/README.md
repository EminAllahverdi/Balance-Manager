# Balance Tracker Design System

A personal money app for one household. You log a payment in a few seconds, and everything else exists to show clearly where the money went and who it was for. The look is "ink & lime": a near-black base, quiet greys, and one lime accent saved for the action that matters on each screen.

## Principles

- **Fast to log beats rich to look at.** The quick-add panel opens over the home screen from the widget, with the amount field ready to type. Every screen has exactly one `accent-fill` action.
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

## Components (Compose names)

- `BalanceCard`: the total balance, account chips, and spent/income for the month. Past months show only spent and income, because balances are always today's.
- `PersonStrip`: one card per person with their monthly total.
- `CategoryRow`: icon tile, name, people line, amount, chevron, and a person split bar (6 px tall, 2 px gaps). Tapping opens that month's transactions, where each one can be edited.
- `TransactionRow`: icon tile, merchant or sub-category, the line "category · sub · account", amount, `PersonTag`.
- `Chip`: default (`bg-surface-raised` with a hairline) or selected (`accent-fill`). A filter chip ends in a small arrow and opens a `ChoiceSheet`.
- `SegmentedToggle`: Category / Person, or Expense / Income / Transfer.
- `QuickAddSheet`: amount, category, sub-category, merchant, for whom, date, note, and a custom keypad with Save.
- `RecurringPayment`: a reminder, never an entry. Nothing adds itself: when one is due and missing from the list it shows in the Due list, as a row on Home and in a daily notification, with Skip and Add. Add opens `QuickAddSheet` filled in, for the user to check and save. A reminder clears on its own once an entry with the same name, or the same category and amount, is in the list.
- `BankLoan`: a recurring payment with a set number of payments, so it reminds the same way. A card with the monthly amount, a progress bar, payments left with what's still to pay, and the next date, in `status-warning` when it's within a week. Its payments are normal spending everywhere.
- `Debt`: money with someone outside the household. One card per person and direction, with `I owe` in `money-expense` or `Owes me` in `money-income`, a line per open debt with Pay or Received beside it, and Pay all with the total when there's more than one. Something they paid for you becomes an expense in its own category when it's paid back. Borrowed or lent money uses the handshake icon, a sign and a plain label (Borrowed from, Paid back to, Lent to, Paid back by), and is never income or spending.
- `FloatingAddButton`: the lime add button at the bottom right of a list with no bottom bar, in reach of the thumb, on Categories and Loans & debts. It says what it adds, and the list leaves room for it to scroll clear.
- `BtTimePickerSheet`: a time of day on two endless wheels, hours and minutes, like a phone's alarm clock, in a panel that slides up from the bottom. Always 24-hour. The wheels settle on a highlighted middle row, the rows around it fade, tapping one turns to it, a soft tick marks each number, and each wheel takes a full half of the panel, so a drag anywhere on that side turns it. The panel has no drag handle and can't be dragged, so it never fights the wheels: it closes with Save, the X, Back or a tap on the dim area.
- `ChoiceSheet`: a bottom sheet for picking one thing, used instead of a menu or spinner everywhere: History filters and Settings. Long choices become grids that fit one screen: a year stepper over a 12-month grid, or a 4-column category grid. Short choices are `OptionRow`s with a check on the selected one, and a caption line when a choice needs explaining. In a filter, the first option always clears it.
- `EditorSheet`: a bottom sheet with a live preview, the title and Remove in the header, then the fields, then Save. Remove sits at the top so it's visible without scrolling, and it always asks first. A field that would hold a bare number gets a label above it, not a placeholder, which is gone once it's filled in.
- `BottomBar`: Home, History, Insights, Accounts, with a centre lime add button 64 px with 22 px corners.
- Widgets: `BalanceWidget` 2×1, `QuickActionsWidget` 4×1, `TodayWidget` 4×2. `BalanceWidget` has two tap areas: the + in its corner always adds an expense, and the rest of the card follows the Widget balance setting. The amount sits below the +, at 20, and shrinks further when it's long. All use `radius-lg` and a translucent `bg-surface`. Text uses the system font, because home-screen widgets can't load the bundled ones. The four one-tap categories are the first four in the user's own order.

## Writing

Use short, plain labels in sentence case: "Spent this month", "Add expense", "For whom". Dates read "Today", "Yesterday", then "Tue 28 Oct". Don't use exclamation marks or the word "successfully".
