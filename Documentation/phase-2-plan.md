# NOTTA-POS: Phase 2 plan

Revision 2, 2026-11-20. Revision 1 was written after an independent review of pull requests #263 to #274. This revision follows a check of #275 to #279 and adds four things the owner asked for: a home test host, an app on staff phones, bringing the build and sign-in onto standard tools, and switchable UI templates.

Revision 3 notes, 2026-11-21 (a second note follows this one): a check of #281 to #286 found the work sound (`main` at `83ad70e`, CI green). The owner answered four questions, recorded as D4, D9, D10, and D11 below. They add one small step, A6, which is next, and they change the feature order. Later the same day a check of the project's versions found Spring Boot 3.5 and Node.js 20 past the end of their support; the owner asked for the project to be kept current provided nothing breaks, which is the new Part U.

This is the work order. Read `CLAUDE.md` first; its working rules and process rules apply to everything here.

## What changed in this revision

- A1 and A2 are done and moved to "Completed" at the end, with their text unchanged.
- **A3 now moves sign-in onto Spring Security** instead of extending the home-built interceptor, and is split into two pull requests.
- A4 gains one item: sessions stored in the database.
- New **Part P (platform)**: Vite, a single thing to start, a home test host, phone-sized screens, and an installable app. This replaces the old B6, which was notes only.
- **B4 is rewritten** as a template system with four named templates.
- The order of work is set out in one table, below.

## Where things stand

- `main` is at `d2ecb33` (the merge of #279).
- Done since revision 1: process rules in `CLAUDE.md` (#276), one server-side ring-in path (#277), Flyway migrations (#278), and row locks, an order version, and one open order per table enforced by the database (#279).
- Checked in this revision: migrations `V1` to `V3` exist and `ddl-auto` is `validate`; repository and integration tests build their schema from the real migrations, not from Hibernate; `Order` has `@Version`; the lock queries are in `OrderRepository` and `TableRepository`.
- Worth the owner knowing: `V3` changes existing data once. If a table had more than one open order, it moves their items and payments onto the oldest and deletes the extras. That is the right fix, and it is one more reason to take a database backup before the first start on a new build.
- Removed in #284: `backend/src/test/resources/application-test.properties`, which still described SQLite. Nothing read it (no test switches on a "test" profile).

## Order of work

One pull request at a time, in this order. Steps 3 to 9 need nothing from the owner except the decisions marked.

| Step | Item | Needs |
|---|---|---|
| 1 | A1. One ring-in path | Done |
| 2 | A2. Two terminals, one check (with Flyway) | Done |
| 3 | A3a. Move sign-in onto Spring Security, same rules as today | Done (#281) |
| 4 | A3b. Default deny, the role matrix, remove unused endpoints | Done (#282) |
| 5 | A4. Sign-in hardening and sessions in the database | Done (#283) |
| 6 | A5. Small consistency fixes | Done (#284) |
| 7 | P1. Replace Create React App with Vite | Done (#285) |
| 8 | P2. One thing to start, and the home test host | Done (#286) |
| 8a | **A6. Two follow-ups from the review: tips on closed checks, and releasing a paused terminal** | Next. D10, D11 (decided) |
| 8b | U1. Spring Boot 3.5 to 4.1 (the 3.5 line no longer gets security fixes) | |
| 9 | B4a. Design tokens: every screen reads its look from named values | |
| 10 | B4b and B4c. Template system, then the new templates | D9 (default decided) |
| 11 | P3. Phone-sized screens | |
| 12 | P4. HTTPS on a private network, and the installable app | D8 |
| 13 | B5, then B1, B2, B3 (features, in that order) | D4 (decided), D5, D6 |
| Any gap | U2. Node 24 and the frontend tools held back by Node 20. Fits between any two steps, once the owner has upgraded Node | Owner upgrades Node |
| Later | P5. Store apps and card payments through Square | D7 |
| Later | U4. Standards backlog | D12 |

Why this order: security first, because the test host and phones put the backend on a network other devices can reach. The build tool moves next, while the frontend is not mid-feature. Tokens come before phone layouts because both touch every stylesheet, and doing tokens first means the phone work is written once, against the named values.

## Part A: fix first (remaining)

### A3. Lock the API, on Spring Security

90 endpoints exist; 7 check who is asking. Role-limited screens are cosmetic until this is done. Open today, with no sign-in at all:

- Create, change, deactivate, and delete menu items, including prices.
- `PUT /api/payments/{id}` and `DELETE /api/payments/{id}`.
- `PUT /api/orders/{id}/close` (closes a check with no payment) and `PUT /api/orders/{id}/totals` (takes the tax rate from the caller). Nothing in the frontend calls either.
- Every read, including all payments and tips.

**Why Spring Security.** It is the standard way to do sign-in and permissions in Spring, the access rules are being rewritten in this step anyway, and it replaces code this project would otherwise maintain by hand. The owner's day job uses the same ideas under a different name, so explain the pieces in the pull request as they are introduced: the filter chain (every request passes through a fixed line of checks before it reaches a controller), authentication (who are you) versus authorization (what may you do), and why CSRF protection is switched off here (the token travels in a header the browser never attaches on its own; it is not a cookie).

Check the current Spring Security documentation for the version Spring Boot 3.5 brings before writing any of this. Do not write its configuration from memory.

**A3a: move onto Spring Security with no change in behavior.** Its own pull request.

**Done 2026-11-20 (#281).** Spring Security 6.5.11. `StaffSessionStore` holds the sessions, `StaffTokenFilter` identifies each request, the seven rules are `@PreAuthorize` on the same controller methods, and `StaffSessionInterceptor`, `@RequiresRole`, and `NotAllowedException` are deleted. Controller tests use `@ControllerTest` and `SignIn`. `SignInRulesIntegrationTest` was written against the old check first and then held against the new one. Two things to know:

- **One answer changed.** A request that is both not allowed and badly formed (a bad body, a missing parameter) is now answered 400 where it was 401 or 403, because Spring validates a request before a method-level rule runs. Nothing is let through. A3b's "every request needs a session" check runs earlier and puts the 401 back in front for anyone not signed in; a signed-in person with the wrong role and a bad request will keep getting 400.
- **Responses carry Spring Security's standard headers** (no caching, no framing, no content-type guessing). The frontend does not depend on any of them.

The text below is kept as the record of what was asked for.

- Add the Spring Security starter. No login page, no HTTP Basic, no server-side cookie session.
- Move the session map out of `StaffSessionInterceptor` into its own class (a session store), so both the old and new code paths can read it during the move and A4 can later swap what is behind it.
- A request filter reads `X-Staff-Token`, looks the session up, and tells Spring Security who is asking and what role they hold.
- The seven restricted endpoints keep exactly their current rules, expressed the Spring Security way (method-level rules next to the endpoint are preferred, since that is where `@RequiresRole` sits today). Everything else stays open in this step.
- 401 and 403 answers keep the `ApiError` shape and the messages the frontend already shows.
- CORS keeps reading `notta.cors.allowed-origins`.
- `StaffSessionInterceptor` and `@RequiresRole` are deleted once nothing uses them.
- Expect most of the work to be in tests: controller tests load the security setup, so they need a shared, documented way to "sign in as a manager / server / kitchen". Build that helper first.

Done when: every existing test passes with unchanged meaning, the seven rules behave as before in the browser, and `CLAUDE.md` describes the new pattern in place of the old one.

**A3b: default deny, the role matrix, and removals.** Its own pull request.

**Done 2026-11-20 (#282).** Every request needs a signed-in person except `POST /api/staff/sign-in`; the matrix below is `security/Permissions`, applied with `@PreAuthorize`; the four unused endpoints are deleted. `RoleMatrixIntegrationTest` lists every endpoint and calls each one as nobody and as each role. What was decided along the way, beyond the text below:

- **Endpoints the matrix does not name.** Changing a table's status directly, table status logs, adding or removing a single modifier on an order item, and running the edit-window sweep by hand: no screen uses them, so they are manager-only until a feature needs one. Clearing an empty check, removing an unsent item, and changing an item's modifiers count as taking orders.
- **`POST /api/tables` does two jobs.** A Quick Order is a row in `tables`, so anyone who takes orders may create one; a table on the floor is manager-only.
- **Two more "create" endpoints could overwrite a row** when the request carried an ID: tables (a server could rewrite a floor table) and payments (a new payment could replace an earlier one). Both now always create. Each had a failing test first.
- **Hosts on screen.** The matrix lets a host open a check, ring in, send, and take payment, so `utils/roles.js` now offers hosts Quick Orders and the order screen. START and COMPLETE on the kitchen display are only drawn for the kitchen, the bar, and managers.
- **Signing out, or asking who-am-I, with no valid session** is now answered 401 like any other request from nobody (it was 204 and its own 401 message).
- **Left for the owner:** the Tables and Quick Orders screens still show each closed check's tip and payment method to anyone signed in. Only the `/api/payments` endpoints are manager-only.

The text below is kept as the record of what was asked for.

- Every `/api/**` request needs a valid session except `POST /api/staff/sign-in`.
- A role rule on every endpoint that changes data, following the matrix. **D2 was decided on 2026-11-20: confirmed as written.**

| Action | Roles |
|---|---|
| Read menu, tables, orders, active orders | any signed-in staff |
| Open orders, ring in, send, take payment | server, bartender, host, manager |
| Start and complete items | kitchen, bartender, manager |
| 86 and un-86 | kitchen, bartender, manager (as now) |
| Menu items, modifiers, prep stations: create, change, remove | manager |
| Staff, Financials, floor layout | manager (as now) |
| Read payments and tips | manager |

- Delete the endpoints nothing uses: order close, order totals, payment update, payment delete. Refunds and voids come back properly in B5.
- Keep `utils/roles.js` and the backend rules in step, and say in `CLAUDE.md` which one is the source.
- `OrderController.createOrder` requires a session and always stamps the staff member.

Done when: a test walks every mapped endpoint without a token and gets 401 for all but sign-in, role tests cover each row of the matrix, and each role is clicked through in the browser.

### A4. Sign-in hardening

**Done 2026-11-21 (#283).** Wrong PINs are counted per device by `security/SignInGuard`; sessions are rows in `staff_sessions` (migration V4) holding a hash of each token; the first manager must choose a new PIN before doing anything else (migration V5), and 1234 is refused as a new PIN; `SessionSweepTask` removes sessions unused for 12 hours. What was decided along the way, beyond the text below:

- **D3, the owner's decision: a shared terminal signs a person out only on SIGN OUT**, as it does today ("only on sign-out is the right call for now"). Nothing was built for it. A session still ends when it has not been used for 12 hours.
- **The numbers are Claude's, and are constants at the top of `SignInGuard`.** Five wrong in a row on a device pauses it; so does ten wrong within ten minutes whatever was typed in between (a right PIN restarts the row, so honest mistakes on a shared terminal never add up; the ten-in-ten rule stops someone with their own PIN guessing four at a time between proper sign-ins). Pauses on a device run 30 seconds, 1 minute, 5 minutes, 15 minutes, then an hour, and reset after a clean day. The restaurant-wide ceiling is 40 wrong within ten minutes, for a one-minute pause.
- **An existing database is covered.** A manager who still signs in with 1234 is found when the backend starts, marked, and signed out; their next sign-in leads to choosing a new PIN. The owner's own database is in this position unless that PIN was changed already.
- **Choosing a new PIN asks for the current one again**, and a wrong one counts against the same guard as a wrong PIN at sign-in.
- **A corrected name is written into that person's open sessions.** Sessions can now last for days, and the orders a person opens carry the name in their session.
- **What this does not solve** is in `CLAUDE.md` under the known gaps: the counts are in memory; a "device" is an address, which a proxy or a shared router blurs (see P2); and a 4-digit PIN with no name stays guessable by someone on the network who keeps changing address.

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
- **Sessions in the database** (a Flyway migration and a database-backed session store behind the class introduced in A3a). Today a backend restart signs the whole floor out, which is tolerable on fixed terminals and not on staff phones mid-shift. Store a hash of the token, not the token itself, so a copy of the database cannot be used to act as someone.

### A5. Small consistency fixes (one pull request)

**Done 2026-11-21 (#284).** The order screen asks `GET /api/settings` for the tax rate and holds none of its own; every order item carries `ring_in_position` (migration V6) and every list of an order's items is sorted by it; every answer carries the backend's time in `X-Server-Time`, and the screens measure backend times against that (`services/serverClock.js`), not the device's clock. What was decided along the way, beyond the text below:

- **Position, not a ring-in timestamp.** A timestamp set by the backend has the same defect as an ID (it records arrival), and one set by the screen would depend on the tablet's clock, which is the third item on this list. The screen sends each item's place on the order.
- **The screen hands the positions out**, as one after the highest a saved item holds. The backend cannot: the saves of one round arrive together, in any order, and only the screen knows what was tapped first. A request with no position is placed after everything on the order. Existing items all hold position 0 and tie on ID, so old orders list as they did.
- **The backend's time goes on every answer as a header, not into each response body.** One mechanism then covers the countdown, the "5m ago" labels, the kitchen display's waiting time, and the Financials "today", and a new screen gets it without anyone remembering. The header is in the same form as the timestamps (local time, no zone), so a tablet in another time zone is covered as well as one with a wrong clock.
- **With no tax rate the screen shows `--` for tax and total**, never a number from a guessed rate. Taking orders does not depend on the rate arriving.
- **Not changed:** the timestamps themselves still have no time zone (`CLAUDE.md`, known gaps). That is a data-model change across every table and belongs with P2, when the backend first runs on a machine that is not the developer's.
- **Found on the way:** `OrderItemRepository.findByOrderId` had no `ORDER BY`, so the order screen's list of a reopened check had no defined order at all. It is sorted now.

- **One tax rate.** The order screen should get the rate from the backend rather than hold its own `TAX_RATE`. The two can disagree today, and the guest would see one total while being charged another.
- **Ticket order.** Items should print in the order the server rang them in. Parallel saves hand out IDs out of sequence and Active Orders sorts by ID. Add an explicit position or ring-in timestamp and sort on that.
- **Clock.** The countdown compares backend timestamps with the tablet's clock. Send the time remaining (or the server's current time) with the response so a tablet with a wrong clock still counts down correctly.

### A6. Two follow-ups from the review (one pull request)

Both come from the owner's answers on 2026-11-21. They are small and go before B4a.

**Tips on closed checks: own checks only (D10).**

- Today `GET /api/tables/overview` gives each closed check's tip and payment method to anyone signed in, and the Tables and Quick Orders screens show them.
- The rule: a person sees the tip and payment method on checks they opened. A manager sees them on every check. Everyone still sees that a check was closed and its total.
- Decide it on the backend: leave the two fields out of the answer for a check the person may not see them on. Do not send them and hide them on the screen.
- "Opened" means the order's `staffId`. A closed check with no `staffId` (closed before staff profiles existed) shows its tip to managers only.
- `components/TableChecks` draws both screens, so one change covers both. It should show nothing in that spot, not a blank label or a zero.
- Add the endpoint's new behavior to `RoleMatrixIntegrationTest` or an integration test beside it: a server reads the overview and gets their own tip and nobody else's.

**A paused terminal: a manager can release it, and no pause is longer than 5 minutes (D11).**

- Today a device that keeps getting PINs wrong is paused for 30 seconds, then 1 minute, 5 minutes, 15 minutes, and an hour, and nothing can end a pause early. On a Friday night a terminal dead for an hour is worse than the risk it guards against.
- Cap the pauses at 5 minutes: 30 seconds, 1 minute, then 5 minutes each time after. The restaurant-wide rule stays as it is.
- A manager can see which devices are paused (the address, and how long is left) and release one. Releasing ends the pause and clears that device's wrong-PIN count. It does not sign anyone in.
- The manager does this from a device that is not paused, or from one where they are already signed in: a pause stops new sign-ins, not people signed in. Put it on the Staff screen.
- Manager-only on the backend, in the role matrix test like every other endpoint. Log each release with who did it.
- Update the `SignInGuard` rules where `CLAUDE.md` lists them.

## Part P: platform

These make the app something that can be installed in one place and used from devices other than the developer's.

### P1. Replace Create React App with Vite

**Done 2026-11-21 (#285).** The frontend is served and built by Vite 8 and tested with Vitest 4; `react-scripts` is gone (1,315 installed packages became 258). `VITE_API_URL` replaces `REACT_APP_API_URL`. Lint is its own command and CI step. No screen changed. What was decided along the way, beyond the text below:

- **Files that contain JSX were renamed `.jsx`** (31 files: screens, components, and the tests that draw them). Vite 8 reads `.js` as plain JavaScript, and the setting that used to override that per file was removed with the move from esbuild to Oxc. The contents are unchanged and git records them as renames.
- **Vitest, not Jest alongside.** All 175 tests moved with one mechanical change (`jest.` to `vi.`, 139 places, three functions). One test was corrected: it matched text in a form that was about to close, and passed under Jest on timing alone.
- **Vitest 4 and jsdom 29, one major version behind the newest**, because the newest need Node.js 22 and this machine has Node 20, installed system-wide. Node 20 is past its end of support; upgrading it is an owner action (see the list below the decisions table). `./start.sh` now says so plainly if Node is too old.
- **ESLint 10 with the core rules and the hooks rules.** The React and accessibility plugins Create React App bundled do not support ESLint 10, and ESLint 9 is past its end of support, so those two rule sets are not enforced for now (`CLAUDE.md`, known gaps).
- **The build still writes to `build/`**, not Vite's default `dist/`, so nothing that refers to it had to change. P2 can rename it if the packaged build wants to.
- **Two Create React App behaviors were kept on purpose, in `vite.config.js`:** the dev server answers on every network address (a tablet can open it), and tests ignore `.env.local`.
- **`./start.sh` reinstalls packages when `package-lock.json` is newer than what is installed.** Without it, the first start after pulling this change fails, because the old `node_modules` has no Vite in it.

Create React App is retired and no longer maintained. Vite is the standard replacement: it does the same two jobs (a development server and a production build) and is much faster at both.

- Swap `react-scripts` for Vite. `index.html` moves to the frontend's root folder, as Vite expects.
- Environment settings change name: `REACT_APP_API_URL` becomes `VITE_API_URL` and is read through `import.meta.env`. Update `services/api.js`, `start.sh`, the README, `CLAUDE.md`, and CI together.
- Tests: there are 11 test files with 86 `jest.` calls. Move them to Vitest (Vite's test runner; its API mirrors Jest's, with `vi.` in place of `jest.`) unless that proves worse than keeping Jest alongside. Every existing test must still pass and still test the same thing.
- Create React App supplied the lint rules that make CI fail on warnings. Add an ESLint setup so that still happens.
- Keep the dev server on port 3000 so nothing else has to change.
- No screen changes in this pull request.

Done when: `npm run build`, the tests, and CI pass on the new tool, `./start.sh` still brings everything up, and a full service is clicked through.

### P2. One thing to start, and the home test host

**Done 2026-11-21 (#286), with the Windows steps still to be confirmed on the real machine.** `./package.sh` makes `release/notta-pos/notta-pos.jar`: the backend with the built frontend inside, serving both from one address. `deploy/windows` has the start and backup scripts. `Documentation/test-host.md` is the step-by-step, and ends with a checklist for the owner. What was decided along the way, beyond the text below:

- **The frontend's files are readable without a sign-in, as an exact list** (`SecurityConfig.FRONTEND_FILES`). The sign-in screen is one of those files, so this could not be avoided; a list, and GET only, keeps "everything else is closed" true for whatever is added later. `CLAUDE.md` said not to add an open address without the owner's say: this step of the plan is taken as that say, and it is called out in the pull request for the owner to confirm.
- **A built app with no address set calls the address it was loaded from.** That is the whole of "the CORS setting and the API address setting are only needed in development". Development is unchanged.
- **The package is built on the development machine and copied over.** The test host needs Java and PostgreSQL only, not Node.js or Maven. The package script does not run the tests unless asked (`--with-tests`): package from a `main` that CI has passed.
- **A third CI job makes the package and starts it** against an empty database (`deploy/smoke-test.sh`). The other tests run the code; this one runs the file that gets copied.
- **Built assets are cached by the device for a year, `index.html` never**, so phones do not download the app on every visit and still get an update at once.
- **The app runs in a window on the test host, started by a double-click.** Running it as a Windows service that starts with the machine was left out: it needs a third-party wrapper or a scheduled task, and a test host is better served by a window whose log can be read.
- **What Claude could and could not check.** The jar: built, started from an empty folder, and clicked through on Linux at the machine's network address (not `localhost`), which is what a phone does. The PowerShell scripts: parsed by Windows PowerShell 5.1 and run as far as their first check ("Java is not installed"), which is as far as they go on a Windows without Java. Not checked: everything in the guide from step 1 onward on a real Windows 10 machine.

The owner wants to run the app on a Windows 10 machine at home (it also runs a media server) and reach it from phones and tablets on the home Wi-Fi.

Build:

- **A packaged build.** One command produces one runnable file: the backend with the built frontend inside it, served by Spring Boot. In that build the frontend calls the API on the same address it was loaded from, so the CORS setting and the API address setting are only needed in development. The two-server development setup keeps working as it does now.
- **A Windows start script** (PowerShell), and `Documentation/test-host.md`, a step-by-step for a machine with nothing installed: Java 21, PostgreSQL, creating the database and user, `application-local.properties`, starting the app (Flyway builds the tables on first start), loading the seed file, allowing the port through Windows Firewall on private networks only, finding the machine's address, and opening it from a phone.
- **A backup command** in the same document, and the instruction to run it before every update.

Rules for the test host, to state in the document:

- Home network only. No port forwarding on the router. The machine runs an operating system that no longer gets security updates, so it must not be reachable from the internet.
- Change the first manager's PIN before any other device connects. (Since A4 the app forces this at the first sign-in.)
- A3 and A4 must be merged before the app is put on the test host.
- If anything is put in front of the backend that passes requests on (a reverse proxy), it must hand over the real address of each device, and the backend must be told to read it (`server.forward-headers-strategy`). Wrong PINs are counted per address (A4); behind a proxy that does not do this, every phone and tablet looks like one device. The packaged build in this step has no proxy, so this is a note for whoever adds one.

Claude Code works in Linux and cannot run the Windows script or reach the machine. Say plainly in the pull request that the Windows steps are unverified, and give the owner a short checklist to run on the real machine.

### P3. Phone-sized screens

The screens are laid out for a tablet. Staff will use their own phones.

- Sign-in, tables, the order screen, the modifier picker, payment, and Active Orders get a narrow layout. The Floor Map editor, Financials, and Staff can stay tablet-and-up; say so on screen.
- Large tap targets, primary actions within thumb reach, nothing that depends on hovering.
- Built on the tokens from B4a, with no new hardcoded sizes or colors.
- Verify at a phone-sized window in the browser, and give the owner a checklist for a real phone on the test host.

### P4. HTTPS on a private network, and the installable app

An installable web app (a PWA) is the existing site with an icon on the phone's home screen that opens full-screen like an app. Phones only allow installing from an HTTPS address, and HTTPS on a private in-restaurant address needs a certificate plan.

- **Investigate first and bring the owner options. Decision D8.** Compare the realistic ways to serve a private-network app over HTTPS on staff-owned phones, with cost, setup effort per restaurant, and what breaks when the internet is down. Do not build until the owner picks.
- Then: the app manifest (name, icons, colors from the active template), a service worker that caches the app's own files so it opens instantly, and an "install this app" prompt with instructions for iPhone and Android.
- A QR code on a manager screen that carries the server's address, so joining a phone is "scan, then enter your PIN".
- Not in this step: taking orders while offline. Show a clear "lost connection to the restaurant server" state and recover cleanly when it returns.
- Two things to settle in the investigation, both found in the review of #283: whatever serves HTTPS must not make every phone look like one device to `SignInGuard` (a proxy in front of the backend does exactly that unless the real address is passed on), and the sign-in token is kept in the browser tab's `sessionStorage`, which an installed app loses each time it is closed. Whether a phone asks for the PIN on every open is the owner's call; ask it with D8.

### P5. Store apps and card payments through Square (later; do not build yet)

- **Proposed direction, decision D7:** when a store listing is wanted, wrap the same web app with Capacitor (a tool that packages a web app inside a native iPhone or Android shell) instead of rewriting the screens natively.
- **Card payments go through Square's own software.** The app must never see or store card numbers; that keeps this project out of card-security compliance scope. Two routes existed as of November 2026, to be re-checked when this is built: Square's Point of Sale API, which a web app can use by handing an amount to the Square app on the same phone, and Square's Mobile Payments SDK, which is smoother but only works inside a native app. A wrapped app can use the second.
- The payment record will need the processor's transaction reference, and `PaymentService.createPayment` a "card approved by the processor" path.
- Open questions for the owner then: whether each restaurant uses its own Square account, how tips are entered on a phone handed to a guest, and what a server does when their phone dies mid-shift.

### P6. Pushed updates in place of polling (later)

Screens ask the backend for changes every few seconds. That is fine at this size. Before real service volume, the kitchen display and the order screen should be sent changes as they happen (server-sent events or WebSockets). Not scheduled; listed so new screens do not make polling harder to replace. Keep each screen's data loading in one place.

## Part U: keeping current

Added 2026-11-21. The owner wants the application as current and as close to industry standard as it can be, on one condition: an upgrade must not break what works. Every step here is judged by that condition first.

**The rule for every upgrade in this part.** The existing tests must pass with their meaning unchanged. A test is changed only when the thing it tests was renamed or moved by the upgrade, never to make a failure go away. If an upgrade cannot be finished cleanly in one pull request, stop, leave `main` as it is, and report what is in the way. A half-finished upgrade is worse than an old version.

### U1. Spring Boot 3.5 to 4.1

Why: version 3.5.16 (June 2026) was the last release of the 3.5 line, and its open-source support ended on June 30, 2026. Security fixes now land only in 4.x. Go to 4.1, not 4.0: 4.0's support ends on December 31, 2026, and 4.1's runs to July 31, 2027.

This is a migration, not a version bump. Spring Boot 4 sits on Spring Framework 7 and Spring Security 7 and changes several things underneath this app. **Read the official Spring Boot 4.0 and 4.1 migration guides and release notes before changing anything, and follow them over this list.** The list below is what to look for, written from general knowledge on the date above; check every item against the guides.

Before the upgrade, on 3.5 (first commits of the pull request, or a small pull request of their own):

- Build with deprecation warnings shown and clear every one. What 3.x marks as deprecated, 4.0 removes, so this is the cheapest way to find most of the work.
- **Pin down the JSON the screens depend on.** Add tests that record the exact JSON of the main answers from a seeded database: the menu, tables overview, active orders, a check, the sign-in answer, the settings, and an `ApiError`. A new major version of the JSON library could change a field name, a date format, or a number format and leave every other test green, and the screens would be the first to notice. These tests must pass unchanged after the upgrade. Known oddities to capture on purpose: `xposition` / `yposition`, `isQuickOrder`, `is86d`, and how dates and money are written.

Areas to check during the upgrade:

- **Security.** `SecurityConfig`, `StaffTokenFilter`, `RefusalResponder`, method rules (`@PreAuthorize`), and the test helper `@ControllerTest` / `SignIn`. `RoleMatrixIntegrationTest` is the proof: every endpoint, every role, before and after.
- **JSON.** Whatever the migration guide says about the JSON library's new major version: imports, configuration, records, dates.
- **Database layer.** The JPA provider's new major version: the entities, `@Version` on `Order`, the two locking finders, ID generation on the older `bigserial` tables, and `ddl-auto=validate` against the Flyway schema. `TwoTerminalsIntegrationTest` and `DatabaseMigrationTest` are the proof.
- **Flyway.** That migrations V1 onward are still recognised as already run on an existing database, with unchanged checksums. No migration file may be edited.
- **Tests.** Test annotations and helpers that moved or were removed, and whether Testcontainers needs a newer version.
- **The packaged build.** `./package.sh`, the "packaged" Maven profile, static file serving and its cache headers, and `deploy/smoke-test.sh`.
- **Start-up and settings.** Any renamed property in `application.properties`, and the scheduled tasks.

Protecting the owner's data:

- Before asking the owner to run the new build, start it against a copy of a database created by the current build with data in it, and confirm it starts, changes nothing, and reads everything.
- The pull request tells the owner to take a backup before the first start, and how.

Done when: all backend tests pass with unchanged meaning, the JSON tests pass unchanged, CI's three jobs are green, a full service is clicked through (sign in as each role, seat, ring in with modifiers, send, the edit window, kitchen start and complete, pay, the 86 board, Financials), and `CLAUDE.md` names the new versions and anything that now works differently.

### U2. Node 24 and the frontend tools

Waits on the owner upgrading Node.js on the development machine from 20 to 24. Recommended to the owner: install a version manager (nvm) and add Node 24 through it, leaving the system-wide Node 20 in place, so nothing else on the machine is affected.

One pull request, once Node 24 is there:

- CI moves from Node 22 to Node 24, so CI tests what the owner runs. Add an `.nvmrc` file naming the version, and say so in `CLAUDE.md` and the README.
- Bump the tools Node 20 was holding back: Vitest, jsdom, and `@testing-library/jest-dom`.
- Bump `@testing-library/user-event` from 13 to 14. Its API changed between those versions, so expect to touch the tests that use it; they must still test the same thing.
- Bump `lucide-react` (the icons) to its 1.x line, and look at every screen for a missing or changed icon.
- Remove the unused leftovers: the `web-vitals` package, `src/reportWebVitals.js`, and `src/logo.svg`.
- Fix the one `case` in `Financials.jsx` and switch `no-case-declarations` back on.
- Check whether `eslint-plugin-react` and `eslint-plugin-jsx-a11y` now support the ESLint version in use. If they do, bring them back; if not, leave the known gap in `CLAUDE.md` as it is.

Done when: lint, build, tests, and the packaged-build check pass on Node 24 locally and in CI, and the app is clicked through.

### U3. Staying current from here on

Upgrades should stop arriving as surprises.

- **A versions table in `CLAUDE.md`:** Spring Boot, Java, Node.js, PostgreSQL, and the main frontend tools, each with the version in use and the date its support ends. Update it in any pull request that changes a version.
- **A check at the start of every plan step:** read the table, and if something is within three months of its support ending, or past it, tell the owner before starting the step.
- **Owner action:** turn on GitHub's Dependabot alerts for the repository. It is a setting, free for a public repository, and it reports known security problems in the libraries the project uses. Alerts only, not automatic pull requests: those would break the one-pull-request-at-a-time rule.
- Do this as part of U1's pull request (the table) and note the check in the process rules.

### U4. Standards backlog (later, not scheduled)

Things that would bring the project closer to common practice but are not urgent. None is started without the owner's say.

- **TypeScript on the frontend, a file at a time.** It is the norm for new React work and catches a class of mistakes before the app runs. It is also the largest item here. **Decision D12.**
- **Request shapes of their own.** The create endpoints a manager can reach still take the database entity as the request body (a known gap in `CLAUDE.md`). Standard practice is a small request class per endpoint, as `OrderItemRequest` already is.
- **Enum-typed status fields** on the entities, in place of text columns read through enums.
- **Times with a zone** on the older tables (`timestamptz`), as the new tables already use.
- **Pushed updates** in place of polling (P6).
- **Java 25**, the newer long-term-support release. Java 21 is supported for years yet, so this is a nice-to-have. It needs the owner to install it on the development machine and the test host.
- **A newer PostgreSQL** than 16. Version 16 is supported until late 2028.

## Part B: features

### B1. Working notifications

The nav bar bell shows a hardcoded count. Build real ones: an 86 or un-86 reaches every order screen, "order up" reaches the server who opened the order when the kitchen completes its items, and kitchen and managers can write and send a message to the floor. Needs decision D5: who may send to whom, and whether a notification must be acknowledged. Polling is acceptable to start, consistent with the rest of the app.

### B2. Host view with waitlist

A host screen: floor with table statuses (use the existing `TableStatusLog` for how long a table has been seated), a waitlist (name, party size, quoted wait, time added, seated or left), seat-from-waitlist onto a table, and quick orders. Statuses beyond available and occupied (reserved, needs bussing) belong here; add them to `TableStatus`.

### B3. Challenges and leaderboard on real data

`views/Challenges` is entirely mock data. Orders now carry `staffId`, so per-person sales are available. Needs decision D6: which measures count (sales, a featured item, check average, fewest edits inside the timer) and over what period. Build the per-person sales report first; the leaderboard is a view of it.

### B4. UI templates

The owner wants a list of default templates a manager can switch between: **Plain, Coffee Shop, Nightclub, and 8-Bit**. Confirmed by the owner on 2026-11-21: the look the app has today is the 8-Bit design, and 8-Bit is the default a new restaurant starts on. Three pull requests.

**B4a. Design tokens, with no visible change.**

- A token is a named value (for example `--color-surface`, `--font-heading`, `--radius-card`) that stylesheets use in place of a literal color, font, or size. Changing a template then means changing the values, not the stylesheets.
- Go through every CSS file and replace hardcoded colors, fonts, corner sizes, border widths, shadows, and spacing steps with tokens. The token list must cover fonts and shapes, not only colors: 8-Bit has a pixel font and hard square borders, and Nightclub will want glow on dark surfaces.
- Status meanings get their own tokens (item states, the kitchen display's wait warnings, 86'd, errors) so a template can restyle them without losing the meaning.
- Today's values become the 8-Bit template. Take screenshots of every screen before and after; they must match.

**B4b. The template system.**

- Each template is one file that sets the token values. The active template is applied at the top of the page.
- The choice is stored on the backend (a settings table, added by a Flyway migration) so every terminal and phone shows the same template. Any signed-in person can read it; only a manager can change it.
- A manager screen to pick a template, with a preview before it is applied.
- Fonts ship with the app. Nothing is loaded from the internet, so a template works when the connection is down.

**B4c. The new templates:** Plain, Coffee Shop, Nightclub, one pull request each.

- Propose each as a palette and font choice with screenshots of the order screen, the kitchen display, and the payment screen, and get the owner's approval before finishing it. **Decision D9:** which template is the default for a new restaurant, and any direction the owner wants to give for Coffee Shop and Nightclub.
- Every template must pass the same checks: body text and status colors meet the standard contrast ratio of 4.5 to 1, the kitchen display is readable from a distance, and a locked item, an 86'd item, and a late ticket are each unmistakable.

### B5. Check handling (recommended addition)

`CLAUDE.md` lists it as a known gap: one payment closes the whole order, with no split checks, partial payments, voids of sent items, or comps. These are everyday floor actions. A sensible first slice is void and comp with a manager PIN and a reason, each recorded with who approved it, followed by split by item and split evenly.

## Decisions needed from the owner

| | Decision | Needed before |
|---|---|---|
| D1 | Adopt Flyway migrations. **Decided 2026-11-20: yes. Done in #278.** | |
| D2 | Role matrix. **Decided 2026-11-20: confirmed as written**, including hosts opening orders, ringing in, sending, and taking payment. | A3b |
| D3 | How shared terminals sign people out. **Decided 2026-11-20: only on SIGN OUT, for now.** | A4 |
| D4 | Feature order. **Decided 2026-11-21: check handling first (voids, comps, splits), then notifications, host view, Challenges.** | Step 13 |
| D5 | Notifications: who sends to whom, acknowledged or not | B1 |
| D6 | Challenges: which measures and what period | B3 |
| D7 | Store apps: wrap the web app (proposed) or write native apps; the Square account model | P5 |
| D8 | How the app is served over HTTPS inside a restaurant (after the investigation in P4) | P4 |
| D9 | Default template: **decided 2026-11-21, 8-Bit (today's look).** Still open: any direction the owner wants to give for Coffee Shop and Nightclub; ask when proposing each | B4c |
| D10 | Who sees tips on closed checks. **Decided 2026-11-21: each person sees tips on checks they opened; managers see all.** | A6 |
| D11 | A terminal paused for wrong PINs. **Decided 2026-11-21: a manager can release it, and no pause is longer than 5 minutes.** | A6 |
| D12 | TypeScript on the frontend: adopt it gradually, or stay with JavaScript | U4 |

Ask these at the point they are needed. Do not guess and build.

Owner actions still open unless already done: upgrade Node.js on the development machine to 24 (see U2 for the safe way), turn on Dependabot alerts (U3), turn on branch protection for `main` (require both CI checks and an up-to-date branch), change the local database password, which is still readable in this public repository's history, and upgrade Node.js on the development machine from 20 to 22 or 24 (Node 20 is past its end of support; it needs the machine's password, so Claude cannot do it).

## How to continue

1. Start A6, then U1 (Spring Boot 4.1), then B4a. None needs a decision from the owner. U2 goes in whenever the owner reports Node 24 is installed. Part A (A1 to A5), P1, and P2 are finished. (P2's Windows steps wait on the owner's run through the checklist in `test-host.md`; fix what that turns up before B4a if it blocks using the test host.)
2. After each merge, mark the step done in the table above and in its section, the way A1 and A2 were.

## Completed

The sections below are kept as written, as the record of what was found and what was done.

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
