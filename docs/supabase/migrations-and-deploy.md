# Migrations and deploying schema changes

How schema changes are written, tested, applied locally, and pushed to prod, and the rules that keep the app and server in step.

**Related:** [schema](schema.md) · [triggers-and-cron](triggers-and-cron.md) · [performance-notes](performance-notes.md) · [local-setup](../dev/local-setup.md) · [testing](../dev/testing.md) · [runbooks](../ops/runbooks.md) · [release-checklist](../ops/release-checklist.md) · prod deploy runbook (`prod-deploy-runbook.md`, local only)

## The files under `supabase/`

| File | What it is | Authoritative for |
|---|---|---|
| [migrations/](../../supabase/migrations/) | Forward-only SQL changes, named `YYYYMMDDNNNN_description.sql`. They use `create or replace` / `if not exists`, so there are no down migrations. Each file is one `begin; … commit;` transaction | Changes made since the snapshot |
| [full_schema.sql](../../supabase/full_schema.sql) | `supabase db dump` of prod (schema only, no data). Last refreshed 2026-10-07 (commit "Snapshot live Supabase schema and cron jobs") | Prod's schema, together with `202610070001`: a read-only catalog check on 2026-10-22 found prod matches the two exactly (apart from Postgres 17's `MAINTAIN` bit in table grants) |
| [cron_jobs.sql](../../supabase/cron_jobs.sql) | `cron.schedule(...)` calls that recreate the pg_cron jobs. Jobs are rows in `cron.job` and are **not** in schema dumps | The jobs as pulled on 2026-09-30, before `202610070001` changed the batch size to 10 (prod's job was not checked on 2026-10-22) |
| [post_fcc_submission_func.sql](../../supabase/post_fcc_submission_func.sql) | Original setup script for the FCC view, `fcc_submission_info`, and posting functions | History only; partly superseded. **Do not re-run** (it would bring back the synchronous auto-post trigger) |
| [seed.sql](../../supabase/seed.sql) | Empty. Real data must never go here (the file is tracked) | |
| [sync_from_remote.sh](../../supabase/sync_from_remote.sh) | Rebuilds the local stack from prod; see below | |
| [db_tests/](../../supabase/db_tests/) | SQL tests for individual migrations, run in a throwaway container | |
| [config.toml](../../supabase/config.toml) | Local CLI stack config | |

Migrations in `supabase/migrations/` (the first two are on main; #136 adds the last two):

| Migration | Status |
|---|---|
| `202607140001_repair_fcc_submission_functions.sql` | On main; superseded on prod. Its `fcc_submission_is_ready` and `post_fcc_submission` have the same text as prod's (`full_schema.sql`) apart from line endings (prod's copies have CRLF endings). Its `auto_post_fcc_submission` has since been replaced on prod by the queue version, so **do not re-apply** |
| `202610070001_unclog_fcc_retry_queue.sql` | On main; **pushed to prod: prod's `process_fcc_submission_retries` matches this migration's version (catalog hash match, 2026-10-22). The cron job's batch size of 10 was not checked**; check it with runbook Step 0 |
| `202610220101_fix_dashboard_rpcs.sql` | **In #136; not pushed to prod.** Fixes `rpc_group_detail`'s filter, requires `x-dashboard-secret` on the three dashboard RPCs, reports latency in ms. **Before pushing it, set `DASHBOARD_SECRET` on the dashboard backend** (CellWatch/demo-dashboard, Flask on Render; it sends `x-dashboard-secret` on `rpc_map_points` and `rpc_group_detail` and doesn't call `rpc_group_summaries`), or its map stops loading. Passed a rolled-back check on the test project on 2026-10-22 |
| `202610220102_tighten_database_helpers.sql` | **In #136; not pushed to prod.** Drops `log_user_data`, fixes `get_raw_header(item)`, pins `search_path` on the device/dashboard checks, revokes `TRUNCATE` from `anon`/`authenticated`. Passed a rolled-back check on the test project on 2026-10-22 |

Migrations that are not on main. None of them is pushed to prod:

| Migration | Where |
|---|---|
| `202609300001_track_live_fcc_queue_and_role_settings.sql` | Open PR #134. Records the queue table, queue-only trigger, and role settings that were made by hand on prod |
| `202610210001_fast_upload_rls.sql` | Open PR #132. Upload RLS fix and `measurements(device_id)` index ([performance-notes](performance-notes.md#1-upload-cost-grows-with-device-history)) |
| `202610210101`, `0103`, `0104` | Open PR #133. Per-submission FCC payload and `measurements(group_id)` index, queue processing, queue health (no `0102`: blank carrier names are not filled in) |
| `202610130001_record_local_ports.sql` | Uncommitted on the `client-port-header` branch, with its test `db_tests/local_ports_test.sql` |
| `202610220001_retry_safe_device_registration.sql` | Unpushed local branch `register-device-retry`. Must be pushed to prod before any app release that includes the matching app change |

## Local stack

From [config.toml](../../supabase/config.toml): API on `54321`, Postgres on `54322`, Studio on `54323`, Inbucket on `54324`. The REST API exposes `public`, `storage`, `graphql_public`. On main, `[db].major_version` is still `15`, but prod runs Postgres 17; set it to `17` before `supabase start` (that change is uncommitted on the `client-port-header` branch). `sync_from_remote.sh` assumes the local stack runs 17.

```bash
supabase start        # bring up the local stack (Docker)
supabase db reset     # drop the local DB, apply supabase/migrations/* in order, then seed.sql
```

> **Caveat:** main has **no baseline migration**. `migrations/` holds only incremental changes that assume the live tables exist. `202607140001` creates a SQL-language function that references `public.measurements`, so `supabase db reset` on a fresh database fails. The runbook calls #134's `202609300001` a baseline, but it only records the hand-made queue table, trigger, and role settings; it doesn't create the `public` tables and sorts after `202607140001`, so it doesn't fix this. Until a full baseline lands, choose one:
> - run `sync_from_remote.sh`. It writes a timestamped `*_remote_baseline.sql` into `migrations/`, which is untracked, so don't commit it by accident.
> - load `full_schema.sql` into an empty database first, then apply the migrations by hand.
>
> See [local-setup](../dev/local-setup.md). > TODO: verify once the baseline is merged.

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
- Each test creates **minimal copies** of the tables it needs, adds stand-ins for anything external (for example a fake `post_fcc_submission`), then loads the migration under test with `\i /migrations/<file>.sql`. In `fcc_retry_queue_test.sql`, assertions are `perform expect(<bool>, '<description>')`, which raise `FAILED: …` on failure and print `ok - …` on success; the other tests raise an exception on failure and print `ok: …` on success.
- Kept out of `supabase/tests/` on purpose: `supabase test db` expects pgTAP there and runs against the local stack.

| Test | Covers |
|---|---|
| `fcc_retry_queue_test.sql` | `202610070001`: waiting entries (5 min / 1 h recheck, not counted as attempts), batch of 10, cap at 10, blank provider → blocked, already-sent entries removed, 400 → blocked, 503 → retried in 60 s, 60 s per-run cap, logging |
| `local_ports_test.sql` (not on main; uncommitted on `client-port-header`) | `202610130001`: header parsing, malformed values → null, `tuple_local_port` range check, `source_port_comparison` counts |
| `dashboard_rpcs_test.sql` | `202610220101` (#136): detail returns only its group, all three refuse without the dashboard secret, latency in ms, a caller's temporary types don't run as their owner |
| `database_helpers_test.sql` | `202610220102` (#136): `log_user_data` dropped, `get_raw_header(item)`, pinned `search_path`, no `TRUNCATE` for app roles |

App-side checks are in [testing](../dev/testing.md).

## Deploying to prod

Migrations are pushed to prod **by hand with `psql`**, one migration file at a time, through the session pooler. There is no CI deploy, and the `supabase db push` command is not used. **Merging a migration to main does not push it**: a Supabase change is in prod only once its migration has been applied to the prod project ("CellWatch Server"). The runbook checks whether a `supabase_migrations.schema_migrations` history table exists at all.

The step-by-step procedure, with pre-checks, pausing the cron job, per-migration checks, and rollback notes, is in **`prod-deploy-runbook.md`** at the repo root (a local file, not in git). Its outline:

1. Read-only pre-checks (is the previous migration pushed, current cron job, queue state).
2. Pause `fcc-submission-retries` (`cron.alter_job(..., active := false)`) if the change touches the FCC pipeline.
3. `psql -v ON_ERROR_STOP=1 -f supabase/migrations/<file>.sql` for each migration, in order, checking after each one.
4. Resume the job and watch one run in `cron.job_run_details`.
5. Refresh `full_schema.sql` and `cron_jobs.sql` and commit them.

Every step that changes prod needs an explicit go-ahead from the owner. Credentials come from the keychain at run time. Never put a password, connection string, project ref, or FCC secret in a migration, a doc, or a commit.

A test project, "CellWatch Test Database", exists in the same Supabase org. On 2026-10-22 its schema was brought up to match prod, with two deliberate differences: `trg_auto_post_fcc_submission` is disabled, and pg_cron and pg_graphql aren't installed, so nothing there posts to the FCC. Its `private.fcc_submission_info` still has a prod row, so don't re-enable the trigger or run the retry function by hand there. #136's migrations passed a rolled-back check on it on 2026-10-22.

## Ordering rule: server first

**A migration that adds a request-body field, a column the app writes, or a new RPC overload must be pushed to prod before any app release that sends it.** (An app change is in prod only once it is in a Play Store release.)

Why:
- supabase-kt 1.0.3 serializes with `ignoreUnknownKeys = true` and `encodeDefaults = false`. Old app builds therefore ignore new columns in responses (adding columns is safe), and null or default fields are **left out** of request bodies (so column defaults and `WHEN (x IS NULL)` triggers still fire).
- A new app build that sends a **non-null field the server doesn't know yet** fails: PostgREST rejects unknown columns, so the insert errors until the migration is pushed.
- PostgREST chooses a function overload from the **argument names** in the JSON body. A new build that calls a new signature (for example a two-argument `register_device`) gets "function not found" until that signature exists on prod.

Current examples:
- The local-port app change (uncommitted on the `client-port-header` branch) sends `tuple_local_port` on every FCC submission. Push `202610130001` (uncommitted on the same branch) to prod before any app release with that change, or FCC submission uploads from that release will fail. The `X-Local-Port` / `X-Transport` headers are harmless without the migration, because unknown headers are ignored.
- The `register-device-retry` branch (local, unpushed) calls a new `register_device(device_id, device_secret)` overload. Push its `202610220001` to prod before any app release that includes it.

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
