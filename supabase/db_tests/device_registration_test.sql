-- Tests for 202610220001_retry_safe_device_registration.sql, run by supabase/db_tests/run.sh in
-- a throwaway container. Loads copies of the live register_device and secret check, shows the
-- lockout, applies the migration, and checks that a phone can register again after losing the
-- response while a device that has uploaded keeps its secret.
\set ON_ERROR_STOP on
set client_min_messages = notice;

-- PostgREST requests run with this search_path (extra_search_path in supabase/config.toml).
-- The live functions call crypt() without a schema, so they rely on it.
set search_path = public, extensions;

create schema if not exists extensions;
create extension if not exists pgcrypto with schema extensions;
create schema if not exists private;

-- Copies of the live objects, as in supabase/full_schema.sql. Only device_id matters in the
-- two upload tables.
create table private.devices (
    id uuid not null primary key,
    hashed_secret text not null
);
create table public.measurements (id uuid default gen_random_uuid() primary key, device_id uuid);
create table public.fcc_submissions (id uuid default gen_random_uuid() primary key, device_id uuid);

create function public.register_device(device_id uuid) returns text
language plpgsql security definer as $$
declare
    device_secret text;
begin
    device_secret := encode(gen_random_bytes(32), 'base64');
    insert into private.devices (id, hashed_secret) values (
        device_id,
        crypt(device_secret, gen_salt('bf'))
    ) on conflict do nothing;

    if not found then
        raise exception 'device id % already registered', device_id;
    end if;

    return device_secret;
end;
$$;

create function private.verified_device_id_or_null() returns uuid
language plpgsql security definer as $$
declare
  device_id     text;
  device_secret text;
  hashed        text;
begin
  device_id     := current_setting('request.headers', true)::json->>'x-device-id';
  device_secret := current_setting('request.headers', true)::json->>'x-device-secret';

  if device_id is null or device_id = '' then return null; end if;
  if device_secret is null or device_secret = '' then return null; end if;

  select hashed_secret into hashed
  from private.devices
  where id = uuid(device_id);
  if not found then return null; end if;

  if crypt(device_secret, hashed) <> hashed then return null; end if;

  return uuid(device_id);
end;
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

-- The error a statement raises, or null if it succeeds.
create function error_of(statement text) returns text
language plpgsql as $$
begin
    execute statement;
    return null;
exception when others then
    return sqlerrm;
end;
$$;

-- Whether a request with these headers would be let in as this device.
create function verifies(device uuid, secret text) returns boolean
language plpgsql as $$
begin
    perform set_config('request.headers',
        json_build_object('x-device-id', device, 'x-device-secret', secret)::text, true);
    return private.verified_device_id_or_null() is not distinct from device;
end;
$$;

do $$
begin
    -- A phone whose first registration went through on the server, but whose response was lost.
    perform public.register_device('00000000-0000-0000-0000-00000000000a');
    perform expect(
        error_of($q$select public.register_device('00000000-0000-0000-0000-00000000000a')$q$)
            = 'device id 00000000-0000-0000-0000-00000000000a already registered',
        'before the migration, registering again fails, so that phone can never get a secret');
end;
$$;

\i /migrations/202610220001_retry_safe_device_registration.sql

-- The released app: register_device(device_id) returns a secret.
do $$
declare
    stuck uuid := '00000000-0000-0000-0000-00000000000a';
    fresh uuid := gen_random_uuid();
    used uuid := gen_random_uuid();
    used_fcc uuid := gen_random_uuid();
    secret text;
    used_secret text;
    used_fcc_secret text;
begin
    secret := public.register_device(fresh);
    perform expect(verifies(fresh, secret), 'released app: a new device gets a secret that works');

    secret := public.register_device(stuck);
    perform expect(verifies(stuck, secret),
        'released app: a device that never uploaded gets a new secret instead of an error');
    perform expect((select count(*) from private.devices where id = stuck) = 1, 'and keeps one row');

    used_secret := public.register_device(used);
    insert into public.measurements (device_id) values (used);
    perform expect(
        error_of(format('select public.register_device(%L::uuid)', used)) like '%already registered',
        'released app: a device with measurements still can''t register again');
    perform expect(verifies(used, used_secret), 'and its secret still works');

    used_fcc_secret := public.register_device(used_fcc);
    insert into public.fcc_submissions (device_id) values (used_fcc);
    perform expect(
        error_of(format('select public.register_device(%L::uuid)', used_fcc)) like '%already registered',
        'released app: nor can a device with only FCC submissions');
    perform expect(verifies(used_fcc, used_fcc_secret), 'and its secret still works');
end;
$$;

-- Newer apps: register_device(device_id, device_secret) with a secret the phone made.
do $$
declare
    fresh uuid := gen_random_uuid();
    stuck uuid := gen_random_uuid();
    used uuid := gen_random_uuid();
    secret text := encode(gen_random_bytes(32), 'hex');
    other_secret text := encode(gen_random_bytes(32), 'hex');
    old_secret text;
begin
    perform public.register_device(fresh, secret);
    perform expect(verifies(fresh, secret), 'a new device registers with its own secret');

    perform public.register_device(fresh, secret);
    perform expect(verifies(fresh, secret) and (select count(*) from private.devices where id = fresh) = 1,
        'a retry with the same secret succeeds and changes nothing');

    -- Locked out under the released app, then updated: the server has a secret the phone never got.
    perform public.register_device(stuck);
    perform public.register_device(stuck, other_secret);
    perform expect(verifies(stuck, other_secret), 'a device that never uploaded takes the new secret');

    -- Registered and uploaded with the released app, then updated: the app confirms the
    -- secret it already has.
    old_secret := public.register_device(used);
    insert into public.measurements (device_id) values (used);
    perform public.register_device(used, old_secret);
    perform expect(verifies(used, old_secret),
        'a device that uploaded can confirm the secret it already has');
    perform expect(
        error_of(format('select public.register_device(%L::uuid, %L)', used, other_secret))
            like '%already registered',
        'but can''t register with a different secret');
    perform expect(verifies(used, old_secret) and not verifies(used, other_secret),
        'and its secret is unchanged');

    perform expect(
        error_of(format('select public.register_device(%L::uuid, %L)', gen_random_uuid(), 'short'))
            = 'device secret is too short',
        'a short secret is refused');
    perform expect(
        error_of(format('select public.register_device(%L::uuid, null)', gen_random_uuid()))
            = 'device secret is too short',
        'so is a missing one');
end;
$$;

do $$
begin
    perform expect(has_function_privilege('anon', 'public.register_device(uuid, text)', 'execute'),
        'the app''s anonymous role may call the new function');
end;
$$;
