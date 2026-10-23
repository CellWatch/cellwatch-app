# Triggers and pg_cron jobs

Every trigger on CellWatch tables and every scheduled pg_cron job: when each one fires and what it does.

**Related:** [schema](schema.md) · [rpc-reference](rpc-reference.md) · [fcc-submission-pipeline](fcc-submission-pipeline.md) · [migrations-and-deploy](migrations-and-deploy.md) · [runbooks](../ops/runbooks.md)

Sources: [full_schema.sql](../../supabase/full_schema.sql) (triggers), [cron_jobs.sql](../../supabase/cron_jobs.sql) (jobs), [migrations/](../../supabase/migrations/).

## Triggers

Postgres fires triggers with the same timing and event **in name order**, which is why the names carry `_100_`, `_200_`, `_300_` prefixes. All are `FOR EACH ROW`.

### `public` tables

| Table | Trigger | Timing | Condition | Function | What it does |
|---|---|---|---|---|---|
| `measurements` | `_100_measurement_set_updated_on` | BEFORE UPDATE | | `set_updated_on()` | `updated_on = now()` |
| `measurements` | `_100_cell_set_updated_on` | BEFORE UPDATE | | `set_updated_on()` | Same. Attached to `measurements`, not `cells` (likely a mistake) |
| `measurements` | `_100_challenge_data_set_updated_on` | BEFORE UPDATE | | `set_updated_on()` | Same (legacy name) |
| `measurements` | `_200_measurement_force_created_on` | BEFORE INSERT | `new.created_on IS NULL` | `force_created_on()` | `created_on = updated_on = now()` |
| `upload_download_data` | `_100_upload_download_data_set_updated_on` | BEFORE UPDATE | | `set_updated_on()` | |
| `upload_download_data` | `_200_upload_download_data_force_created_on` | BEFORE INSERT | `created_on IS NULL` | `force_created_on()` | |
| `latency_data` | `_100_latency_data_set_updated_on` | BEFORE UPDATE | | `set_updated_on()` | |
| `latency_data` | `_200_latency_data_force_created_on` | BEFORE INSERT | `created_on IS NULL` | `force_created_on()` | |
| `latency_data` | `fix_jitter_pre_100alpha5` | BEFORE INSERT | | `fix_jitter()` | Replaces `jitter` with `sqrt(jitter)` when the parent measurement's `app_version` is null or `1.0.0-alpha.2/3/4`. Those builds apparently sent a squared value. With `app_version` always `1.0.0` today, it no longer changes anything |
| `locations` | `_100_location_set_updated_on` | BEFORE UPDATE | | `set_updated_on()` | |
| `locations` | `_200_location_force_created_on` | BEFORE INSERT | `created_on IS NULL` | `force_created_on()` | |
| `cells` | `_200_cell_force_created_on` | BEFORE INSERT | `created_on IS NULL` | `force_created_on()` | (No `set_updated_on` trigger on `cells`) |
| `fcc_submissions` | `_100_fcc_submission_set_updated_on` | BEFORE UPDATE | | `set_updated_on()` | |
| `fcc_submissions` | `_200_fcc_submission_force_created_on` | BEFORE INSERT | `created_on IS NULL` | `force_created_on()` | |
| `fcc_submissions` | `_300_fcc_submission_update_source_ip` | BEFORE INSERT | `new.source_ip IS NULL` | `fcc_submission_update_source_ip()` | Sets `source_ip` to the first entry of `x-forwarded-for`. That entry can be set by the client; `cf-connecting-ip` is the trustworthy header. The app normally sends `source_ip` from the tuple service, so this rarely fires |
| `fcc_submissions` | `_310_fcc_submission_record_local_ports` | BEFORE INSERT | | `fcc_submission_record_local_ports()` | **Pending, not deployed** (`202610130001`). Stores `X-Local-Port` → `upload_local_port`, `X-Transport` → `upload_transport`, `cf-connecting-ip` → `upload_ip`. Checks each value's format and stores null rather than failing the insert. Range-checks `tuple_local_port` |
| `fcc_submissions` | `trg_auto_post_fcc_submission` | AFTER INSERT | | `private.auto_post_fcc_submission()` | If `submitted` is false, upserts a `pending` entry into `private.fcc_submission_retry_queue` (`fcc_env = 'prod'`, due now). It does **not** call the FCC itself. See [fcc-submission-pipeline](fcc-submission-pipeline.md) |

Why the `_200` triggers matter: `insert_measurement` inserts whole composite rows, and supabase-kt leaves null fields out of the JSON. So `created_on` arrives as an explicit null and the column default never applies. The trigger fills it before the `NOT NULL` check.

Order on a single `fcc_submissions` insert: `_200` → `_300` → (`_310`, pending) → row stored → `trg_auto_post_fcc_submission`.

### `published` tables

| Table | Triggers |
|---|---|
| `published.measurements` | `_100_published_measurement_set_updated_on` (BEFORE UPDATE), `_200_published_measurement_force_created_on` (BEFORE INSERT, **unconditional**) |
| `published.cells` | `_100_published_cell_set_updated_on`, `_200_published_cell_force_created_on` |
| `published.latency_data` | `_100_published_latency_set_updated_on`, `_200_published_latency_force_created_on` |
| `published.throughput_data` | `_100_published_throughput_set_updated_on`, `_200_published_throughput_force_created_on` |

The `published` insert triggers always overwrite `created_on` / `updated_on` with the publish time.

### Trigger history: the FCC auto-post

| Version | Where | Behaviour |
|---|---|---|
| Original | [post_fcc_submission_func.sql](../../supabase/post_fcc_submission_func.sql) | Called `post_fcc_submission(new.id, 'prod')` **inside the app's INSERT** and logged the outcome to `fcc_submission_failures` |
| 2026-07-14 | [202607140001](../../supabase/migrations/202607140001_repair_fcc_submission_functions.sql) | Same synchronous design, with readiness and payload checks added |
| Live snapshot | [full_schema.sql](../../supabase/full_schema.sql) | Only queues the submission; the cron job posts it. This change is not in any migration in this checkout |

> Warning: re-running `202607140001` or `post_fcc_submission_func.sql` against prod would put back the synchronous trigger and undo the queue design.

## pg_cron jobs

Jobs are rows in `cron.job`, so **schema dumps (`full_schema.sql`) do not include them**. [cron_jobs.sql](../../supabase/cron_jobs.sql) is the source of truth for recreating them. It was pulled from the remote on 2026-09-30. Never commit a job command that contains a key or token.

| Job name | Schedule | Command | Runs as | Purpose |
|---|---|---|---|---|
| `fcc-submission-retries` | `*/2 * * * *` (every 2 min) | `cron_jobs.sql`: `select private.process_fcc_submission_retries(1);` | `postgres`, database `postgres` | Posts queued FCC submissions, with retry and backoff |
| | | After [202610070001](../../supabase/migrations/202610070001_unclog_fcc_retry_queue.sql): `select private.process_fcc_submission_retries(10);` | | Same, up to 10 entries per run |

Notes:
- `202610070001` reschedules the job with `cron.schedule('fcc-submission-retries', ...)`, which updates the job of that name in place. It skips this step when `pg_cron` is not installed (for example a fresh local DB).
- `202610070001` is live on prod: prod's `process_fcc_submission_retries` matches this migration's version (catalog hash match, 2026-10-22). The cron job's batch size of 10 was not checked. The prod deploy runbook (`prod-deploy-runbook.md`, a local file not in git) Step 0 checks both (the function source contains `60 seconds`, and the job command uses `(10)`). `cron_jobs.sql` predates it. After any job change, refresh `cron_jobs.sql`.
- A job's whole command runs in **one transaction**. If a run is cancelled (for example by `statement_timeout`), every database change from that run rolls back. FCC POSTs already sent are not undone, so a submission can be accepted by the FCC and still look unsent locally. PL/pgSQL `exception when others` does not catch `query_canceled`.
- Run history is in `cron.job_run_details` (`status`, `return_message`, `start_time`, `end_time`).

Useful read-only checks:

```sql
select jobid, jobname, schedule, command, active from cron.job;

select status, return_message, start_time, end_time - start_time as took
from cron.job_run_details
where jobid = (select jobid from cron.job where jobname = 'fcc-submission-retries')
order by start_time desc limit 10;
```

To pause or resume the job, see the runbook (`cron.alter_job(jobid, active := false|true)`).
