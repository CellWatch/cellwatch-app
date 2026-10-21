begin;

-- Fill a blank carrier name from the SIM's country and network codes (#118).
--
-- The released app sends provider "" when Android reports an empty network operator name.
-- The retry job blocks submissions without a provider ('Missing provider'), so these were
-- held until someone filled the name in by hand, as was done for a batch in August 2026.
--
-- A BEFORE INSERT trigger now fills a blank or null provider from private.carrier_by_plmn,
-- using the submission's sim_country_code and sim_network_code. When the code isn't in the
-- table the provider stays blank, and the retry job blocks the entry with a reason that
-- names the code (202610210103), so the code can be added here and the entry released.

create table if not exists private.carrier_by_plmn (
    mcc text not null check (mcc ~ '^[0-9]{3}$'),
    mnc text not null check (mnc ~ '^[0-9]{2,3}$'),
    provider text not null check (btrim(provider) <> ''),
    primary key (mcc, mnc)
);

comment on table private.carrier_by_plmn is
    'Carrier name sent to the FCC as provider_name when the app sent a blank provider, by the '
    'SIM''s mobile country code (mcc) and mobile network code (mnc). Used by the '
    '_400_fcc_submission_fill_provider trigger and the FCC retry job.';

-- The codes used when blank carriers were filled by hand in August 2026, with the same
-- lowercase names. Other codes of these carriers are left out until someone confirms them;
-- the retry job reports unknown codes in its block reason.
insert into private.carrier_by_plmn (mcc, mnc, provider)
values
    ('310', '260', 't-mobile'),
    ('310', '410', 'at&t'),
    ('310', '280', 'at&t'),
    ('311', '480', 'verizon')
on conflict (mcc, mnc) do nothing;

-- Security definer because the app inserts submissions as anon, which can't read the
-- private schema.
create or replace function private.fcc_provider_for_sim(sim_mcc text, sim_mnc text)
returns text
language sql
stable
security definer
set search_path = public, private
as
$$
    select c.provider
    from private.carrier_by_plmn c
    where c.mcc = btrim(sim_mcc)
      and c.mnc = btrim(sim_mnc);
$$;

revoke all on function private.fcc_provider_for_sim(text, text) from public;

create or replace function private.fill_fcc_submission_provider()
returns trigger
language plpgsql
security definer
set search_path = public, private
as
$$
begin
    new.provider := coalesce(
        private.fcc_provider_for_sim(new.sim_country_code, new.sim_network_code),
        new.provider
    );
    return new;
end;
$$;

revoke all on function private.fill_fcc_submission_provider() from public;

-- Triggers of the same kind fire in name order, so this runs after
-- _200_fcc_submission_force_created_on and _300_fcc_submission_update_source_ip, which
-- don't touch the provider. The AFTER INSERT trigger that queues the submission for the FCC
-- (trg_auto_post_fcc_submission) runs after all BEFORE INSERT triggers.
create or replace trigger _400_fcc_submission_fill_provider
    before insert on public.fcc_submissions
    for each row
    when (nullif(btrim(new.provider), '') is null)
    execute function private.fill_fcc_submission_provider();

-- One-off data fix for submissions already in the queue. It changes queue data when this
-- migration is applied: entries blocked with 'Missing provider' whose provider is still
-- blank and whose SIM code is now in private.carrier_by_plmn get the provider filled in and
-- go back to 'pending', so the retry job posts them on its next runs. It names no
-- particular submission, and running it again changes nothing, since the entries it
-- matches no longer have a blank provider. Entries with unknown codes stay blocked.
with filled as (
    update public.fcc_submissions s
    set provider = private.fcc_provider_for_sim(s.sim_country_code, s.sim_network_code)
    from private.fcc_submission_retry_queue q
    where q.submission_id = s.id
      and q.state = 'blocked'
      and q.last_error like 'Missing provider%'
      and nullif(btrim(s.provider), '') is null
      and coalesce(s.submitted, false) = false
      and private.fcc_provider_for_sim(s.sim_country_code, s.sim_network_code) is not null
    returning s.id
)
update private.fcc_submission_retry_queue q
set
    state = 'pending',
    next_attempt_at = now(),
    last_error = 'Provider filled from the SIM code by migration 202610210102',
    updated_on = now()
from filled f
where q.submission_id = f.id;

commit;
