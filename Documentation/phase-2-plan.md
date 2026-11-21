# NOTTA-POS: Phase 2 plan

Written 2026-11-20 after an independent review of pull requests #263 to #274. This is the work order for the next phase. Read `CLAUDE.md` first; its working rules apply to everything here.

## Where things stand

- `main` (commit `947e550`) is file-for-file identical to `6c239c0`, the last tested commit of the staff-profiles branch. The restore in #274 was complete.
- CI on `main` is green: 547 backend tests, 134 frontend tests.
- Built and merged: modifiers and kitchen notes, the working-state pass, 86 board, editable floor map, combined endpoints for Tables / Quick Orders / Financials, kitchen display, Spring Boot 3.5, the start script, staff profiles with PIN sign-in and roles.

What the review confirmed is sound:

- Payment (`PaymentService.createPayment`) builds the check on the server, refuses a payment whose amount differs from it, refuses unsent items and closed orders, and writes subtotal, tax, and total on the order.
- Items rung in through `/api/order-items/with-modifiers` get their price from the menu and the chosen modifiers on the server, with modifier rules enforced.
- PINs are stored as BCrypt hashes, staff responses use `StaffView` (no hash leaves the backend), session tokens come from `SecureRandom`, and a role or PIN change ends that person's sessions.

What the review did not cover line by line: the Financials arithmetic, the Tables overview queries, the Floor Map drag code, and the Spring Boot upgrade. Those rest on their tests and on the click-throughs recorded in their pull requests.

## Process rules for this phase

These come out of the #273 merge, where conflicts resolved in GitHub's web editor removed the role checks on 86 and floor layout, removed the tests that would have caught it, and left `main` not compiling until #274.

1. **One pull request open at a time, each branched from an up-to-date `main`.** Wait for the owner to merge before starting the next. No branch is built on top of another unmerged branch.
2. **Conflicts are never resolved in the web editor.** If GitHub shows conflicts, the owner says so and Claude resolves them locally and re-runs the tests.
3. **Delete a branch once its pull request is merged.** Sixteen merged branches are still on the remote; the first housekeeping step is to remove them. One of them is `dev`, which Claude did not create: ask the owner before deleting it. **Answered 2026-11-20: `dev` is the owner's safety branch. Leave it alone.**
4. **Bugs get a failing test first.** For each defect below, write the test that reproduces it, watch it fail, then fix it.
5. **Keep `CLAUDE.md` current** in the same pull request as the change, as has been done so far.

Owner actions (settings Claude cannot change):

- Turn on branch protection for `main`: require both CI checks to pass and the branch to be up to date before merging. That would have blocked the broken merge.
- Change the local database password. The old one is still readable in this public repository's history.

## Part A: fix first

Do these in order, one pull request each. They are defects or open doors in what is already built, and the features in Part B depend on them.

### A1. One way to ring in an item, and only onto an open order

**Done 2026-11-20.** `POST /api/order-items` is the single ring-in endpoint (`OrderItemModifierService.ringInItem`); `/with-modifiers` and `PUT /api/order-items/{id}` are removed. The text below is kept as the record of what was found.

Found in `OrderItemService.createOrderItem`, `OrderItemService.updateOrderItem`, and `OrderItemModifierService.createOrderItemWithModifiers`.

- **Neither ring-in path checks the order.** An item can be saved onto a closed order, or onto an order ID that does not exist. On the floor: server A has a table's order screen open, server B closes that check on another terminal, A adds a round and presses Send. The items attach to the closed order. They are never billed, and the kitchen never sees them because Active Orders lists open orders only.
- **The plain path trusts the browser's price.** `POST /api/order-items` saves whatever `price` the request carries. The order screen uses this path for every item without modifiers or a note (`createDraftItem` in `orderActions.js`). `PUT /api/order-items/{id}` also takes a price from the request; nothing in the frontend calls it.
- The plain path does not check that the menu item exists or is active, and (already listed in `CLAUDE.md`) skips modifier rules.

Build:

- A single server-side ring-in routine used by both endpoints: load the order and refuse unless it is open (`OrderStateException`, 409), load the menu item and refuse if missing, inactive, or 86'd, validate modifiers, and set the price on the server. The request's `price` is ignored.
- Either point the order screen at one endpoint for all items, or keep both endpoints as thin wrappers over the shared routine. Prefer one endpoint.
- Apply the same open-order check to send, send-now, and modifier replacement.
- Remove `PUT /api/order-items/{id}` if nothing needs it. If it stays, it must not accept a price, and its timer reset must check `limbo` (the known gap in `CLAUDE.md`).
- On the order screen, a 409 for a closed order should tell the server plainly that the check was closed elsewhere and reload the table.

Done when: tests show a ring-in onto a closed order and onto a missing order is refused, a request with a made-up price is saved at the menu price, and the two-terminal case above is clicked through with two browser tabs.

### A2. Two terminals, one check

**Done 2026-11-20** (Flyway in #278, the rest in #279). `Order` has a `@Version` column, and migration `V3` adds the partial unique index `orders_one_open_per_table` after merging any table's extra open orders into its oldest one. Two things were added beyond the list below:

- **Row locks.** A version column only catches two requests that both save the order. A ring-in or a removal never saves the order, so no version check stood between it and a payment landing at the same instant: the item ended up on, or came off, a check that had just been paid. Two new tests showed both on the old code. Paying and clearing now hold the order row alone (`findByIdLockedForUpdate`); ring-in, send, modifier changes, and removing an item hold it open (`findByIdLockedForShare`, inside `requireOpenOrder`). Opening an order locks the table row. The version column and the index stay as the net underneath.
- **`POST /api/orders` could overwrite an existing order.** A request body that named an existing order's ID was saved over it, so a paid check could be reopened. `createOrder` now builds a new order from the table, type, and server only.

The terminal that loses a race is told so with the code `ORDER_NOT_OPEN` (payment, removal). The pay button was already disabled while a payment is in flight; a test now holds it there. `TwoTerminalsIntegrationTest` runs each race twenty times. The text below is kept as the record of what was found.

No entity has a version column and no query takes a lock.

- **Double payment.** Two `createPayment` calls for the same order at the same moment both read it as open, and both save a payment.
- **Two open orders on one table.** The one-open-order rule lives only in service code, so two simultaneous opens can both succeed. #264 covered this with a unit test only.

Build:

- Optimistic locking on `Order`: a `@Version` column. The database then rejects the second of two saves that started from the same copy of the row, and that becomes a clear 409 ("this check was just changed on another terminal").
- A database guarantee of one open order per table. In PostgreSQL that is a partial unique index: unique on `table_id` where `status = 'open'`. `ddl-auto=update` cannot create one, which is the reason for the decision in the next point.
- **Flyway adopted 2026-11-20** (the bullet below, done as its own pull request). One departure from the text: `V1` matches the owner's real database, not what Hibernate would create today. A structure-only dump showed the two differ (older-style ID columns on eleven tables, and no default on `tables.is_quick_order`), so a baseline written from Hibernate's output would not have made them identical. `V2` adds the missing default. The version column and the partial unique index are still to do.
- **Decided (D1): adopt Flyway.** Flyway runs versioned SQL files (`V1__baseline.sql`, `V2__...sql`) at start-up, in order, and records which have run, so every database goes through the same schema changes. Do this as its own pull request before the locking work:
  - `V1` is a baseline that matches the schema Hibernate creates today, so a brand-new database and the owner's existing one end up identical. Use Flyway's baseline setting so the existing database is marked as already at `V1` instead of being rebuilt.
  - Switch `ddl-auto` from `update` to `validate`: Hibernate then checks that the tables match the entities and refuses to start if they do not, but never changes them.
  - From then on every new column, table, or index is a new migration file in the same pull request as the entity change. Add that rule to `CLAUDE.md`.
  - The seed data stays a separate hand-loaded file; do not make it a migration.
  - Test the owner's case specifically: start the new build against a database created by the old one, with data in it, and confirm nothing is lost.
- Disable the pay button while a payment request is in flight, if it is not already.

Done when: a test fires two payments at one order and exactly one succeeds, and a repository test shows the second open order on a table is rejected by the database itself.

### A3. Lock the API

90 endpoints exist; 7 check who is asking. Role-limited screens are cosmetic until this is done. Open today, with no sign-in at all:

- Create, change, deactivate, and delete menu items, including prices.
- `PUT /api/payments/{id}` and `DELETE /api/payments/{id}`.
- `PUT /api/orders/{id}/close` (closes a check with no payment) and `PUT /api/orders/{id}/totals` (takes the tax rate from the caller). Nothing in the frontend calls either.
- Every read, including all payments and tips.

Build:

- **Default deny.** Every `/api/**` request needs a valid session except `POST /api/staff/sign-in`. Flip `StaffSessionInterceptor` so a missing session is refused unless the method is explicitly marked public.
- A role rule on every endpoint that changes data. **Owner decision D2:** confirm the matrix below before building.

| Action | Roles |
|---|---|
| Read menu, tables, orders, active orders | any signed-in staff |
| Open orders, ring in, send, take payment | server, bartender, host, manager |
| Start and complete items | kitchen, bartender, manager |
| 86 and un-86 | kitchen, bartender, manager (as now) |
| Menu items, modifiers, prep stations: create, change, remove | manager |
| Staff, Financials, floor layout | manager (as now) |
| Read payments and tips | manager |

- Delete the endpoints nothing uses: order close, order totals, payment update, payment delete. Refunds and voids come back properly in Part B.
- Keep `utils/roles.js` and the backend rules in step, and say in `CLAUDE.md` which one is the source.
- `OrderController.createOrder` can then require a session and always stamp the staff member, instead of accepting a `serverName` from the request.

Done when: a test walks every mapped endpoint without a token and gets 401 for all but sign-in, role tests cover each row of the matrix, and each role is clicked through in the browser.

### A4. Sign-in hardening

Found in `StaffService` and `StaffSessionInterceptor`.

- **The lockout is one counter for the whole restaurant.** Five wrong PINs in a minute from any device pause sign-in for every device. A busy shift can trip it by accident, and anything on the network can hold it shut by sending five bad PINs every 30 seconds.
- The counter is cleared after each pause, so guessing continues at about ten tries a minute indefinitely. With a 4-digit PIN and no username, that finds someone's PIN within hours.
- The first manager's PIN is 1234 on every new database and nothing forces a change (already in `CLAUDE.md`).
- Sessions are removed only when their token is next used, so abandoned tokens stay in memory until restart.

Build:

- Count failures per device (client address is enough for now) and lengthen the pause on repeat offences. Keep a much higher restaurant-wide ceiling as a backstop.
- Force a PIN change the first time the starter manager signs in, and refuse 1234 as a new PIN.
- A scheduled sweep that drops expired sessions.
- **Owner decision D3:** how a shared terminal signs people out. Options: after every sent order or payment (how most restaurant terminals work), after N minutes without a tap, or only on SIGN OUT (today). This is a floor-workflow call.

### A5. Small consistency fixes (one pull request)

- **One tax rate.** The order screen should get the rate from the backend rather than hold its own `TAX_RATE`. The two can disagree today, and the guest would see one total while being charged another.
- **Ticket order.** Items should print in the order the server rang them in. Parallel saves hand out IDs out of sequence and Active Orders sorts by ID. Add an explicit position or ring-in timestamp and sort on that.
- **Clock.** The countdown compares backend timestamps with the tablet's clock. Send the time remaining (or the server's current time) with the response so a tablet with a wrong clock still counts down correctly.

## Part B: features

**Owner decision D4:** confirm this order. Items 1 to 4 are from the original roadmap. Item 5 was not on it and is a recommendation. Item 6 is the owner's longer-term idea, recorded here so earlier work does not close it off.

### B1. Working notifications

The nav bar bell shows a hardcoded count. Build real ones: an 86 or un-86 reaches every order screen, "order up" reaches the server who opened the order when the kitchen completes its items, and kitchen and managers can write and send a message to the floor. Needs decision D5: who may send to whom, and whether a notification must be acknowledged. Polling is acceptable to start, consistent with the rest of the app.

### B2. Host view with waitlist

A host screen: floor with table statuses (use the existing `TableStatusLog` for how long a table has been seated), a waitlist (name, party size, quoted wait, time added, seated or left), seat-from-waitlist onto a table, and quick orders. Statuses beyond available and occupied (reserved, needs bussing) belong here; add them to `TableStatus`.

### B3. Challenges and leaderboard on real data

`views/Challenges` is entirely mock data. Orders now carry `staffId`, so per-person sales are available. Needs decision D6: which measures count (sales, a featured item, check average, fewest edits inside the timer) and over what period. Build the per-person sales report first; the leaderboard is a view of it.

### B4. Switchable themes

Move colors into CSS variables on a root class, add a theme picker for managers, and store the choice so every terminal shows the same theme. No per-component rewrites beyond replacing hardcoded colors.

### B5. Check handling (recommended addition)

`CLAUDE.md` lists it as a known gap: one payment closes the whole order, with no split checks, partial payments, voids of sent items, or comps. These are everyday floor actions. A sensible first slice is void and comp with a manager PIN and a reason, each recorded with who approved it, followed by split by item and split evenly.

### B6. Staff phones as terminals (later; think about it, do not build yet)

The owner's idea: servers and bartenders install the app on their own phones, the phones talk to a server running inside the restaurant, and payment could go through Square. The restaurant then needs no dedicated terminals.

How it fits what exists:

- The frontend is already a web app, so the shortest route to "an app on the phone" is an installable web app (a PWA: a website the phone can add to its home screen and open full-screen like an app). That avoids writing and publishing separate iPhone and Android apps. A native app only becomes necessary if a feature demands it.
- A server inside the restaurant is the original "runs without the internet" idea for this project. In practice it is one small machine on the restaurant's network running the backend, the database, and serving the frontend.

What it would take, roughly in order:

1. **Phone-sized screens.** The order screen, tables, and payment are laid out for a tablet. They need a narrow layout with large tap targets.
2. **Serve the app from the restaurant's server** and make it installable. Installable web apps require HTTPS, and HTTPS on a private network address needs a certificate plan. This is the least obvious part and should be investigated first.
3. **Part A is a hard requirement, not a nicety.** Personal phones on the restaurant Wi-Fi mean any device can reach the backend. A3 (lock the API) and A4 (per-device lockout, sign-out) must be finished, and sessions should move from memory to the database so a backend restart does not sign out the whole floor mid-shift.
4. **Joining a phone to the restaurant.** A QR code on the manager's screen that carries the server address is the usual approach; staff then sign in with their PIN as now.
5. **Card payments through Square.** The app must never see or store card numbers; Square's own software handles the card, which keeps this project out of card-security compliance scope. Two routes exist as of November 2026 and need re-checking when this is built:
   - Square's Point of Sale API works from a mobile web page: the app hands an amount to the Square app on the same phone, Square takes the card (reader or tap-to-pay on the phone), and returns the result. It carries an amount only, not the itemized check.
   - Square's Mobile Payments SDK gives a smoother checkout inside one app, but only for native iPhone and Android apps.
   The payment record needs a field for the processor's transaction reference, and `PaymentService.createPayment` needs a "card approved by processor" path alongside today's flow.
6. **Flaky Wi-Fi.** A phone that drops off the network mid-order should hold the order and retry, not lose it. Decide how much offline behavior is worth building.

Design choices in earlier steps that keep this open: keep all business rules on the backend (A1 already moves pricing there), keep the frontend talking only through `services/api.js`, and avoid anything that assumes a large screen or a mouse in new screens.

Open questions for the owner when this is picked up: installable web app or native apps, whether each restaurant uses its own Square account, how tips are entered on a phone handed to a guest, and what a server does when their phone dies mid-shift.

## Decisions needed from the owner

| | Decision | Needed before |
|---|---|---|
| D1 | Adopt Flyway migrations and stop Hibernate from changing the schema at start-up? **Decided 2026-11-20: yes.** | A2 |
| D2 | Confirm or correct the role matrix. **Decided 2026-11-20: confirmed as written**, including hosts opening orders, ringing in, sending, and taking payment. | A3 |
| D3 | How shared terminals sign people out | A4 |
| D4 | Feature order for Part B, and whether B5 is in | Part B |
| D5 | Notifications: who sends to whom, acknowledged or not | B1 |
| D6 | Challenges: which measures and what period | B3 |
| D7 | Staff phones: installable web app or native apps, and the Square account model | B6 |

Ask these at the point they are needed. Do not guess and build.

## How to start

1. Housekeeping pull request: delete the merged remote branches, and add the process rules above to `CLAUDE.md`.
2. Ask the owner D2 while A1 is in progress, since A3 follows closely. D1 is already decided. (D2 was answered on 2026-11-20.)
3. Start A1.
