#!/usr/bin/env bash
# ============================================================================
# NOTTA-POS package smoke test
# ============================================================================
# WHAT: Starts the packaged jar (the one ./package.sh makes) the way the test
#       host starts it, as one program, and checks from the outside that it
#       does the four things a package must do:
#         1. hand the sign-in page to someone who is not signed in
#         2. hand over the script that page names, marked "keep for a year"
#         3. still refuse the API to someone who is not signed in
#         4. accept the first manager's PIN, and say it must be changed
#       Then it stops the jar.
#
# WHY:  A "smoke test" is the quick check that a thing switches on at all,
#       before any careful testing. The unit and integration tests run the
#       code; none of them runs the FILE that is copied to another machine.
#       A jar built without the screens in it, or one that cannot start
#       outside the project folder, passes every other test. CI runs this
#       after ./package.sh so that cannot go unnoticed.
#
# NEEDS: a PostgreSQL database that is EMPTY (check 4 expects a brand-new
#        database, whose first manager still has PIN 1234), and the settings
#        to reach it, given the way a server gives them, for example:
#
#   SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/notta_pos \
#   SPRING_DATASOURCE_USERNAME=nottauser NOTTA_DB_PASSWORD=... \
#   ./deploy/smoke-test.sh
#
# Do not point it at a database you care about: it starts the app against it.
#
# USAGE:  ./deploy/smoke-test.sh [path-to-jar] [port]
#         defaults: release/notta-pos/notta-pos.jar and port 8099
# ============================================================================

set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="${1:-${ROOT}/release/notta-pos/notta-pos.jar}"
PORT="${2:-8099}"
APP="http://localhost:${PORT}"

[ -f "$JAR" ] || { echo "No jar at ${JAR}. Run ./package.sh first."; exit 1; }
JAR="$(cd "$(dirname "$JAR")" && pwd)/$(basename "$JAR")"       # A full path, because the next step changes folder

# Start from an empty folder that is NOT the project, as the other machine would:
# this is what shows that the jar needs nothing from the folders it was built in.
WORK_DIR="$(mktemp -d)"
LOG="${WORK_DIR}/smoke-test.log"

FAILED=0
pass() { printf 'PASS  %s\n' "$*"; }
miss() { printf 'FAIL  %s\n' "$*"; FAILED=1; }

(cd "$WORK_DIR" && exec java -jar "$JAR" "--server.port=${PORT}") > "$LOG" 2>&1 &
JAR_PID=$!

# Whatever happens below, stop the jar and remove the folder on the way out
cleanup() {
    kill "$JAR_PID" 2>/dev/null
    wait "$JAR_PID" 2>/dev/null
    rm -rf "$WORK_DIR"
}
trap cleanup EXIT

# Wait for the app to say it has started (up to 90 seconds), or to stop trying
STARTED=0
for _ in $(seq 1 90); do
    if grep -q "Started NotaPosApplication" "$LOG"; then STARTED=1; break; fi
    if ! kill -0 "$JAR_PID" 2>/dev/null; then break; fi
    sleep 1
done
if [ "$STARTED" != "1" ]; then
    echo "FAIL  the jar did not start. The end of its log:"
    tail -n 30 "$LOG"
    exit 1
fi
pass "the jar starts by itself, from an empty folder"

# curl -s: quiet. -o: where to put the body. -w '%{http_code}': print the status code.

# 1. The sign-in page, with no sign-in
STATUS="$(curl -s -o "${WORK_DIR}/index.html" -w '%{http_code}' "${APP}/")"
if [ "$STATUS" = "200" ] && grep -q '<div id="root">' "${WORK_DIR}/index.html"; then
    pass "the sign-in page is served to someone who is not signed in"
else
    miss "GET / answered ${STATUS}, or the page is not the app's (expected 200 and the app's page)"
fi

# 2. The script the page names, and how long a device may keep it
SCRIPT_PATH="$(grep -o '/assets/[^"]*\.js' "${WORK_DIR}/index.html" | head -n 1)"
if [ -z "$SCRIPT_PATH" ]; then
    miss "the page names no script under /assets"
else
    HEADERS="$(curl -s -o /dev/null -D - "${APP}${SCRIPT_PATH}" | tr -d '\r')"
    if printf '%s' "$HEADERS" | grep -q '^HTTP/[0-9.]* 200' && printf '%s' "$HEADERS" | grep -qi '^cache-control:.*immutable'; then
        pass "the app's script (${SCRIPT_PATH}) is served, and a device may keep it"
    else
        miss "the app's script (${SCRIPT_PATH}) was not served with 200 and an immutable Cache-Control"
    fi
fi

# 3. The API is still closed to someone who is not signed in
STATUS="$(curl -s -o /dev/null -w '%{http_code}' "${APP}/api/tables")"
if [ "$STATUS" = "401" ]; then
    pass "the API refuses a request with no sign-in (401)"
else
    miss "GET /api/tables with no sign-in answered ${STATUS} (expected 401)"
fi

# 4. A brand-new database: the first manager signs in with 1234 and is told to change it
BODY="$(curl -s -X POST "${APP}/api/staff/sign-in" -H 'Content-Type: application/json' -d '{"pin":"1234"}')"
if printf '%s' "$BODY" | grep -q '"token"' && printf '%s' "$BODY" | grep -q '"mustChangePin":true'; then
    pass "the first manager can sign in, and must choose a new PIN"
else
    miss "signing in with the starter PIN did not answer with a token and mustChangePin (is the database empty?)"
fi

echo ""
if [ "$FAILED" = "0" ]; then
    echo "The package works."
else
    echo "The package does NOT work. The end of the jar's log:"
    tail -n 20 "$LOG"
fi
exit "$FAILED"
