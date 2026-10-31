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
- `Chip`: default (`bg-surface-raised` with a hairline) or selected (`accent-fill`). A filter chip ends in a small arrow and opens a `FilterSheet`.
- `SegmentedToggle`: Category / Person, or Expense / Income / Transfer.
- `QuickAddSheet`: amount, category, sub-category, merchant, for whom, date, note, and a custom keypad with Save.
- `FilterSheet`: a bottom sheet for one filter, used instead of a menu. Long choices become grids that fit one screen: a year stepper over a 12-month grid, or a 4-column category grid. Short choices are rows with a check on the selected one. The first option always clears the filter.
- `EditorSheet`: a bottom sheet with a live preview, the title and Remove in the header, then the fields, then Save. Remove sits at the top so it's visible without scrolling, and it always asks first.
- `BottomBar`: Home, History, Insights, Accounts, with a centre lime add button 64 px with 22 px corners.
- Widgets: `BalanceWidget` 2×1, `QuickActionsWidget` 4×1, `TodayWidget` 4×2. All use `radius-lg` and a translucent `bg-surface`.

## Writing

Use short, plain labels in sentence case: "Spent this month", "Add expense", "For whom". Dates read "Today", "Yesterday", then "Tue 28 Oct". Don't use exclamation marks or the word "successfully".
