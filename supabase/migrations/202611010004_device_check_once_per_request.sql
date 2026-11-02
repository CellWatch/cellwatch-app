begin;

-- Check a device's secret with bcrypt once per request, not once per inserted row.
--
-- The row level security policies on the upload tables check the X-Device-ID and
-- X-Device-Secret headers through private.verified_device_id() and
-- private.verified_device_id_or_null(), which compare the secret with its bcrypt hash
-- (crypt(), about 5 ms per call on the live project). 202610210001 made each policy run its
-- check once per statement, but one request runs many statements: insert_measurement inserts
-- the measurement and then one row at a time into upload_download_data or latency_data,
-- locations and cells. Each of those inserts runs two checks: its own INSERT policy, and the
-- measurements SELECT policy (on the RETURNING of the measurement, and on the lookup of the
-- device's measurements for the others). The latency_data trigger fix_jitter reads
-- measurements twice, two more. Counted in a copy of the live schema with the queries
-- PostgREST runs (db_tests/device_check_once_per_request_test.sql):
--     insert_measurement, download or upload:  4 + 2 per location + 2 per cell
--     insert_measurement, latency:             6 + 2 per location + 2 per cell
--     insert into fcc_submissions:             1 (its INSERT and SELECT policies are the same
--                                                expression, which Postgres checks once)
-- One test (three measurements, each with two locations and C cells, then its FCC submission)
-- ran bcrypt 27 + 6C times: 45 with 3 cells, 87 with 10 (an earlier review estimated about
-- 20). It also let a client with a valid secret make the server run bcrypt twice for every
-- cell it sent.
--
-- PostgREST runs each request in one transaction, and the headers can't change during it, so
-- every check in it gets the same answer. Now the first check in a transaction runs bcrypt and
-- remembers that it passed, and later checks in the same transaction with the same device id,
-- secret and stored hash use that: bcrypt runs once per request, four times per test, however
-- many locations and cells it has. A refusal is not remembered, so every check of a wrong
-- secret still runs bcrypt, and guessing costs what it did.
--
-- Why the remembered check can't be reused or forged:
--   * It is kept in the setting cellwatch.device_check, set with set_config(..., true), so
--     Postgres drops it when the transaction ends (commit or rollback), or when the
--     transaction rolls back to a savepoint taken before it was set.
--   * Any role can set a setting like this one (SET, set_config()), so its value proves
--     nothing on its own. It holds an HMAC-SHA256 tag over the device id, the secret
--     presented, the stored bcrypt hash, the backend's pid and the transaction's start time,
--     keyed with 32 random bytes in private.device_check_key. A check uses it only if it equals
--     the tag computed again from the current request headers and the hash now on file:
--       - another device id, another secret, or a secret replaced by register_device gives a
--         different tag, so that check runs bcrypt;
--       - another session has a different pid, and a later transaction in the same session a
--         later start time (unless the clock is set back), so a value copied out of one
--         transaction doesn't match in another; if it did, it would only repeat a check that
--         passed (see the last point);
--       - making a tag that matches takes the key. No role but its owner has privileges on the
--         key table (not anon, authenticated, service_role, authenticator, which PostgREST logs
--         in as, or readonly_user), and row level security without policies hides its row from
--         roles that read every table (pg_read_all_data) unless they also bypass row level
--         security. The roles that can read it (postgres, superusers and, in the
--         supabase/postgres image, supabase_read_only_user and supabase_etl_admin) can't act as
--         anon or authenticated, except postgres and superusers, which can write the upload
--         tables directly anyway.
--   * The tag is set only by private.device_secret_matches(), only after bcrypt accepted the
--     secret, and only the owner may call that function (the two check functions call it as
--     their owner). PostgREST exposes neither it nor set_config(), and sets no settings from
--     a request other than its own request.* ones. A request can't set the tag at all; a
--     direct database login can set the setting but can't make a tag that matches.
--   * A remembered check gives the answer bcrypt would give again: the same secret checked
--     against the same hash. The cache changes how often bcrypt runs, never who gets in. The
--     header checks, the "not registered" error and verified_device_id()'s advisory lock
--     (which waits for a secret replacement in progress, 202610220001) still run on every
--     call, before it.
--
-- What it relies on: the key stays secret. It is in the database, so it is in the backups,
-- unlike the device secrets (only their hashes are); sync_from_remote.sh leaves it out of the
-- local data dump. Someone with a dump who could
-- also run SQL as anon or authenticated could make tags for any device. If a dump leaks, give
-- it a new key (update private.device_check_key set key = extensions.gen_random_bytes(32));
-- requests in progress then just run bcrypt again. Without a key row (a database built from a
-- schema-only dump) every check runs bcrypt, as before this migration.
--
-- Unchanged: the policies, register_device, the error messages, and the two functions' owner,
-- privileges and search_path.
--
-- Deploying: no request waits. CREATE OR REPLACE FUNCTION doesn't wait for requests in
-- progress; they finish with the old functions. The key table is new, so no request holds it.
-- Running this again changes nothing, keeps the key and makes no request wait.

-- The key the tags are made with. One row.
create table if not exists private.device_check_key (
    only_row boolean primary key default true check (only_row),
    key bytea not null check (length(key) >= 32)
);

-- Owned, like the functions that read it, by postgres, whichever role runs this. ALTER TABLE
-- locks the table even when it changes nothing, and every device check reads it, so on a run
-- of this file again every request would wait behind the slowest one in progress. So it's only
-- altered if it needs to be, here and below.
do $$
begin
    if (select c.relowner <> 'postgres'::regrole from pg_class c where c.oid = 'private.device_check_key'::regclass) then
        alter table private.device_check_key owner to postgres;
    end if;
end;
$$;

insert into private.device_check_key (key) values (extensions.gen_random_bytes(32))
on conflict do nothing;

-- Only the owner may read it. On the live project new tables in private get SELECT for
-- readonly_user by default (alter default privileges), so take back every grant, to whomever.
do $$
declare
    grantee_name text;
begin
    for grantee_name in
        select distinct case when a.grantee = 0 then 'public' else quote_ident(r.rolname) end
        from pg_class c
            cross join lateral aclexplode(c.relacl) a
            left join pg_roles r on r.oid = a.grantee
        where c.oid = 'private.device_check_key'::regclass
          and a.grantee <> c.relowner
    loop
        execute format('revoke all on table private.device_check_key from %s', grantee_name);
    end loop;
end;
$$;

do $$
begin
    if not (select c.relrowsecurity from pg_class c where c.oid = 'private.device_check_key'::regclass) then
        alter table private.device_check_key enable row level security;
    end if;
end;
$$;

-- Whether the secret is the device's: null if the device isn't registered. The first check in
-- a transaction runs bcrypt and, if the secret is right, keeps the tag described above; later
-- checks with the same device id, secret and stored hash in that transaction use the tag.
-- A refusal is not kept, so every check of a wrong secret runs bcrypt.
create or replace function private.device_secret_matches(device uuid, secret text)
returns boolean
language plpgsql
volatile
set search_path = public, private, extensions, pg_temp
as
$$
declare
    hashed text;
    tag_key bytea;
    tag text;
begin
    select d.hashed_secret into hashed from private.devices d where d.id = device;
    if not found then
        return null;
    end if;

    select k.key into tag_key from private.device_check_key k;
    if tag_key is not null then
        tag := encode(extensions.hmac(
            convert_to(json_build_array('cellwatch device check', device, secret, hashed,
                                        pg_backend_pid(), extract(epoch from transaction_timestamp()))::text,
                       'UTF8'),
            tag_key, 'sha256'), 'hex');
        if current_setting('cellwatch.device_check', true) = tag then
            return true;
        end if;
    end if;

    -- A null hash (the column is not null) refuses instead of letting any secret in.
    if hashed is null or extensions.crypt(secret, hashed) is distinct from hashed then
        return false;
    end if;

    if tag is not null then
        perform set_config('cellwatch.device_check', tag, true);
    end if;
    return true;
end;
$$;

-- Only the owner, so only the security definer functions below (also owned by postgres), may
-- call it.
alter function private.device_secret_matches(uuid, text) owner to postgres;
revoke all on function private.device_secret_matches(uuid, text) from public;

-- The live secret check, which the upload policies call. Changes are marked "Changed".
create or replace function private.verified_device_id()
returns uuid
language plpgsql
security definer
set search_path = public, private, extensions, pg_temp
as
$$
declare
    device_id text;
    device_secret text;
    matches boolean;
begin
    device_id := current_setting('request.headers', true)::json->>'x-device-id';
    device_secret := current_setting('request.headers', true)::json->>'x-device-secret';

    if device_id is null or device_id = '' then
        raise exception 'missing x-device-id header';
    end if;

    if device_secret is null or device_secret = '' then
        raise exception 'missing x-device-secret header';
    end if;

    -- Wait for a secret replacement in progress, and hold one off until this ends.
    perform pg_advisory_xact_lock_shared(hashtextextended('cellwatch device ' || uuid(device_id)::text, 0));

    -- Changed: bcrypt runs once per transaction (see private.device_secret_matches()).
    matches := private.device_secret_matches(uuid(device_id), device_secret);
    if matches is null then
        raise exception 'device id % is not registered', device_id;
    end if;

    if not matches then
        raise exception 'incorrect device secret';
    end if;

    return uuid(device_id);
end;
$$;

-- The live check the SELECT policies call, which returns null instead of raising.
create or replace function private.verified_device_id_or_null()
returns uuid
language plpgsql
security definer
set search_path = public, private, extensions, pg_temp
as
$$
declare
  device_id     text;
  device_secret text;
begin
  device_id     := current_setting('request.headers', true)::json->>'x-device-id';
  device_secret := current_setting('request.headers', true)::json->>'x-device-secret';

  if device_id is null or device_id = '' then return null; end if;
  if device_secret is null or device_secret = '' then return null; end if;

  -- Changed: bcrypt runs once per transaction (see private.device_secret_matches()). Null
  -- (not registered) and false both mean no device.
  if private.device_secret_matches(uuid(device_id), device_secret) then
    return uuid(device_id);
  end if;

  return null;
end;
$$;

commit;
