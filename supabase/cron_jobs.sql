-- pg_cron jobs on the remote project (CellWatch Server, xepxxvpbexkyxrwtrgqv).
-- Schema dumps (supabase/full_schema.sql) do not include these, because jobs are
-- rows in cron.job. Pulled from the remote on 2026-09-30; run this file to
-- recreate them. Never commit a job command that contains a key or token.

-- job 1: runs as postgres in database postgres
select cron.schedule('fcc-submission-retries', '*/2 * * * *', 'select private.process_fcc_submission_retries(1);');
