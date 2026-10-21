-- Tests for 202609300001_track_live_fcc_queue_and_role_settings.sql, run by supabase/db_tests/run.sh
-- in a throwaway container. The migration has to create the live-only objects on a fresh database,
-- and change nothing when it runs again where they already exist (as on the live project).
\set ON_ERROR_STOP on
set client_min_messages = notice;

-- Minimal copy of the table the trigger hangs off (columns as in the live schema).
create schema if not exists private;

create table public.fcc_submissions (
    id uuid primary key,
    submitted boolean default false
);

create function expect(ok boolean, description text) returns void
language plpgsql as $$
begin
    if ok is distinct from true then
        raise exception 'FAILED: %', description;
    end if;
    raise notice 'ok - %', description;
end;
$$;

\i /migrations/202609300001_track_live_fcc_queue_and_role_settings.sql

do $$
declare
    new_sub uuid := gen_random_uuid();
    done_sub uuid := gen_random_uuid();
begin
    insert into public.fcc_submissions (id) values (new_sub);
    insert into public.fcc_submissions (id, submitted) values (done_sub, true);

    perform expect(
        (select state = 'pending' and fcc_env = 'prod' and attempt_count = 0
         from private.fcc_submission_retry_queue where submission_id = new_sub),
        'a new submission is queued for prod');
    perform expect(
        not exists (select 1 from private.fcc_submission_retry_queue where submission_id = done_sub),
        'a submission that is already submitted is not queued');
end;
$$;

-- Change the queue entry, so that a second run which touched data would show up.
update private.fcc_submission_retry_queue set state = 'blocked', last_error = 'kept';

-- The second run reports "already exists, skipping" for the table; hide that.
set client_min_messages = warning;
\i /migrations/202609300001_track_live_fcc_queue_and_role_settings.sql
set client_min_messages = notice;

do $$
begin
    perform expect(
        (select count(*) = 1 and bool_and(state = 'blocked' and last_error = 'kept')
         from private.fcc_submission_retry_queue),
        'running the migration again leaves the queue as it was');
    perform expect(
        (select count(*) = 1 from pg_trigger
         where tgname = 'trg_auto_post_fcc_submission' and not tgisinternal),
        'running the migration again leaves one trigger');
    perform expect(
        (select 'statement_timeout=2min' = any (setconfig)
         from pg_db_role_setting where setrole = 'anon'::regrole and setdatabase = 0),
        'anon has a 2 minute statement_timeout');
    perform expect(
        (select setconfig @> array['statement_timeout=8s', 'lock_timeout=8s']
         from pg_db_role_setting where setrole = 'authenticator'::regrole and setdatabase = 0),
        'authenticator has 8 second statement and lock timeouts');
end;
$$;

do $$
begin
    begin
        update private.fcc_submission_retry_queue set state = 'done';
        raise exception 'FAILED: the queue accepted an unknown state';
    exception when check_violation then
        perform expect(true, 'the queue rejects states other than pending and blocked');
    end;

    delete from public.fcc_submissions;
    perform expect(
        not exists (select 1 from private.fcc_submission_retry_queue),
        'deleting a submission removes its queue entry');
end;
$$;
