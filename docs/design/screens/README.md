# Cibim screens

Every screen of the app as of Cibim 1.4.0, 96 boards in all: one page per area of the app (Home, Adding, History, Reports, Accounts, Loans & debts, To confirm, Settings, First run, Widgets & icon), and on each a titled row per flow, left to right in the order things happen. Settings' Language row shows the app in Azerbaijani.

- `index.html` shows them all, laid out that way. Open it in a browser from a clone, or through GitHub Pages if it's turned on for `docs`.
- `boards/` holds each board as a self-contained page, 390 × 844 like a phone. They load Manrope, Space Grotesk and Material Symbols from Google Fonts, and link to each other where a tap leads somewhere.
- `canvas.json` is where each board sits: its page, position and title, and the titled rows.

Colours, type, spacing and the patterns the boards follow are in the design system one folder up (`../README.md`, `../tokens.json`), which the app's Compose theme mirrors one to one.

Something new is drawn first in a row titled **Proposal**, for the owner to choose from, and becomes an ordinary row once it ships. Where a board and the app differ in detail, the app is the final word.

The boards are a copy of the design file the app is drawn in, kept in step with it: names, figures and card numbers on them are made up.
