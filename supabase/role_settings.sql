-- Per-role timeouts on the remote project (CellWatch Server, xepxxvpbexkyxrwtrgqv). Schema dumps
-- (supabase/full_schema.sql) leave role settings out, as they do cron jobs (cron_jobs.sql).
-- check_drift.sh compares the *_timeout settings of anon, authenticated, authenticator, service_role
-- and postgres against the statements below.
-- Written on 2026-10-21 from #121 and the stock supabase/postgres 17.6.1.084 image, not pulled from
-- the remote. Only anon's value is a hand change (the image default is 3s); the other three are
-- Supabase defaults. If the first check_drift.sh run shows a difference, `check_drift.sh --write`
-- replaces this list with the remote's.
alter role anon set statement_timeout = '2min';
alter role authenticated set statement_timeout = '8s';
alter role authenticator set lock_timeout = '8s';
alter role authenticator set statement_timeout = '8s';
