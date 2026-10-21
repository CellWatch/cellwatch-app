-- Tests for filling a blank FCC provider from the SIM code (#118), run by
-- supabase/db_tests/run.sh in a throwaway container.
\set ON_ERROR_STOP on
set client_min_messages = notice;

\i /db_tests/lib/fcc_schema.sql
\i /migrations/202610210101_build_fcc_payload_per_submission.sql

-- Queue entries as they might be on the live database before the migration.
create table test_entry (name text primary key, sub_id uuid not null);

create function test_queued(name text, provider text, mcc text, mnc text, state text,
                            last_error text, submitted boolean default false) returns void
language plpgsql as $$
declare
    sub uuid := gen_random_uuid();
begin
    insert into test_entry values (name, sub);
    insert into public.fcc_submissions (id, provider, sim_country_code, sim_network_code, submitted)
    values (sub, provider, mcc, mnc, submitted);
    -- The live trigger queued it; put it in the state we want.
    insert into private.fcc_submission_retry_queue (submission_id, state, last_error, created_on)
    values (sub, state, last_error, now() - interval '10 days')
    on conflict (submission_id) do update
    set state = excluded.state, last_error = excluded.last_error,
        created_on = excluded.created_on, updated_on = now() - interval '9 days';
end;
$$;

do $$
begin
    perform test_queued('known code', '', '310', '410', 'blocked', 'Missing provider');
    perform test_queued('known code, null provider', null, '311', '480', 'blocked', 'Missing provider');
    perform test_queued('unknown code', '', '310', '999', 'blocked', 'Missing provider');
    perform test_queued('no SIM code', ' ', null, null, 'blocked', 'Missing provider');
    perform test_queued('blocked for something else', '', '310', '260', 'blocked',
        'FCC HTTP status 400 for submission x: {"errors":[]}');
    perform test_queued('still waiting', '', '310', '260', 'pending',
        'Waiting for complete latency/download/upload data');
    perform test_queued('already sent', '', '310', '410', 'blocked', 'Missing provider', true);
end;
$$;

create function test_provider(entry text) returns text
language sql as $$
    select s.provider from public.fcc_submissions s join test_entry e on e.sub_id = s.id where e.name = entry
$$;

create function test_queue(entry text) returns private.fcc_submission_retry_queue
language sql as $$
    select q.* from private.fcc_submission_retry_queue q join test_entry e on e.sub_id = q.submission_id
    where e.name = entry
$$;

\i /migrations/202610210102_fill_blank_fcc_provider_from_sim.sql

do $$
begin
    perform expect(test_provider('known code') = 'at&t'
                   and (test_queue('known code')).state = 'pending'
                   and (test_queue('known code')).next_attempt_at <= now(),
        'backfill: a blocked entry with a known SIM code gets its provider and is pending again');
    perform expect(test_provider('known code, null provider') = 'verizon'
                   and (test_queue('known code, null provider')).state = 'pending',
        'backfill: a null provider is filled too');
    perform expect(test_provider('unknown code') = '' and (test_queue('unknown code')).state = 'blocked',
        'backfill: an entry with an unknown SIM code stays blocked');
    perform expect(test_provider('no SIM code') = ' ' and (test_queue('no SIM code')).state = 'blocked',
        'backfill: an entry without a SIM code stays blocked');
    perform expect(test_provider('blocked for something else') = ''
                   and (test_queue('blocked for something else')).state = 'blocked',
        'backfill: an entry blocked for another reason is left alone');
    perform expect(test_provider('still waiting') = ''
                   and (test_queue('still waiting')).state = 'pending'
                   and (test_queue('still waiting')).last_error like 'Waiting%',
        'backfill: an entry still waiting for data is left alone');
    perform expect(test_provider('already sent') = '' and (test_queue('already sent')).state = 'blocked',
        'backfill: an entry already sent is left alone');
end;
$$;

create table test_before as select * from private.fcc_submission_retry_queue;
\i /migrations/202610210102_fill_blank_fcc_provider_from_sim.sql
do $$
begin
    perform expect(
        not exists (select * from private.fcc_submission_retry_queue
                    except select * from test_before)
        and (select count(*) from private.carrier_by_plmn) = 4,
        'running the migration again changes nothing');
end;
$$;

-- New submissions, inserted the way the app does: as anon, with request headers.
grant usage on schema public to anon;
grant select, insert on public.fcc_submissions to anon;

set role anon;
set request.headers = '{"x-forwarded-for": "203.0.113.9, 10.0.0.1"}';
insert into public.fcc_submissions (id, provider, sim_country_code, sim_network_code) values
    ('00000000-0000-0000-0000-000000000001', '', '310', '260'),
    ('00000000-0000-0000-0000-000000000002', null, ' 311 ', '480'),
    ('00000000-0000-0000-0000-000000000003', 'Verizon', '310', '260'),
    ('00000000-0000-0000-0000-000000000004', '', '310', '999'),
    ('00000000-0000-0000-0000-000000000005', '  ', null, null),
    ('00000000-0000-0000-0000-000000000006', '', '310', '28');
reset role;

do $$
declare
    providers text[];
begin
    select array_agg(coalesce(provider, '<null>') order by id) into providers
    from public.fcc_submissions where id::text like '00000000-0000-0000-0000-00000000000_';

    perform expect(providers[1] = 't-mobile', 'a blank provider is filled from the SIM code (310/260 is t-mobile)');
    perform expect(providers[2] = 'verizon', 'a null provider is filled, ignoring spaces around the code');
    perform expect(providers[3] = 'Verizon', 'a provider the app sent is kept');
    perform expect(providers[4] = '', 'an unknown SIM code leaves the provider blank');
    perform expect(providers[5] = '  ', 'no SIM code leaves the provider as sent');
    perform expect(providers[6] = '', 'codes must match exactly (310/28 is not 310/280)');

    perform expect(
        (select bool_and(source_ip = '203.0.113.9') from public.fcc_submissions
         where id::text like '00000000-0000-0000-0000-00000000000_'),
        'the source IP trigger still fills source_ip');
    perform expect(
        (select count(*) from private.fcc_submission_retry_queue
         where submission_id::text like '00000000-0000-0000-0000-00000000000_' and state = 'pending') = 6,
        'every new submission is still queued for the FCC');
    perform expect(
        (select array_agg(tgname::text order by tgname) from pg_trigger
         where tgrelid = 'public.fcc_submissions'::regclass and tgname like '\_%' and not tgisinternal)
            = array['_100_fcc_submission_set_updated_on', '_200_fcc_submission_force_created_on',
                    '_300_fcc_submission_update_source_ip', '_400_fcc_submission_fill_provider'],
        'the new trigger sorts after the other BEFORE triggers');
end;
$$;
