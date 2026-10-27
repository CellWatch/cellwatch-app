begin;

-- Some of the FCC pipeline and some role settings were created by hand on the live project, after
-- 202607140001 and before the 2026-09-30 snapshot (full_schema.sql). No migration had them (#121).
-- This file records them. Everything below matches the snapshot and is safe to run more than once:
-- on the live project it changes nothing, and on a fresh database it creates the objects.
--
-- Deliberately not here:
--   * private.process_fcc_submission_retries() and the fcc-submission-retries cron job.
--     202610070001 already defines both in full.
--   * The dashboard RLS policies and private.is_dashboard_request() / verified_device_id_or_null().
--     Policies have no "if not exists" or "or replace", so including them would mean dropping and
--     re-creating them, and running this file again after #115 changes them would undo #115. They
--     also depend on private.dashboard_settings, whose shared secret must not go in a migration.
--     They stay in the snapshot until the #115 migration takes them over.

-- Queue that trg_auto_post_fcc_submission fills and process_fcc_submission_retries() drains. If the
-- table already exists this does nothing, constraints included.
create table if not exists private.fcc_submission_retry_queue (
    submission_id uuid not null,
    fcc_env text default 'prod'::text not null,
    state text default 'pending'::text not null,
    attempt_count integer default 0 not null,
    next_attempt_at timestamptz default now() not null,
    last_attempt_at timestamptz,
    last_error text,
    created_on timestamptz default now() not null,
    updated_on timestamptz default now() not null,
    constraint fcc_submission_retry_queue_state_check check (state = any (array['pending'::text, 'blocked'::text])),
    constraint fcc_submission_retry_queue_pkey primary key (submission_id),
    constraint fcc_submission_retry_queue_submission_id_fkey foreign key (submission_id)
        references public.fcc_submissions (id) on delete cascade
);

-- readonly_user exists only on the live project.
do $$
begin
    if exists (select 1 from pg_roles where rolname = 'readonly_user') then
        grant select on table private.fcc_submission_retry_queue to readonly_user;
    end if;
end;
$$;

-- The live trigger only queues a new submission; the cron job posts it. This replaces the version in
-- 202607140001, which posted to the FCC inside the inserting transaction.
create or replace function private.auto_post_fcc_submission()
returns trigger
language plpgsql
security definer
set search_path to 'public', 'private', 'extensions'
as $$
begin
    if coalesce(new.submitted, false) = false then
        insert into private.fcc_submission_retry_queue (
            submission_id,
            fcc_env,
            state,
            next_attempt_at
        )
        values (
            new.id,
            'prod',
            'pending',
            now()
        )
        on conflict (submission_id) do update
        set
            state = 'pending',
            next_attempt_at = least(
                private.fcc_submission_retry_queue.next_attempt_at,
                now()
            ),
            updated_on = now();
    end if;

    return new;
end;
$$;

create or replace trigger trg_auto_post_fcc_submission
    after insert on public.fcc_submissions
    for each row execute function private.auto_post_fcc_submission();

-- Role timeouts listed in #121. Only anon's is a hand change: Supabase's default is 3s, and nobody
-- recorded why it became 2 minutes. authenticator's two values are Supabase's defaults (they're in
-- the stock supabase/postgres 17.6.1.084 image), so those lines change nothing on a Supabase
-- database; they're here so the expected values are written down. supabase/role_settings.sql lists
-- them for check_drift.sh. PostgREST picks up role settings when it reconnects; the notify asks it
-- to reload now.
alter role anon set statement_timeout = '2min';
alter role authenticator set statement_timeout = '8s';
alter role authenticator set lock_timeout = '8s';
notify pgrst, 'reload config';

commit;
