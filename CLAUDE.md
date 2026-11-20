# NOTTA-POS

A restaurant point-of-sale system built by someone with 20 years on the floor. The goal is to ease the pain points of the people who actually use a POS: servers, bartenders, hosts, and kitchen staff. Signature feature: after a server presses Send, items sit in a 15-second edit window ("limbo") before they lock and reach the kitchen.

This file is read by Claude Code at the start of every session. It carries the context and working rules for the project.

## Layout

```
backend/    Spring Boot 3.2 (Java 21, Maven), PostgreSQL
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
- **Avoid N+1 queries:** when a screen needs several entities together, add one endpoint that returns a DTO built from batched `WHERE ... IN` queries (see `ActiveOrderService` and `TableOverviewService`). `JOIN FETCH` does not apply because entities are not linked by JPA relationships.
- **Prefer a data-model change over a React workaround.** Example: Quick Orders were kept off the floor map with an `is_quick_order` column, not a frontend filter.
- **Frontend requests** go through `apiFetch` / `getJson` / `sendJson` in `services/api.js`. Never hardcode the backend address.
- **Money math** goes through `utils/totals.js`.
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

- The Financials screen still downloads every order, payment, order item, and table and adds them up in the browser. Active Orders, Tables, and Quick Orders each use one combined endpoint.
- Entity status fields are `String`, not enum-typed.
- `OrderItemService.updateOrderItem` resets the edit timer only when status is `pending`, but pending items are locked and rejected earlier in the method, so the reset never runs. It probably should check `limbo`. A test asserts the current behavior.
- `GET /api/menu-items` returns active items whether or not `?active=true` is passed.
- `subtotal`, `tax`, and `total` on orders are written when the order is paid. Orders closed before that hold zeros; `closedOrderTotals` in `utils/totals.js` falls back to the items for those. Nothing calls `PUT /api/orders/{id}/totals` or `PUT /api/orders/{id}/close`.
- The tax rate lives in two places: `notta.tax-rate` (backend, the one that is charged) and `TAX_RATE` in `utils/totals.js` (the order screen's running total). They must be changed together.
- Timestamps are `LocalDateTime` with no time zone, and the countdown and "5m ago" labels compare them with the device's own clock. A tablet whose clock is off, or a backend in another time zone, would show wrong times and a wrong countdown.
- One payment closes the whole order. There are no split checks, partial payments, refunds, or voids of sent items.
- Every Quick Order creates a row in `tables` that is never removed.
- The plain `POST /api/order-items` endpoint does not check modifier rules, so an item with required groups could be saved bare through it. The order screen never does this (anything with modifiers or a note goes through `/with-modifiers`).
- The Tables and Quick Orders screens show the 50 most recently closed checks, not all of them. There is no way to page back further or search for an older check.
- The order screen does not refresh while it is open. Item statuses changed elsewhere (kitchen starts an item, another server adds one) show up only when the table is reopened. The Payment modal is not affected: it reads the check from the backend.
- Parallel draft saves give items IDs that do not follow ring-in order, and Active Orders sorts by ID, so a ticket can list items in a different order than the server entered them.
- The nav bar shows a hardcoded user and a hardcoded notification count; the search box does nothing.

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

### 4. Staff profiles and roles

- Nothing exists. `serverName` is free text on orders and tables.
- Roles: server, bartender, manager, host, kitchen.
- **Two decisions are still open - ask the owner before building:**
  1. Sign-in: a 4-digit PIN per person, or tap your name from a list?
  2. Permissions: do managers see everything while other roles are limited to their own screens?

### Later

Challenges and leaderboard on real data (needs staff profiles), host view with waitlist, switchable themes, working notifications.
