-- Tests for leaving the FCC queue entries blocked before the 2026-10-29 pipeline push out of
-- the digest (202610310002): which entries go into private.fcc_queue_ignored, and that the
-- attention view, the digest and the check for something new skip every entry listed there.
-- Run by supabase/db_tests/run.sh in a throwaway container. A stand-in replaces the request to
-- Resend, so nothing is sent anywhere.
\set ON_ERROR_STOP on
set client_min_messages = notice;

\i /db_tests/lib/fcc_schema.sql
\i /migrations/202610210101_build_fcc_payload_per_submission.sql
\i /migrations/202610210103_fcc_retry_queue_processing.sql
\i /migrations/202610210104_fcc_queue_health.sql
\i /migrations/202610310001_email_fcc_queue_digest.sql

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

-- A queue entry, added the way the app adds a submission (the live trigger queues it), then
-- put in the given state as of the given times.
create function test_entry(p_state text, p_queued timestamptz, p_changed timestamptz, p_error text)
returns uuid
language plpgsql as $$
declare
    sub uuid;
begin
    insert into public.fcc_submissions (device_id, provider)
    values (gen_random_uuid(), 'at&t')
    returning id into sub;
    update private.fcc_submission_retry_queue
    set state = p_state, last_error = p_error, created_on = p_queued, updated_on = p_changed
    where submission_id = sub;
    return sub;
end;
$$;

create function test_id(name text) returns uuid
language sql as $$ select current_setting('test.' || name)::uuid $$;

-- The queue as on prod before the migration: three entries blocked before the push (the last
-- a second before the job was paused for it), an entry that expired after it, and an entry
-- blocked after it.
select test_entry('blocked', '2026-07-14', '2026-08-17', 'HOLD 2026-08-17: posted to FCC but DB rolled back') as old1,
       test_entry('blocked', '2026-10-25', '2026-10-25', 'Missing provider') as old2,
       test_entry('blocked', '2026-10-29 19:00', '2026-10-29 19:17:59', 'FCC HTTP status 400: rejected') as old3,
       test_entry('expired', '2026-08-01', '2026-10-29 20:16', 'Expired: measurements still incomplete 30 days after queueing') as expired1,
       test_entry('blocked', '2026-10-30', '2026-10-30', 'FCC HTTP status 400: rejected') as new1
\gset
set test.old1 = :'old1';
set test.old2 = :'old2';
set test.old3 = :'old3';
set test.expired1 = :'expired1';
set test.new1 = :'new1';

\i /migrations/202610310002_ignore_old_blocked_fcc_entries.sql
-- Applying it again changes nothing.
\i /migrations/202610310002_ignore_old_blocked_fcc_entries.sql

do $$
begin
    perform expect((select array_agg(submission_id order by submission_id) from private.fcc_queue_ignored)
                       = (select array_agg(x order by x) from unnest(array[test_id('old1'), test_id('old2'), test_id('old3')]) x),
                   'exactly the entries blocked before the push are ignored, once each');
    perform expect((select array_agg(submission_id) from private.fcc_queue_attention) = array[test_id('new1')],
                   'the attention view lists the entry blocked after the push, not the ignored ones');
    perform expect((select count(*) from private.fcc_queue_health where state = 'blocked') > 0
                       and (select sum(entries) from private.fcc_queue_health) = 5,
                   'fcc_queue_health still counts every entry');
end;
$$;

-- The digest leaves the ignored entries out of its counts and lists.
do $$
declare
    digest text := private.fcc_queue_digest();
begin
    perform expect(digest like '%In the queue: 1 blocked, 1 expired%', 'the queue counts skip the ignored entries');
    perform expect(digest like '%Blocked, needs a person (1, newest first):' || E'\n' || '- ' || test_id('new1') || ' %',
                   'only the entry blocked after the push is listed');
    perform expect(strpos(digest, test_id('old1')::text) = 0 and strpos(digest, test_id('old2')::text) = 0
                       and strpos(digest, test_id('old3')::text) = 0,
                   'no ignored entry appears anywhere in it');
end;
$$;

-- With only ignored entries left blocked, nothing needs attention.
do $$
begin
    delete from public.fcc_submissions where id = test_id('new1');
    perform expect(private.fcc_queue_digest() like '%In the queue: 1 expired%Nothing needs attention.',
                   'only ignored entries blocked: nothing needs attention');
end;
$$;

-- The check for something new skips ignored entries, even ones that change.
do $$
begin
    perform expect(private.send_fcc_queue_digest() = 'sent', 'the first run sends');
    update private.fcc_digest_runs set ran_at = ran_at - interval '1 day';
    update private.fcc_submission_retry_queue set updated_on = now() where submission_id = test_id('old2');
    perform expect(private.send_fcc_queue_digest() = 'nothing new', 'a change to an ignored entry is not news');
    perform expect(strpos((select body ->> 'text' from test_resend_log order by n desc limit 1), test_id('old1')::text) = 0
                       and (select body ->> 'text' from test_resend_log order by n desc limit 1) like '%Nothing needs attention.',
                   'and the email that went out mentions none of them');
end;
$$;

-- Ignoring applies to any state, so an ignored entry that is held later stays out of the counts.
do $$
begin
    update private.fcc_submission_retry_queue set state = 'held', last_error = 'Missing provider: no carrier name'
    where submission_id = test_id('old3');
    perform expect(private.fcc_queue_digest() not like '%Held until there is a way to send them%',
                   'a held entry that is ignored is not counted');
end;
$$;

-- Deleting the row reports the entry again; deleting the submission removes the row.
do $$
begin
    delete from private.fcc_queue_ignored where submission_id = test_id('old1');
    perform expect(exists (select 1 from private.fcc_queue_attention where submission_id = test_id('old1')),
                   'an entry taken off the list is reported again');
    delete from public.fcc_submissions where id = test_id('old2');
    perform expect(not exists (select 1 from private.fcc_queue_ignored where submission_id = test_id('old2')),
                   'a submission''s removal takes its ignore row with it');
end;
$$;

\echo 'fcc_digest_ignore_test: all checks passed'
