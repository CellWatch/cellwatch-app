-- Tests for private.process_fcc_submission_retries, run by supabase/db_tests/run.sh in a
-- throwaway container. Stand-ins replace FCC posting and the readiness check, so nothing is
-- sent anywhere.
\set ON_ERROR_STOP on
set client_min_messages = notice;

-- Minimal copies of the tables the retry function uses (columns as in the live schema).
create schema if not exists private;

create table public.fcc_submissions (
    id uuid primary key,
    provider text,
    submitted boolean default false,
    submission_response text
);

create table private.fcc_submission_retry_queue (
    submission_id uuid primary key,
    fcc_env text default 'prod' not null,
    state text default 'pending' not null check (state in ('pending', 'blocked')),
    attempt_count integer default 0 not null,
    next_attempt_at timestamptz default now() not null,
    last_attempt_at timestamptz,
    last_error text,
    created_on timestamptz default now() not null,
    updated_on timestamptz default now() not null
);

create table private.fcc_submission_failures (
    id uuid default gen_random_uuid() primary key,
    submission_id uuid,
    measurement_group_id uuid,
    error_message text,
    occurred_on timestamptz default now() not null,
    succeeded boolean,
    fcc_env text,
    response_status integer,
    response_snippet text
);

-- Which submissions have all three measurements, and how the stand-in FCC answers.
create table test_ready (submission_id uuid primary key);
create table test_fcc (status integer not null, delay_seconds numeric not null);
insert into test_fcc values (200, 0);

create function private.fcc_submission_is_ready(sub_id uuid) returns boolean
language sql stable as $$
    select exists (select 1 from test_ready where submission_id = sub_id)
$$;

create function private.post_fcc_submission(sub_id uuid, fcc_env text) returns boolean
language plpgsql as $$
declare
    fcc record;
begin
    select * into fcc from test_fcc;
    perform pg_sleep(fcc.delay_seconds);
    if fcc.status <> 200 then
        raise exception 'FCC HTTP status % for submission %: test response', fcc.status, sub_id;
    end if;
    update public.fcc_submissions set submitted = true, submission_response = 'ok' where id = sub_id;
    return true;
end;
$$;

\i /migrations/202610070001_unclog_fcc_retry_queue.sql

-- Adds a due queue entry for a submission that was queued `queued_ago` ago.
create function test_add(sub_id uuid, ready boolean, queued_ago interval) returns void
language sql as $$
    insert into public.fcc_submissions (id, provider) values (sub_id, 'Test Carrier');
    insert into private.fcc_submission_retry_queue (submission_id, next_attempt_at, created_on)
        values (sub_id, now() - interval '1 minute', now() - queued_ago);
    insert into test_ready select sub_id where ready;
$$;

create function test_reset() returns void
language sql as $$
    truncate public.fcc_submissions, private.fcc_submission_retry_queue,
        private.fcc_submission_failures, test_ready;
    update test_fcc set status = 200, delay_seconds = 0;
$$;

create function expect(ok boolean, description text) returns void
language plpgsql as $$
begin
    if ok is distinct from true then
        raise exception 'FAILED: %', description;
    end if;
    raise notice 'ok - %', description;
end;
$$;

do $$
declare
    waited_long uuid := gen_random_uuid();
    waited_short uuid := gen_random_uuid();
    processed integer;
begin
    perform test_reset();
    perform test_add(waited_long, false, interval '2 days');
    perform test_add(waited_short, false, interval '1 hour');

    processed := private.process_fcc_submission_retries(10);

    perform expect(processed = 0, 'waiting entries are not counted as processed');
    perform expect(
        (select next_attempt_at from private.fcc_submission_retry_queue where submission_id = waited_long)
            = now() + interval '1 hour',
        'an entry waiting more than a day is rechecked in an hour');
    perform expect(
        (select next_attempt_at from private.fcc_submission_retry_queue where submission_id = waited_short)
            = now() + interval '5 minutes',
        'an entry waiting less than a day is rechecked in 5 minutes');
    perform expect(
        (select bool_and(attempt_count = 0 and state = 'pending') from private.fcc_submission_retry_queue),
        'waiting does not count as a failed attempt');
end;
$$;

do $$
declare
    processed integer;
begin
    perform test_reset();
    perform test_add(gen_random_uuid(), true, interval '1 hour') from generate_series(1, 12);

    processed := private.process_fcc_submission_retries(10);

    perform expect(processed = 10, 'one run handles 10 ready entries');
    perform expect((select count(*) from public.fcc_submissions where submitted) = 10,
        '10 submissions are marked sent');
    perform expect((select count(*) from private.fcc_submission_retry_queue) = 2,
        'the other 2 stay queued for the next run');
    perform expect((select count(*) from private.fcc_submission_failures where succeeded) = 10,
        'each success is logged');
end;
$$;

do $$
begin
    perform test_reset();
    perform test_add(gen_random_uuid(), true, interval '1 hour') from generate_series(1, 12);

    perform expect(private.process_fcc_submission_retries(50) = 10, 'a run never takes more than 10 entries');
end;
$$;

do $$
declare
    sub uuid := gen_random_uuid();
begin
    perform test_reset();
    insert into public.fcc_submissions (id, provider) values (sub, ' ');
    insert into private.fcc_submission_retry_queue (submission_id, next_attempt_at)
        values (sub, now() - interval '1 minute');

    perform private.process_fcc_submission_retries(10);

    perform expect(
        (select state = 'blocked' and last_error = 'Missing provider'
         from private.fcc_submission_retry_queue where submission_id = sub),
        'an entry without a carrier name is blocked');
end;
$$;

do $$
declare
    sub uuid := gen_random_uuid();
begin
    perform test_reset();
    insert into public.fcc_submissions (id, provider, submitted) values (sub, 'Test Carrier', true);
    insert into private.fcc_submission_retry_queue (submission_id) values (sub);

    perform private.process_fcc_submission_retries(10);

    perform expect(not exists (select 1 from private.fcc_submission_retry_queue),
        'an entry already sent some other way is removed from the queue');
end;
$$;

do $$
declare
    rejected uuid := gen_random_uuid();
begin
    perform test_reset();
    update test_fcc set status = 400;
    perform test_add(rejected, true, interval '1 hour');

    perform private.process_fcc_submission_retries(10);

    perform expect(
        (select state = 'blocked' and attempt_count = 1
         from private.fcc_submission_retry_queue where submission_id = rejected),
        'an FCC validation error (400) blocks the entry');
    perform expect(
        (select count(*) = 1 from private.fcc_submission_failures where submission_id = rejected and not succeeded),
        'the rejection is logged');
    perform expect(not (select submitted from public.fcc_submissions where id = rejected),
        'a rejected submission is not marked sent');
end;
$$;

do $$
declare
    unavailable uuid := gen_random_uuid();
begin
    perform test_reset();
    update test_fcc set status = 503;
    perform test_add(unavailable, true, interval '1 hour');

    perform private.process_fcc_submission_retries(10);

    perform expect(
        (select state = 'pending' and attempt_count = 1 and next_attempt_at = now() + interval '60 seconds'
         from private.fcc_submission_retry_queue where submission_id = unavailable),
        'an FCC outage (503) is retried in 60 seconds');
end;
$$;

\echo 'The last test simulates a slow FCC and takes about a minute.'
do $$
declare
    processed integer;
begin
    perform test_reset();
    update test_fcc set delay_seconds = 31;
    perform test_add(gen_random_uuid(), true, interval '1 hour') from generate_series(1, 5);

    processed := private.process_fcc_submission_retries(10);

    perform expect(processed = 2, 'a run stops taking new entries after 60 seconds');
    perform expect((select count(*) from private.fcc_submission_retry_queue) = 3,
        'the rest stay queued for the next run');
end;
$$;
