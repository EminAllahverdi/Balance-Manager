# Cibim screens

Every screen of the app as drawn, 95 boards in all: one page per area of the app (Home, Adding, History, Insights & groceries, Accounts, Loans & debts, To confirm, Settings, First run, Widgets & icon), and on each a titled row per flow, left to right in the order things happen.

- `index.html` shows them all, laid out that way. Open it in a browser from a clone, or through GitHub Pages if it's turned on for `docs`.
- `boards/` holds each board as a self-contained page, 390 × 844 like a phone. They load Manrope, Space Grotesk and Material Symbols from Google Fonts, and link to each other where a tap leads somewhere.
- `canvas.json` is where each board sits: its page, position and title, and the titled rows.

Colours, type, spacing and the patterns the boards follow are in the design system one folder up (`../README.md`, `../tokens.json`), which the app's Compose theme mirrors one to one.

Rows titled **Proposal** were drawn before they were built, for the owner to choose from. What shipped can differ in detail, and the app is the final word.

The boards are a copy of the design file the app is drawn in, kept in step with it: names, figures and card numbers on them are made up.
