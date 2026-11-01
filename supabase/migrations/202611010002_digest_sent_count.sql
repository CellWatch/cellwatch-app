begin;

-- Say in each FCC digest email how many tests reached the FCC (asked for on 2026-11-01).
--
-- Under the opening line, the email now says how many tests the FCC accepted since the last
-- email: for the weekly email that is the past week. It counts posts to the prod BDC, once per
-- submission, from private.fcc_submission_failures, which logs every post. Each test is three
-- measurements (latency, download and upload), so the line gives both, for example
-- "Sent to the FCC since the last email (2026-11-01 13:01 UTC): 12 tests (36 measurements)."
-- Tests the FCC already had (a duplicate_test_id answer) are counted apart.

-- As in 202610310002, with the "Sent to the FCC" line (marked "Changed").
create or replace function private.send_fcc_queue_digest(p_force boolean default false)
returns text
language plpgsql
set search_path = public, private, pg_temp
as
$$
declare
    since timestamptz;
    last_sent timestamptz;
    n integer;
    news text[] := '{}';
    api_key text;
    email_to text;
    subject text;
    intro text;
    posted integer;
    already integer;
    sent_line text;
    reply record;
begin
    -- One run at a time, so a run by hand and the job's can't both send.
    perform pg_advisory_xact_lock(hashtextextended('cellwatch fcc digest', 0));

    select coalesce(max(r.ran_at), now() - interval '1 day') into since from private.fcc_digest_runs r;
    select max(r.ran_at) into last_sent from private.fcc_digest_runs r where r.sent;

    select count(*) into n from private.fcc_submission_retry_queue q
    where q.state = 'blocked' and q.updated_on > since and not exists (select 1 from private.fcc_queue_ignored i where i.submission_id = q.submission_id);
    if n > 0 then
        news := news || format('%s newly blocked', n);
    end if;

    -- The daily re-check of held entries changes updated_on only when the reason changes.
    select count(*) into n from private.fcc_submission_retry_queue q
    where q.state = 'held' and q.updated_on > since and not exists (select 1 from private.fcc_queue_ignored i where i.submission_id = q.submission_id);
    if n > 0 then
        news := news || format('%s newly held', n);
    end if;

    -- A duplicate_test_id answer counts as a success here, as in the digest.
    select count(*) into n from private.fcc_submission_failures f
    where not f.succeeded and f.occurred_on > since;
    if n > 0 then
        news := news || format('%s failed FCC post%s', n, case when n = 1 then '' else 's' end);
    end if;

    -- Entries that passed 3 days pending since the last run. The digest lists all of them.
    select count(*) into n from private.fcc_submission_retry_queue q
    where q.state = 'pending'
      and q.created_on < now() - interval '3 days'
      and q.created_on >= since - interval '3 days'
      and not exists (select 1 from private.fcc_queue_ignored i where i.submission_id = q.submission_id);
    if n > 0 then
        news := news || format('%s now pending over 3 days', n);
    end if;

    if cardinality(news) = 0 and not coalesce(p_force, false)
       and last_sent > now() - interval '7 days' then
        insert into private.fcc_digest_runs (sent, detail) values (false, 'Nothing new');
        return 'nothing new';
    end if;

    select s.decrypted_secret into api_key
    from vault.decrypted_secrets s where s.name = 'fcc_digest_resend_api_key';
    select s.decrypted_secret into email_to
    from vault.decrypted_secrets s where s.name = 'fcc_digest_email_to';
    if coalesce(btrim(api_key), '') = '' or coalesce(btrim(email_to), '') = '' then
        raise exception 'The FCC digest email is not set up: add the Vault secrets '
                        'fcc_digest_resend_api_key and fcc_digest_email_to';
    end if;

    if cardinality(news) > 0 then
        subject := 'CellWatch FCC queue: ' || array_to_string(news, ', ');
        intro := format('New since %s UTC: %s.',
                        to_char(since at time zone 'UTC', 'YYYY-MM-DD HH24:MI'), array_to_string(news, ', '));
    elsif last_sent is null then
        subject := 'CellWatch FCC queue: first email';
        intro := 'Nothing new needs attention. This email comes when something is new, and at '
                 'least once a week, so silence for longer than a week means it stopped working.';
    else
        subject := 'CellWatch FCC queue: nothing new this week';
        intro := format('Nothing new since the last email (%s UTC). This weekly email shows that '
                        'sending still works.', to_char(last_sent at time zone 'UTC', 'YYYY-MM-DD HH24:MI'));
    end if;

    -- Changed: what reached the FCC since the last email (in the last 7 days, before the first).
    -- Posts to the prod BDC that it accepted, counted once per submission. A submission is one
    -- test: three measurements, latency, download and upload (only complete ones are posted).
    -- A duplicate_test_id answer means the FCC had it already; counted apart, as in the digest.
    select count(distinct f.submission_id) filter (where coalesce(f.response_snippet, '') not like 'FCC HTTP status %'),
           count(distinct f.submission_id) filter (where f.response_snippet like 'FCC HTTP status %')
    into posted, already
    from private.fcc_submission_failures f
    where f.succeeded
      and f.fcc_env = 'prod'
      and f.occurred_on > coalesce(last_sent, now() - interval '7 days');
    sent_line := format('Sent to the FCC %s: %s test%s (%s measurements)%s.',
        case
            when last_sent is null then 'in the last 7 days'
            else format('since the last email (%s UTC)', to_char(last_sent at time zone 'UTC', 'YYYY-MM-DD HH24:MI'))
        end,
        posted, case when posted = 1 then '' else 's' end, posted * 3,
        case when already > 0 then format(', and %s already there (duplicate_test_id)', already) else '' end);

    select * into reply
    from private.resend_http_post(
        btrim(api_key),
        jsonb_build_object(
            'from', 'CellWatch FCC queue <onboarding@resend.dev>',
            'to', jsonb_build_array(btrim(email_to)),
            'subject', subject,
            'text', intro || E'\n' || sent_line || E'\n\n' || private.fcc_queue_digest(since)
        )::text
    );

    if reply.status is null or reply.status not between 200 and 299 then
        raise exception 'Resend did not send the FCC digest: HTTP % %',
            reply.status, left(coalesce(reply.content, ''), 300);
    end if;

    insert into private.fcc_digest_runs (sent, detail) values (true, left(reply.content, 300));
    return 'sent';
end;
$$;

commit;
