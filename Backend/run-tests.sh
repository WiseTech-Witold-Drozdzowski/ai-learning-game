#!/usr/bin/env bash
#
# Runs the backend tests entirely in Docker:
#   - throwaway Postgres (test-db)
#   - Gradle container (jdk25) -> gradle test against that database
#
# Requires only Docker + docker compose (no local Java or Gradle).
# Exit code = the tests' exit code (CI-friendly).
#
# Usage:
#   ./run-tests.sh                 # run all tests
#   ./run-tests.sh Ping            # run only tests matching *Ping*
#   ./run-tests.sh Ping Job        # run tests matching *Ping* OR *Job*
#
# Each argument is a (partial) test-class/method name; it is wrapped in
# wildcards and passed to Gradle's --tests filter (so "Ping" matches
# PingControllerTest, "savesAndReads" matches a single method, etc.).
#
set -euo pipefail

cd "$(dirname "$0")"

# Map the host user into the test container (see docker-compose.test.yml).
export HOST_UID="$(id -u)"
export HOST_GID="$(id -g)"

COMPOSE="docker compose -f docker-compose.test.yml -p career-coach-test"

cleanup() {
    echo "==> Cleaning up test containers..."
    $COMPOSE down -v --remove-orphans >/dev/null 2>&1 || true
}
trap cleanup EXIT

# Build the Gradle command. Each positional arg becomes a --tests *<arg>* filter.
GRADLE_CMD=(gradle --no-daemon test)
if [ "$#" -gt 0 ]; then
    for name in "$@"; do
        GRADLE_CMD+=(--tests "*${name}*")
    done
    echo "==> Test filter: $*"
fi

echo "==> Running tests in Docker (Postgres + Gradle/JDK 25)..."
# `run` (not `up`) so we can pass an explicit argv with the test filters,
# preserving argument boundaries. It starts test-db via depends_on and
# returns the command's exit code.
$COMPOSE run --rm -T test "${GRADLE_CMD[@]}"

echo "==> Tests finished successfully."
