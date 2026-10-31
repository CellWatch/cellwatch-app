-- pg_cron jobs on the remote project (CellWatch Server, xepxxvpbexkyxrwtrgqv), as the migrations
-- in supabase/migrations/ leave them. Schema dumps (supabase/full_schema.sql) do not include
-- these, because jobs are rows in cron.job; run this file to recreate them.
-- supabase/check_drift.sh compares it with the live jobs, and --write refreshes it from them.
-- Never commit a job command that contains a key or token.
--
-- 202610210103 (pushed 2026-10-29) runs fcc-submission-retries as a CALL of the procedure,
-- which commits after each post. Before that the job ran
-- `select private.process_fcc_submission_retries(10);` (one transaction per run).
-- The job runs as postgres in database postgres.
-- Updated by check_drift.sh --write on 2026-10-31.

select cron.schedule('fcc-submission-retries', '*/2 * * * *', 'call private.run_fcc_submission_retries(10);');
