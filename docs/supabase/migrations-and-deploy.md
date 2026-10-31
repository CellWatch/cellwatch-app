# Migrations and deploying schema changes

How schema changes are written, tested, applied locally, and pushed to prod, and the rules that keep the app and server in step.

**Related:** [schema](schema.md) · [triggers-and-cron](triggers-and-cron.md) · [performance-notes](performance-notes.md) · [local-setup](../dev/local-setup.md) · [testing](../dev/testing.md) · [runbooks](../ops/runbooks.md) · [release-checklist](../ops/release-checklist.md) · prod deploy runbook (`prod-deploy-runbook.md`, local only)

## The files under `supabase/`

| File | What it is | Authoritative for |
|---|---|---|
| [migrations/](../../supabase/migrations/) | Forward-only SQL changes, named `YYYYMMDDNNNN_description.sql`. They use `create or replace` / `if not exists`, so there are no down migrations. Each file is one `begin; … commit;` transaction | Changes made since the snapshot |
| [full_schema.sql](../../supabase/full_schema.sql) | `supabase db dump` of prod (schema only, no data). Last refreshed 2026-11-01 by `check_drift.sh --write` | Prod's schema. It includes every migration on prod: `202607140001` and `202610070001` (applied by hand), `202609300001`, `202610210001`, `0101`, `0103`, `0104` (pushed 2026-10-29), `202610220001`, `0102`, `202610310001` (pushed 2026-10-31), and `202610220101`, `202610310002` (pushed 2026-11-01). That is every migration on main. Their function bodies match the migration files exactly |
| [cron_jobs.sql](../../supabase/cron_jobs.sql) | `cron.schedule(...)` calls that recreate the pg_cron jobs. Jobs are rows in `cron.job` and are **not** in schema dumps | The jobs as pulled from prod on 2026-11-01 |
| [role_settings.sql](../../supabase/role_settings.sql) | The `*_timeout` settings of the API roles and `postgres`. Role settings are **not** in schema dumps | The settings as pulled from prod on 2026-11-01 |
| [post_fcc_submission_func.sql](../../supabase/post_fcc_submission_func.sql) | Original setup script for the FCC view, `fcc_submission_info`, and posting functions | History only; partly superseded. **Do not re-run** (it would bring back the synchronous auto-post trigger) |
| [seed.sql](../../supabase/seed.sql) | Empty. Real data must never go here (the file is tracked) | |
| [sync_from_remote.sh](../../supabase/sync_from_remote.sh) | Rebuilds the local stack from prod; see below | |
| [db_tests/](../../supabase/db_tests/) | SQL tests for individual migrations, run in a throwaway container | |
| [config.toml](../../supabase/config.toml) | Local CLI stack config | |

Migrations in `supabase/migrations/` on main, and where each stands (2026-10-31):

| Migration | Status |
|---|---|
| `202607140001_repair_fcc_submission_functions.sql` | Applied to prod by hand before the October pushes. Its `auto_post_fcc_submission` was later replaced by `202609300001`'s queue version, so **never apply it again** |
| `202609300001_track_live_fcc_queue_and_role_settings.sql` | **Pushed 2026-10-29** (#134). Records the queue table, queue-only trigger, and role settings that were made by hand on prod |
| `202610070001_unclog_fcc_retry_queue.sql` | Applied to prod by hand on 2026-10-07 (#114) |
| `202610210001_fast_upload_rls.sql` | **Pushed 2026-10-29** (#132). Upload RLS fix and `measurements(device_id)` index ([performance-notes](performance-notes.md#1-upload-cost-grows-with-device-history)) |
| `202610210101`, `0103`, `0104` | **Pushed 2026-10-29** (#133, review fixes in #151). Per-submission FCC payload and `measurements(group_id)` index, queue processing, queue health. There is no `0102`: blank carrier names are not filled in |
| `202610220001_retry_safe_device_registration.sql` | **Pushed 2026-10-31** (#148, review fixes in #151). Makes `register_device` safe to retry and adds `register_device(device_id, device_secret)`, which the app on main calls; it had to be live before any app release from main |
| `202610220101_fix_dashboard_rpcs.sql` | **Pushed 2026-11-01** (#136, #151). Fixes `rpc_group_detail`'s filter, requires `x-dashboard-secret` on the three dashboard RPCs (calls without it get HTTP 401, `42501`), reports latency in ms, fixes the bytes/duration speed fallback. Before the push, the dashboard backend's `DASHBOARD_SECRET` on Render (CellWatch/demo-dashboard, service `cellwatch-dashboard-backend-flask`) was checked to match prod's `private.dashboard_settings` secret |
| `202610220102_tighten_database_helpers.sql` | **Pushed 2026-10-31** (#136, #151). Drops `log_user_data`, fixes `get_raw_header(item)`, pins `search_path` on the device/dashboard checks, revokes `TRUNCATE` from `anon`/`authenticated` |
| `202610310001_email_fcc_queue_digest.sql` | **Pushed 2026-10-31** (#154). Emails `private.fcc_queue_digest()` through Resend from the daily job `fcc-queue-digest` at 13:01 UTC, but only when, since the last run, an entry was blocked or held, a post failed, or an entry passed 3 days pending; otherwise at least once a week. Runs are logged in `private.fcc_digest_runs`. Its two Vault secrets, `fcc_digest_resend_api_key` and `fcc_digest_email_to`, were added by hand on prod; without a domain verified in Resend, it can only email the Resend account's own address. Tested in `db_tests/fcc_digest_email_test.sql` |
| `202610310002_ignore_old_blocked_fcc_entries.sql` | **Pushed 2026-11-01** (#156). Lists the 89 queue entries blocked before the 2026-10-29 pipeline push in `private.fcc_queue_ignored`. `fcc_queue_attention`, the digest's counts and `send_fcc_queue_digest`'s check for something new skip every listed entry, so they never appear in the email. The queue rows are unchanged, and `fcc_queue_health` still counts them. Delete a row to report that entry again. Tested in `db_tests/fcc_digest_ignore_test.sql` |

Every version on prod is recorded in `supabase_migrations.schema_migrations`, the Supabase CLI's history (done 2026-10-31). Its newest entry is still `20991231235959`, so `supabase db push` refuses to run against prod. Push with psql as in the runbook, then add the version to that table.

Not on main: `202610130001_record_local_ports.sql` is uncommitted on the `client-port-header` branch, with its test `db_tests/local_ports_test.sql`. Not pushed.

## Local stack

From [config.toml](../../supabase/config.toml): API on `54321`, Postgres on `54322`, Studio on `54323`, Inbucket on `54324`. The REST API exposes `public`, `storage`, `graphql_public`. `[db].major_version` is `17`, matching prod; `sync_from_remote.sh` and `check_drift.sh` need that, since the CLI's `pg_dump` follows it and refuses to dump a newer server.

```bash
supabase start        # bring up the local stack (Docker)
supabase db reset     # drop the local DB, apply supabase/migrations/* in order, then seed.sql
```

> **Caveat:** main has **no full baseline migration**. `migrations/` holds only incremental changes that assume the live tables exist. `202607140001` creates a SQL-language function that references `public.measurements`, so `supabase db reset` on a fresh database fails. #134's `202609300001` (merged 2026-10-27) is called a baseline, but it only records the hand-made queue table, trigger, and role settings; it doesn't create the `public` tables and sorts after `202607140001`, so it doesn't fix this. Until a full baseline lands, choose one:
> - run `sync_from_remote.sh`. It writes a timestamped `*_remote_baseline.sql` into `migrations/`, which is untracked, so don't commit it by accident. It sorts after the committed migrations, so rename it to sort first before a reset ([supabase/README.md](../../supabase/README.md#local-stack)).
> - load `full_schema.sql` into an empty database first, then apply the migrations by hand.
>
> See [local-setup](../dev/local-setup.md).

### `sync_from_remote.sh`

Rebuilds the local database from prod, treating prod as the source of truth.

1. Reads the DB password from the macOS login keychain (the item the Supabase CLI stores), or prompts silently. The password stays in memory only.
2. Connects through the IPv4 **session pooler**, port 5432. The direct DB host is IPv6-only, and `pg_dump` needs session mode.
3. Read-only phase: dumps roles, schema (→ new baseline migration **and** `full_schema.sql`), and data. The data goes to `~/cellwatch-remote-dump/`, **outside the repo**, because it contains real device, location, and contact data. It aborts if any dump is under 1000 bytes.
4. Destructive phase, **local only**, after you type `yes`: `supabase stop --no-backup`, `supabase start`, `supabase db reset`, then loads the data dump into the local container.

It never writes to the remote. After a prod schema change, re-run it, or use another `supabase db dump`, to refresh `full_schema.sql`. Refresh `cron_jobs.sql` by hand from `cron.job`.

## Database tests

```bash
supabase/db_tests/run.sh      # needs Docker
```

- Starts `public.ecr.aws/supabase/postgres:17.6.1.084` (same version as prod) with `--network none`, so tests cannot reach prod or the FCC. Override the image with `POSTGRES_IMAGE`.
- Copies `migrations/` and `db_tests/` into the container. Runs each `db_tests/*_test.sql` in its own new database with `ON_ERROR_STOP`.
- Each test creates **minimal copies** of the tables it needs, adds stand-ins for anything external (for example a stand-in for the FCC HTTP request), then loads the migration under test with `\i /migrations/<file>.sql`. The FCC pipeline tests share their copy of the live tables, triggers, view and functions in `db_tests/lib/fcc_schema.sql`. Most tests assert with `perform expect(<bool>, '<description>')`, which raises `FAILED: …` on failure and prints `ok - …` on success; `dashboard_rpcs_test.sql` and `database_helpers_test.sql` raise an exception on failure and print `ok: …` on success.
- Kept out of `supabase/tests/` on purpose: `supabase test db` expects pgTAP there and runs against the local stack.

| Test | Covers |
|---|---|
| `live_baseline_test.sql` | `202609300001` (#134): creates the live-only queue table, trigger and role settings on a fresh database, and changes nothing when run again |
| `fcc_retry_queue_test.sql` | `process_fcc_submission_retries` as `202610210103` (#133) keeps it for manual runs, on top of `202610070001` and `0101`: waiting entries (5 min / 1 h recheck, not counted as attempts), batch of 10, cap at 10, blank provider → held once the measurements are complete, already-sent entries removed, 400 → blocked, 503 → retried in 60 s, 60 s per-run cap, logging |
| `upload_rls_test.sql` | `202610210001` (#132): every kind of caller is allowed and refused exactly as before on five tables, devices stay isolated, and an upload and the duplicate check each take under 1 s at 400 measurements |
| `fcc_payload_test.sql` | `202610210101` (#133): `fcc_submission_payload` gives byte for byte the old view's payload on each fixture, with locations and cells in the same order; `post_fcc_submission` posts it |
| `fcc_queue_test.sql` | `0101`, `0103`, `0104` (#133): per-post commits (a later timeout keeps earlier posts recorded, including a `CALL` under pg_cron), `duplicate_test_id` → sent, blank provider → held, `held` reasons and their daily re-check, 30-day `expired` and revival, health views and digest |
| `device_registration_test.sql` | `202610220001` (#148): reproduces the lockout, then a phone that lost its response registers again with the same secret, a device that never uploaded can re-register, and one that has uploaded keeps its secret |
| `local_ports_test.sql` (not on main; uncommitted on `client-port-header`) | `202610130001`: header parsing, malformed values → null, `tuple_local_port` range check, `source_port_comparison` counts |
| `dashboard_rpcs_test.sql` | `202610220101` (#136): detail returns only its group, all three refuse without the dashboard secret, latency in ms, speed fallback from bytes and duration, a caller's temporary types don't run as their owner |
| `database_helpers_test.sql` | `202610220102` (#136): `log_user_data` dropped, `get_raw_header(item)`, pinned `search_path`, no `TRUNCATE` for app roles |
| `migration_deploy_test.sql` | `202610210001`, `0101`, `0103` while other sessions hold their tables: uploads that arrive meanwhile don't wait for the migration, and leftover invalid indexes from a failed concurrent build are rebuilt. Starts the other sessions with psql's `\!` |
| `fcc_digest_email_test.sql` | `202610310001` (#154): an email only when something is new since the last run, or after 7 days without one; unchanged blocked entries as one line; a run Resend refuses fails without being logged |
| `fcc_digest_ignore_test.sql` | `202610310002` (#156): which entries are listed in `fcc_queue_ignored`, and that the attention view, the digest and the check for something new skip them |

App-side checks are in [testing](../dev/testing.md).

## Deploying to prod

Migrations are pushed to prod **by hand with `psql`**, one migration file at a time, through the session pooler. There is no CI deploy, and the `supabase db push` command is not used. **Merging a migration to main does not push it**: a Supabase change is in prod only once its migration has been applied to the prod project ("CellWatch Server"). After each push, record the version in prod's `supabase_migrations.schema_migrations` (version, name, and the file text as `statements`), as was done on 2026-10-31 for every 2026 migration on prod. Its last row is still `20991231235959`, so `supabase db push` refuses to run; don't use `--include-all` either, since it would apply every unrecorded file, including unpushed and uncommitted ones.

The step-by-step procedure, with pre-checks, pausing the cron job, per-migration checks, and rollback notes, is in **`prod-deploy-runbook.md`** at the repo root (a local file, not in git). Its outline:

1. Read-only pre-checks (is the previous migration pushed, current cron job, queue state).
2. Pause `fcc-submission-retries` (`cron.alter_job(..., active := false)`) if the change touches the FCC pipeline or the upload tables, and wait for a run in progress to end (pausing doesn't stop it).
3. `psql -v ON_ERROR_STOP=1 -f supabase/migrations/<file>.sql` for each migration, in order, checking after each one.

A migration that needs a strong lock on a table the app writes to must not wait for it in line: while a lock request waits, every later request on that table waits behind it, so uploads stall and fail at the API's 8 second `lock_timeout`. `202610210001`, `0101` and `0103` take their locks up front with `LOCK TABLE ... NOWAIT` in a retry loop (giving up after a minute) and set `lock_timeout = '3s'` for anything else. If one gives up, nothing has changed; run it again. `db_tests/migration_deploy_test.sql` checks that an upload arriving while one of them waits for a busy table goes straight through.
4. Resume the job and watch one run in `cron.job_run_details`.
5. Refresh `full_schema.sql`, `cron_jobs.sql` and `role_settings.sql` with `supabase/check_drift.sh --write` (on main since #134), and commit them.

Every step that changes prod needs an explicit go-ahead from the owner. Credentials come from the keychain at run time. Never put a password, connection string, project ref, or FCC secret in a migration, a doc, or a commit.

A test project, "CellWatch Test Database", exists in the same Supabase org. On 2026-10-22, before the 2026-10-29 push, its schema was brought up to match prod, with two deliberate differences: `trg_auto_post_fcc_submission` is disabled, and pg_cron and pg_graphql aren't installed, so nothing there posts to the FCC. Its `private.fcc_submission_info` still has a prod row, so don't re-enable the trigger or run the retry function by hand there. #136's migrations passed a rolled-back check on it on 2026-10-22.

## Ordering rule: server first

**A migration that adds a request-body field, a column the app writes, or a new RPC overload must be pushed to prod before any app release that sends it.** (An app change is in prod only once it is in a Play Store release.)

Why:
- supabase-kt 1.0.3 serializes with `ignoreUnknownKeys = true` and `encodeDefaults = false`. Old app builds therefore ignore new columns in responses (adding columns is safe), and null or default fields are **left out** of request bodies (so column defaults and `WHEN (x IS NULL)` triggers still fire).
- A new app build that sends a **non-null field the server doesn't know yet** fails: PostgREST rejects unknown columns, so the insert errors until the migration is pushed.
- PostgREST chooses a function overload from the **argument names** in the JSON body. A new build that calls a new signature (for example a two-argument `register_device`) gets "function not found" until that signature exists on prod.

Current examples:
- The local-port app change (uncommitted on the `client-port-header` branch) sends `tuple_local_port` on every FCC submission. Push `202610130001` (uncommitted on the same branch) to prod before any app release with that change, or FCC submission uploads from that release will fail. The `X-Local-Port` / `X-Transport` headers are harmless without the migration, because unknown headers are ignored.
- Main's app (since #148, merged 2026-10-27) calls a new `register_device(device_id, device_secret)` overload, from `202610220001`. That migration was pushed to prod on 2026-10-31, so a release built from main can register.

## Read-only prod access

For investigation, connect through the Supabase **session pooler** (IPv4, port 5432; the direct host is IPv6-only), as `sync_from_remote.sh` does. Get the password from the keychain only with the owner's go-ahead.

- The pooler **ignores `PGOPTIONS`**, so `-c default_transaction_read_only=on` has no effect. Make the session read-only yourself as the first command:

  ```sql
  set default_transaction_read_only = on;
  -- or: set session characteristics as transaction read only;
  -- or per transaction: begin read only; ... commit;
  ```

  With `psql`: `psql "<conninfo>" -X -c "set default_transaction_read_only = on" -f query.sql`.
- Read-only transactions reject temp tables and temp views; use CTEs.
- The pooler session's timezone is UTC.
- **`app_version` is always `'1.0.0'`** in `measurements` and `fcc_submissions` (the app's `versionName` was never bumped), so it cannot tell builds apart. Use dates, or a different signal.
- Tables with PII: `fcc_submissions` (contact name, email, phone, IMEI). Don't copy query results containing them into issues or docs.
- Don't test RLS on prod by impersonating `anon` or reading `private.devices.hashed_secret`. Reproduce locally instead ([performance-notes](performance-notes.md#how-to-profile)).
