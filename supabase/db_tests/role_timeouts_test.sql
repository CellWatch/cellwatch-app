-- Tests for 202611010003_record_authenticated_timeout.sql: after the migrations, the API roles
-- have prod's statement timeouts (supabase/role_settings.sql), and applying it again changes
-- nothing. Run by supabase/db_tests/run.sh in a throwaway container. Role settings are
-- cluster-wide, so this sets authenticated to prod's value for the tests that run after it.
\set ON_ERROR_STOP on
set client_min_messages = notice;

create function expect(ok boolean, description text) returns void
language plpgsql as $$
begin
    if ok is distinct from true then
        raise exception 'FAILED: %', description;
    end if;
    raise notice 'ok - %', description;
end;
$$;

create function test_setting(p_role text) returns text[]
language sql stable as $$
    select coalesce((select setconfig from pg_db_role_setting
                     where setrole = p_role::regrole and setdatabase = 0), '{}')
$$;

-- The stock image's value, which a database built from the migrations used to keep.
do $$
begin
    perform expect('statement_timeout=8s' = any (test_setting('authenticated')),
                   'before the migration, authenticated has the stock image''s 8 s');
end;
$$;

\i /migrations/202611010003_record_authenticated_timeout.sql
-- Applying it again changes nothing.
\i /migrations/202611010003_record_authenticated_timeout.sql

do $$
begin
    perform expect('statement_timeout=2min' = any (test_setting('authenticated')),
                   'authenticated has a 2 minute statement_timeout, as on prod');
    perform expect((select count(*) from unnest(test_setting('authenticated')) s
                    where s like 'statement_timeout=%') = 1,
                   'and only one statement_timeout entry after applying it twice');
end;
$$;

\echo 'role_timeouts_test: all checks passed'
