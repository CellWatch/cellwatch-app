-- Shared setup for checking that a caller's temporary objects can't make a SECURITY DEFINER
-- function run the caller's code as its owner. Include it in a fresh session (\c) after
-- `set role` to an unprivileged login role, call the functions, then check the probe with
-- test_shadow_ran_as_owner() below.
--
-- When a function's search_path doesn't list pg_temp, Postgres searches the session's
-- temporary schema first for table and type names. These domains are named like the built-in
-- types the functions use, and their check runs pg_temp.shadow_probe(), which records
-- current_user. If a SECURITY DEFINER function picks one up, the probe records its owner.
-- With pg_temp last in the function's search_path, the built-in types win and the probe only
-- ever records the caller.

create function pg_temp.shadow_probe() returns pg_catalog.bool
language plpgsql as $$
begin
    perform pg_catalog.set_config(
        'test.shadow_ran_as',
        pg_catalog.concat_ws(' ', pg_catalog.current_setting('test.shadow_ran_as', true), current_user),
        false);
    return true;
end;
$$;

create domain pg_temp.text as pg_catalog.text check (pg_temp.shadow_probe());
create domain pg_temp.jsonb as pg_catalog.jsonb check (pg_temp.shadow_probe());
create domain pg_temp.uuid as pg_catalog.uuid check (pg_temp.shadow_probe());

-- Whether the probe ran as anyone other than the caller (the role set before including this).
create function pg_temp.test_shadow_ran_as_owner() returns pg_catalog.bool
language sql as $$
    select exists (
        select 1
        from pg_catalog.unnest(pg_catalog.string_to_array(
            pg_catalog.current_setting('test.shadow_ran_as', true), ' ')) as u(name)
        where u.name operator(pg_catalog.<>) current_user::pg_catalog.text
    );
$$;
