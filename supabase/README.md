# Supabase backend

The live project is **CellWatch Server** (`xepxxvpbexkyxrwtrgqv`, Postgres 17). Everything the app and the FCC
pipeline depend on lives in its database: tables, RLS policies, functions, triggers, pg_cron jobs and role
settings.

| File | What it is |
|---|---|
| `full_schema.sql` | Snapshot of the live schema (`supabase db dump`). It's a record, not something to apply. |
| `cron_jobs.sql` | The pg_cron jobs as the migrations leave them; run it to recreate them. Schema dumps leave them out. |
| `role_settings.sql` | Expected per-role timeouts. Schema dumps leave them out too. |
| `migrations/` | Every change made to the live database since July 2026, in order. |
| `db_tests/` | Tests for the migrations. They run in a throwaway container. |
| `check_drift.sh` | Compares the live project with the three snapshot files. |
| `sync_from_remote.sh` | Rebuilds the **local** stack from the live project. It's destructive locally. |
| `post_fcc_submission_func.sql` | Older setup script for FCC posting: the payload view `private.fcc_submissions_view`, `private.fcc_submission_info`, and early versions of the posting functions and trigger function. It isn't a migration and isn't safe to re-run (it drops and re-creates the view, and its trigger function posts directly). The current definitions are in `migrations/` and `full_schema.sql`. Its comments explain how to register a BDC environment and token in Vault. |

## Every change to the live database is a migration

Make changes to the live project only by applying a file from `migrations/`. Don't make them in the SQL editor
or the dashboard.
- **Name:** `YYYYMMDDNNNN_description.sql`. Wrap the body in `begin; ... commit;`.
- **Comments:** say why, in plain English, as in `202610070001_unclog_fcc_retry_queue.sql`. Mark what changed
  from the live definition.
- **Re-runnable where possible:** use `create or replace`, `if not exists`, `alter ... set`, and
  `cron.schedule()` (it updates a job with the same name). Things that can't be written this way (policies, for
  example) need a comment saying the file must run exactly once.
- **Tests:** add a test to `db_tests/` (below).
- **Secrets:** never put a secret in a migration (FCC token, dashboard shared secret, keys). Those live in Vault
  or tables that are filled by hand, and the migration says so.
- **App compatibility:** if a migration adds a column the app will send, apply it before releasing the app
  build. supabase-kt 1.0.3 leaves unknown columns out of responses but fails inserts with them (see
  doc/release-checklist.md).

## Testing

```bash
supabase/db_tests/run.sh      # needs Docker
```

Each `db_tests/*_test.sql` runs in its own database inside a throwaway `supabase/postgres:17.6.1.084` container
started with `--network none`. That's the live project's version, and the container can't reach the live
project or the FCC.
- A test builds minimal copies of the tables it needs (columns as in `full_schema.sql`), loads migrations with
  `\i /migrations/<file>`, and prints `ok - ...` for each check.
- Stand-ins replace anything that would leave the box, such as `post_fcc_submission()`.
- The local Supabase stack (`supabase start`) isn't used and isn't touched.

## Applying a migration to the live project

The repo doesn't record how the two July/October migrations reached production. There's no CI deploy, and the
snapshot doesn't show whether the project has `supabase db push` history. Until the team agrees on something
else, this is the proposed procedure:

1. The migration and its test are merged to `main`, and `run.sh` passes.
2. `supabase/check_drift.sh` shows no drift. If there is drift, a hand change has happened: capture it in a
   migration first.
3. Apply the file through the session pooler (the connection `sync_from_remote.sh` and `check_drift.sh` use):
   ```bash
   psql "$SUPABASE_DB_URL" -X -v ON_ERROR_STOP=1 -f supabase/migrations/<file>.sql
   ```
   Pasting the file into the SQL editor works too, but don't run long batch jobs there (see #117).
4. Run `supabase/check_drift.sh --write` and commit the refreshed `full_schema.sql`, `cron_jobs.sql` and
   `role_settings.sql` as "Snapshot after <migration>".

Don't use `supabase db push` until someone has checked `supabase_migrations.schema_migrations` on the live
project. If that history is missing or incomplete, `db push` would replay every file in `migrations/`, including
202607140001's version of `auto_post_fcc_submission()` that posts to the FCC directly. A later file replaces it,
but that intermediate state would briefly be live.

## Drift check

```bash
supabase/check_drift.sh            # exit 0: no drift, 1: drift (diffs printed), 2: couldn't check
supabase/check_drift.sh --write    # also overwrite the three snapshot files with the live state
```

It reads the live project only:
- `supabase db dump` (as in `sync_from_remote.sh`), compared with `full_schema.sql`. The random
  `\restrict` key that pg_dump 17.6+ adds is ignored.
- `cron.job`, compared with `cron_jobs.sql`. Paused jobs, and jobs not running as `postgres` in database
  `postgres`, are reported separately.
- `*_timeout` settings of `anon`, `authenticated`, `authenticator`, `service_role` and `postgres`, compared with
  `role_settings.sql`.

The queries run in a read-only transaction. The password comes from `SUPABASE_DB_URL`, the Supabase CLI's
keychain entry, or a prompt.

Run it before writing a migration, after applying one, and at least monthly. It hasn't been run against the
live project yet; it was dry-run against a local container with stubbed `supabase`/`psql` commands. Expect the
first real run to show:
- the cron job's command until 202610210103 is pushed (`cron_jobs.sql` has the `call private.run_fcc_submission_retries(10);` that 202610210103 schedules);
- any role timeout that `role_settings.sql` guessed wrong.

Review, then `--write` and commit.

## Objects that were created by hand (#121)

These were created directly on the live project and only appeared in the repo with the 2026-09-30 snapshot.

| Object | Now tracked by |
|---|---|
| `trg_auto_post_fcc_submission` and its enqueue-only `private.auto_post_fcc_submission()` | `202609300001_track_live_fcc_queue_and_role_settings.sql` |
| `private.fcc_submission_retry_queue` | `202609300001_...` (`create table if not exists`, with the live constraints) |
| `private.process_fcc_submission_retries()` | `202610070001_unclog_fcc_retry_queue.sql`, which defines it in full |
| `fcc-submission-retries` cron job | `202610070001_...` (`cron.schedule`, batch 10), then `202610210103_...` (`call private.run_fcc_submission_retries(10)`), and `cron_jobs.sql` |
| `anon` `statement_timeout` = 2min | `202609300001_...` and `role_settings.sql`. Supabase's default is 3s, so this is the one real hand change. |
| `authenticator` `statement_timeout` and `lock_timeout` = 8s | Same files. These match Supabase's defaults (they're in the stock image), so they weren't hand changes. |
| Dashboard RLS policies, `private.is_dashboard_request()`, `private.verified_device_id_or_null()` | **Snapshot only.** See below. |

**Why the dashboard policies have no baseline migration:**
- `create policy` has no `if not exists` or `or replace`, so a re-runnable version would have to drop and
  re-create them.
- #115 is about to change exactly these policies (wrapping the device check in `(select ...)`). A baseline
  that re-created the current versions would undo #115 if anyone ran it afterwards.
- `is_dashboard_request()` reads a shared secret from `private.dashboard_settings`, which mustn't go in a
  migration.

The #115 migration (`alter policy ...`) becomes the first tracked definition. Until then `full_schema.sql` is
the record, and the drift check catches any further hand changes.

The repo can't say when hand changes happened. For example, the per-row device check in the `measurements`
policy has no date. The drift check is there so the next one is caught when it happens.

## Local stack

`supabase db reset` applies `migrations/*` and then `seed.sql`. It does **not** load `full_schema.sql`, and the
migrations alone can't build the schema, because they assume the base tables already exist.
`sync_from_remote.sh` works around this by writing the live dump into `migrations/` as
`<now>_remote_baseline.sql`. Because that file is timestamped now, it sorts after the committed migrations, so
on an empty database `202607140001` would run before any table exists. This hasn't been run here; expect to
rename the baseline so it sorts first (e.g. `000000000000_remote_baseline.sql`, uncommitted) when rebuilding
locally. For database tests, use `db_tests/run.sh`, which doesn't need the local stack.
