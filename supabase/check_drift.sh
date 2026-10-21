#!/usr/bin/env bash
#
# Compare the live Supabase project with what the repo says it should be, and fail if they differ:
#
#   * schema       live `supabase db dump`         vs  supabase/full_schema.sql
#   * cron jobs    live cron.job                    vs  supabase/cron_jobs.sql
#   * role timeouts live pg_db_role_setting (*_timeout settings of the API roles and postgres)
#                                                   vs  supabase/role_settings.sql
#
# Exit status: 0 = no drift, 1 = drift (diffs printed), 2 = could not check.
#
# It only reads from the remote. The schema is dumped the same way sync_from_remote.sh does it (session
# pooler, `supabase db dump`); the other two use psql inside a read-only transaction. Unlike
# sync_from_remote.sh it never touches the local database.
#
#   supabase/check_drift.sh            # check
#   supabase/check_drift.sh --write    # also overwrite the three tracked files with the live state,
#                                      # so `git diff` shows the drift; review before committing
#
# Credentials: set SUPABASE_DB_URL to a full postgresql:// URL, or leave it unset to use the password
# the Supabase CLI saved in the login keychain (falls back to a silent prompt), as sync_from_remote.sh
# does. The password is kept in memory only.
#
# Needs: the Supabase CLI (and Docker, which `supabase db dump` uses) and psql.
#
set -euo pipefail

REF="xepxxvpbexkyxrwtrgqv"                       # CellWatch Server
POOLER_HOST="aws-0-us-east-1.pooler.supabase.com"
POOLER_PORT=5432                                 # session mode; required by pg_dump

WRITE=false
case "${1:-}" in
    "") ;;
    --write) WRITE=true ;;
    *) echo "usage: $0 [--write]" >&2; exit 2 ;;
esac

cd "$(dirname "$0")"                             # supabase/

for tool in supabase psql diff; do
    command -v "$tool" >/dev/null || { echo "check_drift: $tool not found" >&2; exit 2; }
done

# ---------------------------------------------------------------- credentials
if [ -n "${SUPABASE_DB_URL:-}" ]; then
    DBURL="$SUPABASE_DB_URL"
else
    if DBPW=$(security find-generic-password -s "Supabase CLI" -a "$REF" -w 2>/dev/null) && [ -n "$DBPW" ]; then
        # go-keyring wraps stored secrets in an encoding prefix; see sync_from_remote.sh.
        case "$DBPW" in
            go-keyring-base64:*)  DBPW=$(printf '%s' "${DBPW#go-keyring-base64:}" | base64 --decode) ;;
            go-keyring-encoded:*) DBPW=$(printf '%s' "${DBPW#go-keyring-encoded:}" | xxd -r -p) ;;
        esac
    else
        printf 'Supabase DB password for %s (not echoed): ' "$REF" >&2
        IFS= read -rs DBPW
        printf '\n' >&2
    fi
    [ -n "$DBPW" ] || { echo "check_drift: no password available" >&2; exit 2; }
    ENC=$(DBPW="$DBPW" python3 -c 'import os,urllib.parse; print(urllib.parse.quote(os.environ["DBPW"], safe=""))')
    DBURL="postgresql://postgres.${REF}:${ENC}@${POOLER_HOST}:${POOLER_PORT}/postgres"
    unset DBPW ENC
fi

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

# Runs one query in a read-only transaction and writes its rows, unaligned, to the file given as $1.
# The pooler ignores PGOPTIONS, so read-only mode is set inside the session. A failed query is an
# error (exit 2), not drift.
live_query() {
    if ! psql "$DBURL" -X -q -At -v ON_ERROR_STOP=1 > "$1" <<SQL
begin read only;
$2
commit;
SQL
    then
        echo "check_drift: query failed: $2" >&2
        exit 2
    fi
}

# Keeps the leading comment block of a tracked file (minus any earlier "Updated by" line) and
# replaces the statements below it with the live ones.
rewrite_tracked() {
    local file=$1 live=$2
    {
        awk '/^--/{print; next} {exit}' "$file" | grep -v '^-- Updated by check_drift.sh' || true
        echo "-- Updated by check_drift.sh --write on $(date -u +%Y-%m-%d)."
        echo
        cat "$live"
    } > "$TMP/rewrite"
    mv "$TMP/rewrite" "$file"
}

# pg_dump 17.6+ brackets the dump in \restrict / \unrestrict with a random key, so two dumps of the
# same schema never match byte for byte. Drop those lines before comparing.
normalize_schema() {
    grep -v -E '^\\(un)?restrict ' "$1"
}

# cron_jobs.sql and role_settings.sql hold one statement per line plus comments.
statements_only() {
    grep -v -E '^[[:space:]]*(--|$)' "$1"
}

drift=false

# --------------------------------------------------------------------- schema
echo "==> schema" >&2
supabase db dump --db-url "$DBURL" -f "$TMP/schema.sql" >&2 \
    || { echo "check_drift: supabase db dump failed" >&2; exit 2; }
# The CLI can exit 0 after writing an empty file when it can't reach the database.
size=$(wc -c < "$TMP/schema.sql" | tr -d ' ')
if [ "$size" -lt 1000 ]; then
    echo "check_drift: schema dump is only ${size} bytes; the dump failed" >&2
    exit 2
fi
if ! diff -u --label full_schema.sql --label live <(normalize_schema full_schema.sql) \
        <(normalize_schema "$TMP/schema.sql"); then
    drift=true
fi

# ------------------------------------------------------------------ cron jobs
echo "==> cron jobs" >&2
live_query "$TMP/cron.sql" "
select format('select cron.schedule(%L, %L, %L);', jobname, schedule, command)
from cron.job
order by jobname;"
if ! diff -u --label cron_jobs.sql --label live <(statements_only cron_jobs.sql) "$TMP/cron.sql"; then
    drift=true
fi
# cron.schedule() can't express these, so report them separately.
live_query "$TMP/cron_other.txt" "
select format('job %s: active=%s, runs as %s in database %s', jobname, active, username, database)
from cron.job
where not active or username <> 'postgres' or database <> 'postgres'
order by jobname;"
if [ -s "$TMP/cron_other.txt" ]; then
    echo "cron jobs that are paused or don't run as postgres in database postgres:"
    cat "$TMP/cron_other.txt"
    drift=true
fi

# -------------------------------------------------------------- role settings
echo "==> role timeouts" >&2
live_query "$TMP/roles.sql" "
select case when s.setdatabase = 0
            then format('alter role %I set %s = %L;', r.rolname, split_part(c.cfg, '=', 1), substr(c.cfg, strpos(c.cfg, '=') + 1))
            else format('alter role %I in database %I set %s = %L;', r.rolname, d.datname, split_part(c.cfg, '=', 1), substr(c.cfg, strpos(c.cfg, '=') + 1))
       end
from pg_db_role_setting s
join pg_roles r on r.oid = s.setrole
left join pg_database d on d.oid = s.setdatabase
cross join lateral unnest(s.setconfig) as c(cfg)
where r.rolname in ('anon', 'authenticated', 'authenticator', 'service_role', 'postgres')
  and split_part(c.cfg, '=', 1) like '%\_timeout'
order by r.rolname, d.datname nulls first, split_part(c.cfg, '=', 1);"
if ! diff -u --label role_settings.sql --label live <(statements_only role_settings.sql) "$TMP/roles.sql"; then
    drift=true
fi

# ---------------------------------------------------------------------- write
if $WRITE; then
    cp "$TMP/schema.sql" full_schema.sql
    rewrite_tracked cron_jobs.sql "$TMP/cron.sql"
    rewrite_tracked role_settings.sql "$TMP/roles.sql"
    echo "wrote full_schema.sql, cron_jobs.sql and role_settings.sql; review with git diff" >&2
fi

if $drift; then
    echo "DRIFT: the live project differs from the repo (see diffs above)." >&2
    exit 1
fi
echo "no drift" >&2
