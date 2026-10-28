begin;

-- Make device registration safe to retry.
--
-- register_device(device_id) made the device's secret, stored only its bcrypt hash in
-- private.devices, and returned the secret once. The phone saved it only after the response
-- arrived. If the server committed but the response never reached the phone (a timeout, a
-- dropped connection, the app killed at that moment), every later call with the same id
-- raised "already registered". The phone had no secret, so it could never upload again;
-- clearing the app's data was the only way out, and that deletes the results waiting on it.
--
-- Changes:
--   * New register_device(device_id, device_secret): the phone makes the secret and saves it
--     before calling, so a retry sends the same secret, and a call with the secret already on
--     file succeeds. App versions after versionCode 13 use it. PostgREST tells the two
--     functions apart by the arguments in the request.
--   * Both functions let a device that has never uploaded anything (no measurements and no
--     FCC submissions) register again, replacing its secret. A phone locked out as above
--     never uploaded anything, so it recovers on its next attempt, the released app included.
--     The cost: someone who learns the id of a phone that registered but hasn't uploaded yet
--     could take that id over. Device ids are random, and none is published before the
--     device's first upload.
--   * A device that has uploaded still can't be registered with a different secret.

-- Whether anything has been uploaded as this device. Such a device's secret is never replaced.
create or replace function private.device_has_uploaded(device uuid)
returns boolean
language sql
stable
security definer
set search_path = public, private, pg_temp
as
$$
    select exists (select 1 from public.measurements m where m.device_id = device)
        or exists (select 1 from public.fcc_submissions s where s.device_id = device);
$$;

-- For the released app (versionCode 13), which sends only the id and saves the secret this
-- returns. Changes from the live version are marked "Changed".
create or replace function public.register_device(device_id uuid)
returns text
language plpgsql
security definer
-- Changed: a fixed search_path, as security definer functions should have. crypt(),
-- gen_salt() and gen_random_bytes() are in extensions. pg_temp comes last (here and in the
-- other two functions): otherwise Postgres looks up type names in the caller's temporary
-- schema first, and a temporary domain named text would run the caller's code as the owner.
set search_path = public, private, extensions, pg_temp
as
$$
declare
    device_secret text;
begin
    device_secret := encode(gen_random_bytes(32), 'base64');
    insert into private.devices (id, hashed_secret) values (
        device_id,
        crypt(device_secret, gen_salt('bf'))
    ) on conflict do nothing;

    if not found then
        -- Changed: a device that never uploaded anything gets a new secret instead of an
        -- error, so a phone that lost the first response can still register.
        if private.device_has_uploaded(device_id) then
            raise exception 'device id % already registered', device_id;
        end if;

        update private.devices
        set hashed_secret = crypt(device_secret, gen_salt('bf'))
        where id = device_id;
    end if;

    return device_secret;
end;
$$;

-- For app versions that make their own secret. Succeeds when the device is new, when the
-- secret matches the one on file (a retry after a lost response), or when the device has never
-- uploaded anything (its secret is replaced). Otherwise raises "already registered".
create or replace function public.register_device(device_id uuid, device_secret text)
returns void
language plpgsql
security definer
set search_path = public, private, extensions, pg_temp
as
$$
declare
    hashed text;
begin
    -- The app sends 32 random bytes as 64 hex digits. Anything much shorter could be guessed.
    if length(coalesce(device_secret, '')) < 32 then
        raise exception 'device secret is too short';
    end if;

    insert into private.devices (id, hashed_secret)
    values (device_id, crypt(device_secret, gen_salt('bf')))
    on conflict do nothing;
    if found then
        return;
    end if;

    -- Checked the same way as in private.verified_device_id_or_null().
    select d.hashed_secret into hashed from private.devices d where d.id = device_id;
    if crypt(device_secret, hashed) = hashed then
        return;
    end if;

    if private.device_has_uploaded(device_id) then
        raise exception 'device id % already registered', device_id;
    end if;

    update private.devices
    set hashed_secret = crypt(device_secret, gen_salt('bf'))
    where id = device_id;
end;
$$;

grant execute on function public.register_device(uuid, text) to anon, authenticated, service_role;

commit;
