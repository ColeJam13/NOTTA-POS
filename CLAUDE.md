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
    entity/      11 JPA entities, linked by plain ID columns (not @ManyToOne)
    dto/         response shapes that combine several entities
    enums/       status values (OrderItemStatus, OrderStatus, TableStatus, PaymentStatus)
    exception/   ResourceNotFoundException, ItemLockedException, GlobalExceptionHandler
    scheduled/   TimerScheduledTask - locks items when the edit window expires
  peach-blossom-seed-data.sql   menu, modifiers, and tables for the demo restaurant
frontend/   React 19 (Create React App), lucide-react icons
  src/services/api.js     the only place the backend address lives
  src/utils/              formatters.js, totals.js (tax rate + order math)
  src/components/         shared pieces (NavBar, PaymentModal)
  src/views/              one folder per screen
Documentation/            spec sheet, data model, UX mockups
```

## Running it

- Everything at once: `./start.sh` from the project folder. It starts PostgreSQL if needed, then the backend and frontend, opens the browser, and stops both on Ctrl+C. Output goes to `logs/`.
- The database password is not in git. It lives in `backend/application-local.properties` (ignored by git; copy `application-local.properties.example`), which `application.properties` reads through `spring.config.import`. On a server, set `NOTTA_DB_PASSWORD` instead. Never put a real password back into `application.properties`.
- Backend: `cd backend && mvn spring-boot:run` (port 8080). Needs a local PostgreSQL database; connection settings are in `backend/src/main/resources/application.properties`. Hibernate creates the tables (`ddl-auto=update`); the seed SQL file is loaded by hand.
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

When pair-programming interactively instead of automating: ask before writing code, go one file and one concept at a time, and let the owner type the changes.

## Conventions

- **Backend layering:** controller -> service -> repository. Services throw `ResourceNotFoundException` (becomes 404) or `ItemLockedException` (becomes 409); controllers do not catch. `GlobalExceptionHandler` builds every error response.
- **Validation:** `@Valid` on create endpoints, with constraint annotations on the entity fields.
- **Transactions:** services are `@Transactional(readOnly = true)` at class level; methods that write add `@Transactional`.
- **Status values:** use the enums' `getValue()` and `matches()`. Do not type status strings by hand. Entity status fields are still `String` columns, so the database and JSON hold lowercase text (`draft`, `limbo`, `pending`, `fired`, `completed`).
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
- **A table has one open order.** `OrderService.createOrder` marks the table occupied in the same transaction and returns the existing open order if the table already has one. The frontend does not set table status.
- **One edit window per order.** Sending, removing, or changing an item during the window restarts it for every item on that order still counting down (`OrderItemService.restartEditWindow`), because the screen shows a single countdown. Paying ends it.
- **The order screen is rebuilt on every visit.** `App.js` gives `CreateOrder` a new `key` each time the view is opened, so no state carries over from the previous table. Do not add state to `CreateOrder` that is meant to survive leaving the screen.
- **Item price includes upcharges.** `OrderItem.price` is the price of one item with its modifier upcharges already added, so every screen that sums `price` is correct without knowing about modifiers. For items with modifiers or a note the backend works the price out itself (`OrderItemModifierService`); the request carries no price.
- **Modifiers are saved with the item, not after it.** `POST /api/order-items/with-modifiers` saves the item, its modifiers, and its note in one transaction and checks the choices against the menu's rules (`MenuModifierService` supplies the rules to both the picker and the check). Do not go back to one request per modifier: a half-saved item would reach the kitchen without its modifiers.
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
- The seed file inserts rows with plain SQL and only names some columns. A new column that the app filters on needs a database default (see `is_quick_order` on `RestaurantTable`), or a freshly seeded database behaves differently from the owner's. A missing default once left the Floor Map empty.
- Click through a full service (seat, send, pay, reopen, clear) on a freshly seeded database before calling a flow done. Feature 1 was verified from the Create Order button and never touched the Floor Map, so the empty map went unnoticed.

## Current state (November 2026)

Merged: CI, and a cleanup pass covering eight audit findings (central API module, typed exceptions and validation, transactions, status enums, shared totals, `GET /api/orders/active`, `CreateOrder.js` split into five files with behavior tests).

Merged: feature 1, modifiers and kitchen notes (see the feature plan below for what it covers).

Built: a working-state pass over the core flow (seat, send, change, pay, clear). It fixed the empty Floor Map on a fresh database, checks that charged for unsaved items, tax missing from closed checks, the lost countdown when a table is reopened, tables that could not be freed, and the order screen keeping the previous table's order.

Known gaps left open on purpose:

- The Financials report loads once per period and does not refresh on its own; pick the period again to see checks closed since.
- Entity status fields are `String`, not enum-typed.
- `OrderItemService.updateOrderItem` resets the edit timer only when status is `pending`, but pending items are locked and rejected earlier in the method, so the reset never runs. It probably should check `limbo`. A test asserts the current behavior.
- `GET /api/menu-items` returns active items whether or not `?active=true` is passed.
- `subtotal`, `tax`, and `total` on orders are written when the order is paid. Orders closed before that hold zeros; the backend works their amounts out from their items wherever it reports on closed checks (`TableOverviewService`, `FinancialsReportService`). Nothing calls `PUT /api/orders/{id}/totals` or `PUT /api/orders/{id}/close`.
- The tax rate lives in two places: `notta.tax-rate` (backend, the one that is charged) and `TAX_RATE` in `utils/totals.js` (the order screen's running total). They must be changed together.
- Timestamps are `LocalDateTime` with no time zone, and the countdown and "5m ago" labels compare them with the device's own clock. A tablet whose clock is off, or a backend in another time zone, would show wrong times and a wrong countdown.
- One payment closes the whole order. There are no split checks, partial payments, refunds, or voids of sent items.
- Every Quick Order creates a row in `tables` that is never removed.
- The plain `POST /api/order-items` endpoint does not check modifier rules, so an item with required groups could be saved bare through it. The order screen never does this (anything with modifiers or a note goes through `/with-modifiers`).
- The Tables and Quick Orders screens show the 50 most recently closed checks, not all of them. There is no way to page back further or search for an older check.
- The order screen does not refresh while it is open. Item statuses changed elsewhere (kitchen starts an item, another server adds one) show up only when the table is reopened. The Payment modal is not affected: it reads the check from the backend.
- Parallel draft saves give items IDs that do not follow ring-in order, and Active Orders sorts by ID, so a ticket can list items in a different order than the server entered them.
- The nav bar shows a hardcoded notification count; the search box does nothing.
- Sessions are kept in the backend's memory. Restarting the backend signs everyone out, and the app could not run as several backend copies without moving sessions to the database.
- Only the endpoints marked `@RequiresRole` check who is asking. Ordering, sending, paying, and reading are open to any request that can reach the backend.
- A session does not end by itself while the screen is in use, and there is no sign-out after a period of no activity. On a shared terminal the next person acts as the last one until someone presses SIGN OUT.
- The first manager's starting PIN (1234) is the same for every new database. It should be changed on first sign-in; nothing forces that yet.

## Feature plan

Build in this order, one pull request each.

### 1. Modifiers and kitchen notes (built)

- Tapping an item with modifier groups opens `views/CreateOrder/ModifierPicker.js`: required groups must be answered, `maxSelections` is respected, optional groups can be skipped. Items without groups are still added with one tap.
- Every item can carry a free-text kitchen note. The pencil on an order line reopens the picker to change modifiers or the note while the item is a draft or in the edit window.
- `GET /api/menu-items/modifier-groups` returns every item's groups with their choices in one response; the order screen loads it together with the menu.
- `POST /api/order-items/with-modifiers` and `PUT /api/order-items/{id}/modifiers` save and replace an item's modifiers and note. `GET /api/order-item-modifiers/order/{orderId}` returns an order's modifiers with names.
- `ActiveOrderItemView` carries `modifiers`, and the Active Orders screen prints modifiers and notes under each item in both views.

### 2. 86 board (built)

- `MenuItem.is86d` (column `is_86d`, default false) means out for now. It is separate from `isActive`: an inactive item is off the menu and hidden, an 86'd item stays on the menu.
- `PUT /api/menu-items/{id}/86` and `/un-86` change it. `GET /api/menu-items/86` lists what is out.
- `views/EightySixBoard/` is the board: what is 86'd now at the top, the whole menu by category below, tap to 86 or put back on. It refreshes every 3 seconds.
- On the order screen `MenuPanel.js` shows an 86'd item greyed out, marked 86'D, and untappable. `CreateOrder.js` reloads the menu every 5 seconds to pick up changes.
- The backend refuses to ring in an 86'd item (`ItemUnavailableException`, 409), in both `OrderItemService.createOrderItem` and `OrderItemModifierService.createOrderItemWithModifiers`. Items already sent are not affected when their menu item is 86'd afterwards.
- Not built: who may 86 (needs staff roles), a count of how many are left, and 86'ing a single modifier.

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
