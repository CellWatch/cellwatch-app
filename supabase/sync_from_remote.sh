#!/usr/bin/env bash
#
# Rebuild the local Supabase stack from the remote project, treating remote as
# the source of truth.
#
# Why this script exists instead of a plain `supabase db pull`:
#
#   * The remote's direct DB host (db.<ref>.supabase.co) publishes only an AAAA
#     record, and this machine has no IPv6 egress, so every CLI command that
#     dials it hangs until timeout. The IPv4 pooler works, so we force it via
#     --db-url.
#   * pg_dump needs SESSION pooling (port 5432), not the transaction pooling on
#     6543 recorded in supabase/.temp/pooler-url.
#   * --db-url requires the password percent-encoded.
#   * The remote runs PG17 while the existing local container is PG15, so the
#     container has to be recreated -- `db reset` alone will not change engines,
#     and restoring a PG17 dump into PG15 can fail on newer syntax.
#
# The password is read silently, kept in memory only, and never written to disk
# or to shell history.
#
# Production data is written OUTSIDE the git repo, because supabase/seed.sql is
# tracked and this database holds real device/location records.
#
set -euo pipefail

PROJECT_DIR="/Users/david/Desktop/cellwatch-app"
REF="xepxxvpbexkyxrwtrgqv"                       # CellWatch Server
POOLER_HOST="aws-0-us-east-1.pooler.supabase.com"
POOLER_PORT=5432                                 # session mode; required by pg_dump
DUMP_DIR="$HOME/cellwatch-remote-dump"           # deliberately outside the repo

cd "$PROJECT_DIR"

# ---------------------------------------------------------------- credentials
# The Supabase CLI already stored this project's DB password in the login
# keychain when the project was linked, so normally nothing needs typing --
# macOS will show its own authorisation dialog instead. Falls back to a silent
# prompt if the item is missing or access is denied.
if DBPW=$(security find-generic-password -s "Supabase CLI" -a "$REF" -w 2>/dev/null) && [ -n "$DBPW" ]; then
    # The CLI saves secrets through go-keyring, which wraps them in an encoding
    # prefix; the raw keychain value is not the password and fails to log in.
    case "$DBPW" in
        go-keyring-base64:*)  DBPW=$(printf '%s' "${DBPW#go-keyring-base64:}" | base64 --decode) ;;
        go-keyring-encoded:*) DBPW=$(printf '%s' "${DBPW#go-keyring-encoded:}" | xxd -r -p) ;;
    esac
    echo "    using the DB password from your login keychain" >&2
else
    printf 'Supabase DB password for %s (not echoed): ' "$REF" >&2
    IFS= read -rs DBPW
    printf '\n' >&2
fi
[ -n "$DBPW" ] || { echo "aborting: no password available" >&2; exit 1; }

ENC=$(DBPW="$DBPW" python3 -c 'import os,urllib.parse; print(urllib.parse.quote(os.environ["DBPW"], safe=""))')
DBURL="postgresql://postgres.${REF}:${ENC}@${POOLER_HOST}:${POOLER_PORT}/postgres"
unset DBPW ENC

mkdir -p "$DUMP_DIR" supabase/migrations
chmod 700 "$DUMP_DIR"

TS=$(date -u +%Y%m%d%H%M%S)
BASELINE="supabase/migrations/${TS}_remote_baseline.sql"

# ==========================================================================
# Phase A -- read from remote. Nothing local is touched in this phase.
# ==========================================================================

# Reference only. Not loaded locally: the local stack already provisions the
# Supabase-managed roles, and replaying them would conflict.
echo "==> 1/6  dumping cluster roles (also verifies the pooler connection)"
supabase db dump --db-url "$DBURL" --role-only -f "$DUMP_DIR/roles.sql"

echo "==> 2/6  dumping remote schema -> $BASELINE"
supabase db dump --db-url "$DBURL" -f "$BASELINE"

echo "==> 3/6  dumping remote data -> $DUMP_DIR/data.sql  (outside the repo)"
supabase db dump --db-url "$DBURL" --data-only -f "$DUMP_DIR/data.sql"
chmod 600 "$DUMP_DIR/data.sql"

# ------------------------------------------------------------- safety gate --
# The CLI can exit 0 having written an empty file when it cannot reach the
# database (we hit exactly that against the IPv6-only direct host). Never
# destroy the local database until the remote dumps are known to be real.
for f in "$BASELINE" "$DUMP_DIR/data.sql"; do
    sz=$(wc -c < "$f" | tr -d ' ')
    if [ "$sz" -lt 1000 ]; then
        echo "ABORT: $f is only ${sz} bytes -- remote dump failed." >&2
        echo "Local database left untouched." >&2
        exit 1
    fi
    printf '    ok: %s (%s bytes)\n' "$f" "$sz"
done

cp "$BASELINE" supabase/full_schema.sql   # keep the named snapshot in sync

# ==========================================================================
# Phase B -- destructive to the LOCAL database only. Remote is never written.
# ==========================================================================
cat >&2 <<WARN

The local database currently holds real rows (~245k at last count: cells,
locations, measurements, upload_download_data, latency_data, fcc_submissions).
The next step DROPS it and rebuilds from the remote dumps above.

A backup of the current local PG15 database is at:
  ~/cellwatch-local-backup/

WARN
printf 'Type yes to rebuild the local database: ' >&2
IFS= read -r CONFIRM
[ "$CONFIRM" = "yes" ] || { echo "aborted; local database untouched." >&2; exit 1; }

echo "==> 4/6  recreating the local stack so it runs PG17 like the remote"
supabase stop --no-backup
supabase start

echo "==> 5/6  applying the remote schema baseline"
supabase db reset            # rebuilds local from supabase/migrations/*

echo "==> 6/6  loading remote rows into the local database"
CID=$(docker ps --filter "name=supabase_db_" --format '{{.Names}}' | head -1)
[ -n "$CID" ] || { echo "could not find the local supabase db container" >&2; exit 1; }

# Not using ON_ERROR_STOP: a --data-only restore can trip foreign-key ordering.
# Everything is logged so failures are visible rather than silent.
docker exec -i "$CID" psql -U postgres -d postgres \
    < "$DUMP_DIR/data.sql" > "$DUMP_DIR/load.log" 2>&1 || true

echo
echo "---- local engine ----"
docker exec -i "$CID" psql -U postgres -d postgres -At -c "show server_version;"

echo "---- row counts in the local public schema ----"
docker exec -i "$CID" psql -U postgres -d postgres -c "
  select table_name,
         (xpath('/row/c/text()', query_to_xml(format('select count(*) as c from public.%I', table_name), false, true, '')))[1]::text::int as rows
  from information_schema.tables
  where table_schema = 'public'
  order by table_name;"

ERRS=$(grep -ci '^ERROR:' "$DUMP_DIR/load.log" || true)
echo
echo "data load errors: ${ERRS:-0}   (full log: $DUMP_DIR/load.log)"
echo
echo "done."
echo "  baseline migration (committable):  $BASELINE"
echo "  schema snapshot:                   supabase/full_schema.sql"
echo "  roles (reference):                 $DUMP_DIR/roles.sql"
echo "  production data (NOT in the repo): $DUMP_DIR/data.sql"
echo "  previous local DB backup:          ~/cellwatch-local-backup/"
echo
echo "Nothing was written to the remote project."
