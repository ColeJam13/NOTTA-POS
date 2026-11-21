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
    entity/      13 JPA entities, linked by plain ID columns (not @ManyToOne)
    dto/         response shapes that combine several entities
    enums/       status values (OrderItemStatus, OrderStatus, TableStatus, PaymentStatus) and StaffRole
    exception/   one exception per kind of refusal, and GlobalExceptionHandler, which turns each into a status code
    security/    StaffSessionStore (who is signed in; DatabaseStaffSessionStore keeps it in the database), StaffSession,
                 SessionTokens, StaffTokenFilter (reads the token on each request), SignInGuard (counts wrong PINs),
                 Permissions (the role matrix), RefusalResponder (writes the 401 for a request with no sign-in)
    config/      SecurityConfig (Spring Security set-up), WebConfig (CORS), FirstManagerSetup, TimeConfig (the clock)
    scheduled/   TimerScheduledTask - locks items when the edit window expires
                 SessionSweepTask - removes sessions nobody has used for 12 hours
  src/main/resources/db/migration/   Flyway migrations: V1__baseline.sql, V2__..., one numbered SQL file per schema change
  peach-blossom-seed-data.sql   menu, modifiers, and tables for the demo restaurant
frontend/   React 19 (Create React App), lucide-react icons
  src/services/api.js     the only place the backend address lives
  src/utils/              formatters.js, totals.js (tax rate + order math)
  src/components/         shared pieces (NavBar, PaymentModal, PinPad)
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
7. **Tests come with the code.** Each service method and endpoint gets tests in the existing style (Mockito for services, `@ControllerTest` for controllers, Testcontainers for repositories). For frontend refactors, write behavior tests against the old code first.
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
- **Sign-in and permissions run on Spring Security** (`config/SecurityConfig`). Three pieces: `StaffSessionStore` keeps who is signed in (in the database); `StaffTokenFilter` reads `X-Staff-Token` on every request and tells Spring Security who is asking and their role (authentication); rules say who may do what (authorization). Stateless, no cookie, no login page, no HTTP Basic, CSRF off because the token is a header and not a cookie. PINs are still checked by `StaffService`. Check the Spring Security documentation for the version in use before changing `SecurityConfig`; do not write its configuration from memory.
- **Default deny: every request needs a signed-in person.** `SecurityConfig` refuses anything else in the filter chain, before a controller is reached, whatever the address. The one exception is `POST /api/staff/sign-in`. A new endpoint is therefore closed to outsiders without anyone remembering to close it. Do not add another `permitAll` without the owner's say.
- **The role matrix is `security/Permissions`, and it is the source.** Four rules: `TAKE_ORDERS` (server, bartender, host, manager), `MAKE_ORDERS` and `CHANGE_86` (kitchen, bartender, manager), `MANAGER_ONLY`. A controller method names one: `@PreAuthorize(Permissions.TAKE_ORDERS)`. A method with no rule is open to anyone who is signed in, which is right for reading the menu, tables, and orders and for nothing that changes data. Reading payments is manager-only. Data-changing endpoints the matrix does not name and no screen uses are `MANAGER_ONLY` until a feature needs one.
- **Every endpoint is in `RoleMatrixIntegrationTest`**, with a real request and the roles allowed. It calls each one as nobody, as every role that is not allowed, and as every role that is. A new endpoint must be added to its table, and a POST, PUT, or DELETE endpoint must carry `@PreAuthorize`: two tests there fail until both are done. One that changes something and is meant for every role says so with `Permissions.ANYONE_SIGNED_IN` (choosing your own PIN), so a missing rule still means someone forgot.
- **Two places answer a refusal, with the same `ApiError` body.** Nobody signed in: `RefusalResponder` writes 401 "Sign in to do that." from the filter chain. Signed in with the wrong role: the rule on the method throws and `GlobalExceptionHandler.handleAccessDenied` answers 403 naming the role. Do not remove that handler: without it a refused request falls into the catch-all and comes out as a 500. The wording is defined once, in `RefusalResponder`.
- **Permissions are decided twice, and the backend is the source.** `@PreAuthorize` on the controller method decides what the backend allows; `utils/roles.js` repeats the matrix so a screen does not offer what the backend would refuse. Hiding a button is for a tidy screen, not for security. When a rule changes, change `Permissions` and `RoleMatrixIntegrationTest` first, then `utils/roles.js` to match.
- **Controller tests use `@ControllerTest(TheController.class)`, not `@WebMvcTest`.** It is `@WebMvcTest` plus the security set-up. To act as someone, autowire `SignIn` and add `.with(signIn.asManager())` (or `asServer()`, `asKitchen()`, `as(7L, "Sarah", StaffRole.SERVER)`) to the request; leave it off to be nobody, which is now refused everywhere but sign-in. Integration tests get the same `signIn` from `BaseIntegrationTest`, which also empties `SignInGuard` before each test (they share one running backend, and one test's wrong PINs must not pause the next). Both are in `src/test/java/com/notapos/testsupport`. A plain `@WebMvcTest` does not load `SecurityConfig`, so Spring Boot's fallback refuses every request and the test fails with 401s and 403s that are not this app's rules.
- **Sessions are rows in `staff_sessions`, found by a hash of the token.** `StaffSessionStore` is an interface; `DatabaseStaffSessionStore` is the real one. The device holds the token; the database holds only its SHA-256 hash (`SessionTokens`), so a copy of the database signs nobody in. Never store or log a token. A session row carries a copy of the person's name, role, and "must change PIN" flag, so a request is identified by reading one row: any code that changes one of those on a staff member must update or end that person's sessions in the same transaction (`StaffService.updateStaff` and `changeOwnPin` do). "Last used" is written at most every five minutes (`TOUCH_INTERVAL`), not on every request.
- **A session ends on SIGN OUT, when a manager changes that person's PIN or role or deactivates them, or after 12 hours unused.** Nothing else ends one: not a backend restart, and not a sent order or a payment (the owner's decision D3, 2026-11-20: a shared terminal signs a person out only when SIGN OUT is pressed). Do not add an automatic sign-out without asking.
- **Every PIN check goes through `SignInGuard`.** `begin(device)` before the PIN is looked at, then exactly one of `endRight`, `endWrong`, or `endWithoutAnswer`. "Device" is the address the request came from (`request.getRemoteAddr()`, passed down by the controller). Its rules: five wrong in a row on a device, or ten wrong within ten minutes whatever came between, pause that device; each pause is longer than the last (30 seconds, 1 minute, 5 minutes, 15 minutes, then an hour); 40 wrong within ten minutes from all devices together pause sign-in everywhere for a minute. A pause stops sign-in, not people already signed in. Two places check a PIN today: `StaffService.signIn` and `changeOwnPin`. A new one that skipped the guard would be a place to guess PINs without limit.
- **The starter PIN must be changed, and the backend enforces it.** A new database's first manager (PIN 1234) is marked `must_change_pin`. `SecurityConfig` lets that sign-in do three things (who-am-I, `PUT /api/staff/me/pin`, sign-out) and answers everything else 403 with `code: "PIN_CHANGE_REQUIRED"`, in the filter chain, so a new endpoint is covered without anyone remembering. `App.js` shows `views/ChangePin` in place of the app while `staff.mustChangePin` is true. 1234 is refused as anybody's new PIN. At every start, `StaffService.requireChangeOfStarterPin` marks and signs out anyone still holding 1234 (a migration cannot: only hashes are stored).
- **A rule that depends on time passing asks a `Clock`, not `Instant.now()`.** `TimeConfig` provides the clock; `SignInGuard` and `DatabaseStaffSessionStore` take it in their constructors. Their tests hand in `MutableClock` (in `testsupport`) and move it by hand, so "a one-hour pause" is tested in no time and no test sleeps.
- **A stand-in used by tests is held to the real thing's tests.** Controller and service tests start no database, so they use `InMemoryStaffSessionStore`. `StaffSessionStoreContract` is one set of tests, written against the interface, that runs against both it and the database store. Change the contract when the store's behavior changes, and both must pass.
- **New tables store moments as `timestamptz` (`Instant` in Java).** `staff_sessions` does. The older tables use `timestamp` with `LocalDateTime`, which is the known clock gap below; do not add to it.
- **Never return the `Staff` entity from a controller.** It holds the PIN hash. Use `StaffView`.
- **Reading who is signed in:** on the frontend `const { staff } = useStaff();` (null in a test that draws one screen on its own, which `utils/roles.js` treats as unrestricted). On the backend a controller method takes `@AuthenticationPrincipal StaffSession currentStaff`. It is never null there: a request with no sign-in does not reach a controller.
- **Money math** goes through `utils/totals.js`.
- **Reports are added up on the backend.** `GET /api/reports/financials?from=...&to=...` returns the Financials screen's numbers for the checks closed in a period, using `BigDecimal`. The screen only picks the period and draws the answer. `from` and `to` are local date-times with no zone (`2026-11-18T00:00:00`), the same clock orders are stamped with.
- **The backend works out the check.** `OrderCheckService` adds up an order's sent items, tax, and total; `GET /api/orders/{id}/check` returns it and the Payment modal shows it. `PaymentService.createPayment` charges that number and refuses (409) anything that does not match it. `utils/totals.js` is only for the running total on the order screen and for orders closed before totals were recorded. Draft items are not on the check.
- **An order is only closed by a payment or cleared when empty.** `PaymentService.createPayment` refuses an order that is missing, already closed, empty, or holding unsent items, then records subtotal, tax, and total on the order. `OrderService.deleteOrder` clears an empty open order and frees its table; it refuses anything else. Refusals throw `OrderStateException` (409) with a message written for the person at the screen, and the frontend shows that message as-is.
- **A table has one open order, and the database enforces it.** `OrderService.createOrder` locks the table row, marks the table occupied in the same transaction, and returns the existing open order if the table already has one. Behind that, the index `orders_one_open_per_table` (unique on `table_id` where `status = 'open'`, migration V3) makes PostgreSQL refuse a second one. The frontend does not set table status.
- **A create endpoint that staff can reach builds a new row.** `OrderService.createOrder` builds a new `Order` from the table and order type in the request, and the controller stamps the signed-in person on it (a name or staff ID in the request is ignored). `PaymentService.createPayment` builds a new `Payment`. `TableService.createTable` clears the ID. Never save a request's own entity as it arrived: one that carries an ID means "update that row", which is how a request once reopened a paid check, and how one could replace an earlier payment.
- **`POST /api/tables` has two rules.** A Quick Order is a row in `tables`, so anyone who takes orders may create one (`isQuickOrder` true). A table on the floor is manager-only. The controller checks the second after reading the request, because `@PreAuthorize` only sees the role.
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

- Count an attempt before checking it, not after. The wrong-PIN limit first counted a failure once the PIN had been checked; a hundred guesses sent in the same instant would all have been checked before the first was counted. `SignInGuard.begin` holds a place for each attempt, and only as many may be under way as the device has guesses left.
- Spring looks for `@Transactional` on the type a test method is written in. Tests written as default methods of an interface (`StaffSessionStoreContract`) ran outside the roll-back transaction of the class that implemented it, left their rows behind, and failed on "no transaction". The annotation is repeated on the interface.
- A click-through script that starts from "the first manager signs in with 1234" must now choose a PIN first. On a fresh database, sign in, `PUT /api/staff/me/pin`, and carry on with the new PIN.

## Current state (November 2026)

Everything through pull request #283 is merged, and `main` passes CI (698 backend tests, 154 frontend tests). Built: CI and the first cleanup pass, modifiers and kitchen notes, the working-state pass over the core flow, the 86 board, the editable floor map, combined endpoints for Tables, Quick Orders, and Financials, the kitchen display, Spring Boot 3.5, the start script, and staff profiles with PIN sign-in and roles.

**The work order from here is `Documentation/phase-2-plan.md`** (revision 2, which followed a check of #275 to #279). Read it before starting anything; its "Order of work" table says what is next. In short:

- Part A fixes defects and open doors in what is built, in order: A1 one server-side ring-in path (done), A2 two terminals on one check (done), A3 lock the API (done: Spring Security in A3a, default deny and the role matrix in A3b), A4 sign-in hardening and sessions in the database (done), A5 one tax rate, ticket order, and the clock (next).
- Part P is platform work: Vite in place of Create React App, a packaged build and a home test host, phone-sized screens, an installable app, and later store apps with Square.
- Part B is features: notifications, host view with waitlist, challenges on real data, UI templates (Plain, Coffee Shop, Nightclub, 8-Bit), check handling.
- It lists decisions that are the owner's (D4 to D9 are open). Ask each one when it is needed. Do not guess and build.

Some items in the list below are scheduled in that plan (the two tax rates, ticket order, the clock). They stay listed here until the pull request that fixes each one removes it.

Known gaps left open on purpose:

- A table opened at the very instant its previous check is being paid can be handed that check. The first item rung onto it is refused with `ORDER_NOT_OPEN`, and the screen recovers the usual way (it says so, and Send starts a new check). Nothing is lost or mischarged.
- The task that locks items when the edit window runs out (`TimerScheduledTask`) does not take the order lock. Removing an item in the same few milliseconds its window expires is not guarded.

- The Financials report loads once per period and does not refresh on its own; pick the period again to see checks closed since.
- Entity status fields are `String`, not enum-typed.
- `GET /api/menu-items` returns active items whether or not `?active=true` is passed.
- `subtotal`, `tax`, and `total` on orders are written when the order is paid. Orders closed before that hold zeros; the backend works their amounts out from their items wherever it reports on closed checks (`TableOverviewService`, `FinancialsReportService`).
- The tax rate lives in two places: `notta.tax-rate` (backend, the one that is charged) and `TAX_RATE` in `utils/totals.js` (the order screen's running total). They must be changed together.
- Timestamps are `LocalDateTime` with no time zone, and the countdown and "5m ago" labels compare them with the device's own clock. A tablet whose clock is off, or a backend in another time zone, would show wrong times and a wrong countdown.
- One payment closes the whole order. There are no split checks, partial payments, refunds, or voids of sent items.
- Every Quick Order creates a row in `tables` that is never removed.
- The Tables and Quick Orders screens show the 50 most recently closed checks, not all of them. There is no way to page back further or search for an older check.
- The order screen does not refresh while it is open. Item statuses changed elsewhere (kitchen starts an item, another server adds one) show up only when the table is reopened. The Payment modal is not affected: it reads the check from the backend.
- Parallel draft saves give items IDs that do not follow ring-in order, and Active Orders sorts by ID, so a ticket can list items in a different order than the server entered them.
- The nav bar shows a hardcoded notification count; the search box does nothing.
- Wrong-PIN counts are kept in the backend's memory (`SignInGuard`). Restarting the backend clears every count and pause, and several backend copies would each keep their own. Sessions themselves are in the database.
- "A device" for the wrong-PIN count is the address a request comes from. Terminals that share one address (behind the same router as seen from the backend, or behind a proxy) share one count. When a proxy is put in front of the backend (P2), it must pass on the real address (`server.forward-headers-strategy`) or every terminal becomes one device.
- A 4-digit PIN with no name is weak however it is guarded. A guesser on the restaurant's network who changes address for every few tries is held only by the restaurant-wide rule, about 40 tries every eleven minutes. The same rule lets anyone on that network keep new sign-ins paused by sending wrong PINs (people already signed in are not affected). Closing both needs terminals the backend recognises, not a longer pause.
- Only the starter PIN is forced to change. A PIN a manager sets for someone on the Staff screen is known to that manager, and nothing asks the person to choose their own. `must_change_pin` and `PUT /api/staff/me/pin` are there to build that on; no screen offers "change my PIN" outside the forced case.
- A signed-in person whose role is not allowed, sending a request that is also badly formed, is answered 400 (the validation message), not 403: Spring validates the request before the rule on the controller method runs. Nothing is let through. A request with no sign-in is always 401.
- The Tables and Quick Orders screens show each closed check's tip and payment method to anyone signed in (`GET /api/tables/overview`). Only the `/api/payments` endpoints are manager-only. Whether servers should see tips on closed checks is the owner's call.
- `POST` and `DELETE /api/order-item-modifiers` change an item's modifiers with none of the ring-in checks and without changing its price. No screen uses them; they are manager-only and are candidates for removal.
- The create endpoints only a manager can reach (menu items, modifiers, modifier groups, prep stations, their links, table status logs) still save the request's own entity, so a POST that carries an ID updates that row. A manager can change those rows anyway.
- Starting and completing an item do not check the item's current status, or that its check is still open.
- A session does not end while the screen is in use, and there is no sign-out after a period of no activity or after a sent order. On a shared terminal the next person acts as the last one until someone presses SIGN OUT. This is the owner's decision (D3: "only on sign-out is the right call for now"), not an oversight.

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
- `Staff` (name, role, BCrypt PIN hash, active) and `StaffService`. A PIN belongs to one active person, and nobody may hold 1234. Wrong PINs are counted per device and pause sign-in there, longer each time (`SignInGuard`; the rules are under Conventions). The last active manager cannot be deactivated or demoted. Bringing back a deactivated person needs a new PIN.
- `POST /api/staff/sign-in` returns the person and a token. The frontend sends the token in the `X-Staff-Token` header (`services/api.js`). `StaffSessionStore` keeps the sessions in the database (table `staff_sessions`, a hash of each token), so a backend restart signs nobody out. `StaffTokenFilter` identifies each request, and `@PreAuthorize` rules on controller methods are enforced by Spring Security: 401 with no sign-in, 403 with the wrong role.
- Every request needs a sign-in except signing in itself. What each role may do is the matrix in `security/Permissions` (see Conventions): anyone signed in reads the menu, tables, and orders; servers, bartenders, hosts, and managers take orders and payment; the kitchen, bartenders, and managers start and complete items and change the 86 board; managers alone change the menu, staff, and floor layout and read Financials and payments.
- `utils/roles.js` is the one list of which role is offered which screen and button (it follows the backend's matrix). Hosts are offered the Floor Map, Tables, Quick Orders, and the order screen; the kitchen the kitchen display and the 86 board. `App.js` shows the PIN pad (`views/SignIn`) until someone signs in, and provides who that is through `context/StaffContext.js` (`useStaff()`).
- `views/Staff` (managers) adds people, changes names and roles, sets new PINs, and deactivates.
- A new order is always stamped with the signed-in person: `serverName` and `staffId` (`OrderController.createOrder`). The request cannot name someone else.
- When the staff table is empty, start-up creates a manager with PIN 1234 (`FirstManagerSetup`) and logs a warning. That manager must choose a new PIN before doing anything else: `views/ChangePin` asks for the current PIN, a new one, and the new one again (`PUT /api/staff/me/pin`).
- `components/PinPad` is the pad both PIN screens use (dots, message line, keys, keyboard). It only collects four digits; the screen decides what they are for.
- Not built: sign-out after a period of no activity or after each order (decided against for now, D3), a "change my PIN" button for everyone, a record of who did what besides opening an order, and per-person sales.

### Kitchen display (built, not in the original plan)

- The BOH view of Active Orders (`views/ActiveOrders/ActiveOrders.js`) is the kitchen display. The FOH view is unchanged.
- Station buttons (ALL, plus one per prep station from `GET /api/prep-stations`) show a station only its own items. An item whose menu item has no station is shown to every station.
- Each ticket shows how long its oldest unfinished item has been waiting (yellow at 10 minutes, red at 15; the constants are at the top of the file). Tickets are listed longest wait first.
- A ticket with nothing left to make drops off the view. SHOW DONE brings finished tickets back.
- `ActiveOrderItemView` carries `prepStationId` and `sentAt` for this.
- Not built: sound or flashing on a new ticket, a "bump" button for a whole ticket, per-station default (each tablet remembering its station), and routing tickets to printers.

### Later

Challenges and leaderboard on real data (needs staff profiles), host view with waitlist, switchable themes, working notifications.
