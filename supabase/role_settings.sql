-- Per-role timeouts on the remote project (CellWatch Server, xepxxvpbexkyxrwtrgqv). Schema dumps
-- (supabase/full_schema.sql) leave role settings out, as they do cron jobs (cron_jobs.sql).
-- check_drift.sh compares the *_timeout settings of anon, authenticated, authenticator, service_role
-- and postgres against the statements below.
-- Pulled from the remote with `check_drift.sh --write`. anon's and authenticated's 2min were hand
-- changes, made at an unrecorded date (the stock supabase/postgres image sets 3s and 8s); the
-- migrations now set both (anon in 202609300001, authenticated in 202611010003). The
-- authenticator values are Supabase defaults, also set in 202609300001. service_role has none
-- of its own, so API calls made with its key get authenticator's 8s. The version written on
-- 2026-10-21 came from the stock image and wrongly listed authenticated at 8s.
-- Updated by check_drift.sh --write on 2026-11-01.

alter role anon set statement_timeout = '2min';
alter role authenticated set statement_timeout = '2min';
alter role authenticator set lock_timeout = '8s';
alter role authenticator set statement_timeout = '8s';
