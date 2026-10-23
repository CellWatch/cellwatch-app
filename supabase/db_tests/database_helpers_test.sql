-- Tests for 202610220102_tighten_database_helpers.sql, run by supabase/db_tests/run.sh in a
-- throwaway container. Stand-ins have the live signatures; their bodies don't matter here.
\set ON_ERROR_STOP on
set client_min_messages = notice;

create schema if not exists private;
do $$ begin create role anon nologin; exception when duplicate_object then null; end $$;
do $$ begin create role authenticated nologin; exception when duplicate_object then null; end $$;

create table public.cells (id uuid);
create table public.latency_data (id uuid);
create table public.locations (id uuid);
create table public.measurements (id uuid);
create table public.upload_download_data (id uuid);
create table public.fcc_submissions (id uuid);
grant select, insert, update, delete, truncate on all tables in schema public to anon, authenticated;

create function public.log_user_data() returns trigger language plpgsql as $$ begin return new; end $$;
create function public.get_raw_header(item text) returns text language sql stable as $$
    select current_setting('request.headers', true)::json
$$;
create function private.is_dashboard_request() returns boolean language sql stable security definer as $$ select false $$;
create function private.verified_device_id() returns uuid language sql security definer as $$ select null::uuid $$;
create function private.verified_device_id_or_null() returns uuid language sql security definer as $$ select null::uuid $$;
create function public.register_device(device_id uuid) returns text language sql security definer as $$ select '' $$;

\i /migrations/202610220102_tighten_database_helpers.sql

do $$
begin
    if to_regprocedure('public.log_user_data()') is not null then
        raise exception 'log_user_data still exists';
    end if;
    raise notice 'ok: log_user_data is gone';
end $$;

select set_config('request.headers', '{"user-agent": "test-agent", "host": "example.test"}', false);
do $$
begin
    if public.get_raw_header('user-agent') is distinct from 'test-agent'
        or public.get_raw_header('missing') is not null
    then
        raise exception 'get_raw_header(item) returned %', public.get_raw_header('user-agent');
    end if;
    raise notice 'ok: get_raw_header(item) returns the one header';
end $$;

do $$
declare
    f text;
begin
    foreach f in array array[
        'private.is_dashboard_request()',
        'private.verified_device_id()',
        'private.verified_device_id_or_null()',
        'public.register_device(uuid)'
    ] loop
        if not exists (
            select 1 from pg_proc
            where oid = to_regprocedure(f)
              and 'search_path=public, private, extensions, pg_temp' = any(proconfig)
        ) then
            raise exception '% has no fixed search_path', f;
        end if;
    end loop;
    raise notice 'ok: SECURITY DEFINER device and dashboard checks have a fixed search_path';
end $$;

do $$
declare
    t text;
    r text;
begin
    foreach t in array array['cells', 'latency_data', 'locations', 'measurements', 'upload_download_data', 'fcc_submissions'] loop
        foreach r in array array['anon', 'authenticated'] loop
            if has_table_privilege(r, 'public.' || t, 'TRUNCATE') then
                raise exception '% can still truncate %', r, t;
            end if;
            if not has_table_privilege(r, 'public.' || t, 'INSERT') then
                raise exception '% lost INSERT on %', r, t;
            end if;
        end loop;
    end loop;
    raise notice 'ok: anon and authenticated can no longer truncate the app tables';
end $$;
