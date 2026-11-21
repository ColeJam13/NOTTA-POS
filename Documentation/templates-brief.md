# NOTTA-POS: template brief (step B4c)

Written 2026-11-21 for the owner. It replaces the B4c section of `phase-2-plan.md`. Read `CLAUDE.md` first, in particular the conventions on tokens and templates; this brief builds on the system merged in #290 and #291.

## What the owner asked for

Twelve new templates beside 8-Bit, all selectable, so the owner can switch between them in the running app and judge them there. 8-Bit stays the default.

That changes how B4c is run. The plan said one template per pull request, each approved from screenshots before it was finished. With twelve, the owner would rather see them live. So:

- **Build them as drafts in three pull requests of four templates each**, after one groundwork pull request. One pull request open at a time, as always.
- The owner reviews by switching templates in the app, then gives notes. Notes are handled in a polish pull request after each batch or at the end, whichever the owner asks for.
- The colors below are starting points, not a specification. The mood, the light-or-dark choice, and the rules in "Rules every template must pass" are the specification. Adjust any shade to pass the contrast test.

## Order

| Pull request | Contents |
|---|---|
| 1 | Groundwork (below). No new template yet |
| 2 | Plain, Coffee Shop, Nightclub, Fancy |
| 3 | Dive Bar, Brunch Spot, Diner, Sports Bar |
| 4 | Cantina, Izakaya, Beach Bar, High Contrast |

## Groundwork (pull request 1)

1. **An automated contrast test.** Contrast ratio is a standard measure of how readable one color is on another; 4.5 to 1 is the accepted minimum for normal text. Add a test that reads every template's tokens and checks a fixed list of pairs:
   - 4.5 to 1: `--color-text` and `--color-text-muted` on `--color-bg`, `--color-surface`, and `--color-control`; every `--color-on-...` on its fill; every `--status-...-text` and `--status-...-label` on the surface it is drawn on; `--color-heading`, `--color-subheading`, `--color-accent`, and `--color-primary` where they are used as text.
   - 3 to 1: borders and icons that carry a meaning (`--status-...-border`, the lock icon) against the surface behind them, and the fainter text tokens (`subtle`, `faint`, `hint`).
   - Switched-off controls are exempt.
   - Run it on 8-Bit first. Where 8-Bit fails, do not change 8-Bit: list each failure in the test as a named, commented exception and tell the owner. Every new template must pass with no exceptions.
2. **Check the tokens can describe a light template.** 8-Bit is dark, and so far it is the only template. Go through the token list and the stylesheets for anything that only works on a dark background: the overlay, the eleven glow tokens, the navbar shadow, the grid line on the floor editor, the gradient, focus rings, and any place a light color is assumed behind text. A template with no glow sets those tokens to `none`. If a stylesheet needs a new token to look right in a light template, add it to every template in the same pull request (8-Bit's value must leave 8-Bit unchanged; prove it the way #290 did).
3. **The logo.** The Home and PIN screens show one picture drawn in 8-Bit colors. Default decision for now (**D13**, the owner may overrule): the picture belongs to 8-Bit. Every other template shows the name NOTTA-POS as text, in that template's heading font and accent color. Make which one is shown a property of the template in `templates/index.js`, so artwork can be added to a template later without touching the screens.
4. **A template gallery.** One manager-only screen, reached from Settings, that shows every piece of the interface in every state on one page, in the template being previewed: the button kinds; an order line as draft, saved draft, in the edit window, locked, and done; an 86'd menu card; a kitchen ticket on time, at the warning, and late; a table available and occupied; an error message; the PIN pad; a payment row; the paused-terminal row. It is how twelve templates get reviewed quickly, and it is the fixed subject for screenshots. Build it from the real components, not copies.
5. **A picker that scales to thirteen.** Each entry in `TEMPLATES` carries a name, one line of description, whether it is light or dark, and four swatch colors. The Settings screen shows them as a grid of cards. PREVIEW and choosing for the whole restaurant work as they do now.
6. **Fonts.** Only fonts under an open license (SIL Open Font License or Apache 2.0). Check the license of each one named below before using it; if one does not qualify, substitute a similar open font and say so. Each font goes in `src/fonts` as `woff2` with its license file beside it and a block in `templates/fonts.css`, only the weights actually used. Report the size each template adds.

## Rules every template must pass

- The contrast test, with no exceptions.
- **Each meaning stays distinct inside a template.** A saved draft, an item in the edit window, a locked item, an item in the kitchen, a finished one, an 86'd item, and an error must not be confusable with each other, or with the template's accent and primary colors. Where a state already has an icon or a word (the lock, "86'D"), keep it; color is never the only signal.
- **Wait warnings keep their everyday meaning:** on time reads calm, the 10-minute warning reads yellow or amber, late reads red. A template may choose the shades, not the meanings.
- **The kitchen display is readable at arm's length** in every template: item names no smaller than they are in 8-Bit, and strong contrast.
- **No one-off values.** 8-Bit keeps ten of them for historical reasons (the last section of `eight-bit.css`). A new template sets each of those tokens equal to its neighbor.
- **Mood comes from color, type, and shape only.** No background pictures, no patterns from the internet, no novelty lettering that imitates a script or a culture, no cultural symbols. Nothing is loaded from outside the app.
- Tap targets and spacing do not shrink. A template may round or square the corners, thicken or thin the borders, and add or remove shadows and glow.

## The twelve templates

Names in `code` are the template's name in `TEMPLATES` and its file name.

### Light templates

| Template | Mood | Colors to start from | Type | Shape |
|---|---|---|---|---|
| **Plain** `plain` | Neutral and quiet. The one a restaurant with no strong identity picks. | Background `#F6F7F9`, surface `#FFFFFF`, text `#1B1F24`, accent blue `#2F5BEA`, primary action green `#1F7A4D` with white text, danger `#C62828`, attention `#9A6700` | Inter for everything | 8px corners, 1px borders, a soft shadow, no glow |
| **Coffee Shop** `coffee-shop` | Warm, soft, unhurried. Cream paper and espresso. | Background `#F5EFE6`, surface `#FFFBF5`, text `#3B2A20`, accent caramel `#8B5E3C`, primary action deep green `#2F6F4F` with white text, danger `#B3422E`, attention `#A8650A` | Fraunces for headings, Nunito for the rest | 12 to 14px corners, soft shadows, 1px borders |
| **Brunch Spot** `brunch` | Bright, airy, cheerful. | Background `#FFF8F1`, surface `#FFFFFF`, text `#2E2A33`, accent coral `#E8604C`, primary action teal `#1F7F70` with white text, pale yellow `#FFE9A8` for attention surfaces, danger `#C2334D`, attention text `#9A6B00` | DM Serif Display for headings, DM Sans for the rest | 16px corners, pill-shaped buttons, soft shadows |
| **Diner** `diner` | 1950s lunch counter: cream, cherry red, chrome. | Background `#FDFBF4`, surface `#FFFFFF`, text `#1E1E24`, accent cherry `#D62839`, primary action teal `#0E7C86` with white text, borders chrome grey `#B8BDC7`, danger a darker red than the accent with an icon, attention `#9C6500` | Pacifico for the largest titles only, Barlow for everything else | 10px corners, 2px borders |
| **Cantina** `cantina` | Sun-baked and warm: sand, terracotta, turquoise. | Background `#FBF3E4`, surface `#FFFDF8`, text `#2F1E14`, accent terracotta `#C1502E`, primary action turquoise `#0F766E` with white text, danger `#B4232A`, attention marigold `#9C6500` | Zilla Slab for headings, Mulish for the rest | 6px corners, 2px borders, flat (no shadow) |

### Dark templates

| Template | Mood | Colors to start from | Type | Shape |
|---|---|---|---|---|
| **Fancy** `fancy` | Fine dining: candlelight, restraint, a little gold. | Background `#14110F`, surface `#1F1B18`, text ivory `#F2EBDD`, accent gold `#C9A227`, primary action gold with dark text, quiet borders `#5A4B2A`, danger `#E0605C`, attention a paler yellow than the gold | Cormorant Garamond for headings with wide letter spacing, Lato for the rest | 2px corners, 1px borders, no glow |
| **Nightclub** `nightclub` | Smooth modern neon. Must not read as a recolored 8-Bit: rounded where 8-Bit is square, gradients where 8-Bit is flat. | Background `#07060D`, surface `#12101F`, text `#F4F1FF`, accent magenta `#FF2DAA`, primary action cyan `#00E5FF` with dark text, danger `#FF4D4D`, attention `#FFE14D` | Space Grotesk throughout, heavier for headings | 14px corners, strong glow on accent and primary, the gradient token in use |
| **Dive Bar** `dive-bar` | Dark wood, beer signs, a chalkboard. Rough on purpose. | Background `#17120E`, surface `#231B14`, text `#EDE3D0`, accent bar-sign blue `#5CC8FF`, primary action amber `#F2A900` with dark text, danger `#FF5A4D`, attention a yellow clearly lighter than the amber | Bebas Neue for headings, Barlow for the rest | 4px corners, 2 to 3px borders, a faint glow on the accent only |
| **Izakaya** `izakaya` | Quiet and minimal: ink, paper, one warm color. | Background ink `#101522`, surface `#1A2133`, text paper `#ECE7DB`, quiet slate borders `#3A4560`, accent soft blue `#8FB3FF`, primary action paper `#ECE7DB` with ink text, danger vermilion `#F0563B`, attention `#F2C14E`, success `#8BC79A` | Noto Serif for headings, IBM Plex Sans for the rest | 2px corners, 1px borders, no glow, generous spacing |
| **Beach Bar** `beach-bar` | Sunset over water. | Background deep teal `#0E2A2B`, surface `#143839`, text `#FFF4D6`, accent pink `#FF5E8A`, primary action orange `#FF8A1F` with dark text, attention `#FFE066`, danger a crimson fill `#C81D25` with white text and an icon, so it cannot be mistaken for the orange or the pink | Lilita One for headings, Nunito for the rest | 14px corners, 2px borders, soft shadows |
| **Sports Bar** `sports-bar` | Stadium at night: navy, white lines, a scoreboard. | Background `#0D1520`, surface `#152232`, text `#F3F6FA`, borders silver `#9AA7B8`, accent turf green `#2FBF71`, primary action blue `#1F6FEB` with white text, danger `#FF5A5F`, attention `#FFD23F` | Oswald for headings, Inter for the rest, and a monospaced face (Roboto Mono) for timers and totals | 6px corners, 2px borders, no glow |
| **High Contrast** `high-contrast` | Not a mood: the most readable the app can be. For bright kitchens, sunlit patios, and tired eyes. | Background `#000000`, surface `#000000` with white borders, text `#FFFFFF`, accent and attention yellow `#FFD400`, primary action white with black text, danger `#FF6B6B` always with an icon or a word | Atkinson Hyperlegible throughout, type sizes one step larger than 8-Bit where the layout allows | 4px corners, 2 to 3px borders, no glow, no shadow |

High Contrast should clear 7 to 1 for text, not just 4.5 to 1. Add that to the contrast test for this one template.

## What each template pull request contains

- The four stylesheets, their fonts, and their entries in `TEMPLATES`.
- Screenshots of each template: the gallery, sign-in, the order screen with items in each state, the modifier picker, payment, the kitchen display with an on-time, a warning, and a late ticket, the 86 board, and the Floor Map.
- The contrast test passing, and `tokens.test.js` passing (every template defines every token, nothing loaded from outside the app).
- A click-through in the real app: preview each of the four, choose one for the restaurant, confirm a second browser picks it up within 15 seconds, then put 8-Bit back.
- `CLAUDE.md` updated: the list of templates, and anything learned.
- "Not verified" stated plainly, as always.

## What the owner will do

Switch through the templates on the Settings screen and the gallery, use each for a mock service, and send notes: which to keep, which to change, which to drop. Nothing here is final until the owner has seen it running.
