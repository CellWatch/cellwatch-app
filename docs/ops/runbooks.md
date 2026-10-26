# Operations runbooks

Purpose: step-by-step procedures for routine and incident work on the production Supabase project and its FCC posting pipeline.

Related: [Release checklist](release-checklist.md) · [Inquiries and reconciliation](../fcc/inquiries-and-reconciliation.md) · [FCC submission pipeline](../supabase/fcc-submission-pipeline.md) · [Triggers and cron](../supabase/triggers-and-cron.md) · [Migrations and deploy](../supabase/migrations-and-deploy.md) · Prod deploy runbook (`prod-deploy-runbook.md`, local only)

**Placeholders:** `<PROJECT_REF>`, `<POOLER_HOST>` and `<DB_PASSWORD>` stand for real values. Get them from the Supabase dashboard (Connect → Session pooler) and never write them into files in the repo. Every step that writes to prod needs an explicit go-ahead from the project owner.

## Read-only prod SQL

The direct DB host (`db.<PROJECT_REF>.supabase.co`) is **IPv6-only**, so from an IPv4-only network use the **IPv4 session pooler** on port **5432**. The transaction pooler on 6543 also breaks `pg_dump`.

```bash
# The password is read without echo and never stored.
read -rs PGPASSWORD && export PGPASSWORD
psql "host=<POOLER_HOST> port=5432 dbname=postgres user=postgres.<PROJECT_REF> sslmode=require" -X
```

Then make the session read-only **as the first command**:

```sql
SET default_transaction_read_only = on;
-- or wrap each batch in: BEGIN READ ONLY; ... COMMIT;
```

For a script file:

```bash
psql "<conn>" -X -v ON_ERROR_STOP=1 -c "set default_transaction_read_only = on" -f query.sql
```

Gotchas:
- **`PGOPTIONS` is ignored by the pooler.** `PGOPTIONS='-c default_transaction_read_only=on'` silently leaves the session read-write, so always use the in-session `SET`.
- Read-only transactions reject `CREATE TEMP VIEW`/`TABLE`. Use CTEs.
- The pooler session's timezone is **UTC**.
- `app_version` is always `'1.0.0'` (the version name has never been bumped), so you can't use it to tell builds apart. Use `created_on` ranges or fields only newer builds send (for example `tuple_local_port`, once `202610130001`, uncommitted on the `client-port-header` branch, is pushed to prod and an app that sends it is released).
- Don't read `private.devices.hashed_secret` or `vault.decrypted_secrets`, and don't `SET ROLE anon` on prod. To test RLS, use a local copy ([Local setup](../dev/local-setup.md#when-docker-is-unavailable)) or the CellWatch Test Database project (see [Release checklist](release-checklist.md#1-server-push-to-prod) for its cautions).
- Don't `select *` from `public.fcc_submissions` (contact PII).

## Refreshing the local schema snapshot

[`supabase/sync_from_remote.sh`](../../supabase/sync_from_remote.sh) treats prod as the source of truth:

| Phase | Steps | Touches |
|---|---|---|
| A (read remote) | 1. dump roles → `~/cellwatch-remote-dump/roles.sql`; 2. dump schema → `supabase/migrations/<UTC timestamp>_remote_baseline.sql` and copy to [`supabase/full_schema.sql`](../../supabase/full_schema.sql); 3. dump data → `~/cellwatch-remote-dump/data.sql` (mode 600) | Prod read only |
| Safety gate | Aborts if either dump is under 1000 bytes; the CLI can "succeed" with an empty file | none |
| B (local, destructive) | Asks you to type `yes`, then 4. `supabase stop --no-backup` + `supabase start` (meant to get PG17 like prod, but `supabase start` follows `major_version` in [config.toml](../../supabase/config.toml), which is 15 on main; the change to 17 is uncommitted on the `client-port-header` branch); 5. `supabase db reset`; 6. load `data.sql` into the local DB container | Local DB only |

Notes:
- It uses the session pooler with a percent-encoded password. It reads the password from the login keychain item the Supabase CLI created, or prompts silently. The script hard-codes the project directory, project ref and pooler host. > TODO: parameterize them.
- **Phase A also writes a new `*_remote_baseline.sql` into `supabase/migrations/`.** Decide whether to commit it; usually you commit only the refreshed `full_schema.sql`. > TODO: verify step 5. `db reset` applies migrations in version order, so `202607140001` runs before the new baseline and may fail the same way as a fresh reset (see [Local setup](../dev/local-setup.md#local-supabase)).
- `full_schema.sql` doesn't include pg_cron jobs. Refresh [`cron_jobs.sql`](../../supabase/cron_jobs.sql) by hand: `select jobname, schedule, command from cron.job;`.
- Step 3 pulls production rows with PII onto your machine. See [Prod dumps](#handling-prod-dumps).
- Open PR #134 (`board-docs`) adds `supabase/check_drift.sh`, which diffs and refreshes the snapshot without the destructive local rebuild. It isn't on main yet.

## Recreating cron jobs

pg_cron jobs live in `cron.job` rows, not in the schema dump. The single job:

| Job | Schedule | Command (expected) |
|---|---|---|
| `fcc-submission-retries` | `*/2 * * * *` | `select private.process_fcc_submission_retries(10);` |

[`cron_jobs.sql`](../../supabase/cron_jobs.sql) is a 2026-09-30 snapshot and still shows batch size `1`. Migration [`202610070001`](../../supabase/migrations/202610070001_unclog_fcc_retry_queue.sql), which is pushed to prod, re-schedules it with batch `10` where pg_cron is installed. The batch size in prod's `cron.job` row hasn't been verified, so check it with the query below. To recreate:

```sql
-- cron.schedule upserts by job name
select cron.schedule('fcc-submission-retries', '*/2 * * * *',
                     'select private.process_fcc_submission_retries(10);');
```

Check (read-only):

```sql
select jobid, jobname, schedule, command, active from cron.job;
select status, return_message, start_time, end_time
from cron.job_run_details
where jobid = (select jobid from cron.job where jobname = 'fcc-submission-retries')
order by start_time desc limit 5;
```

Pause or resume (write):

```sql
select cron.alter_job(jobid, active := false) from cron.job where jobname = 'fcc-submission-retries';
select cron.alter_job(jobid, active := true)  from cron.job where jobname = 'fcc-submission-retries';
```

Never commit a job command that contains a key or token. In open PR #133 (not pushed to prod), `202610210103` changes the command to `call private.run_fcc_submission_retries(10);`. See `prod-deploy-runbook.md` (local file, not in git) for the fallback if `CALL` can't commit under pg_cron.

## Inspecting and draining a stuck FCC retry queue

How the job behaves with [`202610070001`](../../supabase/migrations/202610070001_unclog_fcc_retry_queue.sql), which is pushed to prod: every 2 minutes it takes up to 10 due `pending` entries (the batch size the job passes; see above) and **stops taking new ones after 60 s**. Entries still waiting for measurements are re-checked every 5 minutes, or hourly once queued for more than a day. Entries with a blank `provider` become `blocked` ("Missing provider") and are never re-checked. A 4xx response other than 408/429, or 6 failed attempts, also blocks an entry.

### 1. Look (read-only)

```sql
-- Is the unclog version live?
select position('60 seconds' in prosrc) > 0 as unclog_live
from pg_proc where proname = 'process_fcc_submission_retries';

-- Queue shape
select state, left(last_error, 60) as reason, count(*),
       min(created_on) as oldest, min(next_attempt_at) as next_due
from private.fcc_submission_retry_queue
group by 1, 2 order by 3 desc;

-- Due now, i.e. the backlog the job still has to work through
select count(*) from private.fcc_submission_retry_queue
where state = 'pending' and next_attempt_at <= now();

-- Posting outcomes in the last day
select succeeded, left(coalesce(error_message, response_snippet), 60) as what, count(*)
from private.fcc_submission_failures
where occurred_on > now() - interval '1 day'
group by 1, 2 order by 3 desc;

-- Unsent submissions that were never queued (inserted before the trigger existed)
select count(*) from public.fcc_submissions s
where not coalesce(s.submitted, false)
  and not exists (select 1 from private.fcc_submission_retry_queue q where q.submission_id = s.id);
```

Also check the cron history above. Each run can take long, because building one payload through `private.fcc_submissions_view` scans every location and cell row. That has caused `statement_timeout` rollbacks **after** the BDC had accepted a POST, which later produces `duplicate_test_id`. The fix (`202610210101`) is in open PR #133, not pushed to prod; see [Performance notes](../supabase/performance-notes.md).

### 2. Fix the cause first

| Reason | Fix |
|---|---|
| `Missing provider` | Backfill `fcc_submissions.provider` (`202610210102` in open PR #133 derives it from SIM codes once pushed to prod), then re-pend. |
| Waiting for data | Nothing on the server: the phone has to upload. See [A device that never uploads](#a-device-that-never-uploads). |
| BDC 4xx (`null_properties`, `null_net_mobile_country_code`, …) | A data problem. Re-posting the same payload fails again. Decide with the FCC first. |
| `duplicate_test_id` | The BDC already has the test. On prod (and main) it stays a failure; the pipeline in open PR #133 records it as sent once pushed to prod. |
| 5xx / transport errors | Retried automatically. Blocked only after 6 attempts. |

### 3. Drain or re-queue (prod writes that post to the FCC; explicit approval required)

```sql
-- \set ids '{<uuid>,<uuid>}'
-- Re-pend specific blocked entries after fixing their cause
update private.fcc_submission_retry_queue
set state = 'pending', attempt_count = 0, next_attempt_at = now(), updated_on = now()
where submission_id = any(:'ids'::uuid[]);

-- Queue unsent submissions that were never queued
insert into private.fcc_submission_retry_queue (submission_id, fcc_env, state, next_attempt_at)
select s.id, 'prod', 'pending', now()
from public.fcc_submissions s
where not coalesce(s.submitted, false) and s.id = any(:'ids'::uuid[])
on conflict (submission_id) do nothing;
```

Let the cron job post the entries. Avoid calling `private.process_fcc_submission_retries(...)` by hand: it posts to the BDC immediately and runs in your session's transaction and timeout. Watch `cron.job_run_details` and the failures table for a few runs afterwards.

## A device that never uploads

Use read-only queries with `\set device_id '…'`:

```sql
-- Registered?
select exists (select 1 from private.devices where id = :'device_id') as registered;
-- Anything received, and when last?
select max(created_on) as last_received, max(timestamp) as last_test, count(*)
from public.measurements where device_id = :'device_id';
select count(*), max(created_on) from public.fcc_submissions where device_id = :'device_id';
```

| Finding | Likely cause | What to do |
|---|---|---|
| Not registered, or registered with nothing ever received | `register_device` lockout: the phone lost the first response and every retry gets "already registered". | Not fixed on prod or main. Migration `202610220001` on the unpushed local branch `register-device-retry` makes registration retry-safe and lets a device with no uploads re-register. Once pushed to prod, it also rescues phones on the current Play build without an app update. Push it **before** releasing an app that uses the two-argument `register_device`. |
| Registered, last upload long ago, user still testing | Uploads only run after a finished test or on a cold start (on main: `UploadWorker.enqueue` in `MeasureViewModel` / `MapActivity`). The 10 s client timeout plus slow upload RLS on devices with long histories make each attempt fail. | Server: push the RLS fix `202610210001` (open PR #132) to prod. App: hourly and foreground uploads with 60 s timeouts are on the unpushed local branch `background-upload`, so they need a merge and a release. Meanwhile, ask the user to open the app on a good connection. |
| Submissions without measurements | App builds without #111 uploaded the submission row first. #111 is merged to main (2026-10-07) but not known to be released, so the current Play build may still do this. | Results are still on the phone, or lost if the app was uninstalled. |

Crashlytics non-fatals carry the device id as a custom key (`device_id`), which helps match server state with client errors.

## Handling prod dumps

- `data.sql` from `sync_from_remote.sh` contains **contact PII** (name, email, phone in `fcc_submissions`), device ids and precise locations.
- Keep dumps **outside the repo** (the script uses `~/cellwatch-remote-dump`, mode 700/600). Never copy rows into `supabase/seed.sql` (tracked), tests, issues, PRs or docs.
- When you analyse a dump, **print IDs, counts and response text only**, never whole rows. The dump uses `INSERT … VALUES` with one row per line, so `grep`/`awk` counts work without loading it.
- Keep derived per-user files (reconciliation CSVs) next to the dump, not in the repo.
- Delete dumps you no longer need.
