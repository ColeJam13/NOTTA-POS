#!/usr/bin/env bash
# ============================================================================
# NOTTA-POS start script
# ============================================================================
# WHAT: Starts everything the app needs with one command:
#         1. the PostgreSQL database (if it is not already running)
#         2. the backend  (Spring Boot, port 8080)
#         3. the frontend (React, served by Vite, port 3000)
#       then opens the app in your browser. Press Ctrl+C to stop it all.
#
# WHY:  Starting the app used to take three terminals and three commands.
#
# USAGE (from the project folder):
#         ./start.sh             start everything and open the browser
#         ./start.sh --no-open   start everything, do not open the browser
#
# The backend and frontend write their output to logs/backend.log and
# logs/frontend.log (the folder is ignored by git). If something will not
# start, the reason is at the bottom of one of those files.
# ============================================================================

# -u: treat a misspelled variable name as an error instead of an empty value
set -u

# The folder this script lives in, so it works no matter where it is run from
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Ports and addresses. They can be changed for one run by setting them first,
# for example:  BACKEND_PORT=8081 FRONTEND_PORT=3001 ./start.sh
BACKEND_PORT="${BACKEND_PORT:-8080}"
FRONTEND_PORT="${FRONTEND_PORT:-3000}"
DB_HOST="${NOTTA_DB_HOST:-localhost}"
DB_PORT="${NOTTA_DB_PORT:-5432}"

BACKEND_URL="http://localhost:${BACKEND_PORT}"
FRONTEND_URL="http://localhost:${FRONTEND_PORT}"
LOG_DIR="${ROOT}/logs"

OPEN_BROWSER=true
if [ "${1:-}" = "--no-open" ]; then
    OPEN_BROWSER=false
fi

say()  { printf '%s\n' "$*"; }
fail() { printf '\nCould not start: %s\n' "$*" >&2; exit 1; }

# True if something is already listening on the given port
port_in_use() { (exec 3<>"/dev/tcp/127.0.0.1/$1") 2>/dev/null; }

# ----------------------------------------------------------------------------
# 1. Things that must be installed
# ----------------------------------------------------------------------------
for tool in java mvn node npm curl; do
    command -v "$tool" >/dev/null 2>&1 || fail "'$tool' is not installed (see \"Running it\" in README.md)."
done

# Vite, which serves the frontend, needs Node.js 20.19 or newer (or 22.12 or newer).
# An older Node fails with an error that does not say so, so it is checked here.
# "node -p" prints the result of a line of JavaScript: 1 if this Node is new enough.
NODE_OK="$(node -p 'const [major, minor] = process.versions.node.split(".").map(Number); (major === 20 && minor >= 19) || (major === 22 && minor >= 12) || major >= 23 ? 1 : 0')"
if [ "$NODE_OK" != "1" ]; then
    fail "Node.js $(node --version) is too old for the frontend. Install Node.js 22 or 24 (20.19 is the oldest that works)."
fi

# ----------------------------------------------------------------------------
# 2. The database password
# ----------------------------------------------------------------------------
# The password is not kept in git. It comes from backend/application-local.properties
# (a file only on this computer), or from the NOTTA_DB_PASSWORD setting if that is used instead.
if [ ! -f "${ROOT}/backend/application-local.properties" ] && [ -z "${NOTTA_DB_PASSWORD:-}" ] && [ -z "${SPRING_DATASOURCE_PASSWORD:-}" ]; then
    fail "backend/application-local.properties is missing.
  Copy backend/application-local.properties.example to backend/application-local.properties
  and put your database password in it."
fi

# ----------------------------------------------------------------------------
# 3. Nothing else is already using the ports
# ----------------------------------------------------------------------------
if port_in_use "$BACKEND_PORT"; then
    fail "something is already running on port ${BACKEND_PORT}. Is the backend already started in another terminal?"
fi
if port_in_use "$FRONTEND_PORT"; then
    fail "something is already running on port ${FRONTEND_PORT}. Is the frontend already started in another terminal?"
fi

# ----------------------------------------------------------------------------
# 4. Database
# ----------------------------------------------------------------------------
# pg_isready answers "is a PostgreSQL server accepting connections here?"
database_ready() {
    if command -v pg_isready >/dev/null 2>&1; then
        pg_isready -q -h "$DB_HOST" -p "$DB_PORT"
    else
        port_in_use "$DB_PORT"
    fi
}

if database_ready; then
    say "Database: already running."
else
    say "Database: not running. Starting PostgreSQL (this asks for your computer password)..."
    sudo service postgresql start || fail "PostgreSQL did not start. Try 'sudo service postgresql start' by hand to see why."

    # Give it up to 15 seconds to accept connections
    for _ in $(seq 1 15); do
        database_ready && break
        sleep 1
    done
    database_ready || fail "PostgreSQL was started but is not accepting connections on ${DB_HOST}:${DB_PORT}."
    say "Database: started."
fi

# ----------------------------------------------------------------------------
# 5. Frontend packages (first run only)
# ----------------------------------------------------------------------------
# package-lock.json is the list of packages the frontend needs. npm keeps a copy of the
# list it last installed in node_modules/.package-lock.json. If the list in the project
# is newer than that copy (a pull brought in a change to the packages), install again.
# Without this, the first start after such a pull fails with "command not found".
# ("-nt" means "is newer than".)
if [ ! -d "${ROOT}/frontend/node_modules" ]; then
    say "Frontend: installing packages (first run only, takes a minute or two)..."
    (cd "${ROOT}/frontend" && npm install) || fail "npm install failed."
    touch "${ROOT}/frontend/node_modules/.package-lock.json"
elif [ "${ROOT}/frontend/package-lock.json" -nt "${ROOT}/frontend/node_modules/.package-lock.json" ]; then
    say "Frontend: the list of packages changed, installing..."
    (cd "${ROOT}/frontend" && npm install) || fail "npm install failed."
    touch "${ROOT}/frontend/node_modules/.package-lock.json"      # So the next start sees them as up to date
fi

# ----------------------------------------------------------------------------
# 6. Stop everything when this script stops
# ----------------------------------------------------------------------------
# "trap" runs a command when the script is interrupted (Ctrl+C) or exits.
# "kill 0" sends the stop signal to every process this script started, so the
# backend and frontend never keep running in the background by accident.
cleanup() {
    trap - INT TERM EXIT            # Do not run this twice
    say ""
    say "Stopping NOTTA-POS..."
    kill 0 2>/dev/null
}
trap cleanup INT TERM EXIT

# ----------------------------------------------------------------------------
# 7. Backend and frontend
# ----------------------------------------------------------------------------
mkdir -p "$LOG_DIR"

say "Backend: starting on port ${BACKEND_PORT}..."
(cd "${ROOT}/backend" && SERVER_PORT="$BACKEND_PORT" mvn -q spring-boot:run) > "${LOG_DIR}/backend.log" 2>&1 &
BACKEND_PID=$!

say "Frontend: starting on port ${FRONTEND_PORT}..."
# PORT is read by frontend/vite.config.js. Vite does not open a browser tab by itself
# (open: false there); this script opens one below.
# VITE_API_URL is only set when the backend is not on its usual port.
if [ "$BACKEND_PORT" = "8080" ]; then
    (cd "${ROOT}/frontend" && PORT="$FRONTEND_PORT" npm start) > "${LOG_DIR}/frontend.log" 2>&1 &
else
    (cd "${ROOT}/frontend" && PORT="$FRONTEND_PORT" VITE_API_URL="$BACKEND_URL" npm start) > "${LOG_DIR}/frontend.log" 2>&1 &
fi
FRONTEND_PID=$!

# Wait until a web address answers, or give up after two minutes.
# If the process behind it has already died, stop waiting and show why.
wait_for() {
    local name="$1" url="$2" pid="$3" log="$4"
    for _ in $(seq 1 120); do
        if curl -s -o /dev/null --max-time 2 "$url"; then
            return 0
        fi
        if ! kill -0 "$pid" 2>/dev/null; then
            say ""
            say "---- last lines of ${log} ----"
            tail -n 25 "$log"
            fail "the ${name} stopped while starting. The reason is above (full output in ${log})."
        fi
        sleep 1
    done
    fail "the ${name} did not answer within two minutes. Look at ${log}."
}

wait_for "backend" "${BACKEND_URL}/api/tables" "$BACKEND_PID" "${LOG_DIR}/backend.log"
say "Backend: ready."
wait_for "frontend" "$FRONTEND_URL" "$FRONTEND_PID" "${LOG_DIR}/frontend.log"
say "Frontend: ready."

# ----------------------------------------------------------------------------
# 8. A brand new database has no menu yet
# ----------------------------------------------------------------------------
if [ "$(curl -s --max-time 5 "${BACKEND_URL}/api/menu-items")" = "[]" ]; then
    say ""
    say "The menu is empty. To load the demo restaurant (menu, modifiers, tables), run this in another terminal:"
    say "  psql -h ${DB_HOST} -p ${DB_PORT} -U nottauser -d notta_pos -f backend/peach-blossom-seed-data.sql"
fi

# ----------------------------------------------------------------------------
# 9. Open the app
# ----------------------------------------------------------------------------
say ""
say "NOTTA-POS is running: ${FRONTEND_URL}"
say "Press Ctrl+C here to stop it."

if [ "$OPEN_BROWSER" = true ]; then
    if command -v explorer.exe >/dev/null 2>&1; then
        explorer.exe "$FRONTEND_URL" >/dev/null 2>&1 || true      # WSL: opens the Windows browser (explorer.exe reports "failure" even when it works)
    elif command -v xdg-open >/dev/null 2>&1; then
        xdg-open "$FRONTEND_URL" >/dev/null 2>&1 || true          # Linux
    elif command -v open >/dev/null 2>&1; then
        open "$FRONTEND_URL" >/dev/null 2>&1 || true              # macOS
    fi
fi

# Stay here until Ctrl+C, or until the backend or frontend stops by itself
wait -n "$BACKEND_PID" "$FRONTEND_PID"
say ""
say "The backend or frontend stopped. The reason is at the bottom of logs/backend.log or logs/frontend.log."
