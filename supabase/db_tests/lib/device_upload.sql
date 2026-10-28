-- A phone's upload, run in the background by device_registration_test.sql with
--     psql -v device=<id> -v secret=<secret> -v hold=<seconds> -f /db_tests/lib/device_upload.sql
-- It checks the device secret as the upload policies do (private.verified_device_id()),
-- inserts a measurement, keeps the transaction open for :hold seconds, and logs the outcome
-- ('uploaded' or the error) to test_upload_log.
\set ON_ERROR_STOP on
select set_config('request.headers',
    json_build_object('x-device-id', :'device', 'x-device-secret', :'secret')::text, false) as ignored \gset
select set_config('test.hold', :'hold', false) as ignored \gset

do $$
declare
    device uuid := (current_setting('request.headers')::json ->> 'x-device-id')::uuid;
begin
    perform private.verified_device_id();
    insert into public.measurements (device_id) values (device);
    perform pg_sleep(current_setting('test.hold')::numeric);
    insert into test_upload_log values (device, 'uploaded');
exception when others then
    insert into test_upload_log values (device, sqlerrm);
end;
$$;
