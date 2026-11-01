-- Tests for the "Sent to the FCC" line in the digest email (202611010002): it counts the posts
-- the prod BDC accepted since the last email (in the last 7 days, before the first), once per
-- submission, three measurements each, with tests the FCC already had counted apart. Run by
-- supabase/db_tests/run.sh in a throwaway container. A stand-in replaces the request to Resend,
-- so nothing is sent anywhere.
\set ON_ERROR_STOP on
set client_min_messages = notice;

\i /db_tests/lib/fcc_schema.sql
\i /migrations/202610210101_build_fcc_payload_per_submission.sql
\i /migrations/202610210103_fcc_retry_queue_processing.sql
\i /migrations/202610210104_fcc_queue_health.sql
\i /migrations/202610310001_email_fcc_queue_digest.sql
\i /migrations/202610310002_ignore_old_blocked_fcc_entries.sql
\i /migrations/202611010002_digest_sent_count.sql
-- Applying it again changes nothing.
\i /migrations/202611010002_digest_sent_count.sql

create table test_resend_log (n bigserial, body jsonb);
create or replace function private.resend_http_post(
    p_api_key text, p_body text, out status integer, out content text)
language plpgsql as $$
begin
    status := 200;
    content := '{"id":"test-email-id"}';
    insert into test_resend_log (body) values (p_body::jsonb);
end;
$$;
insert into vault.secrets (name, secret)
values ('fcc_digest_resend_api_key', 're_test_key'), ('fcc_digest_email_to', 'digest@example.invalid');

-- One logged post attempt: 'posted' (the FCC accepted it), 'already' (duplicate_test_id),
-- or 'failed'.
create function test_post(p_sub uuid, p_ago interval, p_kind text, p_env text default 'prod') returns void
language sql as $$
    insert into private.fcc_submission_failures (submission_id, occurred_on, succeeded, fcc_env, response_status, response_snippet)
    values (p_sub, now() - p_ago, p_kind <> 'failed', p_env,
            case p_kind when 'posted' then 200 else 400 end,
            case p_kind
                when 'posted' then '{"data":[{"refId":"test"}]}'
                else 'FCC HTTP status 400 for submission ' || p_sub || ': duplicate_test_id'
            end);
$$;

create function test_email() returns text
language sql as $$ select body ->> 'text' from test_resend_log order by n desc limit 1 $$;

-- The first email: the last 7 days. A post 10 days ago, a post to the demo BDC and a failed
-- attempt don't count; a submission logged as posted twice (a retry) counts once.
do $$
declare
    a uuid := gen_random_uuid();
begin
    perform test_post(a, '2 days', 'posted');
    perform test_post(a, '2 days 1 minute', 'posted');
    perform test_post(gen_random_uuid(), '1 day', 'posted');
    perform test_post(gen_random_uuid(), '3 days', 'already');
    perform test_post(gen_random_uuid(), '10 days', 'posted');
    perform test_post(gen_random_uuid(), '1 day', 'posted', 'demo');
    perform test_post(gen_random_uuid(), '8 days', 'failed');
    perform expect(private.send_fcc_queue_digest() = 'sent', 'the first run sends');
    perform expect(test_email() like E'Nothing new needs attention.%\nSent to the FCC in the last 7 days: 2 tests (6 measurements), and 1 already there (duplicate_test_id).\n\nFCC submission queue, %',
                   'the first email counts the last 7 days, each submission once, prod only');
end;
$$;

-- The weekly email: since the last email, 8 days ago.
do $$
declare
    last_email timestamptz := now() - interval '8 days';
begin
    delete from private.fcc_submission_failures;
    update private.fcc_digest_runs set ran_at = last_email;
    perform test_post(gen_random_uuid(), '9 days', 'posted');
    perform test_post(gen_random_uuid(), '1 day', 'posted');
    perform expect(private.send_fcc_queue_digest() = 'sent', 'a week without an email sends one');
    perform expect(test_email() like 'Nothing new since the last email (%'
                       || format(E'\nSent to the FCC since the last email (%s UTC): 1 test (3 measurements).\n\n',
                                 to_char(last_email at time zone 'UTC', 'YYYY-MM-DD HH24:MI')) || '%',
                   'the weekly email counts the posts since the last email, singular for one');
end;
$$;

-- A "something new" email carries the line too, and says 0 when nothing was sent.
do $$
declare
    last_email timestamptz;
    sub uuid;
begin
    delete from private.fcc_submission_failures;
    update private.fcc_digest_runs set ran_at = ran_at - interval '1 day';
    last_email := (select max(ran_at) from private.fcc_digest_runs where sent);
    insert into public.fcc_submissions (device_id, provider) values (gen_random_uuid(), 'at&t') returning id into sub;
    update private.fcc_submission_retry_queue
    set state = 'blocked', last_error = 'FCC HTTP status 400: rejected', updated_on = now()
    where submission_id = sub;
    perform expect(private.send_fcc_queue_digest() = 'sent', 'a newly blocked entry sends');
    perform expect(test_email() like 'New since % UTC: 1 newly blocked.'
                       || format(E'\nSent to the FCC since the last email (%s UTC): 0 tests (0 measurements).\n\n',
                                 to_char(last_email at time zone 'UTC', 'YYYY-MM-DD HH24:MI')) || '%',
                   'a news email has the line, with 0 when nothing was sent');
end;
$$;

\echo 'fcc_digest_sent_count_test: all checks passed'
