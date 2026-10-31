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
| B (local, destructive) | Asks you to type `yes`, then 4. `supabase stop --no-backup` + `supabase start` (to get PG17 like prod: `supabase start` follows `major_version` in [config.toml](../../supabase/config.toml), which is 17); 5. `supabase db reset`; 6. load `data.sql` into the local DB container | Local DB only |

Notes:
- It uses the session pooler with a percent-encoded password. It reads the password from the login keychain item the Supabase CLI created, or prompts silently. The script hard-codes the project directory, project ref and pooler host. > TODO: parameterize them.
- **Phase A also writes a new `*_remote_baseline.sql` into `supabase/migrations/`.** Decide whether to commit it; usually you commit only the refreshed `full_schema.sql`. > TODO: verify step 5. `db reset` applies migrations in version order, so `202607140001` runs before the new baseline and may fail the same way as a fresh reset (see [Local setup](../dev/local-setup.md#local-supabase)).
- `full_schema.sql` doesn't include pg_cron jobs. Refresh [`cron_jobs.sql`](../../supabase/cron_jobs.sql) by hand: `select jobname, schedule, command from cron.job;`.
- Step 3 pulls production rows with PII onto your machine. See [Prod dumps](#handling-prod-dumps).
- [`supabase/check_drift.sh`](../../supabase/check_drift.sh) (merged to `main` in #134, 2026-10-27) compares prod with the snapshot files without the destructive local rebuild: the schema with `full_schema.sql`, `cron.job` with `cron_jobs.sql`, and role timeouts with [`role_settings.sql`](../../supabase/role_settings.sql). It only reads prod. Exit 0 means no drift, 1 drift (diffs printed), 2 couldn't check. `--write` also overwrites the three files with prod's state, for review before committing. It was first run against prod on 2026-10-31 (#152, after the 2026-10-29 push), and again after each later push (#153, #155, #157, #158).

## Recreating cron jobs

pg_cron jobs live in `cron.job` rows, not in the schema dump. The jobs:

| Job | Schedule | Command (expected) |
|---|---|---|
| `fcc-submission-retries` | `*/2 * * * *` | `call private.run_fcc_submission_retries(10);`, on prod since 2026-10-29 ([`202610210103`](../../supabase/migrations/202610210103_fcc_retry_queue_processing.sql), #133). Before that: `select private.process_fcc_submission_retries(10);` |
| `fcc-queue-digest` | `1 13 * * *` | `select private.send_fcc_queue_digest();`, on prod since 2026-10-31 ([`202610310001`](../../supabase/migrations/202610310001_email_fcc_queue_digest.sql), #154) |

[`cron_jobs.sql`](../../supabase/cron_jobs.sql) has both jobs as pulled from prod on 2026-11-01; `fcc-queue-digest` emails the queue digest ([triggers-and-cron](../supabase/triggers-and-cron.md#pg_cron-jobs)). The rest of this section is about `fcc-submission-retries`. Recreate it with the `call` wherever `private.run_fcc_submission_retries` exists (prod since 2026-10-29), and the old `select` only where it doesn't. The old function runs the whole batch in one transaction, so putting it back on prod would bring back the lost-record problem (#117).

```sql
-- cron.schedule upserts by job name
select cron.schedule('fcc-submission-retries', '*/2 * * * *',
                     case when exists (select 1 from pg_proc where proname = 'run_fcc_submission_retries')
                          then 'call private.run_fcc_submission_retries(10);'
                          else 'select private.process_fcc_submission_retries(10);' end);
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

Never commit a job command that contains a key or token. `202610210103` (#133, pushed 2026-10-29) changed the command to `call private.run_fcc_submission_retries(10);`, a procedure that commits after each entry; its runs have succeeded since. If they ever fail with "invalid transaction termination", set the command back to `select private.process_fcc_submission_retries(10);`. See `prod-deploy-runbook.md` (local file, not in git) for the steps.

## Inspecting and draining a stuck FCC retry queue

How the job behaves on prod since 2026-10-29 ([`202610210103`](../../supabase/migrations/202610210103_fcc_retry_queue_processing.sql), #133; details in the [pipeline doc](../supabase/fcc-submission-pipeline.md#3-retry-processing-the-cron-job)): every 2 minutes it takes up to 10 due `pending` entries, **stops taking new ones after 60 s**, and commits after each entry.
- Readiness is checked first. Entries still waiting for measurements are re-checked every 5 minutes, or hourly once queued for more than a day. After 30 days they become `expired`, and each run puts them back in line once their measurements are complete.
- Complete entries the BDC is known to reject, or that fall short of the spec, become `held` (`No network registration`, `No locations`, `One location`, `Missing provider`). Held entries are checked again daily.
- A `duplicate_test_id` answer marks the submission sent.
- 400, 409, 413 and 422 block an entry at once. Any other failure is retried with backoff, blocks the entry after 6 attempts, and ends the run.
- `202610210104` adds `private.fcc_queue_health`, `private.fcc_queue_attention` and `private.fcc_queue_digest()` for step 1 ([pipeline → operating it](../supabase/fcc-submission-pipeline.md#operating-it)).
- Since 2026-10-31 the daily job `fcc-queue-digest` emails that digest when something is new, and at least weekly (`202610310001`). Its runs are in `private.fcc_digest_runs`; if no email came for more than a week, check that table and the job's runs in `cron.job_run_details`.

Before 2026-10-29, prod ran [`202610070001`](../../supabase/migrations/202610070001_unclog_fcc_retry_queue.sql)'s version: a blank `provider` was `blocked` ("Missing provider") before the readiness check and never re-checked, and a 4xx other than 408/429 blocked the entry at once. Those 89 entries are still `blocked`. Since 2026-11-01 they are listed in `private.fcc_queue_ignored` (`202610310002`), so `fcc_queue_attention` and the digest leave them out; `fcc_queue_health` and the queue-shape query below still count them.

### 1. Look (read-only)

```sql
-- Is the per-entry-commit version live? (true on prod since 2026-10-29)
select exists (select 1 from pg_proc where proname = 'run_fcc_submission_retries') as per_entry_commits;

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

Also check the cron history above. Before 2026-10-29 a run could take long, because building one payload through `private.fcc_submissions_view` scanned every location and cell row. That caused `statement_timeout` rollbacks **after** the BDC had accepted a POST, which later produced `duplicate_test_id`. `202610210101` (#133), pushed on 2026-10-29, builds each payload from its own submission's rows, and runs since then have taken under a second; see [Performance notes](../supabase/performance-notes.md).

### 2. Fix the cause first

| Reason | Fix |
|---|---|
| `Missing provider` | Blank carrier names are not filled in (decided 2026-10-27); a fix belongs in the app. Since 2026-10-29 new ones are `held` and re-checked daily, so they stop showing up as needing a person; entries blocked before that stay blocked. The app on `main` sends the network operator name, as the released build does (#148 reverted c51980e's SIM-name fallback); a SIM-first fallback is on the local branch `measurement-process-changes`, not in a PR. |
| Waiting for data | Nothing on the server: the phone has to upload. See [A device that never uploads](#a-device-that-never-uploads). |
| `expired` | Since 2026-10-29: still waiting for data 30 days after queueing. Each run puts it back in line once the phone uploads the measurements. |
| `held` | Since 2026-10-29: waiting on the FCC (no network codes, no locations, one location) or on an app decision (`Missing provider`). When the FCC answers, change `private.fcc_payload_hold_reason` to match. The job checks each held entry again daily and releases those no reason applies to any more, or all at once with `select private.recheck_held_fcc_submission_retries(true);`. |
| BDC 4xx (`null_properties`, `null_net_mobile_country_code`, …) | A data problem. Re-posting the same payload fails again. Decide with the FCC first. |
| `duplicate_test_id` | The BDC already has the test. Since 2026-10-29 the job records it as sent, with `submitted_on` null because the earlier post's time is unknown. Entries blocked for it before then stay blocked. |
| 5xx / transport errors | Retried automatically. Blocked only after 6 attempts. |
| 401 / 403 / 404 | The FCC refused the request, not the test: check the hash value in Vault and the URL in `private.fcc_submission_info`. Since 2026-10-29 these are retried like 5xx, and each run ends at the first failure that isn't a rejection, so a bad key or URL costs one request per run instead of blocking every ready entry. Before then they blocked the entry right away. |

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

Let the cron job post the entries. Avoid calling `private.process_fcc_submission_retries(...)` by hand: it posts to the BDC immediately and runs the whole batch in your session's transaction and timeout. If you must post by hand, use `call private.run_fcc_submission_retries(1);` from `psql`, not the SQL editor: it commits after each entry and refuses to start inside a transaction block. Watch `cron.job_run_details` and the failures table for a few runs afterwards.

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
| Not registered, or registered with nothing ever received | `register_device` lockout: the phone lost the first response and every retry gets "already registered". | Fixed on prod since 2026-10-31: migration `202610220001` (#148) makes registration retry-safe and lets a device with no uploads re-register, which rescues phones on the current Play build without an app update. Ask the user to open the app on a good connection and check that the device starts uploading. The app side (a client-made secret, retried until confirmed) is merged to `main` and not released. |
| Registered, last upload long ago, user still testing | On the Play build, uploads run only after a finished test or on a cold start (`MapActivity`), and give up after 10 s. Until 2026-10-29, slow upload RLS on devices with long histories made each attempt fail. | Server: done. The RLS fix `202610210001` (#132) was pushed on 2026-10-29, and backlogged uploads started arriving right after. App: hourly and foreground uploads with 60 s timeouts are merged to `main` in #148 (2026-10-27) and not released, so they need a release. Meanwhile, ask the user to open the app on a good connection. |
| Submissions without measurements | App builds without #111 uploaded the submission row first. #111 is merged to main (2026-10-07) but not known to be released, so the current Play build may still do this. | Results are still on the phone, or lost if the app was uninstalled. |

Crashlytics non-fatals carry the device id as a custom key (`device_id`), which helps match server state with client errors.

## Handling prod dumps

- `data.sql` from `sync_from_remote.sh` contains **contact PII** (name, email, phone in `fcc_submissions`), device ids and precise locations.
- Keep dumps **outside the repo** (the script uses `~/cellwatch-remote-dump`, mode 700/600). Never copy rows into `supabase/seed.sql` (tracked), tests, issues, PRs or docs.
- When you analyse a dump, **print IDs, counts and response text only**, never whole rows. The dump uses `INSERT … VALUES` with one row per line, so `grep`/`awk` counts work without loading it.
- Keep derived per-user files (reconciliation CSVs) next to the dump, not in the repo.
- Delete dumps you no longer need.
