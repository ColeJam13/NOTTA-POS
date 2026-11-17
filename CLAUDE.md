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
- **Avoid N+1 queries:** when a screen needs several entities together, add one endpoint that returns a DTO built from batched `WHERE ... IN` queries (see `ActiveOrderService`). `JOIN FETCH` does not apply because entities are not linked by JPA relationships.
- **Prefer a data-model change over a React workaround.** Example: Quick Orders were kept off the floor map with an `is_quick_order` column, not a frontend filter.
- **Frontend requests** go through `apiFetch` / `getJson` / `sendJson` in `services/api.js`. Never hardcode the backend address.
- **Money math** goes through `utils/totals.js`.
- **Frontend structure:** `views/` for screens, `components/` for shared pieces, one CSS file per component or view. Pieces private to one screen live in that screen's folder (see `views/CreateOrder/`).
- **Separation of concerns:** payment actions stay separate from order actions.
- JSON quirk: `xPosition` / `yPosition` on tables serialize as `xposition` / `yposition`.

## Things learned the hard way

- The edit-window countdown is set to 16 seconds on the backend so the screen can show a full 15. Do not "fix" the extra second.
- Save order items in parallel with `Promise.all` and wait for all of them before sending. Sequential saves caused items to miss the send.
- Match newly saved items to on-screen items by position, not by menu item. Matching by menu item gave two of the same dish one shared ID.
- A slow Floor Map load once turned out to be a Firefox-on-localhost issue, not the code. Measure backend response times before changing code for performance.
- On PostgreSQL 15+, the application's database user needs an explicit `CREATE` grant on the `public` schema on a new machine.
- Testcontainers must be 1.21.4 or newer to work with current Docker engines.

## Current state (November 2026)

Merged: CI, and a cleanup pass covering eight audit findings (central API module, typed exceptions and validation, transactions, status enums, shared totals, `GET /api/orders/active`, `CreateOrder.js` split into five files with behavior tests).

Known gaps left open on purpose:

- Tables, Quick Orders, and Financials screens still download several lists and match them up in the browser. Only Active Orders uses a combined endpoint.
- Entity status fields are `String`, not enum-typed.
- `OrderItemService.updateOrderItem` resets the edit timer only when status is `pending`, but pending items are locked and rejected earlier in the method, so the reset never runs. It probably should check `limbo`. A test asserts the current behavior.
- `GET /api/menu-items` returns active items whether or not `?active=true` is passed.
- `subtotal`, `tax`, and `total` on orders are never written (nothing calls `PUT /api/orders/{id}/totals`). Totals are computed on the frontend only.
- Order totals ignore modifier upcharges (modifiers are not wired into ordering yet).
- `backend/target/`, `backend/restaurant.db`, and `.DS_Store` are committed. The README still describes SQLite.
- The nav bar shows a hardcoded user and a hardcoded notification count; the search box does nothing.

## Feature plan

Build in this order, one pull request each.

### 1. Modifiers and kitchen notes

- Backend already has: `ModifierGroup`, `Modifier`, `MenuItemModifierGroup` (which groups apply to which item), `OrderItemModifier`, full CRUD endpoints, seed data, and a `specialInstructions` column on `OrderItem`.
- Missing: the order screen never loads or sends any of it.
- Build: tapping an item with modifier groups opens a picker (required groups must be answered, `maxSelections` respected, optional groups skippable); a free-text note per item; upcharges included in the item price and totals; modifiers and notes shown on the kitchen ticket (Active Orders, BOH view). Add modifiers and notes to `ActiveOrderItemView`. Consider one endpoint that returns a menu item's groups with their modifiers, to avoid a request per group.

### 2. 86 board

- Backend has `isActive` on `MenuItem` (a soft delete). The data model doc calls for a separate `is_86d` flag: 86'd means temporarily out, not removed from the menu.
- Build: `is86d` column, 86 / un-86 endpoints, a board screen for kitchen and managers, and 86'd items shown greyed out and untappable on the order screen (visible, so servers know it is out rather than wondering where it went). The order screen should pick up changes within a few seconds.

### 3. Editable floor map

- Backend has `xPosition`, `yPosition`, `rotation`, `shape` on tables. `TableService.updateTable` only saves `status`.
- Build: a layout endpoint that saves position and rotation, and an edit mode on the Floor Map with drag and drop and an explicit Save. Outside edit mode a tap still opens the table's order.

### 4. Staff profiles and roles

- Nothing exists. `serverName` is free text on orders and tables.
- Roles: server, bartender, manager, host, kitchen.
- **Two decisions are still open - ask the owner before building:**
  1. Sign-in: a 4-digit PIN per person, or tap your name from a list?
  2. Permissions: do managers see everything while other roles are limited to their own screens?

### Later

Challenges and leaderboard on real data (needs staff profiles), host view with waitlist, switchable themes, working notifications.
