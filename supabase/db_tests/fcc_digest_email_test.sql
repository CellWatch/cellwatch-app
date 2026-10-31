-- Tests for emailing the FCC queue digest (202610310001): an email goes out only when
-- something is new since the last run, or after 7 days without one; blocked entries that
-- haven't changed are one line; a run Resend refuses fails without being logged, so the next
-- run covers its period. Run by supabase/db_tests/run.sh in a throwaway container. A stand-in
-- replaces the request to Resend, so nothing is sent anywhere.
\set ON_ERROR_STOP on
set client_min_messages = notice;

\i /db_tests/lib/fcc_schema.sql
\i /migrations/202610210101_build_fcc_payload_per_submission.sql
\i /migrations/202610210103_fcc_retry_queue_processing.sql
\i /migrations/202610210104_fcc_queue_health.sql
\i /migrations/202610310001_email_fcc_queue_digest.sql
-- Applying it again changes nothing.
\i /migrations/202610310001_email_fcc_queue_digest.sql

-- How the stand-in Resend answers (200 unless a row says otherwise), and what it was sent.
-- The body is stored as jsonb, so a body that isn't valid JSON fails the run.
create table test_resend_reply (status integer, content text);
create table test_resend_log (n bigserial, api_key text, body jsonb);

create or replace function private.resend_http_post(
    p_api_key text, p_body text, out status integer, out content text)
language plpgsql as $$
begin
    status := coalesce((select r.status from test_resend_reply r), 200);
    content := coalesce((select r.content from test_resend_reply r), '{"id":"test-email-id"}');
    insert into test_resend_log (api_key, body) values (p_api_key, p_body::jsonb);
end;
$$;

-- A queue entry, added the way the app adds a submission (the live trigger queues it), then
-- put in the given state as of the given times.
create function test_entry(
    p_state text,
    p_queued_ago interval,
    p_changed_ago interval,
    p_error text default null
) returns uuid
language plpgsql as $$
declare
    sub uuid;
begin
    insert into public.fcc_submissions (device_id, provider)
    values (gen_random_uuid(), 'at&t')
    returning id into sub;
    update private.fcc_submission_retry_queue
    set state = p_state,
        last_error = p_error,
        created_on = now() - p_queued_ago,
        updated_on = now() - p_changed_ago
    where submission_id = sub;
    return sub;
end;
$$;

-- A day passes: the logged runs, the queue and the post log all move a day back, as if the
-- job had last run yesterday.
create function test_a_day_later() returns void
language sql as $$
    update private.fcc_digest_runs set ran_at = ran_at - interval '1 day';
    update private.fcc_submission_retry_queue
    set created_on = created_on - interval '1 day', updated_on = updated_on - interval '1 day';
    update private.fcc_submission_failures set occurred_on = occurred_on - interval '1 day';
$$;

create function test_last_email() returns jsonb
language sql as $$
    select body from test_resend_log order by n desc limit 1;
$$;

do $$
begin
    perform expect((select count(*) from pg_proc where proname = 'fcc_queue_digest') = 1,
                   'only one fcc_queue_digest remains, so a call without arguments is not ambiguous');
end;
$$;

-- Three entries blocked long ago, like the ones left blocked on 2026-10-29.
do $$
begin
    perform test_entry('blocked', '10 days', '5 days', 'Missing provider');
    perform test_entry('blocked', '10 days', '5 days', 'Missing provider');
    perform test_entry('blocked', '9 days', '4 days', 'FCC HTTP status 400: rejected');
end;
$$;

-- Not set up yet: the first run would send, so it fails and says what to add.
do $$
begin
    begin
        perform private.send_fcc_queue_digest();
        raise exception 'no error';
    exception when others then
        perform expect(sqlerrm like 'The FCC digest email is not set up%fcc_digest_resend_api_key%fcc_digest_email_to',
                       'without the Vault secrets, a run that would send fails and names them');
    end;
    perform expect(not exists (select 1 from private.fcc_digest_runs), 'the failed run is not logged');
end;
$$;

insert into vault.secrets (name, secret)
values ('fcc_digest_resend_api_key', ' re_test_key '), ('fcc_digest_email_to', 'digest@example.invalid');

-- The first run sends even though nothing is new, so a working setup shows itself.
do $$
declare
    email jsonb;
begin
    perform expect(private.send_fcc_queue_digest() = 'sent', 'the first run sends');
    email := test_last_email();
    perform expect((select api_key from test_resend_log) = 're_test_key', 'the API key is sent trimmed');
    perform expect(email ->> 'from' = 'CellWatch FCC queue <onboarding@resend.dev>', 'from Resend''s shared sender');
    perform expect(email -> 'to' = '["digest@example.invalid"]'::jsonb, 'to the address in Vault');
    perform expect(email ->> 'subject' = 'CellWatch FCC queue: first email', 'the first email says so');
    perform expect(email ->> 'text' like 'Nothing new needs attention.%at least once a week%',
                   'and says it comes at least weekly');
    perform expect(email ->> 'text' like '%Blocked, needs a person (3, newest first):'
                       || E'\n' || '- 3 not listed: unchanged since before %',
                   'the old blocked entries are one line, not a list');
    perform expect((select count(*) from private.fcc_digest_runs where sent and detail like '%test-email-id%') = 1,
                   'the run is logged with Resend''s answer');
end;
$$;

-- Nothing new, and an email went out within 7 days: nothing is sent.
do $$
begin
    perform test_a_day_later();
    perform expect(private.send_fcc_queue_digest() = 'nothing new', 'a quiet day sends nothing');
    perform expect((select count(*) from test_resend_log) = 1, 'no request is made');
    perform expect((select count(*) from private.fcc_digest_runs where not sent) = 1, 'the quiet run is logged');
end;
$$;

-- An entry blocked since the last run: listed, while the old ones stay one line. Its error
-- has quotes and a line break, as FCC answers do, and still makes a valid request body.
do $$
declare
    fresh uuid;
    email jsonb;
begin
    perform test_a_day_later();
    fresh := test_entry('blocked', '2 hours', '1 hour',
        E'FCC HTTP status 400: {"message":"Missing \\"locations\\""}\nsecond line');
    perform expect(private.send_fcc_queue_digest() = 'sent', 'a newly blocked entry sends');
    email := test_last_email();
    perform expect(email ->> 'subject' = 'CellWatch FCC queue: 1 newly blocked', 'the subject says what is new');
    perform expect(email ->> 'text' like 'New since % UTC: 1 newly blocked.%', 'and so does the first line');
    perform expect(strpos(email ->> 'text', E'Blocked, needs a person (4, newest first):\n- ' || fresh || ' (device ') > 0
                       and strpos(email ->> 'text',
                           E'): FCC HTTP status 400: {"message":"Missing \\"locations\\""}\nsecond line\n'
                           || '- 3 not listed: unchanged since before ') > 0,
                   'the new entry is listed with its error as is, the old ones counted');
end;
$$;

-- Held, a failed post and an entry passing 3 days pending, all since the last run. A
-- duplicate_test_id answer (logged as a success) and an entry pending for long before
-- don't count.
do $$
begin
    perform test_a_day_later();
    perform test_entry('held', '1 hour', '1 hour', 'Missing provider: no carrier name (blank names are not filled in)');
    perform test_entry('pending', '3 days 1 hour', '1 hour', 'Waiting for complete latency/download/upload data');
    perform test_entry('pending', '10 days', '1 hour', 'Waiting for complete latency/download/upload data');
    insert into private.fcc_submission_failures (submission_id, succeeded, response_snippet)
    values (gen_random_uuid(), false, 'FCC HTTP status 503'),
           (gen_random_uuid(), true, 'FCC HTTP status 400 for submission x: duplicate_test_id');
    perform expect(private.send_fcc_queue_digest() = 'sent', 'held, failed and newly overdue entries send');
    perform expect(test_last_email() ->> 'subject'
                       = 'CellWatch FCC queue: 1 newly held, 1 failed FCC post, 1 now pending over 3 days',
                   'each counted once, and the duplicate and the long-pending entry not at all');
    perform expect(test_last_email() ->> 'text' like '%Pending for more than 3 days (2):%',
                   'the digest still lists every overdue entry');
end;
$$;

-- Resend refuses: the run fails and isn't logged, and the next run covers its period.
do $$
declare
    runs_before integer := (select count(*) from private.fcc_digest_runs);
    since_before timestamptz;
begin
    perform test_a_day_later();
    since_before := (select max(ran_at) from private.fcc_digest_runs);
    perform test_entry('blocked', '1 hour', '30 minutes', 'FCC HTTP status 400: rejected');
    insert into test_resend_reply values (403, '{"message":"You can only send testing emails to your own email address"}');
    begin
        perform private.send_fcc_queue_digest();
        raise exception 'no error';
    exception when others then
        perform expect(sqlerrm like 'Resend did not send the FCC digest: HTTP 403 %own email address%',
                       'a refused send fails with Resend''s answer');
    end;
    perform expect((select count(*) from private.fcc_digest_runs) = runs_before, 'the refused run is not logged');
    delete from test_resend_reply;
    perform expect(private.send_fcc_queue_digest() = 'sent', 'the next run sends');
    perform expect(test_last_email() ->> 'subject' = 'CellWatch FCC queue: 1 newly blocked'
                       and test_last_email() ->> 'text'
                           like format('New since %s UTC:%%', to_char(since_before at time zone 'UTC', 'YYYY-MM-DD HH24:MI')),
                   'and reports what the refused run would have');
end;
$$;

-- 7 days without an email: a short one goes out anyway.
do $$
begin
    delete from public.fcc_submissions;
    delete from private.fcc_submission_failures;
    update private.fcc_digest_runs set ran_at = ran_at - interval '8 days';
    perform expect(private.send_fcc_queue_digest() = 'sent', 'a week without an email sends one');
    perform expect(test_last_email() ->> 'subject' = 'CellWatch FCC queue: nothing new this week'
                       and test_last_email() ->> 'text' like 'Nothing new since the last email (%',
                   'saying that nothing is new');
    perform expect(private.send_fcc_queue_digest() = 'nothing new', 'and the next quiet run sends nothing');
    perform expect(private.send_fcc_queue_digest(p_force => true) = 'sent', 'p_force sends anyway');
end;
$$;

-- Without p_new_since, the digest lists every blocked entry, as before.
do $$
declare
    old1 uuid := test_entry('blocked', '10 days', '5 days', 'Missing provider');
    old2 uuid := test_entry('blocked', '10 days', '5 days', 'Missing provider');
    fresh uuid := test_entry('blocked', '1 hour', '1 hour', 'Missing provider');
    full_text text := private.fcc_queue_digest();
    since_text text := private.fcc_queue_digest(now() - interval '1 day');
begin
    perform expect(full_text like '%' || old1 || '%' and full_text like '%' || old2 || '%'
                       and full_text like '%' || fresh || '%'
                       and full_text not like '%not listed: unchanged since before%',
                   'without p_new_since every blocked entry is listed');
    perform expect(since_text like '%' || fresh || '%' and since_text not like '%' || old1 || '%'
                       and since_text like '%- 2 not listed: unchanged since before %',
                   'with it only the changed one is');
end;
$$;

\echo 'fcc_digest_email_test: all checks passed'
