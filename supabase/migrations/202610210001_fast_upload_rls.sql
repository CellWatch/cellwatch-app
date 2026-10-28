begin;

-- Uploads got slower the more measurements a device had uploaded before (issue #115).
--
-- insert_measurement inserts a measurements row and then one row at a time into
-- upload_download_data, latency_data, locations and cells. Each child insert is checked by
-- "Allow inserting own ...", which looks up the device's measurements:
--     measurement_id in (select id from measurements where device_id = (select verified_device_id()))
-- That lookup is itself filtered by the measurements SELECT policy, which called
-- private.verified_device_id_or_null() without a (select ...) around it. The function is
-- volatile and runs bcrypt (crypt()), so Postgres ran it once for every measurement the device
-- already had, for every child row. A device with 400 measurements took about 12 seconds per
-- upload, past the app's 10 second timeout, so the same upload failed again and again. The
-- app's duplicate check (select measurements with embedded upload_download_data, latency_data,
-- locations and cells) hit the same policies and took about 4 seconds.
--
-- Wrapping a function call in (select ...) makes Postgres run it once per statement instead
-- of once per row. Both helpers only read the request headers and private.devices /
-- private.dashboard_settings, which don't change during a statement, so the result is the
-- same for every row and the policies allow exactly the same rows as before.
--
-- One visible difference: a caller without a valid device secret that reads or writes cells,
-- locations, latency_data or upload_download_data directly may now get no rows or a row level
-- security error instead of "missing x-device-id header" / "incorrect device secret", because
-- Postgres can now check the cheap conditions before calling verified_device_id(). Which of the
-- two such a caller got already depended on the data and the query plan; it is refused either
-- way. Uploads with a wrong secret still fail with "incorrect device secret" on the
-- measurements insert.
--
-- Policies that already wrapped their calls are left alone: the "Allow inserting own ..." and
-- "Allow viewing own ..." policies on cells, latency_data, locations and upload_download_data,
-- "Allow inserting own measurements", and both policies on fcc_submissions.

-- Deploying. Each ALTER POLICY below needs an ACCESS EXCLUSIVE lock on its table, and a lock
-- request that has to wait makes every later request on that table wait behind it. Taken one
-- statement at a time, the migration could hold measurements while it waited for a cron run
-- or a dashboard query to finish with another table; every upload meanwhile would wait behind
-- it and fail at the API's 8 second lock_timeout, and a dashboard query could deadlock with it
-- and roll it back. So take all five locks first, with NOWAIT: either they are all free and
-- this gets them at once, or it gets none and tries again a moment later, without making
-- anyone wait. It gives up after a minute (pause the fcc-submission-retries job and run it
-- again). Once it has them, the rest takes well under a second; lock_timeout makes any other
-- lock it has to wait for fail fast instead.
set local lock_timeout = '3s';
do $$
declare
    give_up_at constant timestamptz := clock_timestamp() + interval '1 minute';
begin
    loop
        begin
            lock table public.measurements, public.latency_data, public.locations,
                public.upload_download_data, public.cells
                in access exclusive mode nowait;
            exit;
        exception when lock_not_available then
            if clock_timestamp() > give_up_at then
                raise exception 'gave up after a minute waiting for a moment when nothing was using the upload tables'
                    using hint = 'Pause the fcc-submission-retries cron job, then run this migration again.';
            end if;
            perform pg_sleep(0.1);
        end;
    end loop;
end;
$$;

alter policy "Allow viewing own measurements" on public.measurements
    using ((select private.is_dashboard_request())
           or (select private.verified_device_id_or_null()) = device_id);

alter policy "Allow viewing latency (dashboard or own)" on public.latency_data
    using ((select private.is_dashboard_request())
           or exists (select 1
                      from public.measurements m
                      where m.id = latency_data.measurement_id
                        and m.device_id = (select private.verified_device_id_or_null())));

alter policy "Allow viewing locations (dashboard or own)" on public.locations
    using ((select private.is_dashboard_request())
           or exists (select 1
                      from public.measurements m
                      where m.id = locations.measurement_id
                        and m.device_id = (select private.verified_device_id_or_null())));

alter policy "Allow viewing ud (dashboard or own)" on public.upload_download_data
    using ((select private.is_dashboard_request())
           or exists (select 1
                      from public.measurements m
                      where m.id = upload_download_data.measurement_id
                        and m.device_id = (select private.verified_device_id_or_null())));

alter policy "dashboard can select cells" on public.cells
    using ((select private.is_dashboard_request()));

alter policy "dashboard can select latency_data" on public.latency_data
    using ((select private.is_dashboard_request()));

alter policy "dashboard can select locations" on public.locations
    using ((select private.is_dashboard_request()));

-- The "own measurements" lookups above filter measurements by device_id, which had no index,
-- so every check read the whole table. The child tables already have indexes on
-- measurement_id (cells_measurement_id_idx, locations_measurement_id_idx, and the unique
-- constraints on latency_data and upload_download_data), so they need nothing new.
--
-- This is a plain create index, not create index concurrently, because concurrently can't run
-- inside a transaction and this file runs in one (so the policy changes and the index go in
-- together or not at all). A plain build blocks writes to measurements while it runs. Built
-- locally, the index took 0.05 seconds on 100k rows and 0.3 seconds on 1M, so uploads that
-- arrive meanwhile just wait a moment. If the table has grown much larger, first run
--     create index concurrently if not exists measurements_device_id_idx on public.measurements (device_id);
-- by hand outside a transaction; this statement then does nothing. A concurrent build that
-- fails or is cancelled leaves an invalid index behind, which `if not exists` would keep, so
-- the lookups would still read the whole table. Such a leftover is dropped and built again.
do $$
begin
    if exists (
        select 1
        from pg_index i
        where i.indexrelid = to_regclass('public.measurements_device_id_idx')
          and not i.indisvalid
    ) then
        drop index public.measurements_device_id_idx;
    end if;
end;
$$;

create index if not exists measurements_device_id_idx on public.measurements (device_id);

commit;
