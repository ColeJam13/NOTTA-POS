# NOTTA-POS

A restaurant point-of-sale system built by someone with 20 years on the floor. The goal is to ease the pain points of the people who actually use a POS: servers, bartenders, hosts, and kitchen staff. Signature feature: after a server presses Send, items sit in a 15-second edit window ("limbo") before they lock and reach the kitchen.

This file is read by Claude Code at the start of every session. It carries the context and working rules for the project.

## Layout

```
backend/    Spring Boot 3.5 (Java 21, Maven), PostgreSQL
  src/main/java/com/notapos/
    controller/  REST endpoints (thin - no try/catch, no business logic)
    service/     business logic, @Transactional
    repository/  Spring Data JPA interfaces
    entity/      12 JPA entities, linked by plain ID columns (not @ManyToOne)
    dto/         response shapes that combine several entities
    enums/       status values (OrderItemStatus, OrderStatus, TableStatus, PaymentStatus) and StaffRole
    exception/   one exception per kind of refusal, and GlobalExceptionHandler, which turns each into a status code
    security/    StaffSessionInterceptor (sign-in sessions) and @RequiresRole
    config/      WebConfig (CORS, registers the interceptor), FirstManagerSetup
    scheduled/   TimerScheduledTask - locks items when the edit window expires
  src/main/resources/db/migration/   Flyway migrations: V1__baseline.sql, V2__..., one numbered SQL file per schema change
  peach-blossom-seed-data.sql   menu, modifiers, and tables for the demo restaurant
frontend/   React 19 (Create React App), lucide-react icons
  src/services/api.js     the only place the backend address lives
  src/utils/              formatters.js, totals.js (tax rate + order math)
  src/components/         shared pieces (NavBar, PaymentModal)
  src/views/              one folder per screen
Documentation/            spec sheet, data model, UX mockups, and phase-2-plan.md (the current work order)
```

## Running it

- Everything at once: `./start.sh` from the project folder. It starts PostgreSQL if needed, then the backend and frontend, opens the browser, and stops both on Ctrl+C. Output goes to `logs/`.
- The database password is not in git. It lives in `backend/application-local.properties` (ignored by git; copy `application-local.properties.example`), which `application.properties` reads through `spring.config.import`. On a server, set `NOTTA_DB_PASSWORD` instead. Never put a real password back into `application.properties`.
- Backend: `cd backend && mvn spring-boot:run` (port 8080). Needs a local PostgreSQL database; connection settings are in `backend/src/main/resources/application.properties`. Flyway creates and changes the tables at start-up from the files in `db/migration`; Hibernate only checks that they match the entities (`ddl-auto=validate`) and refuses to start if they do not. The seed SQL file is loaded by hand.
- Two settings in `application.properties` are specific to this app: `notta.cors.allowed-origins` (the addresses the frontend may be opened from, comma-separated; add the tablet-facing address here) and `notta.tax-rate`.
- Frontend: `cd frontend && npm install && npm start` (port 3000). To point at a different backend, set `REACT_APP_API_URL` in `frontend/.env.local`.
- Backend tests: `cd backend && mvn test`. Repository tests use Testcontainers, so Docker must be running.
- Frontend tests: `cd frontend && npm test -- --watchAll=false`. Strict build: `CI=true npm run build` (lint warnings fail it).
- CI (`.github/workflows/ci.yml`) runs all of the above on every pull request and on pushes to `main`.

## Working rules

1. **Never push to `main`.** One feature per branch, one pull request per feature. The owner reviews and merges.
2. **Read the current files before changing them.** Do not write code from memory of what a file used to contain.
3. **Comment heavily, in brief plain language.** Comments explain what a block does and why. This codebase is also a learning tool.
4. **Preserve existing comments** when editing or moving code. Dropping them during a refactor has been a recurring problem.
5. **Explain new or complex concepts** when introducing them (in comments and in the PR description).
6. **Verify before handing over.** Run the backend tests and the frontend build and tests. For anything that touches a screen, run the app and click through it. Say plainly in the PR what was and was not verified.
7. **Tests come with the code.** Each service method and endpoint gets tests in the existing style (Mockito for services, `@WebMvcTest` for controllers, Testcontainers for repositories). For frontend refactors, write behavior tests against the old code first.
8. **Refer to file and method names, not line numbers.** Line numbers go stale.
9. Plain, direct communication. No emojis.

Process rules, added 2026-11-20. They come out of the merge of pull request #273: it was built on top of seven other unmerged branches, GitHub reported conflicts, and resolving them in the web editor removed the role checks on 86 and floor layout, removed the tests that would have caught it, and left `main` not compiling until #274.

10. **One pull request open at a time, each branched from an up-to-date `main`.** Wait for the owner to merge before starting the next. No branch is built on top of another unmerged branch.
11. **Conflicts are never resolved in GitHub's web editor.** If GitHub shows conflicts, the owner says so, and Claude resolves them locally and re-runs the tests before anything reaches `main`.
12. **Delete a branch once its pull request is merged.**
13. **Bugs get a failing test first.** Write the test that reproduces the defect, watch it fail, then fix it.
14. **Keep this file current** in the same pull request as the change it describes.
15. **Every schema change is a new Flyway migration, in the same pull request as the entity change.** A new column, table, index, or constraint gets a new file `V<next number>__what_it_does.sql` in `backend/src/main/resources/db/migration`. Never edit a migration that has been merged: Flyway keeps a checksum of each file it has run and refuses to start if one changed. If a migration turns out wrong, fix it with another migration.

When pair-programming interactively instead of automating: ask before writing code, go one file and one concept at a time, and let the owner type the changes.

## Conventions

- **Backend layering:** controller -> service -> repository. Services throw `ResourceNotFoundException` (becomes 404) or `ItemLockedException` (becomes 409); controllers do not catch. `GlobalExceptionHandler` builds every error response.
- **Validation:** `@Valid` on create endpoints, with constraint annotations on the entity fields.
- **Transactions:** services are `@Transactional(readOnly = true)` at class level; methods that write add `@Transactional`.
- **Status values:** use the enums' `getValue()` and `matches()`. Do not type status strings by hand. Entity status fields are still `String` columns, so the database and JSON hold lowercase text (`draft`, `limbo`, `pending`, `fired`, `completed`).
- **The schema belongs to Flyway, not Hibernate.** `V1__baseline.sql` was written from the structure of the owner's real database on the day Flyway was adopted, which is why most ID columns are `bigserial` and only `staff` uses `generated by default as identity`. A database that existed before Flyway is marked as already at V1 (`spring.flyway.baseline-on-migrate`); a new one runs V1. Both then run V2 onwards and end up identical (`DatabaseMigrationTest` proves it). Tests get their tables from the same migrations, so every test run also checks that the migrations and the entities agree. The seed data is not a migration.
- **Rules that cross layers get an integration test.** `BaseIntegrationTest` starts the whole backend against a real PostgreSQL container and sends real requests (see `RingInIntegrationTest`). Use it for what a request must not be able to do, whatever it contains. Keep ordinary logic in the faster service and controller tests.
- **Avoid N+1 queries:** when a screen needs several entities together, add one endpoint that returns a DTO built from batched `WHERE ... IN` queries (see `ActiveOrderService`, `TableOverviewService`, and `FinancialsReportService`). `JOIN FETCH` does not apply because entities are not linked by JPA relationships.
- **Prefer a data-model change over a React workaround.** Example: Quick Orders were kept off the floor map with an `is_quick_order` column, not a frontend filter.
- **Frontend requests** go through `apiFetch` / `getJson` / `sendJson` in `services/api.js`. Never hardcode the backend address. `apiFetch` also adds the signed-in person's token, so a request made with plain `fetch` would arrive as nobody.
- **Permissions are decided twice.** `utils/roles.js` decides what a role is offered on screen; `@RequiresRole` on the controller method decides what the backend allows. Hiding a button is for a tidy screen, not for security: anything that must be restricted needs the annotation. A controller test for a restricted endpoint must sign in first (see `StaffControllerTest` for the pattern: autowire `StaffSessionInterceptor`, call `createSession`, send the token header).
- **Never return the `Staff` entity from a controller.** It holds the PIN hash. Use `StaffView`.
- **Reading who is signed in:** on the frontend `const { staff } = useStaff();` (null in a test that draws one screen on its own, which `utils/roles.js` treats as unrestricted). On the backend `@RequestAttribute(name = StaffSessionInterceptor.CURRENT_STAFF, required = false) StaffSession currentStaff`.
- **Money math** goes through `utils/totals.js`.
- **Reports are added up on the backend.** `GET /api/reports/financials?from=...&to=...` returns the Financials screen's numbers for the checks closed in a period, using `BigDecimal`. The screen only picks the period and draws the answer. `from` and `to` are local date-times with no zone (`2026-11-18T00:00:00`), the same clock orders are stamped with.
- **The backend works out the check.** `OrderCheckService` adds up an order's sent items, tax, and total; `GET /api/orders/{id}/check` returns it and the Payment modal shows it. `PaymentService.createPayment` charges that number and refuses (409) anything that does not match it. `utils/totals.js` is only for the running total on the order screen and for orders closed before totals were recorded. Draft items are not on the check.
- **An order is only closed by a payment or cleared when empty.** `PaymentService.createPayment` refuses an order that is missing, already closed, empty, or holding unsent items, then records subtotal, tax, and total on the order. `OrderService.deleteOrder` clears an empty open order and frees its table; it refuses anything else. Refusals throw `OrderStateException` (409) with a message written for the person at the screen, and the frontend shows that message as-is.
- **A table has one open order, and the database enforces it.** `OrderService.createOrder` locks the table row, marks the table occupied in the same transaction, and returns the existing open order if the table already has one. Behind that, the index `orders_one_open_per_table` (unique on `table_id` where `status = 'open'`, migration V3) makes PostgreSQL refuse a second one. The frontend does not set table status.
- **`createOrder` builds a new `Order`.** It takes only the table, order type, and server from the request. Never save a request's own entity: one that carries an ID means "update that row", which is how a request once reopened a paid check.
- **Two terminals, one check: lock the order row before acting on what you read.** "Read that the order is open, then act" is two steps, and another terminal can close the check between them. Two finders in `OrderRepository` hold the row until the transaction ends. `findByIdLockedForShare` is for anything that adds to, sends, changes, or removes items (it is inside `requireOpenOrder`); many requests can hold it together. `findByIdLockedForUpdate` is for anything that closes or removes the order (`PaymentService.createPayment`, `OrderService.deleteOrder`); it waits until nobody else holds the row. New code that closes an order, or changes what is on one, must use the matching finder, not `findById`. Nothing that holds the share lock may update the order row itself: two such requests would each wait for the other (a deadlock), and PostgreSQL would cancel one of them.
- **`Order` has a version number (`@Version`).** A save that started from an out-of-date copy of an order is refused, and `GlobalExceptionHandler` answers 409 ("This check was just changed on another terminal"). It is the net under the locks, for code that reads an order and saves it without one.
- **Races get a test that runs the race.** `TwoTerminalsIntegrationTest` fires two real requests from two threads at the same instant, twenty times over, and checks what the database holds afterwards. A unit test cannot show these defects: each request on its own does the right thing.
- **One edit window per order.** Sending, removing, or changing an item during the window restarts it for every item on that order still counting down (`OrderItemService.restartEditWindow`), because the screen shows a single countdown. Paying ends it.
- **The order screen is rebuilt on every visit.** `App.js` gives `CreateOrder` a new `key` each time the view is opened, so no state carries over from the previous table. Do not add state to `CreateOrder` that is meant to survive leaving the screen.
- **Item price includes upcharges.** `OrderItem.price` is the price of one item with its modifier upcharges already added, so every screen that sums `price` is correct without knowing about modifiers. The backend works the price out for every item (`OrderItemModifierService.ringInItem`); the request carries no price, and one sent anyway is ignored.
- **There is one way to ring in an item.** `POST /api/order-items` takes an `OrderItemRequest` and calls `OrderItemModifierService.ringInItem`, which holds every rule: the order must exist and be open, the menu item must exist, be on the menu, and not be 86'd, the modifiers must follow the menu's rules, and the price comes from the menu. Do not add a second way to create an order item: a new path would have to repeat every one of those checks, and the last time there were two paths one of them had none.
- **Modifiers are saved with the item, not after it.** `ringInItem` saves the item, its modifiers, and its note in one transaction (`MenuModifierService` supplies the rules to both the picker and the check). Do not go back to one request per modifier: a half-saved item would reach the kitchen without its modifiers.
- **Anything that adds to, sends, changes, or removes an order's items calls `OrderItemService.requireOpenOrder` first.** Two terminals can have one table up; if one closes the check, the other must not be able to add to it or take an item off it. The call also holds the order open until the request finishes (see the locking convention above). The refusal is `OrderNotOpenException`: a 409 whose body has `code: "ORDER_NOT_OPEN"`. A payment for a check that is already closed answers the same way. The order screen and the Payment modal check that code (`ORDER_NOT_OPEN` in `services/api.js`), tell the server the check was closed elsewhere and what did not happen (nothing added, removed, or charged), and reload the table.
- **Screens react to `code`, never to `message`.** `ApiError.code` is a fixed word for the few errors a screen must handle in a specific way. `message` is wording for people and may change.
- **Frontend structure:** `views/` for screens, `components/` for shared pieces, one CSS file per component or view. Pieces private to one screen live in that screen's folder (see `views/CreateOrder/`).
- **Tables and Quick Orders are one component.** `components/TableChecks` draws both screens from `GET /api/tables/overview`; `views/Tables/Tables.js` and `views/QuickOrders/QuickOrders.js` only pass it which tables to ask for and what the screen is called. A change to one screen belongs in `TableChecks`, so the other gets it too.
- **Separation of concerns:** payment actions stay separate from order actions.
- JSON quirk: `xPosition` / `yPosition` on tables serialize as `xposition` / `yposition`.

## Things learned the hard way

- The edit-window countdown is set to 16 seconds on the backend so the screen can show a full 15. Do not "fix" the extra second.
- Save order items in parallel with `Promise.all` and wait for all of them before sending. Sequential saves caused items to miss the send.
- Match newly saved items to on-screen items by position, not by menu item. Matching by menu item gave two of the same dish one shared ID.
- A slow Floor Map load once turned out to be a Firefox-on-localhost issue, not the code. Measure backend response times before changing code for performance.
- On PostgreSQL 15+, the application's database user needs an explicit `CREATE` grant on the `public` schema on a new machine.
- Build output (`backend/target/`) and `backend/restaurant.db` (left over from the SQLite days) were committed for a long time, so every Maven run showed up as changed files. They are ignored now. If `git status` ever lists something under `target/` again, it was added with `git add -f`; remove it with `git rm --cached`.
- Testcontainers must be 1.21.4 or newer to work with current Docker engines.
- Hibernate sends new rows to the database before it sends deletes, whatever order the code wrote them in. With the one-open-order index, "delete this table's open order, then save a new one" in one transaction is refused, because the old row is still there when the new one arrives. Call `flush()` on the repository between the two (see `OrderRepositoryTest`).
- The seed file inserts rows with plain SQL and only names some columns. A new column that the app filters on needs a `DEFAULT` in its migration, and the migration should fill in existing rows too (see `V2__default_quick_order_flag.sql`). A missing default once left the Floor Map empty: the seeded tables held NULL for `is_quick_order` and the map asked for `false`.
- `ddl-auto=update` let two databases drift apart without anyone noticing. When Flyway was adopted, the owner's database turned out to differ from a freshly created one in two ways (a missing default, and an older style of ID column). Comparing a structure-only dump (`pg_dump --schema-only`) of the real database with the migrations is what found it; write a baseline from the real thing, not from what the code would create today.
- Click through a full service (seat, send, pay, reopen, clear) on a freshly seeded database before calling a flow done. Feature 1 was verified from the Create Order button and never touched the Floor Map, so the empty map went unnoticed.

## Current state (November 2026)

Everything through pull request #279 is merged, and `main` passes CI (591 backend tests, 142 frontend tests). Built: CI and the first cleanup pass, modifiers and kitchen notes, the working-state pass over the core flow, the 86 board, the editable floor map, combined endpoints for Tables, Quick Orders, and Financials, the kitchen display, Spring Boot 3.5, the start script, and staff profiles with PIN sign-in and roles.

**The work order from here is `Documentation/phase-2-plan.md`** (revision 2, which followed a check of #275 to #279). Read it before starting anything; its "Order of work" table says what is next. In short:

- Part A fixes defects and open doors in what is built, in order: A1 one server-side ring-in path (done), A2 two terminals on one check (done), A3 lock the API (now on Spring Security, in two pull requests), A4 sign-in hardening and sessions in the database, A5 one tax rate, ticket order, and the clock.
- Part P is platform work: Vite in place of Create React App, a packaged build and a home test host, phone-sized screens, an installable app, and later store apps with Square.
- Part B is features: notifications, host view with waitlist, challenges on real data, UI templates (Plain, Coffee Shop, Nightclub, 8-Bit), check handling.
- It lists decisions that are the owner's (D3 to D9 are open). Ask each one when it is needed. Do not guess and build.

Some items in the list below are scheduled in that plan (the two tax rates, ticket order, the clock, the open API, sign-out, the starting PIN). They stay listed here until the pull request that fixes each one removes it.

Known gaps left open on purpose:

- A table opened at the very instant its previous check is being paid can be handed that check. The first item rung onto it is refused with `ORDER_NOT_OPEN`, and the screen recovers the usual way (it says so, and Send starts a new check). Nothing is lost or mischarged.
- The task that locks items when the edit window runs out (`TimerScheduledTask`) does not take the order lock. Removing an item in the same few milliseconds its window expires is not guarded.

- The Financials report loads once per period and does not refresh on its own; pick the period again to see checks closed since.
- Entity status fields are `String`, not enum-typed.
- `GET /api/menu-items` returns active items whether or not `?active=true` is passed.
- `subtotal`, `tax`, and `total` on orders are written when the order is paid. Orders closed before that hold zeros; the backend works their amounts out from their items wherever it reports on closed checks (`TableOverviewService`, `FinancialsReportService`). Nothing calls `PUT /api/orders/{id}/totals` or `PUT /api/orders/{id}/close`.
- The tax rate lives in two places: `notta.tax-rate` (backend, the one that is charged) and `TAX_RATE` in `utils/totals.js` (the order screen's running total). They must be changed together.
- Timestamps are `LocalDateTime` with no time zone, and the countdown and "5m ago" labels compare them with the device's own clock. A tablet whose clock is off, or a backend in another time zone, would show wrong times and a wrong countdown.
- One payment closes the whole order. There are no split checks, partial payments, refunds, or voids of sent items.
- Every Quick Order creates a row in `tables` that is never removed.
- The Tables and Quick Orders screens show the 50 most recently closed checks, not all of them. There is no way to page back further or search for an older check.
- The order screen does not refresh while it is open. Item statuses changed elsewhere (kitchen starts an item, another server adds one) show up only when the table is reopened. The Payment modal is not affected: it reads the check from the backend.
- Parallel draft saves give items IDs that do not follow ring-in order, and Active Orders sorts by ID, so a ticket can list items in a different order than the server entered them.
- The nav bar shows a hardcoded notification count; the search box does nothing.
- Sessions are kept in the backend's memory. Restarting the backend signs everyone out, and the app could not run as several backend copies without moving sessions to the database.
- Only the endpoints marked `@RequiresRole` check who is asking. Ordering, sending, paying, and reading are open to any request that can reach the backend.
- A session does not end by itself while the screen is in use, and there is no sign-out after a period of no activity. On a shared terminal the next person acts as the last one until someone presses SIGN OUT.
- The first manager's starting PIN (1234) is the same for every new database. It should be changed on first sign-in; nothing forces that yet.

## Feature plan

The original four features, all built. What comes next is in `Documentation/phase-2-plan.md`.

### 1. Modifiers and kitchen notes (built)

- Tapping an item with modifier groups opens `views/CreateOrder/ModifierPicker.js`: required groups must be answered, `maxSelections` is respected, optional groups can be skipped. Items without groups are still added with one tap.
- Every item can carry a free-text kitchen note. The pencil on an order line reopens the picker to change modifiers or the note while the item is a draft or in the edit window.
- `GET /api/menu-items/modifier-groups` returns every item's groups with their choices in one response; the order screen loads it together with the menu.
- `POST /api/order-items` rings in an item with its modifiers and note (this was `/with-modifiers` until every item was moved onto the one path). `PUT /api/order-items/{id}/modifiers` replaces them. `GET /api/order-item-modifiers/order/{orderId}` returns an order's modifiers with names.
- `ActiveOrderItemView` carries `modifiers`, and the Active Orders screen prints modifiers and notes under each item in both views.

### 2. 86 board (built)

- `MenuItem.is86d` (column `is_86d`, default false) means out for now. It is separate from `isActive`: an inactive item is off the menu and hidden, an 86'd item stays on the menu.
- `PUT /api/menu-items/{id}/86` and `/un-86` change it. `GET /api/menu-items/86` lists what is out.
- `views/EightySixBoard/` is the board: what is 86'd now at the top, the whole menu by category below, tap to 86 or put back on. It refreshes every 3 seconds.
- On the order screen `MenuPanel.js` shows an 86'd item greyed out, marked 86'D, and untappable. `CreateOrder.js` reloads the menu every 5 seconds to pick up changes.
- The backend refuses to ring in an 86'd item (`ItemUnavailableException`, 409), in `OrderItemModifierService.ringInItem`. Items already sent are not affected when their menu item is 86'd afterwards.
- Who may 86: the kitchen, bartenders, and managers (see feature 4). Everyone else can read the board.
- Not built: a count of how many are left, and 86'ing a single modifier.

### 3. Editable floor map (built)

- `PUT /api/tables/layout` takes `{ "tables": [{ tableId, x, y, rotation }] }` and saves the whole list in one transaction (`TableService.updateLayout`). It only changes position and rotation. `TableService.updateTable` still only saves `status`.
- EDIT LAYOUT on the Floor Map switches to edit mode: drag a table to move it (snaps to a 10 pixel grid, cannot leave the floor), tap one to select it, ROTATE turns it 45 degrees. Nothing is saved until SAVE LAYOUT; CANCEL puts everything back. Only the tables that changed are sent.
- In edit mode the screen works on a copy of the tables and stops its 3-second refresh. Outside edit mode a tap still opens the table's order.
- Dragging uses mouse and touch events, not pointer events, because the test browser (jsdom) does not support pointer events.
- Not built: adding or removing tables, changing a table's shape or seat count, and sections.

### 4. Staff profiles and roles (built)

- The owner's decisions: sign-in is a 4-digit PIN, like Toast. Managers see everything; other roles are limited to their own screens.
- `Staff` (name, role, BCrypt PIN hash, active) and `StaffService`. A PIN belongs to one active person. After five wrong PINs in a minute sign-in pauses for 30 seconds. The last active manager cannot be deactivated or demoted. Bringing back a deactivated person needs a new PIN.
- `POST /api/staff/sign-in` returns the person and a token. The frontend sends the token in the `X-Staff-Token` header (`services/api.js`). `StaffSessionInterceptor` holds the sessions in memory and enforces `@RequiresRole` on controller methods: 401 with no sign-in, 403 with the wrong role.
- Restricted on the backend: staff management, the Financials report, and the floor layout (manager); 86 and un-86 (manager, kitchen, bartender). Everything else is open to any request.
- `utils/roles.js` is the one list of which role may open which screen. `App.js` shows the PIN pad (`views/SignIn`) until someone signs in, and provides who that is through `context/StaffContext.js` (`useStaff()`).
- `views/Staff` (managers) adds people, changes names and roles, sets new PINs, and deactivates.
- A new order is stamped with the signed-in person: `serverName` and `staffId` (`OrderController.createOrder`).
- When the staff table is empty, start-up creates a manager with PIN 1234 (`FirstManagerSetup`) and logs a warning.
- Not built: sign-out after a period of no activity, a record of who did what besides opening an order, per-person sales, and locking the rest of the API to signed-in staff.

### Kitchen display (built, not in the original plan)

- The BOH view of Active Orders (`views/ActiveOrders/ActiveOrders.js`) is the kitchen display. The FOH view is unchanged.
- Station buttons (ALL, plus one per prep station from `GET /api/prep-stations`) show a station only its own items. An item whose menu item has no station is shown to every station.
- Each ticket shows how long its oldest unfinished item has been waiting (yellow at 10 minutes, red at 15; the constants are at the top of the file). Tickets are listed longest wait first.
- A ticket with nothing left to make drops off the view. SHOW DONE brings finished tickets back.
- `ActiveOrderItemView` carries `prepStationId` and `sentAt` for this.
- Not built: sound or flashing on a new ticket, a "bump" button for a whole ticket, per-station default (each tablet remembering its station), and routing tickets to printers.

### Later

Challenges and leaderboard on real data (needs staff profiles), host view with waitlist, switchable themes, working notifications.
