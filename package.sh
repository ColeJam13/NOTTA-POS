#!/usr/bin/env bash
# ============================================================================
# NOTTA-POS package script
# ============================================================================
# WHAT: Makes the app into ONE file that can be copied to another machine and
#       run there:  release/notta-pos/notta-pos.jar
#       The jar is the backend with the built frontend inside it. Started with
#       "java -jar notta-pos.jar", it serves the screens and the API from one
#       address (http://<that machine>:8080), so a phone needs nothing but that
#       address.
#
#       Beside the jar it puts everything else the other machine needs: the
#       Windows start and backup scripts, the settings file to fill in, the
#       seed data, and the step-by-step guide. The whole folder is also zipped.
#
# WHY:  While developing, the app is two programs (start.sh runs both, on two
#       ports), which needs Node.js and Maven and two addresses that must know
#       about each other. The machine the app is USED on should need only Java
#       and PostgreSQL.
#
# USAGE (from the project folder):
#         ./package.sh               build the package (does not run the tests)
#         ./package.sh --with-tests  run the backend and frontend tests first
#
# Package from an up-to-date "main": that is the code CI has tested. The
# tests are skipped by default for that reason, and because the backend's
# tests need Docker running.
#
# What to do with the result: Documentation/test-host.md
# ============================================================================

# -e: stop at the first command that fails. -u: a misspelled variable is an error.
set -eu

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RELEASE_DIR="${ROOT}/release/notta-pos"

say()  { printf '%s\n' "$*"; }
fail() { printf '\nCould not package: %s\n' "$*" >&2; exit 1; }

WITH_TESTS=false
for arg in "$@"; do
    case "$arg" in
        --with-tests) WITH_TESTS=true ;;
        *) fail "unknown option '$arg'. Usage: ./package.sh [--with-tests]" ;;
    esac
done

# ----------------------------------------------------------------------------
# 1. Things that must be installed (on THIS machine, where the package is made)
# ----------------------------------------------------------------------------
for tool in java mvn node npm; do
    command -v "$tool" >/dev/null 2>&1 || fail "'$tool' is not installed (see \"Running it\" in README.md)."
done

# ----------------------------------------------------------------------------
# 2. The frontend: build the files a browser downloads
# ----------------------------------------------------------------------------
# Same check as start.sh: install the packages if they are missing or the list changed.
if [ ! -d "${ROOT}/frontend/node_modules" ] || [ "${ROOT}/frontend/package-lock.json" -nt "${ROOT}/frontend/node_modules/.package-lock.json" ]; then
    say "Frontend: installing packages..."
    (cd "${ROOT}/frontend" && npm install) || fail "npm install failed."
    touch "${ROOT}/frontend/node_modules/.package-lock.json"
fi

if [ "$WITH_TESTS" = true ]; then
    say "Frontend: lint and tests..."
    (cd "${ROOT}/frontend" && npm run lint && npm test) || fail "the frontend's lint or tests failed. Nothing was packaged."
fi

say "Frontend: building..."
# VITE_API_URL is set to nothing ON PURPOSE. With no address built in, the app sends
# its requests to wherever the page was loaded from, which in the package is the
# backend itself (see backendAddressFrom in frontend/src/services/api.js).
# Setting it here, even to nothing, also wins over frontend/.env.local: without this
# line a .env.local made for a tablet ("VITE_API_URL=http://192.168.1.50:8080") would
# be baked into the package, and it would only work on a network with that address.
(cd "${ROOT}/frontend" && VITE_API_URL= npm run build) || fail "the frontend build failed."
[ -f "${ROOT}/frontend/build/index.html" ] || fail "the frontend build did not produce frontend/build/index.html."

# ----------------------------------------------------------------------------
# 3. The backend: one jar, with the built frontend inside
# ----------------------------------------------------------------------------
# -Ppackaged switches on the "packaged" profile in backend/pom.xml (it copies
# frontend/build into the jar and builds in backend/target-packaged).
# "clean" empties target-packaged first, so nothing from an earlier package is left in.
if [ "$WITH_TESTS" = true ]; then
    say "Backend: tests, then building the jar (the tests need Docker running)..."
    (cd "${ROOT}/backend" && mvn -q -Ppackaged clean package) || fail "the backend's tests or build failed. Nothing was packaged."
else
    say "Backend: building the jar..."
    (cd "${ROOT}/backend" && mvn -q -Ppackaged clean package -DskipTests) || fail "the backend build failed."
fi

JAR="${ROOT}/backend/target-packaged/notta-pos.jar"
[ -f "$JAR" ] || fail "the build did not produce ${JAR}."

# Look inside the jar: the frontend must be in it. ("jar tf" lists a jar's contents.)
# A jar without it would start normally and answer every phone with a blank 404.
jar tf "$JAR" | grep -q '^BOOT-INF/classes/static/index.html$' \
    || fail "the jar was built without the frontend in it (no static/index.html)."
jar tf "$JAR" | grep -q '^BOOT-INF/classes/static/assets/.*\.js$' \
    || fail "the jar was built without the frontend's scripts in it (nothing in static/assets)."

# ----------------------------------------------------------------------------
# 4. The folder to copy to the other machine
# ----------------------------------------------------------------------------
rm -rf "$RELEASE_DIR"
mkdir -p "$RELEASE_DIR"

cp "$JAR"                                                   "$RELEASE_DIR/notta-pos.jar"
cp "${ROOT}/backend/application-local.properties.example"   "$RELEASE_DIR/"
cp "${ROOT}/backend/peach-blossom-seed-data.sql"            "$RELEASE_DIR/"
cp "${ROOT}/Documentation/test-host.md"                     "$RELEASE_DIR/"

# The Windows scripts: start-notta-pos and backup-notta-pos, each as a .ps1 (the script)
# and a .cmd (the file to double-click, which runs the script).
# Windows ends each line of a text file with two characters (CR LF) where Linux uses one
# (LF), and .cmd files can misbehave without the CR. The sed line removes a CR if one is
# already there and then adds one, so the result is right whichever way the file was saved.
for script in "${ROOT}"/deploy/windows/*.ps1 "${ROOT}"/deploy/windows/*.cmd; do
    sed -e 's/\r$//' -e 's/$/\r/' "$script" > "$RELEASE_DIR/$(basename "$script")"
done

# Which code this is, so that months later the machine can still say what it is running.
# "git describe --always --dirty" prints the commit's short ID, with "-dirty" added if
# files were changed since that commit (a package that is not exactly what is in git).
{
    printf 'NOTTA-POS package\n'
    printf 'Built:  %s\n' "$(date '+%Y-%m-%d %H:%M')"
    printf 'Commit: %s\n' "$(git -C "$ROOT" describe --always --dirty 2>/dev/null || echo unknown)"
    printf 'Branch: %s\n' "$(git -C "$ROOT" rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)"
} > "$RELEASE_DIR/VERSION.txt"

# One zip of the folder, for copying. The JDK's "jar" tool writes ordinary zip files
# (-M: without the extra entry a real jar has), and Java is already required above,
# so nothing else needs installing.
rm -f "${ROOT}/release/notta-pos.zip"
(cd "${ROOT}/release" && jar -c -M -f notta-pos.zip notta-pos)

say ""
say "Packaged: $(du -h "$RELEASE_DIR/notta-pos.jar" | cut -f1) jar, commit $(sed -n 's/^Commit: //p' "$RELEASE_DIR/VERSION.txt")"
say "  Folder: release/notta-pos/"
say "  Zip:    release/notta-pos.zip"
say ""
say "To try it on this machine (PostgreSQL running, and the backend from start.sh stopped):"
say "  cd release/notta-pos && cp ../../backend/application-local.properties . && java -jar notta-pos.jar"
say "  then open http://localhost:8080"
say ""
say "To put it on another machine: Documentation/test-host.md"
