-- Small fixes to database helpers and grants.

-- public.log_user_data() inserts into a log_table that does not exist, and no trigger uses it.
drop function if exists public.log_user_data();

-- get_raw_header(item) ignored its argument and returned every header as text. Return the one
-- header asked for, like get_header(item). The no-argument version still returns them all.
create or replace function public.get_raw_header(item text) returns text
    language sql stable
    as $$
    select (current_setting('request.headers', true)::json)->>item
$$;

-- SECURITY DEFINER functions without a fixed search_path resolve unqualified names (crypt,
-- gen_salt, gen_random_bytes, uuid) through the caller's search_path. Pin it, with pg_temp last
-- so a session's temporary objects can never shadow them.
alter function private.is_dashboard_request() set search_path = public, private, extensions, pg_temp;
alter function private.verified_device_id() set search_path = public, private, extensions, pg_temp;
alter function private.verified_device_id_or_null() set search_path = public, private, extensions, pg_temp;
alter function public.register_device(uuid) set search_path = public, private, extensions, pg_temp;

-- The app's roles never need TRUNCATE, and row-level security does not cover it. PostgREST
-- cannot issue it, but a leaked direct-connection role could empty a table in one statement.
revoke truncate on table
    public.cells,
    public.latency_data,
    public.locations,
    public.measurements,
    public.upload_download_data,
    public.fcc_submissions
from anon, authenticated;

alter default privileges for role postgres in schema public revoke truncate on tables from anon, authenticated;
