# NOTTA-POS: nothing fake (work order)

Written 2026-11-22 for the owner, after a sweep of `main` at `a12507a` (the merge of #296, CI green). Read `CLAUDE.md` first; its working rules and conventions apply to every step here. This brief replaces the B1 and B3 sections of `phase-2-plan.md` and moves them ahead of everything else that is left.

## What the owner asked for

"Let's see NOTHING fake in the app. Challenges, notifications, all of it."

The demo link for an outside visitor waits. Functionality comes first.

## What "real" means here

Hold every screen to these four statements. They are the test for whether a step is finished.

1. **Every number on a screen comes from the database, through the backend.** No count, name, amount, or percentage is written in frontend code.
2. **Every control does something, or it is not drawn.** No button without an action, no input that goes nowhere.
3. **The app shows nothing it cannot back up.** No feedback scores (the app collects none), no prize it keeps no record of, no "streak" nobody has defined.
4. **Empty is honest.** A new restaurant has no notifications and no challenges. The screen says so in plain words. It never fills the space with sample rows.

Two places hold made-up data on purpose and stay as they are: the template gallery (`views/Gallery`, which says so on the screen) and the tests.

## What is fake today

Found by reading the code, not from memory. Check each against the current files before starting its step.

| What | Where | What is wrong | Step |
|---|---|---|---|
| The app's name and icons | `frontend/public/manifest.json`; `favicon.ico`, `logo192.png`, `logo512.png` | The manifest says "React App" and "Create React App Sample". The three pictures look like the React logo that Create React App ships; open them to confirm | N1 |
| Template leftovers | `src/reportWebVitals.js` (called from `src/index.jsx`), `src/logo.svg`, the `web-vitals` package | Unused. They measure and draw nothing | N1 |
| The search box | `components/NavBar/NavBar.jsx`, the input inside `navbar-search` | "Find table, order, item..." has no handler. Typing does nothing | N2 |
| The notification bell | `components/NavBar/NavBar.jsx`, the button `notifications-btn` | The badge is the literal number 2. The button has no action | N3 |
| The leaderboard | `views/Challenges/Challenges.jsx`, `mockData.leaderboard` | Seven invented people, one of them "YOU (CJ)", with invented sales, average tips, and streaks | N4 |
| The challenges | The same file: `mockData.activeChallenges`, `completedChallenges`, `availableChallenges` | Invented challenges and progress. START CHALLENGE has no action. One challenge counts "perfect feedback scores", which the app does not collect | N5 |

Nothing fake was found on the backend, or on any other screen.

## Rules for every step

- **One pull request per step, in the order N1 to N5**, each branched from an up-to-date `main`, each merged by the owner before the next begins (working rules 1 and 10). Check the versions table first (rule 17).
- **Numbers are added up on the backend, in `BigDecimal`** (the reports convention). The screen picks a period and draws the answer.
- **One counting rule, shared with Financials.** A sale belongs to a period when its check was CLOSED in that period. A sale belongs to the person who OPENED the check (the order's `staffId`), the same rule tips follow (D10). Use the code `FinancialsReportService` already uses to work out a closed check's amounts; do not copy it. Checks closed before staff profiles existed have no `staffId` and belong to nobody.
- **Tips never leave the backend in any answer built here.** The leaderboard is visible to everyone (D6 below), and tips are private (D10). Do not send a tip and hide it on the screen.
- **Permissions:** every new endpoint gets its rule in `security/Permissions`, a row in `RoleMatrixIntegrationTest`, and its match in `utils/roles.js`. A new rule is named for its job, even when the roles are the same as an existing one.
- **JSON:** every new answer a screen reads gets a recording in `JsonShapeIntegrationTest`.
- **Schema:** each step that needs tables adds one new Flyway migration (`V8` is next). Moments are `timestamptz` (`Instant`). Never edit a merged migration.
- **Time:** "how long ago" and "time left" use `serverNow()`. "Today" is the backend's day.
- **Look:** new pieces use tokens only, work in all thirteen templates, and are added to the gallery in the same pull request. A new text-on-background pair goes in `contrast.test.js` (High Contrast asks 7 to 1). Two states told apart by color go in `distinct.test.js`, and each also has a word or an icon.
- **Refreshing is polling**, as everywhere else in the app. Pushed updates are P6.
- **Phone layout is not part of this.** P3 does every screen together. Do not make it harder: no fixed heights, let one part flex.
- **Verify** as rule 6 says: backend tests, lint, frontend tests, build, then click through on a freshly seeded database, in 8-Bit, one light template, and High Contrast. Say in the pull request what was and was not verified.
- **Update `CLAUDE.md` in the same pull request:** the new conventions, and remove each known gap the step closes.

## N1. The app's own name, and the leftovers

**Done (#298).** The whole logo did not hold up at 16 and 32 pixels, so the owner was asked, and chose a square crop of its emblem (the sword on the plate) for every icon size.

Small. No backend logic.

- `manifest.json`: the name and short name are "Notta-POS", with a one-line description, and theme and background colors that match 8-Bit (the default template). A manifest cannot read tokens, so these are literal values; say so in a comment in the pull request. Do not add install behavior. The installable app is P4; this step only stops the file from describing another project.
- Icons: `favicon.ico` and the 192 and 512 pixel icons are made from the owner's logo artwork, the same picture `components/Logo` shows in 8-Bit. Do not draw a new logo. If the artwork does not hold up at 16 and 32 pixels, stop and ask the owner for a small mark.
- If a file in `frontend/public` is added or renamed, update `SecurityConfig.FRONTEND_FILES`. The smoke test must still pass.
- Remove `src/reportWebVitals.js`, its import and call in `src/index.jsx`, `src/logo.svg`, and the `web-vitals` package (the lock file changes with it).

## N2. A search box that searches

What it is for: getting to a table or a check without walking through screens. It also closes a recorded gap: the Tables screen shows only the 50 most recently closed checks, with no way to find an older one.

**Backend.** One endpoint, `GET /api/search?q=...`, for anyone signed in. The answer has four groups, each capped (ten is enough), built with batched queries:

- **Tables**, by number.
- **Open checks**, by table number, check number (`orderId`), or the name of the person who opened it.
- **Closed checks**, by the same three. Today's (the backend's day) by default, with a parameter to look back further (yesterday, the last 7 days, the last 30).
- **Menu items**, by name, with price and whether the item is 86'd.

Rules:

- The text typed is always a query parameter handed to a repository method. It is never joined into a query string.
- A closed check in the results follows the tip rule exactly as the Tables screen does (`TableOverviewService.maySeeTip`, `ClosedCheckView`). Reuse it.
- Digits search from the first character (table 7). Words search from the second.

**Frontend.** A component of its own (`components/Search`), used by `NavBar`.

- Wait for a pause in typing before asking the backend. This is called debouncing: a timer restarts on every key, and the request goes only when about a quarter of a second passes with no key. Without it, typing "table 12" sends eight requests.
- Results appear in a panel under the box, in the four groups. Arrow keys move, Enter opens, Escape closes. A group with nothing in it is not drawn. Nothing at all: "Nothing found for ...".
- What a result does depends on what the signed-in person may open (`utils/roles.js`). A table or an open check opens the order screen for those who take orders. A closed check opens the closed-check pop-up the Tables screen uses; make that pop-up a shared component if it is not one. A menu item is information only (its price, and available or 86'D); it is a row, not a button, unless the person may change the 86 board, in which case it opens the board.
- Leaving the order screen through a result asks `confirmLeave` first, as every button in the bar does.

If the owner wants it narrower (no menu items, say), he will say so when he reads this. Otherwise build it as written.

## N3. Notifications (was B1)

**Decision D5, decided 2026-11-22.** The kitchen, bartenders, and managers may write and send a message to the floor. The app itself sends the other two kinds.

Three kinds:

| Kind | When | Who receives it |
|---|---|---|
| An item was 86'd, or is back | A menu item's 86 state changes | Everyone except the person who changed it |
| Order up | Completing an item leaves a check with no sent item still unfinished | The person who opened that check, unless they completed it themselves |
| A message | Someone in the kitchen, behind the bar, or a manager writes one (200 characters at most) | Everyone except the writer |

Proposed, not asked. The owner changes any of these by saying so before the step starts:

- A notification stays in a person's list until that person dismisses it. Anything older than 24 hours is no longer listed.
- A message goes to everyone. There is no sending to one role or one person yet.

**Backend.**

- A notification is written in the same transaction as the thing that caused it, so there is never an 86 without its notice or a notice for an 86 that was rolled back.
- Dismissing is per person and is stored in the database, not on the device: a server moves between terminals.
- `GET /api/notifications` answers the signed-in person's undismissed notifications, newest first. `POST /api/notifications/{id}/dismiss` and `POST /api/notifications/dismiss-all` dismiss for that person only. `POST /api/notifications/messages` sends a message and is limited to the three roles by a rule of its own in `Permissions`.
- A person reads and dismisses only their own. An integration test proves it with two people: A 86's an item, B is told and A is not; B dismissing changes nothing for C; "order up" reaches the opener and nobody else; a dismissed notification is still dismissed after signing out and in.
- The order screen already reloads the menu every 5 seconds to grey out an 86'd item. Keep that. The notification is the announcement, not the mechanism.

**Frontend.**

- The bell shows how many undismissed notifications the person has. At zero there is no badge. It never shows 0 and never shows a number the backend did not send.
- Pressing the bell opens a panel: each notification with an icon and a word for its kind, its text, how long ago, and a way to dismiss it; DISMISS ALL; and, for the three roles, a box to write a message. Empty: "No notifications."
- One place asks the backend, every 5 seconds, and every screen reads from it (a context or a hook beside the staff context). Do not add a poll per screen.
- The panel, a notification of each kind, the badge, and the empty state go in the gallery.

Not built: sound or vibration, notifications while the app is closed (that needs the installable app, P4), sending to one role or person, replies.

## N4. Per-person sales, and a real leaderboard (first half of B3)

**Decision D6, decided 2026-11-22.** Everyone signed in sees everyone's name, rank, and sales. The measures are the four below. Tips are not shown to anyone here.

**Backend.** `GET /api/reports/staff-sales?from=...&to=...`, for anyone signed in, in the same form as the Financials report (local date-times, the backend's clock). One row per person who opened at least one check closed in the period:

- checks closed
- total sales (the same figure Financials calls sales, so the two screens agree to the cent)
- check average (sales divided by checks)
- biggest single check
- items sold

A person who has since been deactivated still appears for a period in which they had sales. An integration test adds up every person's sales, adds the checks with no `staffId`, and compares the sum with the Financials report for the same period. The two must be equal.

**Frontend.** The leaderboard on the Challenges screen is drawn from that answer.

- The period is chosen the way Financials chooses it (today, week, month, custom). Share the picker; do not write a second one.
- Ranked by sales. Equal sales share a rank.
- The signed-in person's row is marked as theirs by staff ID, not by name.
- The "average tip" and "streak" columns are removed. If the owner wants a streak, he defines what one is and it becomes a column then.
- Nobody sold anything in the period: "No sales yet for this period."

After this step the challenge sections of the screen are still `mockData`. Remove them from the screen in this pull request (leave the leaderboard alone on the page) so that nothing fake is on `main` between N4 and N5.

## N5. Challenges on real data (second half of B3)

**Decided 2026-11-22, with D6.** A manager builds a challenge from one of four measures. The prize is text the manager writes, and a manager marks it awarded once it has been handed over.

**What a challenge is.** A name, an optional description, a measure, a target, a start and an end, an optional prize ("$20", "first cut Friday"), and who made it.

| Measure | The target means | Extra fields |
|---|---|---|
| Items sold | Sell this many | A menu category, or one menu item |
| Total sales | Reach this much in sales | |
| Check average | Hold an average of at least this | A minimum number of checks, so one large check does not win |
| Biggest single check | Close one check of at least this | |

Rules:

- **Progress is worked out from the checks each time it is asked for. It is never stored.** A stored count can drift from the checks; a computed one cannot. It uses the counting rule at the top of this brief, so a person's progress moves when a check is paid, not when an item is rung in. (Proposed, not asked. The cost is that the bar moves at payment. The gain is one rule, and numbers that match Financials.)
- **Nobody joins a challenge.** Everyone who opens checks in its period is in it. START CHALLENGE is removed.
- A person has completed a challenge when their progress reaches the target inside its period.
- **Awards are stored**, because handing over a prize is a fact, not a sum: which challenge, which person, which manager marked it, and when. Only a person who completed the challenge can be marked.
- Only managers create, change, end early, or delete a challenge, and mark awards. Everyone signed in reads them, with everyone's progress (D6).
- A challenge on a menu item that is later 86'd or taken off the menu keeps counting what was sold. One on a category no item belongs to any more shows zero.
- Categories and items offered in the form come from the menu, not from a list in the code.

**The screen.**

- ACTIVE: each running challenge with the signed-in person's progress toward the target, the time left, and the prize.
- FINISHED: each ended challenge with whether the person completed it, and whether the prize is waiting or awarded.
- For managers: NEW CHALLENGE, every participant's progress on each challenge, AWARDED for each person who completed one, and END or DELETE.
- No challenges: "No challenges yet." and, for a manager, the button to make one.
- `mockData` is gone from the file when this step is done. So are "YOU (CJ)" and every invented name.

Not built: team challenges, challenges that rank people against each other (the leaderboard does that), notifications about challenges, anything paid by the app.

A note for B5 (check handling): once an item can be voided or comped, it must stop counting toward sales and items sold here. Say so in that step's pull request.

## Decisions

| | Decision | State |
|---|---|---|
| D5 | Notifications: who sends to whom | **Decided 2026-11-22:** the kitchen, bartenders, and managers send messages; the app sends 86 changes and "order up". Proposed and open to change: to everyone, kept until dismissed |
| D6 | Challenges: measures, period, who sees what, prizes | **Decided 2026-11-22:** four measures (items sold, total sales, check average, biggest single check); everyone sees everyone's sales and rank, never tips; the prize is a manager's text, marked awarded by a manager. Proposed and open to change: counted when a check is paid |

Nothing else here needs the owner before the work starts. If a step turns up a question this brief does not answer, ask. Do not guess and build.

## When all five are merged

- Mark B1 and B3 done in `phase-2-plan.md`.
- Remove these from `CLAUDE.md`: the known gap about the nav bar's count and search box, the gap about `manifest.json` and the unused files, "Challenges and leaderboard on real data" and "working notifications" under Later, and the two lines that say there are no per-person sales.
- The plan's order resumes: P3 (phone-sized screens), P4, then B5 and B2.
