# NOTTA-POS

**NottaPOS**
- Designed to be a no-frills Point of Sale system that prioritizes the work-flow of the people on the floor and recognize
pain points not addressed in other systems.

**Phase 1 In Progress:**
- Full-stack application: Spring Boot backend, React frontend
- PostgreSQL database with tables, orders, items, modifiers, and payments
- Fully operational POS system that matches industry standards
- Server-Savior feature: delay system on each order to prevent multiple ticket revisions from making it to the kitchen

**Phase 2 Future Features:**
- Kitchen display system integration
- 86'd item tracking and communication
- Server challenges and performance leaderboards
- Real-time sales analytics

**Phase 3 Stretch Goals:**
- Localized hardware for data hosting to prevent any downtime
- Personal knowledge database written in plain langauge for troubleshooting without endless customer service phone calls
- Inventory tracking by analyzing items sold for both FoH and BoH stocking

## What works today

- **Floor Map:** tap a table to open or reopen its order.
- **Create Order:** ring in items, choose modifiers, add kitchen notes, save a draft, or send.
- **The edit window:** after Send, items wait 15 seconds before they lock and reach the kitchen. Adding, changing, or removing an item restarts the window; SEND NOW ends it.
- **Active Orders:** front-of-house and kitchen views of every open order, with modifiers and notes. The kitchen starts and completes items.
- **Close Order:** the check (subtotal, tax, total) is worked out by the backend; pay by cash, credit, debit, or gift card, with a tip.
- **Tables and Quick Orders:** open and closed checks.
- **Financials:** revenue, tips, and payment methods for today, this week, this month, or a custom range.

Challenges is a mock-up with sample data. See `CLAUDE.md` for the build plan and the known gaps.

## Running it

You need Java 21, Maven, Node.js, and PostgreSQL.

**1. Database (first time only).** Create a database and a user that match `backend/src/main/resources/application.properties`. On PostgreSQL 15 or newer the user also needs permission to create tables:

```sql
CREATE DATABASE notta_pos;
CREATE USER nottauser WITH PASSWORD 'the password from application.properties';
GRANT ALL PRIVILEGES ON DATABASE notta_pos TO nottauser;
\c notta_pos
GRANT CREATE ON SCHEMA public TO nottauser;
```

**2. Backend** (port 8080). The first start creates the tables.

```bash
cd backend && mvn spring-boot:run
```

**3. Demo data (first time only).** With the backend started once, load the demo restaurant's menu, modifiers, and tables:

```bash
psql -h localhost -U nottauser -d notta_pos -f backend/peach-blossom-seed-data.sql
```

**4. Frontend** (port 3000).

```bash
cd frontend && npm install && npm start
```

Open http://localhost:3000.

### Using it from another device

To open the app from a tablet on the same network, two settings need the address of the computer running it (for example `192.168.1.50`):

- `frontend/.env.local`: `REACT_APP_API_URL=http://192.168.1.50:8080` (where the frontend finds the backend)
- `backend/src/main/resources/application.properties`: add `http://192.168.1.50:3000` to `notta.cors.allowed-origins` (which addresses the backend answers)

## Tests

```bash
cd backend && mvn test
```

```bash
cd frontend && npm test -- --watchAll=false
```

The backend's repository tests start a PostgreSQL container, so Docker must be running. Both suites run on every pull request (`.github/workflows/ci.yml`).
