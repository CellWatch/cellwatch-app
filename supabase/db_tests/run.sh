#!/usr/bin/env bash
#
# Runs the database tests in supabase/db_tests/*_test.sql, each in its own database inside a
# throwaway Postgres container. The container has no network access, so the tests can't reach
# the live project or the FCC, and the local Supabase stack is never touched.
#
# Kept out of supabase/tests/ on purpose: `supabase test db` expects pgTAP files there and
# runs them against the local stack.
#
# Usage: supabase/db_tests/run.sh   (needs Docker)
#
set -euo pipefail

IMAGE="${POSTGRES_IMAGE:-public.ecr.aws/supabase/postgres:17.6.1.084}"   # same version as the live project
NAME="cellwatch-db-tests-$$"

cd "$(dirname "$0")/.."

docker run -d --rm --network none --name "$NAME" -e POSTGRES_PASSWORD=test-only "$IMAGE" >/dev/null
trap 'docker rm -f "$NAME" >/dev/null 2>&1' EXIT

# The image restarts Postgres once after its init scripts, so wait for it to stay up.
ready=false
for _ in $(seq 1 60); do
    if docker exec "$NAME" psql -U postgres -d postgres -Atqc 'select 1' >/dev/null 2>&1; then
        sleep 5
        if docker exec "$NAME" psql -U postgres -d postgres -Atqc 'select 1' >/dev/null 2>&1; then
            ready=true
            break
        fi
    fi
    sleep 2
done
$ready || { echo "Postgres did not start in the test container" >&2; exit 1; }

docker cp migrations "$NAME":/migrations
docker cp db_tests "$NAME":/db_tests

for test in db_tests/*_test.sql; do
    db="$(basename "$test" .sql)"
    echo "== $test"
    docker exec "$NAME" psql -U postgres -d postgres -Atqc "create database \"$db\""
    docker exec "$NAME" psql -U postgres -d "$db" -X -q -v ON_ERROR_STOP=1 -f "/$test" 2>&1 \
        | sed -E 's/^psql:[^ ]+ NOTICE: +//'
done

echo "All database tests passed."
