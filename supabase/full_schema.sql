
\restrict KpPrRLmO8zOBLYEivKIhhwf6ZVSHohppE19ggB9HFlZXJcseXxvBha2XeySQDu8


SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;


CREATE EXTENSION IF NOT EXISTS "pg_cron" WITH SCHEMA "pg_catalog";






CREATE EXTENSION IF NOT EXISTS "pgsodium";






CREATE SCHEMA IF NOT EXISTS "private";


ALTER SCHEMA "private" OWNER TO "postgres";


COMMENT ON SCHEMA "public" IS 'standard public schema';



CREATE SCHEMA IF NOT EXISTS "published";


ALTER SCHEMA "published" OWNER TO "postgres";


CREATE EXTENSION IF NOT EXISTS "http" WITH SCHEMA "extensions";






CREATE EXTENSION IF NOT EXISTS "pg_graphql" WITH SCHEMA "graphql";






CREATE EXTENSION IF NOT EXISTS "pg_stat_statements" WITH SCHEMA "extensions";






CREATE EXTENSION IF NOT EXISTS "pgcrypto" WITH SCHEMA "extensions";






CREATE EXTENSION IF NOT EXISTS "postgis" WITH SCHEMA "extensions";






CREATE EXTENSION IF NOT EXISTS "supabase_vault" WITH SCHEMA "vault";






CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA "extensions";






CREATE TYPE "public"."cellular_technology" AS ENUM (
    '2G',
    '3G',
    '4G',
    '5G',
    '4G/5G',
    'unknown'
);


ALTER TYPE "public"."cellular_technology" OWNER TO "postgres";


CREATE TYPE "public"."connection_type" AS ENUM (
    'NONE',
    'WIFI',
    'CELLULAR'
);


ALTER TYPE "public"."connection_type" OWNER TO "postgres";


CREATE TYPE "public"."publishable_cell" AS (
	"id" "uuid",
	"measurement_id" "uuid",
	"timestamp" timestamp with time zone,
	"group_offset" numeric
);


ALTER TYPE "public"."publishable_cell" OWNER TO "postgres";


CREATE TYPE "public"."publishable_measurement" AS (
	"id" "uuid",
	"timestamp" timestamp with time zone,
	"group_offset" numeric,
	"center_hex9" bigint,
	"start_hex9" bigint,
	"end_hex9" bigint,
	"center_location" "extensions"."geometry"(Point,4326),
	"start_location" "extensions"."geometry"(Point,4326),
	"end_location" "extensions"."geometry"(Point,4326),
	"cell_tech" "public"."cellular_technology"
);


ALTER TYPE "public"."publishable_measurement" OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "private"."auto_post_fcc_submission"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'private', 'extensions'
    AS $$
begin
    if coalesce(new.submitted, false) = false then
        insert into private.fcc_submission_retry_queue (
            submission_id,
            fcc_env,
            state,
            next_attempt_at
        )
        values (
            new.id,
            'prod',
            'pending',
            now()
        )
        on conflict (submission_id) do update
        set
            state = 'pending',
            next_attempt_at = least(
                private.fcc_submission_retry_queue.next_attempt_at,
                now()
            ),
            updated_on = now();
    end if;

    return new;
end;
$$;


ALTER FUNCTION "private"."auto_post_fcc_submission"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "private"."fcc_submission_is_ready"("sub_id" "uuid") RETURNS boolean
    LANGUAGE "sql" STABLE
    SET "search_path" TO 'public', 'private', 'extensions'
    AS $$
    select count(*) = 3
       and count(distinct lower(m.type::text)) = 3
    from public.measurements m
    where m.group_id = sub_id
      and (
        (
          lower(m.type::text) = 'latency'
          and exists (
            select 1
            from public.latency_data ld
            where ld.measurement_id = m.id
          )
        )
        or
        (
          lower(m.type::text) in ('download', 'upload')
          and exists (
            select 1
            from public.upload_download_data ud
            where ud.measurement_id = m.id
          )
        )
      );
$$;


ALTER FUNCTION "private"."fcc_submission_is_ready"("sub_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "private"."is_dashboard_request"() RETURNS boolean
    LANGUAGE "sql" STABLE SECURITY DEFINER
    AS $$
  select coalesce(
    (current_setting('request.headers', true)::json->>'x-dashboard-secret')
      = (select shared_secret from private.dashboard_settings limit 1),
    false
  )
$$;


ALTER FUNCTION "private"."is_dashboard_request"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "private"."post_fcc_submission"("sub_id" "uuid", "fcc_env" "text") RETURNS boolean
    LANGUAGE "plpgsql"
    SET "search_path" TO 'public', 'private', 'extensions'
    AS $$
declare
    info record;
    payload jsonb;
    tests_payload jsonb;
    response http_response;
begin
    if not exists (
        select 1
        from public.fcc_submissions s
        where s.id = sub_id
    ) then
        raise exception 'no FCC submission found with id %', sub_id;
    end if;

    if not private.fcc_submission_is_ready(sub_id) then
        raise exception 'FCC submission % is incomplete; latency, download, and upload data are required', sub_id;
    end if;

    select i.url, s.decrypted_secret into info
    from private.fcc_submission_info i
        join vault.decrypted_secrets s on i.hash_secret_id = s.id
    where i.env = fcc_env;

    if not found then
        raise exception 'missing FCC submission info for env %', fcc_env;
    end if;

    select v.data into payload
    from private.fcc_submissions_view v
    where v.submission_id = sub_id;

    if not found then
        raise exception 'no FCC submission payload found with id %', sub_id;
    end if;

    tests_payload := payload #> '{submissions,0,tests}';
    if jsonb_typeof(tests_payload) is distinct from 'object'
       or not (tests_payload ?& array['latency', 'download', 'upload']) then
        raise exception 'FCC submission % produced an incomplete tests payload', sub_id;
    end if;

    -- Do not log the payload: it contains contact, device, and location data.
    raise info 'posting FCC submission_id=% env=%', sub_id, fcc_env;

    select * into response from http((
        'POST',
        info.url,
        array[http_header('hash_value', info.decrypted_secret)],
        'application/json',
        payload::text
    )::http_request);

    if response.status is null then
        raise exception 'FCC submission % returned no HTTP status', sub_id;
    end if;

    if response.status < 200 or response.status >= 300 then
        raise exception 'FCC HTTP status % for submission %: %',
            response.status,
            sub_id,
            left(coalesce(response.content, ''), 500);
    end if;

    update public.fcc_submissions set
        submitted = true,
        submission = payload,
        submitted_on = now(),
        submission_response = response.content
    where id = sub_id;

    if not found then
        raise exception 'FCC submission % disappeared before it could be marked submitted', sub_id;
    end if;

    return true;
end;
$$;


ALTER FUNCTION "private"."post_fcc_submission"("sub_id" "uuid", "fcc_env" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "private"."process_fcc_submission_retries"("p_batch_size" integer DEFAULT 1) RETURNS integer
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'private', 'extensions'
    AS $$
declare
    item record;
    processed integer := 0;
    error_text text;
    response_text text;
    new_attempt_count integer;
    delay_seconds integer;
    permanent_failure boolean;
begin
    -- Remove queue entries that were submitted manually or elsewhere.
    delete from private.fcc_submission_retry_queue q
    using public.fcc_submissions s
    where s.id = q.submission_id
      and coalesce(s.submitted, false) = true;

    for item in
        select q.*
        from private.fcc_submission_retry_queue q
        join public.fcc_submissions s
          on s.id = q.submission_id
        where q.state = 'pending'
          and q.next_attempt_at <= clock_timestamp()
          and coalesce(s.submitted, false) = false
        order by q.next_attempt_at
        limit greatest(1, least(coalesce(p_batch_size, 1), 10))
        for update of q skip locked
    loop
        -- A missing provider requires repair, not repeated FCC requests.
        if not exists (
            select 1
            from public.fcc_submissions s
            where s.id = item.submission_id
              and nullif(btrim(s.provider), '') is not null
        ) then
            update private.fcc_submission_retry_queue
            set
                state = 'blocked',
                last_error = 'Missing provider',
                updated_on = now()
            where submission_id = item.submission_id;

            continue;
        end if;

        -- Wait for complete latency, download, and upload payloads.
        -- This does not count as a failed FCC attempt.
        if not private.fcc_submission_is_ready(item.submission_id) then
            update private.fcc_submission_retry_queue
            set
                next_attempt_at = now() + interval '5 minutes',
                last_error =
                    'Waiting for complete latency/download/upload data',
                updated_on = now()
            where submission_id = item.submission_id;

            continue;
        end if;

        begin
            if private.post_fcc_submission(
                item.submission_id,
                item.fcc_env
            ) is distinct from true then
                raise exception 'FCC posting function returned false';
            end if;

            select s.submission_response
            into response_text
            from public.fcc_submissions s
            where s.id = item.submission_id;

            insert into private.fcc_submission_failures (
                submission_id,
                measurement_group_id,
                error_message,
                succeeded,
                fcc_env,
                response_snippet
            )
            values (
                item.submission_id,
                item.submission_id,
                null,
                true,
                item.fcc_env,
                left(coalesce(response_text, ''), 500)
            );

            delete from private.fcc_submission_retry_queue
            where submission_id = item.submission_id;

            processed := processed + 1;

        exception when others then
            error_text := sqlerrm;
            new_attempt_count := item.attempt_count + 1;

            -- Retry 408, 429, 5xx, and transport errors.
            -- Block other 4xx validation errors.
            permanent_failure :=
                error_text ~ 'FCC HTTP status 4[0-9][0-9]'
                and error_text !~ 'FCC HTTP status (408|429)';

            insert into private.fcc_submission_failures (
                submission_id,
                measurement_group_id,
                error_message,
                succeeded,
                fcc_env,
                response_snippet
            )
            values (
                item.submission_id,
                item.submission_id,
                error_text,
                false,
                item.fcc_env,
                left(error_text, 500)
            );

            delay_seconds := least(
                3600,
                (
                    60 * power(
                        2,
                        least(new_attempt_count - 1, 6)
                    )
                )::integer
            );

            update private.fcc_submission_retry_queue
            set
                state = case
                    when permanent_failure
                      or new_attempt_count >= 6
                        then 'blocked'
                    else 'pending'
                end,
                attempt_count = new_attempt_count,
                last_attempt_at = now(),
                next_attempt_at =
                    now() + make_interval(secs => delay_seconds),
                last_error = error_text,
                updated_on = now()
            where submission_id = item.submission_id;

            processed := processed + 1;
        end;
    end loop;

    return processed;
end;
$$;


ALTER FUNCTION "private"."process_fcc_submission_retries"("p_batch_size" integer) OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "private"."verified_device_id"() RETURNS "uuid"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
declare
    device_id text;
    device_secret text;
    hashed text;
begin
    device_id := current_setting('request.headers', true)::json->>'x-device-id';
    device_secret := current_setting('request.headers', true)::json->>'x-device-secret';

    if device_id is null or device_id = '' then
        raise exception 'missing x-device-id header';
    end if;

    if device_secret is null or device_secret = '' then
        raise exception 'missing x-device-secret header';
    end if;

    select hashed_secret into hashed from private.devices where id = uuid(device_id);
    if not found then
        raise exception 'device id % is not registered', device_id;
    end if;

    if crypt(device_secret, hashed) <> hashed then
        raise exception 'incorrect device secret';
    end if;

    return uuid(device_id);
end;
$$;


ALTER FUNCTION "private"."verified_device_id"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "private"."verified_device_id_or_null"() RETURNS "uuid"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
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


ALTER FUNCTION "private"."verified_device_id_or_null"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."any_uuid_agg"("uuid", "uuid") RETURNS "uuid"
    LANGUAGE "sql" IMMUTABLE
    AS $_$
    select coalesce($1, $2);
$_$;


ALTER FUNCTION "public"."any_uuid_agg"("uuid", "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."fcc_submission_update_source_ip"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    AS $$
    BEGIN
        NEW.source_ip = SPLIT_PART(get_header('x-forwarded-for') || ',', ',', 1);
        RETURN NEW;
    END;
$$;


ALTER FUNCTION "public"."fcc_submission_update_source_ip"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."fix_jitter"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    AS $$
begin
    new.jitter = case
        when (select app_version from measurements where id = new.measurement_id) is null
            or (select app_version from measurements where id = new.measurement_id) in ('1.0.0-alpha.4', '1.0.0-alpha.3', '1.0.0-alpha.2')
            then sqrt(new.jitter)
        else new.jitter
    end;
    return new;
end
$$;


ALTER FUNCTION "public"."fix_jitter"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."force_created_on"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    AS $$
begin
    NEW.created_on = now();
    NEW.updated_on = now();
    return new;
end
$$;


ALTER FUNCTION "public"."force_created_on"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_header"("item" "text") RETURNS "text"
    LANGUAGE "sql" STABLE
    AS $$
    SELECT (current_setting('request.headers', true)::json)->>item
$$;


ALTER FUNCTION "public"."get_header"("item" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_raw_header"() RETURNS json
    LANGUAGE "sql" STABLE
    AS $$
    SELECT current_setting('request.headers', true)::json
$$;


ALTER FUNCTION "public"."get_raw_header"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."get_raw_header"("item" "text") RETURNS "text"
    LANGUAGE "sql" STABLE
    AS $$
    SELECT current_setting('request.headers', true)::json
$$;


ALTER FUNCTION "public"."get_raw_header"("item" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."hex_ancestor"("hex" bigint, "res" bigint) RETURNS bigint
    LANGUAGE "sql" IMMUTABLE
    AS $$
    select (
        -- clear resolution bits
        (hex::bit(64) & x'ff0fffffffffffff')
        -- add new resolution
        | ((res::bit(64) << 52) & x'00f0000000000000')
        -- set lower digit bits to all 1s
        | (x'0000ffffffffffff' >> ((res::int + 1) * 3))
    )::bigint
$$;


ALTER FUNCTION "public"."hex_ancestor"("hex" bigint, "res" bigint) OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."hex_res"("hex" bigint) RETURNS bigint
    LANGUAGE "sql" IMMUTABLE
    AS $$
    select ((hex::bit(64) & x'00f0000000000000') >> 52)::bigint
$$;


ALTER FUNCTION "public"."hex_res"("hex" bigint) OWNER TO "postgres";

SET default_tablespace = '';

SET default_table_access_method = "heap";


CREATE TABLE IF NOT EXISTS "public"."cells" (
    "id" "uuid" DEFAULT "extensions"."uuid_generate_v4"() NOT NULL,
    "timestamp" timestamp with time zone DEFAULT "now"() NOT NULL,
    "cell_id" numeric,
    "physical_cell_id" numeric,
    "cell_connection" numeric,
    "network_generation" character varying,
    "network_subtype" character varying,
    "signal_strength" numeric,
    "rssi" numeric,
    "rsrp" numeric,
    "rsrq" numeric,
    "sinr" numeric,
    "csi_rsrp" numeric,
    "csi_rsrq" numeric,
    "csi_sinr" numeric,
    "cqi" numeric,
    "spectrum_band" "text",
    "spectrum_bandwidth" double precision,
    "arfcn" numeric,
    "measurement_id" "uuid",
    "created_on" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_on" timestamp with time zone DEFAULT "now"() NOT NULL
);


ALTER TABLE "public"."cells" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."latency_data" (
    "id" "uuid" DEFAULT "extensions"."uuid_generate_v4"() NOT NULL,
    "measurement_id" "uuid",
    "rtt" numeric,
    "jitter" numeric,
    "sent" numeric,
    "received" numeric,
    "servers" "text"[],
    "created_on" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_on" timestamp with time zone DEFAULT "now"() NOT NULL
);


ALTER TABLE "public"."latency_data" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."locations" (
    "id" "uuid" DEFAULT "extensions"."uuid_generate_v4"() NOT NULL,
    "timestamp" timestamp with time zone DEFAULT "now"() NOT NULL,
    "lat" double precision,
    "lon" double precision,
    "accuracy" double precision,
    "speed" double precision,
    "speed_accuracy" double precision,
    "heading" double precision,
    "measurement_id" "uuid",
    "created_on" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_on" timestamp with time zone DEFAULT "now"() NOT NULL
);


ALTER TABLE "public"."locations" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."measurements" (
    "id" "uuid" DEFAULT "extensions"."uuid_generate_v4"() NOT NULL,
    "group_id" "uuid",
    "campaign_id" "uuid",
    "session_id" "uuid",
    "device_id" "uuid",
    "device_manufacturer" "text",
    "device_model" "text",
    "device_os_name" "text",
    "device_os_version" "text",
    "app_name" "text",
    "provider" "text",
    "type" character varying NOT NULL,
    "timestamp" timestamp with time zone DEFAULT "now"() NOT NULL,
    "duration" numeric,
    "scheduled" boolean,
    "success" boolean,
    "carrier_aggregation" boolean,
    "network_connected" boolean,
    "network_available" boolean,
    "network_roaming" boolean,
    "extra_data" "jsonb",
    "created_on" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_on" timestamp with time zone DEFAULT "now"() NOT NULL,
    "sim_mobile_country_code" "text",
    "sim_mobile_network_code" "text",
    "net_mobile_country_code" "text",
    "net_mobile_network_code" "text",
    "connection_type" "public"."connection_type",
    "cellular_data_enabled" boolean,
    "app_version" "text",
    CONSTRAINT "type_in_scope" CHECK ((("type")::"text" = ANY (ARRAY[('download'::character varying)::"text", ('upload'::character varying)::"text", ('latency'::character varying)::"text"])))
);


ALTER TABLE "public"."measurements" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."upload_download_data" (
    "id" "uuid" DEFAULT "extensions"."uuid_generate_v4"() NOT NULL,
    "measurement_id" "uuid",
    "warmup_duration" numeric,
    "warmup_bytes" numeric,
    "duration" numeric,
    "bytes" numeric,
    "servers" "text"[],
    "created_on" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_on" timestamp with time zone DEFAULT "now"() NOT NULL,
    "application_bytes" numeric,
    "bytes_per_sec" numeric,
    "application_bytes_per_sec" numeric
);


ALTER TABLE "public"."upload_download_data" OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."insert_measurement"("in_measurement" "public"."measurements", "in_measurement_data" "public"."upload_download_data", "in_latency_data" "public"."latency_data", "in_locations" "public"."locations"[], "in_cells" "public"."cells"[]) RETURNS "public"."measurements"
    LANGUAGE "plpgsql"
    AS $$
declare
    new_measurement measurements%ROWTYPE;
    location locations%ROWTYPE;
    cell cells%ROWTYPE;
begin
    insert into measurements select in_measurement.* returning * into new_measurement;

    -- insert related measurement data
    if in_measurement_data is null then
        -- do nothing
    else
        in_measurement_data.measurement_id = new_measurement.id;
        insert into upload_download_data select in_measurement_data.*;
    end if;

    -- insert related latency data
    if in_latency_data is null then
        -- do nothing
    else
        in_latency_data.measurement_id = new_measurement.id;
        insert into latency_data select in_latency_data.*;
    end if;

    -- Insert related location records
    if in_locations is null then
        -- do nothing
    else
        foreach location in array in_locations loop
                location.measurement_id = new_measurement.id;
                insert into locations select location.*;
            end loop;
    end if;

    -- Insert related cell records
    if in_cells is null then
        -- do nothing
    else
        foreach cell in array in_cells loop
                cell.measurement_id = new_measurement.id;
                insert into cells select cell.*;
            end loop;
    end if;

    return new_measurement;
end;
$$;


ALTER FUNCTION "public"."insert_measurement"("in_measurement" "public"."measurements", "in_measurement_data" "public"."upload_download_data", "in_latency_data" "public"."latency_data", "in_locations" "public"."locations"[], "in_cells" "public"."cells"[]) OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."log_user_data"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    AS $$
BEGIN
  INSERT INTO log_table(table_name, key, user_agent, host, origin, referer, ip, request_header)
  VALUES(TG_TABLE_NAME::regclass::text, NEW.id::text, get_header('user-agent'), get_header('host'), get_header('origin'), get_header('referer'), SPLIT_PART(get_header('x-forwarded-for') || ',', ',', 1), get_raw_header());
  RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."log_user_data"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."publish_data"("in_measurements" "public"."publishable_measurement"[], "in_cells" "public"."publishable_cell"[], OUT "measurement_ids" "uuid"[], OUT "cell_ids" "uuid"[]) RETURNS "record"
    LANGUAGE "plpgsql"
    AS $$
begin
    with ids as (
        insert into published.measurements (
            id,
            group_id,
            campaign_id,
            session_id,
            device_id,
            device_manufacturer,
            device_model,
            device_os_name,
            device_os_version,
            app_name,
            app_version,
            provider,
            type,
            timestamp,
            duration,
            scheduled,
            success,
            carrier_aggregation,
            network_connected,
            network_available,
            network_roaming,
            sim_mobile_country_code,
            sim_mobile_network_code,
            net_mobile_country_code,
            net_mobile_network_code,
            connection_type,
            cellular_data_enabled,
            extra_data,
            group_offset,
            cell_tech,
            center_hex9,
            start_hex9,
            end_hex9,
            center_location,
            start_location,
            end_location
        ) select
            im.id,
            group_id,
            campaign_id,
            session_id,
            device_id,
            device_manufacturer,
            device_model,
            device_os_name,
            device_os_version,
            app_name,
            app_version,
            provider,
            type,
            im.timestamp,
            duration,
            scheduled,
            success,
            carrier_aggregation,
            network_connected,
            network_available,
            network_roaming,
            sim_mobile_country_code,
            sim_mobile_network_code,
            net_mobile_country_code,
            net_mobile_network_code,
            connection_type,
            cellular_data_enabled,
            extra_data,
            group_offset,
            cell_tech,
            center_hex9,
            start_hex9,
            end_hex9,
            center_location,
            start_location,
            end_location
        from unnest(in_measurements) as im inner join public.measurements as pm on pm.id = im.id
        on conflict do nothing
        returning id
    ) select array_agg(id) into measurement_ids from ids;

    with ids as (
        insert into published.cells (
            id,
            measurement_id,
            timestamp,
            cell_id,
            physical_cell_id,
            cell_connection,
            network_generation,
            network_subtype,
            signal_strength,
            rssi,
            rsrp,
            rsrq,
            sinr,
            csi_rsrp,
            csi_rsrq,
            csi_sinr,
            cqi,
            spectrum_band,
            spectrum_bandwidth,
            arfcn,
            group_offset
        ) select
            ic.id,
            ic.measurement_id,
            ic.timestamp,
            cell_id,
            physical_cell_id,
            cell_connection,
            network_generation,
            network_subtype,
            signal_strength,
            rssi,
            rsrp,
            rsrq,
            sinr,
            csi_rsrp,
            csi_rsrq,
            csi_sinr,
            cqi,
            spectrum_band,
            spectrum_bandwidth,
            arfcn,
            group_offset
        from unnest(in_cells) as ic inner join public.cells as pc on pc.id = ic.id
        where ic.measurement_id = any(measurement_ids)
        on conflict do nothing
        returning id
    ) select array_agg(id) into cell_ids from ids;

    insert into published.latency_data (
        id,
        measurement_id,
        rtt,
        jitter,
        sent,
        received,
        servers
    ) select
        id,
        measurement_id,
        rtt,
        jitter,
        sent,
        received,
        servers
    from public.latency_data
    where measurement_id = any(measurement_ids);

    insert into published.throughput_data (
        id,
        measurement_id,
        warmup_duration,
        warmup_bytes,
        duration,
        bytes,
        bytes_per_sec,
        application_bytes,
        application_bytes_per_sec,
        servers
    ) select
        id,
        measurement_id,
        warmup_duration,
        warmup_bytes,
        duration,
        bytes,
        coalesce(
            bytes_per_sec,
            case when duration = 0 and bytes = 0 then 0::float
                when duration = 0 then null
                else bytes::float / (duration::float / 1000000.0)
            end
        ),
        application_bytes,
        coalesce(
            application_bytes_per_sec,
            case when duration = 0 and application_bytes = 0 then 0::float
                when duration = 0 then null
                else application_bytes::float / (duration::float / 1000000.0)
            end
        ),
        servers
    from public.upload_download_data
    where measurement_id = any(measurement_ids);
end;
$$;


ALTER FUNCTION "public"."publish_data"("in_measurements" "public"."publishable_measurement"[], "in_cells" "public"."publishable_cell"[], OUT "measurement_ids" "uuid"[], OUT "cell_ids" "uuid"[]) OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."register_device"("device_id" "uuid") RETURNS "text"
    LANGUAGE "plpgsql" SECURITY DEFINER
    AS $$
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


ALTER FUNCTION "public"."register_device"("device_id" "uuid") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."rpc_group_detail"("group_id" "text") RETURNS "jsonb"
    LANGUAGE "sql" STABLE SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
with m as (
  select *
  from measurements
  where group_id = group_id::uuid
),
latest_loc as (
  select distinct on (measurement_id)
    measurement_id, lat, lon, accuracy, timestamp
  from locations
  where measurement_id in (select id from m)
  order by measurement_id, timestamp desc
),
latest_cell as (
  select distinct on (measurement_id)
    measurement_id, network_generation, rsrp, sinr, rsrq, rssi, signal_strength, timestamp
  from cells
  where measurement_id in (select id from m)
  order by measurement_id, timestamp desc
),
ud as (
  select *
  from upload_download_data
  where measurement_id in (select id from m)
),
ld as (
  select *
  from latency_data
  where measurement_id in (select id from m)
),
parts as (
  select
    m.id::text as measurement_id,
    m.type,
    m.timestamp as ts,
    m.provider,
    ll.lat,
    ll.lon,
    ll.accuracy as accuracy_m,
    lc.network_generation,
    lc.rsrp,
    lc.sinr,
    case
      when m.type in ('download','upload') then
        (coalesce(ud.bytes_per_sec, ud.application_bytes_per_sec, (ud.bytes / nullif(ud.duration, 0))) * 8.0) / 1e6
      else null
    end as mbps,
    case when m.type = 'latency' then ld.rtt end as ping_ms,
    case when m.type = 'latency' then ld.jitter end as jitter_ms,
    case
      when m.type = 'latency' and ld.sent is not null and ld.received is not null and ld.sent > 0
        then (100.0 * greatest(0, ld.sent - ld.received)) / ld.sent
      else null
    end as loss_pct
  from m
  left join latest_loc ll on ll.measurement_id = m.id
  left join latest_cell lc on lc.measurement_id = m.id
  left join ud on ud.measurement_id = m.id
  left join ld on ld.measurement_id = m.id
),
pick as (
  select
    max(ts) as ts,
    coalesce(max(provider) filter (where provider is not null), 'Unknown') as provider,
    avg(lon) filter (where lon is not null) as lon,
    avg(lat) filter (where lat is not null) as lat,
    min(accuracy_m) filter (where accuracy_m is not null) as accuracy_m,
    max(network_generation) as network_generation,
    max(rsrp) as rsrp,
    max(sinr) as sinr
  from parts
),
dl as (
  select mbps from parts where type='download' order by ts desc nulls last limit 1
),
ul as (
  select mbps from parts where type='upload' order by ts desc nulls last limit 1
),
la as (
  select ping_ms, jitter_ms, loss_pct from parts where type='latency' order by ts desc nulls last limit 1
)
select jsonb_build_object(
  'group_id', group_id::text,
  'timestamp', (select ts from pick),
  'provider', (select provider from pick),
  'location', jsonb_build_object(
    'lat', (select lat from pick),
    'lon', (select lon from pick),
    'accuracy_m', (select accuracy_m from pick)
  ),
  'cell', jsonb_build_object(
    'network_generation', (select network_generation from pick),
    'rsrp', (select rsrp from pick),
    'sinr', (select sinr from pick)
  ),
  'tests', jsonb_build_object(
    'download', jsonb_build_object('mbps', (select mbps from dl)),
    'upload', jsonb_build_object('mbps', (select mbps from ul)),
    'latency', jsonb_build_object(
      'rtt_ms', (select ping_ms from la),
      'jitter_ms', (select jitter_ms from la),
      'loss_pct', (select loss_pct from la)
    )
  )
);
$$;


ALTER FUNCTION "public"."rpc_group_detail"("group_id" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."rpc_group_summaries"("min_lng" double precision, "min_lat" double precision, "max_lng" double precision, "max_lat" double precision, "t_from" timestamp with time zone DEFAULT NULL::timestamp with time zone, "t_to" timestamp with time zone DEFAULT NULL::timestamp with time zone, "providers" "text"[] DEFAULT NULL::"text"[], "conn" "text"[] DEFAULT NULL::"text"[], "dl_min" double precision DEFAULT NULL::double precision, "dl_max" double precision DEFAULT NULL::double precision, "ul_min" double precision DEFAULT NULL::double precision, "ul_max" double precision DEFAULT NULL::double precision, "lat_min" double precision DEFAULT NULL::double precision, "lat_max" double precision DEFAULT NULL::double precision, "lim" integer DEFAULT 50000) RETURNS SETOF "jsonb"
    LANGUAGE "sql" STABLE SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
with latest_loc as (
  select distinct on (measurement_id)
    measurement_id, lat, lon, accuracy, timestamp
  from locations
  order by measurement_id, timestamp desc
),
latest_cell as (
  select distinct on (measurement_id)
    measurement_id, network_generation, rsrp, sinr, rsrq, rssi, signal_strength, timestamp
  from cells
  order by measurement_id, timestamp desc
),
meas as (
  select
    m.id,
    m.group_id,
    m.provider,
    m.type,
    m.timestamp as ts,
    ll.lat,
    ll.lon,
    ll.accuracy as accuracy_m,
    lc.network_generation,
    case
      when m.type = 'download' then
        (coalesce(ud.bytes_per_sec, ud.application_bytes_per_sec, (ud.bytes / nullif(ud.duration, 0))) * 8.0) / 1e6
      else null
    end as dl_mbps,
    case
      when m.type = 'upload' then
        (coalesce(ud.bytes_per_sec, ud.application_bytes_per_sec, (ud.bytes / nullif(ud.duration, 0))) * 8.0) / 1e6
      else null
    end as ul_mbps,
    case when m.type = 'latency' then ld.rtt end as ping_ms,
    case when m.type = 'latency' then ld.jitter end as jitter_ms,
    case
      when m.type = 'latency' and ld.sent is not null and ld.received is not null and ld.sent > 0
        then (100.0 * greatest(0, ld.sent - ld.received)) / ld.sent
      else null
    end as loss_pct
  from measurements m
  left join latest_loc ll on ll.measurement_id = m.id
  left join latest_cell lc on lc.measurement_id = m.id
  left join upload_download_data ud on ud.measurement_id = m.id
  left join latency_data ld on ld.measurement_id = m.id
  where m.group_id is not null
),
gs as (
  select
    group_id,
    max(ts) as ts,
    coalesce(max(provider) filter (where provider is not null), 'Unknown') as provider,
    case
      when max(network_generation) ilike '%NR%' or max(network_generation) ilike '%5%' then '5G'
      when max(network_generation) ilike '%LTE%' or max(network_generation) ilike '%4%' then '4G'
      else 'Other'
    end as conn_tag,
    avg(lon) filter (where lon is not null) as lon,
    avg(lat) filter (where lat is not null) as lat,
    min(accuracy_m) filter (where accuracy_m is not null) as accuracy_m,
    max(dl_mbps) as dl_mbps,
    max(ul_mbps) as ul_mbps,
    max(ping_ms) as ping_ms,
    max(jitter_ms) as jitter_ms,
    max(loss_pct) as loss_pct
  from meas
  group by group_id
),
f as (
  select *
  from gs
  where
    lon between min_lng and max_lng
    and lat between min_lat and max_lat
    and (t_from is null or ts >= t_from)
    and (t_to is null or ts <= t_to)
    and (providers is null or cardinality(providers) = 0 or provider = any(providers))
    and (conn is null or cardinality(conn) = 0 or conn_tag = any(conn))
    and (dl_min is null or (dl_mbps is not null and dl_mbps >= dl_min))
    and (dl_max is null or (dl_mbps is not null and dl_mbps <= dl_max))
    and (ul_min is null or (ul_mbps is not null and ul_mbps >= ul_min))
    and (ul_max is null or (ul_mbps is not null and ul_mbps <= ul_max))
    and (lat_min is null or (ping_ms is not null and ping_ms >= lat_min))
    and (lat_max is null or (ping_ms is not null and ping_ms <= lat_max))
    and lon is not null and lat is not null
  order by ts desc nulls last
  limit greatest(1, least(coalesce(lim, 50000), 200000))
)
select jsonb_build_object(
  'group_id', f.group_id::text,
  'provider', f.provider,
  'conn_tag', f.conn_tag,
  'timestamp', f.ts,
  'lon', f.lon,
  'lat', f.lat,
  'accuracy_m', f.accuracy_m,
  'dl_mbps', f.dl_mbps,
  'ul_mbps', f.ul_mbps,
  'ping_ms', f.ping_ms,
  'jitter_ms', f.jitter_ms,
  'loss_pct', f.loss_pct
)
from f;
$$;


ALTER FUNCTION "public"."rpc_group_summaries"("min_lng" double precision, "min_lat" double precision, "max_lng" double precision, "max_lat" double precision, "t_from" timestamp with time zone, "t_to" timestamp with time zone, "providers" "text"[], "conn" "text"[], "dl_min" double precision, "dl_max" double precision, "ul_min" double precision, "ul_max" double precision, "lat_min" double precision, "lat_max" double precision, "lim" integer) OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."rpc_map_points"("min_lng" double precision, "min_lat" double precision, "max_lng" double precision, "max_lat" double precision, "t_from" timestamp with time zone DEFAULT NULL::timestamp with time zone, "t_to" timestamp with time zone DEFAULT NULL::timestamp with time zone, "providers" "text"[] DEFAULT NULL::"text"[], "conn" "text"[] DEFAULT NULL::"text"[], "dl_min" double precision DEFAULT NULL::double precision, "dl_max" double precision DEFAULT NULL::double precision, "ul_min" double precision DEFAULT NULL::double precision, "ul_max" double precision DEFAULT NULL::double precision, "lat_min" double precision DEFAULT NULL::double precision, "lat_max" double precision DEFAULT NULL::double precision, "lim" integer DEFAULT 2000) RETURNS SETOF "jsonb"
    LANGUAGE "sql" STABLE SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
with latest_loc as (
  select distinct on (measurement_id)
    measurement_id, lat, lon, accuracy, timestamp
  from locations
  order by measurement_id, timestamp desc
),
latest_cell as (
  select distinct on (measurement_id)
    measurement_id, network_generation, rsrp, sinr, rsrq, rssi, signal_strength, timestamp
  from cells
  order by measurement_id, timestamp desc
),
meas as (
  select
    m.id,
    m.group_id,
    m.provider,
    m.type,
    m.timestamp as ts,
    ll.lat,
    ll.lon,
    ll.accuracy as accuracy_m,
    lc.network_generation,
    case
      when m.type = 'download' then
        (coalesce(ud.bytes_per_sec, ud.application_bytes_per_sec, (ud.bytes / nullif(ud.duration, 0))) * 8.0) / 1e6
      else null
    end as dl_mbps,
    case
      when m.type = 'upload' then
        (coalesce(ud.bytes_per_sec, ud.application_bytes_per_sec, (ud.bytes / nullif(ud.duration, 0))) * 8.0) / 1e6
      else null
    end as ul_mbps,
    case when m.type = 'latency' then (ld.rtt / 1000.0) end as ping_ms,
    case when m.type = 'latency' then (ld.jitter / 1000.0) end as jitter_ms,
    case
      when m.type = 'latency' and ld.sent is not null and ld.received is not null and ld.sent > 0
        then (100.0 * greatest(0, ld.sent - ld.received)) / ld.sent
      else null
    end as loss_pct
  from measurements m
  left join latest_loc ll on ll.measurement_id = m.id
  left join latest_cell lc on lc.measurement_id = m.id
  left join upload_download_data ud on ud.measurement_id = m.id
  left join latency_data ld on ld.measurement_id = m.id
  where m.group_id is not null
),
gs as (
  select
    group_id,
    max(ts) as ts,
    coalesce(max(provider) filter (where provider is not null), 'Unknown') as provider,
    case
      when max(network_generation) ilike '%NR%' or max(network_generation) ilike '%5%' then '5G'
      when max(network_generation) ilike '%LTE%' or max(network_generation) ilike '%4%' then '4G'
      else 'Other'
    end as conn_tag,
    avg(lon) filter (where lon is not null) as lon,
    avg(lat) filter (where lat is not null) as lat,
    min(accuracy_m) filter (where accuracy_m is not null) as accuracy_m,
    max(dl_mbps) as dl_mbps,
    max(ul_mbps) as ul_mbps,
    max(ping_ms) as ping_ms,
    max(jitter_ms) as jitter_ms,
    max(loss_pct) as loss_pct
  from meas
  group by group_id
),
f as (
  select *
  from gs
  where
    lon between min_lng and max_lng
    and lat between min_lat and max_lat
    and (t_from is null or ts >= t_from)
    and (t_to is null or ts <= t_to)
    and (providers is null or cardinality(providers) = 0 or provider = any(providers))
    and (conn is null or cardinality(conn) = 0 or conn_tag = any(conn))
    and (dl_min is null or (dl_mbps is not null and dl_mbps >= dl_min))
    and (dl_max is null or (dl_mbps is not null and dl_mbps <= dl_max))
    and (ul_min is null or (ul_mbps is not null and ul_mbps >= ul_min))
    and (ul_max is null or (ul_mbps is not null and ul_mbps <= ul_max))
    and (lat_min is null or (ping_ms is not null and ping_ms >= lat_min))
    and (lat_max is null or (ping_ms is not null and ping_ms <= lat_max))
    and lon is not null and lat is not null
  order by ts desc nulls last
  limit greatest(1, least(coalesce(lim, 2000), 20000))
)
select jsonb_build_object(
  'group_id', f.group_id::text,
  'provider', f.provider,
  'conn_tag', f.conn_tag,
  'timestamp', f.ts,
  'center', jsonb_build_array(f.lon, f.lat),
  'stats', jsonb_build_object(
    'down_mbps', f.dl_mbps,
    'up_mbps', f.ul_mbps,
    'ping_ms', f.ping_ms,
    'jitter_ms', f.jitter_ms,
    'loss_pct', f.loss_pct
  )
)
from f;
$$;


ALTER FUNCTION "public"."rpc_map_points"("min_lng" double precision, "min_lat" double precision, "max_lng" double precision, "max_lat" double precision, "t_from" timestamp with time zone, "t_to" timestamp with time zone, "providers" "text"[], "conn" "text"[], "dl_min" double precision, "dl_max" double precision, "ul_min" double precision, "ul_max" double precision, "lat_min" double precision, "lat_max" double precision, "lim" integer) OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."set_updated_on"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    AS $$
begin
  new.updated_on = now();
  return new;
end
$$;


ALTER FUNCTION "public"."set_updated_on"() OWNER TO "postgres";


CREATE AGGREGATE "public"."any_uuid"("uuid") (
    SFUNC = "public"."any_uuid_agg",
    STYPE = "uuid"
);


ALTER AGGREGATE "public"."any_uuid"("uuid") OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "published"."measurements" (
    "id" "uuid" NOT NULL,
    "group_id" "uuid",
    "campaign_id" "uuid",
    "session_id" "uuid",
    "device_id" "uuid",
    "device_manufacturer" "text",
    "device_model" "text",
    "device_os_name" "text",
    "device_os_version" "text",
    "app_name" "text",
    "app_version" "text",
    "provider" "text",
    "type" "text" NOT NULL,
    "timestamp" timestamp with time zone,
    "duration" numeric,
    "scheduled" boolean,
    "success" boolean,
    "carrier_aggregation" boolean,
    "network_connected" boolean,
    "network_available" boolean,
    "network_roaming" boolean,
    "sim_mobile_country_code" "text",
    "sim_mobile_network_code" "text",
    "net_mobile_country_code" "text",
    "net_mobile_network_code" "text",
    "connection_type" "public"."connection_type",
    "cellular_data_enabled" boolean,
    "extra_data" "jsonb",
    "created_on" timestamp with time zone NOT NULL,
    "updated_on" timestamp with time zone NOT NULL,
    "group_offset" numeric,
    "cell_tech" "public"."cellular_technology",
    "center_hex9" bigint,
    "start_hex9" bigint,
    "end_hex9" bigint,
    "center_location" "extensions"."geometry"(Point,4326),
    "start_location" "extensions"."geometry"(Point,4326),
    "end_location" "extensions"."geometry"(Point,4326)
);


ALTER TABLE "published"."measurements" OWNER TO "postgres";


CREATE OR REPLACE VIEW "published"."measurement_groups" WITH ("security_invoker"='true') AS
 SELECT "m"."group_id",
    "public"."any_uuid"("l"."id") AS "latency_id",
    "public"."any_uuid"("d"."id") AS "download_id",
    "public"."any_uuid"("u"."id") AS "upload_id"
   FROM ((("published"."measurements" "m"
     LEFT JOIN "published"."measurements" "l" ON ((("l"."group_id" = "m"."group_id") AND ("l"."type" = 'latency'::"text"))))
     LEFT JOIN "published"."measurements" "d" ON ((("d"."group_id" = "m"."group_id") AND ("d"."type" = 'download'::"text"))))
     LEFT JOIN "published"."measurements" "u" ON ((("u"."group_id" = "m"."group_id") AND ("u"."type" = 'upload'::"text"))))
  GROUP BY "m"."group_id";


ALTER VIEW "published"."measurement_groups" OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "published"."measurement_groups"("published"."measurements") RETURNS SETOF "published"."measurement_groups"
    LANGUAGE "sql" STABLE ROWS 1
    AS $_$
    select * from published.measurement_groups where group_id = $1.group_id
$_$;


ALTER FUNCTION "published"."measurement_groups"("published"."measurements") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "published"."measurements_in_hex"("hex" bigint, "match_center" boolean DEFAULT true, "match_start" boolean DEFAULT false, "match_end" boolean DEFAULT false) RETURNS SETOF "published"."measurements"
    LANGUAGE "sql"
    AS $$
    select *
    from published.measurements
    where (match_center and hex_ancestor(center_hex9, hex_res(hex)) = hex)
        or (match_start and hex_ancestor(start_hex9, hex_res(hex)) = hex)
        or (match_end and hex_ancestor(end_hex9, hex_res(hex)) = hex)
$$;


ALTER FUNCTION "published"."measurements_in_hex"("hex" bigint, "match_center" boolean, "match_start" boolean, "match_end" boolean) OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "published"."measurements_in_hex"("hex_string" "text", "match_center" boolean DEFAULT true, "match_start" boolean DEFAULT false, "match_end" boolean DEFAULT false) RETURNS SETOF "published"."measurements"
    LANGUAGE "sql"
    AS $$
    select published.measurements_in_hex(
        ('x' || lpad(hex_string, 16, '0'))::bit(64)::bigint,
        match_center,
        match_start,
        match_end
    )
$$;


ALTER FUNCTION "published"."measurements_in_hex"("hex_string" "text", "match_center" boolean, "match_start" boolean, "match_end" boolean) OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "private"."dashboard_settings" (
    "id" boolean DEFAULT true NOT NULL,
    "shared_secret" "text" NOT NULL
);


ALTER TABLE "private"."dashboard_settings" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "private"."devices" (
    "id" "uuid" NOT NULL,
    "hashed_secret" "text" NOT NULL
);


ALTER TABLE "private"."devices" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "private"."fcc_submission_failures" (
    "id" "uuid" DEFAULT "extensions"."uuid_generate_v4"() NOT NULL,
    "submission_id" "uuid" NOT NULL,
    "measurement_group_id" "uuid",
    "error_message" "text",
    "occurred_on" timestamp with time zone DEFAULT "now"() NOT NULL,
    "succeeded" boolean DEFAULT false NOT NULL,
    "fcc_env" "text",
    "response_status" integer,
    "response_snippet" "text"
);


ALTER TABLE "private"."fcc_submission_failures" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "private"."fcc_submission_info" (
    "env" "text" NOT NULL,
    "url" "text" NOT NULL,
    "hash_secret_id" "uuid" NOT NULL
);


ALTER TABLE "private"."fcc_submission_info" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "private"."fcc_submission_retry_queue" (
    "submission_id" "uuid" NOT NULL,
    "fcc_env" "text" DEFAULT 'prod'::"text" NOT NULL,
    "state" "text" DEFAULT 'pending'::"text" NOT NULL,
    "attempt_count" integer DEFAULT 0 NOT NULL,
    "next_attempt_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "last_attempt_at" timestamp with time zone,
    "last_error" "text",
    "created_on" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_on" timestamp with time zone DEFAULT "now"() NOT NULL,
    CONSTRAINT "fcc_submission_retry_queue_state_check" CHECK (("state" = ANY (ARRAY['pending'::"text", 'blocked'::"text"])))
);


ALTER TABLE "private"."fcc_submission_retry_queue" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."fcc_submissions" (
    "id" "uuid" DEFAULT "extensions"."uuid_generate_v4"() NOT NULL,
    "contact_name" "text",
    "contact_email" "text",
    "contact_phone" "text",
    "device_timestamp" timestamp with time zone,
    "server_timestamp" timestamp with time zone DEFAULT "now"(),
    "source_ip" character varying,
    "source_port" integer,
    "device_imei" character varying,
    "device_tac" character varying,
    "sim_country_code" character varying,
    "sim_network_code" character varying,
    "net_country_code" character varying,
    "net_network_code" character varying,
    "in_vehicle" boolean,
    "external_antenna" boolean,
    "created_on" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_on" timestamp with time zone DEFAULT "now"() NOT NULL,
    "submission" "jsonb",
    "submitted" boolean DEFAULT false,
    "submitted_on" timestamp with time zone,
    "device_manufacturer" "text",
    "device_model" "text",
    "device_os" "text",
    "app_name" "text",
    "app_version" "text",
    "provider" "text",
    "device_type" "text",
    "device_id" "uuid",
    "submission_response" "text"
);


ALTER TABLE "public"."fcc_submissions" OWNER TO "postgres";


CREATE OR REPLACE VIEW "private"."fcc_submissions_view" AS
 WITH "locs" AS (
         SELECT "l"."measurement_id",
            "jsonb_agg"("jsonb_build_object"('timestamp', "l"."timestamp", 'latitude', "l"."lat", 'longitude', "l"."lon", 'horizontal_accuracy', "l"."accuracy", 'speed', "l"."speed", 'speed_accuracy', "l"."speed_accuracy")) AS "locations"
           FROM "public"."locations" "l"
          GROUP BY "l"."measurement_id"
         HAVING ("l"."measurement_id" IS NOT NULL)
        ), "cells" AS (
         SELECT "c"."measurement_id",
            "jsonb_agg"("jsonb_build_object"('timestamp', "c"."timestamp", 'cell_id', "c"."cell_id", 'physical_cell_id', "c"."physical_cell_id", 'cell_connection', "c"."cell_connection", 'network_generation', "c"."network_generation", 'network_subtype', "c"."network_subtype", 'signal_strength', "c"."signal_strength", 'rssi', "c"."rssi", 'rsrp', "c"."rsrp", 'rsrq', "c"."rsrq", 'sinr', "c"."sinr", 'csi_rsrp', "c"."csi_rsrp", 'csi_rsrq', "c"."csi_rsrq", 'csi_sinr', "c"."csi_sinr", 'cqi', "c"."cqi", 'spectrum_band', "c"."spectrum_band", 'spectrum_bandwidth', "c"."spectrum_bandwidth", 'arfcn', "c"."arfcn")) AS "cells"
           FROM "public"."cells" "c"
          GROUP BY "c"."measurement_id"
         HAVING ("c"."measurement_id" IS NOT NULL)
        ), "upload_download_data" AS (
         SELECT "m"."group_id",
            "jsonb_object_agg"("m"."type", "jsonb_build_object"('timestamp', "m"."timestamp", 'warmup_duration', "d"."warmup_duration", 'warmup_bytes_transferred', "d"."warmup_bytes", 'bytes_transferred', "d"."bytes", 'bytes_sec', (
                CASE
                    WHEN (("d"."duration" = (0)::numeric) AND ("d"."bytes" = (0)::numeric)) THEN (0)::double precision
                    WHEN ("d"."duration" = (0)::numeric) THEN NULL::double precision
                    ELSE (("d"."bytes")::double precision / (("d"."duration")::double precision / (1000000.0)::double precision))
                END)::integer, 'success', "m"."success", 'duration', "d"."duration", 'targets', "d"."servers", 'locations', "l"."locations", 'cells', "c"."cells", 'success_flag', "m"."success", 'carrier_aggregation_flag', "m"."carrier_aggregation", 'network_connected_flag', "m"."network_connected", 'network_available_flag', "m"."network_available", 'network_roaming_flag', "m"."network_roaming")) AS "upload_download_test"
           FROM ((("public"."measurements" "m"
             JOIN "public"."upload_download_data" "d" ON (("d"."measurement_id" = "m"."id")))
             LEFT JOIN "locs" "l" ON (("l"."measurement_id" = "m"."id")))
             LEFT JOIN "cells" "c" ON (("c"."measurement_id" = "m"."id")))
          GROUP BY "m"."group_id"
        ), "latency_data" AS (
         SELECT "m"."group_id",
            "jsonb_object_agg"("m"."type", "jsonb_build_object"('timestamp', "m"."timestamp", 'duration', "m"."duration", 'round_trip_time', "l"."rtt", 'jitter', "l"."jitter", 'packets_sent', "l"."sent", 'packets_received', "l"."received", 'locations', "loc"."locations", 'cells', "c"."cells", 'targets', "l"."servers", 'success_flag', "m"."success", 'carrier_aggregation_flag', "m"."carrier_aggregation", 'network_connected_flag', "m"."network_connected", 'network_available_flag', "m"."network_available", 'network_roaming_flag', "m"."network_roaming")) AS "latency_test"
           FROM ((("public"."measurements" "m"
             JOIN "public"."latency_data" "l" ON (("l"."measurement_id" = "m"."id")))
             LEFT JOIN "locs" "loc" ON (("loc"."measurement_id" = "m"."id")))
             LEFT JOIN "cells" "c" ON (("c"."measurement_id" = "m"."id")))
          GROUP BY "m"."group_id"
        ), "tests" AS (
         SELECT "test_data"."group_id",
            (COALESCE("test_data"."latency_test", '{}'::"jsonb") || COALESCE("test_data"."upload_download_test", '{}'::"jsonb")) AS "tests"
           FROM ( SELECT COALESCE("upload_download_data"."group_id", "latency_data"."group_id") AS "group_id",
                    "latency_data"."latency_test",
                    "upload_download_data"."upload_download_test"
                   FROM ("latency_data"
                     LEFT JOIN "upload_download_data" ON (("latency_data"."group_id" = "upload_download_data"."group_id")))) "test_data"
        )
 SELECT "s"."id" AS "submission_id",
    "s"."submitted",
    "jsonb_build_object"('submission_category', 'Consumer Challenge', 'contact', "jsonb_build_object"('name', "s"."contact_name", 'email', "s"."contact_email", 'phone', "s"."contact_phone"), 'submissions', "jsonb_build_array"("jsonb_build_object"('test_id', "s"."id", 'device_timestamp', "s"."device_timestamp", 'server_timestamp', "s"."server_timestamp", 'server_source_ip_address', "s"."source_ip", 'server_source_port', "s"."source_port", 'device_imei', "s"."device_imei", 'device_type', "s"."device_type", 'manufacturer', "s"."device_manufacturer", 'model', "s"."device_model", 'operating_system', "s"."device_os", 'device_tac', "s"."device_tac", 'device_id', "s"."device_id", 'app_name', "s"."app_name", 'app_version', "s"."app_version", 'provider_name', "s"."provider", 'sim_mobile_country_code', "s"."sim_country_code", 'sim_mobile_network_code', "s"."sim_network_code", 'net_mobile_country_code', "s"."net_country_code", 'net_mobile_network_code', "s"."net_network_code", 'in_vehicle_flag', "s"."in_vehicle", 'external_antenna_flag', "s"."external_antenna", 'scheduled_test_flag', false, 'tests', "tests"."tests"))) AS "data"
   FROM ("public"."fcc_submissions" "s"
     LEFT JOIN "tests" ON (("s"."id" = "tests"."group_id")));


ALTER VIEW "private"."fcc_submissions_view" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "published"."cells" (
    "id" "uuid" NOT NULL,
    "measurement_id" "uuid" NOT NULL,
    "timestamp" timestamp with time zone,
    "cell_id" numeric,
    "physical_cell_id" numeric,
    "cell_connection" numeric,
    "network_generation" "text",
    "network_subtype" "text",
    "signal_strength" numeric,
    "rssi" numeric,
    "rsrp" numeric,
    "rsrq" numeric,
    "sinr" numeric,
    "csi_rsrp" numeric,
    "csi_rsrq" numeric,
    "csi_sinr" numeric,
    "cqi" numeric,
    "spectrum_band" "text",
    "spectrum_bandwidth" numeric,
    "arfcn" numeric,
    "created_on" timestamp with time zone NOT NULL,
    "updated_on" timestamp with time zone NOT NULL,
    "group_offset" numeric
);


ALTER TABLE "published"."cells" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "published"."latency_data" (
    "id" "uuid" NOT NULL,
    "measurement_id" "uuid" NOT NULL,
    "rtt" numeric,
    "jitter" numeric,
    "sent" numeric,
    "received" numeric,
    "servers" "text"[],
    "created_on" timestamp with time zone NOT NULL,
    "updated_on" timestamp with time zone NOT NULL
);


ALTER TABLE "published"."latency_data" OWNER TO "postgres";


CREATE OR REPLACE VIEW "published"."providers" WITH ("security_invoker"='true') AS
 SELECT "provider"
   FROM "published"."measurements"
  GROUP BY "provider";


ALTER VIEW "published"."providers" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "published"."throughput_data" (
    "id" "uuid" NOT NULL,
    "measurement_id" "uuid" NOT NULL,
    "warmup_duration" numeric,
    "warmup_bytes" numeric,
    "duration" numeric,
    "bytes" numeric,
    "bytes_per_sec" numeric,
    "application_bytes" numeric,
    "application_bytes_per_sec" numeric,
    "servers" "text"[],
    "created_on" timestamp with time zone NOT NULL,
    "updated_on" timestamp with time zone NOT NULL
);


ALTER TABLE "published"."throughput_data" OWNER TO "postgres";


ALTER TABLE ONLY "private"."dashboard_settings"
    ADD CONSTRAINT "dashboard_settings_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "private"."devices"
    ADD CONSTRAINT "devices_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "private"."fcc_submission_failures"
    ADD CONSTRAINT "fcc_submission_failures_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "private"."fcc_submission_info"
    ADD CONSTRAINT "fcc_submission_info_pkey" PRIMARY KEY ("env");



ALTER TABLE ONLY "private"."fcc_submission_retry_queue"
    ADD CONSTRAINT "fcc_submission_retry_queue_pkey" PRIMARY KEY ("submission_id");



ALTER TABLE ONLY "public"."cells"
    ADD CONSTRAINT "cells_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."fcc_submissions"
    ADD CONSTRAINT "fcc_submissions_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."latency_data"
    ADD CONSTRAINT "latency_data_measurement_id_key" UNIQUE ("measurement_id");



ALTER TABLE ONLY "public"."latency_data"
    ADD CONSTRAINT "latency_data_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."locations"
    ADD CONSTRAINT "locations_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."measurements"
    ADD CONSTRAINT "measurements_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."upload_download_data"
    ADD CONSTRAINT "upload_download_data_measurement_id_key" UNIQUE ("measurement_id");



ALTER TABLE ONLY "public"."upload_download_data"
    ADD CONSTRAINT "upload_download_data_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "published"."cells"
    ADD CONSTRAINT "cells_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "published"."latency_data"
    ADD CONSTRAINT "latency_data_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "published"."measurements"
    ADD CONSTRAINT "measurements_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "published"."throughput_data"
    ADD CONSTRAINT "throughput_data_pkey" PRIMARY KEY ("id");



CREATE INDEX "cells_measurement_id_idx" ON "public"."cells" USING "btree" ("measurement_id");



CREATE INDEX "locations_measurement_id_idx" ON "public"."locations" USING "btree" ("measurement_id");



CREATE INDEX "cells_measurement_id_idx" ON "published"."cells" USING "btree" ("measurement_id");



CREATE INDEX "latency_data_measurement_id_idx" ON "published"."latency_data" USING "btree" ("measurement_id");



CREATE INDEX "measurements_center_hex9_idx" ON "published"."measurements" USING "btree" ("center_hex9");



CREATE INDEX "measurements_end_hex9_idx" ON "published"."measurements" USING "btree" ("end_hex9");



CREATE INDEX "measurements_group_id_idx" ON "published"."measurements" USING "btree" ("group_id");



CREATE INDEX "measurements_start_hex9_idx" ON "published"."measurements" USING "btree" ("start_hex9");



CREATE INDEX "measurements_type_idx" ON "published"."measurements" USING "btree" ("type");



CREATE INDEX "throughput_data_measurement_id_idx" ON "published"."throughput_data" USING "btree" ("measurement_id");



CREATE OR REPLACE TRIGGER "_100_cell_set_updated_on" BEFORE UPDATE ON "public"."measurements" FOR EACH ROW EXECUTE FUNCTION "public"."set_updated_on"();



CREATE OR REPLACE TRIGGER "_100_challenge_data_set_updated_on" BEFORE UPDATE ON "public"."measurements" FOR EACH ROW EXECUTE FUNCTION "public"."set_updated_on"();



CREATE OR REPLACE TRIGGER "_100_fcc_submission_set_updated_on" BEFORE UPDATE ON "public"."fcc_submissions" FOR EACH ROW EXECUTE FUNCTION "public"."set_updated_on"();



CREATE OR REPLACE TRIGGER "_100_latency_data_set_updated_on" BEFORE UPDATE ON "public"."latency_data" FOR EACH ROW EXECUTE FUNCTION "public"."set_updated_on"();



CREATE OR REPLACE TRIGGER "_100_location_set_updated_on" BEFORE UPDATE ON "public"."locations" FOR EACH ROW EXECUTE FUNCTION "public"."set_updated_on"();



CREATE OR REPLACE TRIGGER "_100_measurement_set_updated_on" BEFORE UPDATE ON "public"."measurements" FOR EACH ROW EXECUTE FUNCTION "public"."set_updated_on"();



CREATE OR REPLACE TRIGGER "_100_upload_download_data_set_updated_on" BEFORE UPDATE ON "public"."upload_download_data" FOR EACH ROW EXECUTE FUNCTION "public"."set_updated_on"();



CREATE OR REPLACE TRIGGER "_200_cell_force_created_on" BEFORE INSERT ON "public"."cells" FOR EACH ROW WHEN (("new"."created_on" IS NULL)) EXECUTE FUNCTION "public"."force_created_on"();



CREATE OR REPLACE TRIGGER "_200_fcc_submission_force_created_on" BEFORE INSERT ON "public"."fcc_submissions" FOR EACH ROW WHEN (("new"."created_on" IS NULL)) EXECUTE FUNCTION "public"."force_created_on"();



CREATE OR REPLACE TRIGGER "_200_latency_data_force_created_on" BEFORE INSERT ON "public"."latency_data" FOR EACH ROW WHEN (("new"."created_on" IS NULL)) EXECUTE FUNCTION "public"."force_created_on"();



CREATE OR REPLACE TRIGGER "_200_location_force_created_on" BEFORE INSERT ON "public"."locations" FOR EACH ROW WHEN (("new"."created_on" IS NULL)) EXECUTE FUNCTION "public"."force_created_on"();



CREATE OR REPLACE TRIGGER "_200_measurement_force_created_on" BEFORE INSERT ON "public"."measurements" FOR EACH ROW WHEN (("new"."created_on" IS NULL)) EXECUTE FUNCTION "public"."force_created_on"();



CREATE OR REPLACE TRIGGER "_200_upload_download_data_force_created_on" BEFORE INSERT ON "public"."upload_download_data" FOR EACH ROW WHEN (("new"."created_on" IS NULL)) EXECUTE FUNCTION "public"."force_created_on"();



CREATE OR REPLACE TRIGGER "_300_fcc_submission_update_source_ip" BEFORE INSERT ON "public"."fcc_submissions" FOR EACH ROW WHEN (("new"."source_ip" IS NULL)) EXECUTE FUNCTION "public"."fcc_submission_update_source_ip"();



CREATE OR REPLACE TRIGGER "fix_jitter_pre_100alpha5" BEFORE INSERT ON "public"."latency_data" FOR EACH ROW EXECUTE FUNCTION "public"."fix_jitter"();



CREATE OR REPLACE TRIGGER "trg_auto_post_fcc_submission" AFTER INSERT ON "public"."fcc_submissions" FOR EACH ROW EXECUTE FUNCTION "private"."auto_post_fcc_submission"();



CREATE OR REPLACE TRIGGER "_100_published_cell_set_updated_on" BEFORE UPDATE ON "published"."cells" FOR EACH ROW EXECUTE FUNCTION "public"."set_updated_on"();



CREATE OR REPLACE TRIGGER "_100_published_latency_set_updated_on" BEFORE UPDATE ON "published"."latency_data" FOR EACH ROW EXECUTE FUNCTION "public"."set_updated_on"();



CREATE OR REPLACE TRIGGER "_100_published_measurement_set_updated_on" BEFORE UPDATE ON "published"."measurements" FOR EACH ROW EXECUTE FUNCTION "public"."set_updated_on"();



CREATE OR REPLACE TRIGGER "_100_published_throughput_set_updated_on" BEFORE UPDATE ON "published"."throughput_data" FOR EACH ROW EXECUTE FUNCTION "public"."set_updated_on"();



CREATE OR REPLACE TRIGGER "_200_published_cell_force_created_on" BEFORE INSERT ON "published"."cells" FOR EACH ROW EXECUTE FUNCTION "public"."force_created_on"();



CREATE OR REPLACE TRIGGER "_200_published_latency_force_created_on" BEFORE INSERT ON "published"."latency_data" FOR EACH ROW EXECUTE FUNCTION "public"."force_created_on"();



CREATE OR REPLACE TRIGGER "_200_published_measurement_force_created_on" BEFORE INSERT ON "published"."measurements" FOR EACH ROW EXECUTE FUNCTION "public"."force_created_on"();



CREATE OR REPLACE TRIGGER "_200_published_throughput_force_created_on" BEFORE INSERT ON "published"."throughput_data" FOR EACH ROW EXECUTE FUNCTION "public"."force_created_on"();



ALTER TABLE ONLY "private"."fcc_submission_info"
    ADD CONSTRAINT "fcc_submission_info_hash_secret_id_fkey" FOREIGN KEY ("hash_secret_id") REFERENCES "vault"."secrets"("id");



ALTER TABLE ONLY "private"."fcc_submission_retry_queue"
    ADD CONSTRAINT "fcc_submission_retry_queue_submission_id_fkey" FOREIGN KEY ("submission_id") REFERENCES "public"."fcc_submissions"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."cells"
    ADD CONSTRAINT "cell_measurement_id_fkey" FOREIGN KEY ("measurement_id") REFERENCES "public"."measurements"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "public"."latency_data"
    ADD CONSTRAINT "latency_data_measurement_id_fkey" FOREIGN KEY ("measurement_id") REFERENCES "public"."measurements"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."locations"
    ADD CONSTRAINT "location_measurement_id_fkey" FOREIGN KEY ("measurement_id") REFERENCES "public"."measurements"("id") ON UPDATE CASCADE ON DELETE CASCADE;



ALTER TABLE ONLY "public"."upload_download_data"
    ADD CONSTRAINT "upload_download_data_measurement_id_fkey" FOREIGN KEY ("measurement_id") REFERENCES "public"."measurements"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "published"."cells"
    ADD CONSTRAINT "cells_id_fkey" FOREIGN KEY ("id") REFERENCES "public"."cells"("id");



ALTER TABLE ONLY "published"."cells"
    ADD CONSTRAINT "cells_measurement_id_fkey" FOREIGN KEY ("measurement_id") REFERENCES "published"."measurements"("id");



ALTER TABLE ONLY "published"."latency_data"
    ADD CONSTRAINT "latency_data_id_fkey" FOREIGN KEY ("id") REFERENCES "public"."latency_data"("id");



ALTER TABLE ONLY "published"."latency_data"
    ADD CONSTRAINT "latency_data_measurement_id_fkey" FOREIGN KEY ("measurement_id") REFERENCES "published"."measurements"("id");



ALTER TABLE ONLY "published"."measurements"
    ADD CONSTRAINT "measurements_id_fkey" FOREIGN KEY ("id") REFERENCES "public"."measurements"("id");



ALTER TABLE ONLY "published"."throughput_data"
    ADD CONSTRAINT "throughput_data_id_fkey" FOREIGN KEY ("id") REFERENCES "public"."upload_download_data"("id");



ALTER TABLE ONLY "published"."throughput_data"
    ADD CONSTRAINT "throughput_data_measurement_id_fkey" FOREIGN KEY ("measurement_id") REFERENCES "published"."measurements"("id");



CREATE POLICY "Allow inserting own FCC submissions" ON "public"."fcc_submissions" FOR INSERT TO "anon" WITH CHECK ((( SELECT "private"."verified_device_id"() AS "verified_device_id") = "device_id"));



CREATE POLICY "Allow inserting own cells" ON "public"."cells" FOR INSERT TO "anon" WITH CHECK (("measurement_id" IN ( SELECT "measurements"."id"
   FROM "public"."measurements"
  WHERE ("measurements"."device_id" = ( SELECT "private"."verified_device_id"() AS "verified_device_id")))));



CREATE POLICY "Allow inserting own latency data" ON "public"."latency_data" FOR INSERT TO "anon" WITH CHECK (("measurement_id" IN ( SELECT "measurements"."id"
   FROM "public"."measurements"
  WHERE ("measurements"."device_id" = ( SELECT "private"."verified_device_id"() AS "verified_device_id")))));



CREATE POLICY "Allow inserting own locations" ON "public"."locations" FOR INSERT TO "anon" WITH CHECK (("measurement_id" IN ( SELECT "measurements"."id"
   FROM "public"."measurements"
  WHERE ("measurements"."device_id" = ( SELECT "private"."verified_device_id"() AS "verified_device_id")))));



CREATE POLICY "Allow inserting own measurements" ON "public"."measurements" FOR INSERT TO "anon" WITH CHECK ((( SELECT "private"."verified_device_id"() AS "verified_device_id") = "device_id"));



CREATE POLICY "Allow inserting own upload/download data" ON "public"."upload_download_data" FOR INSERT TO "anon" WITH CHECK (("measurement_id" IN ( SELECT "measurements"."id"
   FROM "public"."measurements"
  WHERE ("measurements"."device_id" = ( SELECT "private"."verified_device_id"() AS "verified_device_id")))));



CREATE POLICY "Allow viewing latency (dashboard or own)" ON "public"."latency_data" FOR SELECT TO "anon" USING (("private"."is_dashboard_request"() OR (EXISTS ( SELECT 1
   FROM "public"."measurements" "m"
  WHERE (("m"."id" = "latency_data"."measurement_id") AND ("m"."device_id" = "private"."verified_device_id_or_null"()))))));



CREATE POLICY "Allow viewing locations (dashboard or own)" ON "public"."locations" FOR SELECT TO "anon" USING (("private"."is_dashboard_request"() OR (EXISTS ( SELECT 1
   FROM "public"."measurements" "m"
  WHERE (("m"."id" = "locations"."measurement_id") AND ("m"."device_id" = "private"."verified_device_id_or_null"()))))));



CREATE POLICY "Allow viewing own FCC submissions" ON "public"."fcc_submissions" FOR SELECT TO "anon" USING ((( SELECT "private"."verified_device_id"() AS "verified_device_id") = "device_id"));



CREATE POLICY "Allow viewing own cells" ON "public"."cells" FOR SELECT TO "anon" USING (("measurement_id" IN ( SELECT "measurements"."id"
   FROM "public"."measurements"
  WHERE ("measurements"."device_id" = ( SELECT "private"."verified_device_id"() AS "verified_device_id")))));



CREATE POLICY "Allow viewing own latency data" ON "public"."latency_data" FOR SELECT TO "anon" USING (("measurement_id" IN ( SELECT "measurements"."id"
   FROM "public"."measurements"
  WHERE ("measurements"."device_id" = ( SELECT "private"."verified_device_id"() AS "verified_device_id")))));



CREATE POLICY "Allow viewing own locations" ON "public"."locations" FOR SELECT TO "anon" USING (("measurement_id" IN ( SELECT "measurements"."id"
   FROM "public"."measurements"
  WHERE ("measurements"."device_id" = ( SELECT "private"."verified_device_id"() AS "verified_device_id")))));



CREATE POLICY "Allow viewing own measurements" ON "public"."measurements" FOR SELECT TO "anon" USING (("private"."is_dashboard_request"() OR ("private"."verified_device_id_or_null"() = "device_id")));



CREATE POLICY "Allow viewing own upload/download data" ON "public"."upload_download_data" FOR SELECT TO "anon" USING (("measurement_id" IN ( SELECT "measurements"."id"
   FROM "public"."measurements"
  WHERE ("measurements"."device_id" = ( SELECT "private"."verified_device_id"() AS "verified_device_id")))));



CREATE POLICY "Allow viewing ud (dashboard or own)" ON "public"."upload_download_data" FOR SELECT TO "anon" USING (("private"."is_dashboard_request"() OR (EXISTS ( SELECT 1
   FROM "public"."measurements" "m"
  WHERE (("m"."id" = "upload_download_data"."measurement_id") AND ("m"."device_id" = "private"."verified_device_id_or_null"()))))));



ALTER TABLE "public"."cells" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "dashboard can select cells" ON "public"."cells" FOR SELECT TO "anon" USING ("private"."is_dashboard_request"());



CREATE POLICY "dashboard can select latency_data" ON "public"."latency_data" FOR SELECT TO "anon" USING ("private"."is_dashboard_request"());



CREATE POLICY "dashboard can select locations" ON "public"."locations" FOR SELECT TO "anon" USING ("private"."is_dashboard_request"());



ALTER TABLE "public"."fcc_submissions" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."latency_data" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."locations" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."measurements" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."upload_download_data" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "Allow viewing all published cells" ON "published"."cells" FOR SELECT TO "anon" USING (true);



CREATE POLICY "Allow viewing all published latency data" ON "published"."latency_data" FOR SELECT TO "anon" USING (true);



CREATE POLICY "Allow viewing all published measurements" ON "published"."measurements" FOR SELECT TO "anon" USING (true);



CREATE POLICY "Allow viewing all published throughput data" ON "published"."throughput_data" FOR SELECT TO "anon" USING (true);



ALTER TABLE "published"."cells" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "published"."latency_data" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "published"."measurements" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "published"."throughput_data" ENABLE ROW LEVEL SECURITY;




ALTER PUBLICATION "supabase_realtime" OWNER TO "postgres";





GRANT USAGE ON SCHEMA "private" TO "readonly_user";



REVOKE USAGE ON SCHEMA "public" FROM PUBLIC;
GRANT USAGE ON SCHEMA "public" TO "anon";
GRANT USAGE ON SCHEMA "public" TO "authenticated";
GRANT USAGE ON SCHEMA "public" TO "service_role";
GRANT USAGE ON SCHEMA "public" TO "postgres";
GRANT USAGE ON SCHEMA "public" TO "readonly_user";



GRANT USAGE ON SCHEMA "published" TO "service_role";
GRANT USAGE ON SCHEMA "published" TO PUBLIC;






































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































































REVOKE ALL ON FUNCTION "private"."process_fcc_submission_retries"("p_batch_size" integer) FROM PUBLIC;



GRANT ALL ON FUNCTION "public"."any_uuid_agg"("uuid", "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."any_uuid_agg"("uuid", "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."any_uuid_agg"("uuid", "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."fcc_submission_update_source_ip"() TO "anon";
GRANT ALL ON FUNCTION "public"."fcc_submission_update_source_ip"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."fcc_submission_update_source_ip"() TO "service_role";



GRANT ALL ON FUNCTION "public"."fix_jitter"() TO "anon";
GRANT ALL ON FUNCTION "public"."fix_jitter"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."fix_jitter"() TO "service_role";



GRANT ALL ON FUNCTION "public"."force_created_on"() TO "anon";
GRANT ALL ON FUNCTION "public"."force_created_on"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."force_created_on"() TO "service_role";



GRANT ALL ON FUNCTION "public"."get_header"("item" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."get_header"("item" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_header"("item" "text") TO "service_role";



GRANT ALL ON FUNCTION "public"."get_raw_header"() TO "anon";
GRANT ALL ON FUNCTION "public"."get_raw_header"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_raw_header"() TO "service_role";



GRANT ALL ON FUNCTION "public"."get_raw_header"("item" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."get_raw_header"("item" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."get_raw_header"("item" "text") TO "service_role";



GRANT ALL ON FUNCTION "public"."hex_ancestor"("hex" bigint, "res" bigint) TO "anon";
GRANT ALL ON FUNCTION "public"."hex_ancestor"("hex" bigint, "res" bigint) TO "authenticated";
GRANT ALL ON FUNCTION "public"."hex_ancestor"("hex" bigint, "res" bigint) TO "service_role";



GRANT ALL ON FUNCTION "public"."hex_res"("hex" bigint) TO "anon";
GRANT ALL ON FUNCTION "public"."hex_res"("hex" bigint) TO "authenticated";
GRANT ALL ON FUNCTION "public"."hex_res"("hex" bigint) TO "service_role";



GRANT SELECT,INSERT,REFERENCES,DELETE,TRIGGER,TRUNCATE,UPDATE ON TABLE "public"."cells" TO "anon";
GRANT SELECT,INSERT,REFERENCES,DELETE,TRIGGER,TRUNCATE,UPDATE ON TABLE "public"."cells" TO "authenticated";
GRANT SELECT,INSERT,REFERENCES,DELETE,TRIGGER,TRUNCATE,UPDATE ON TABLE "public"."cells" TO "service_role";
GRANT SELECT ON TABLE "public"."cells" TO "readonly_user";



GRANT SELECT,INSERT,REFERENCES,DELETE,TRIGGER,TRUNCATE,UPDATE ON TABLE "public"."latency_data" TO "anon";
GRANT SELECT,INSERT,REFERENCES,DELETE,TRIGGER,TRUNCATE,UPDATE ON TABLE "public"."latency_data" TO "authenticated";
GRANT SELECT,INSERT,REFERENCES,DELETE,TRIGGER,TRUNCATE,UPDATE ON TABLE "public"."latency_data" TO "service_role";
GRANT SELECT ON TABLE "public"."latency_data" TO "readonly_user";



GRANT SELECT,INSERT,REFERENCES,DELETE,TRIGGER,TRUNCATE,UPDATE ON TABLE "public"."locations" TO "anon";
GRANT SELECT,INSERT,REFERENCES,DELETE,TRIGGER,TRUNCATE,UPDATE ON TABLE "public"."locations" TO "authenticated";
GRANT SELECT,INSERT,REFERENCES,DELETE,TRIGGER,TRUNCATE,UPDATE ON TABLE "public"."locations" TO "service_role";
GRANT SELECT ON TABLE "public"."locations" TO "readonly_user";



GRANT SELECT,INSERT,REFERENCES,DELETE,TRIGGER,TRUNCATE,UPDATE ON TABLE "public"."measurements" TO "anon";
GRANT SELECT,INSERT,REFERENCES,DELETE,TRIGGER,TRUNCATE,UPDATE ON TABLE "public"."measurements" TO "authenticated";
GRANT SELECT,INSERT,REFERENCES,DELETE,TRIGGER,TRUNCATE,UPDATE ON TABLE "public"."measurements" TO "service_role";
GRANT SELECT ON TABLE "public"."measurements" TO "readonly_user";



GRANT SELECT,INSERT,REFERENCES,DELETE,TRIGGER,TRUNCATE,UPDATE ON TABLE "public"."upload_download_data" TO "anon";
GRANT SELECT,INSERT,REFERENCES,DELETE,TRIGGER,TRUNCATE,UPDATE ON TABLE "public"."upload_download_data" TO "authenticated";
GRANT SELECT,INSERT,REFERENCES,DELETE,TRIGGER,TRUNCATE,UPDATE ON TABLE "public"."upload_download_data" TO "service_role";
GRANT SELECT ON TABLE "public"."upload_download_data" TO "readonly_user";



GRANT ALL ON FUNCTION "public"."insert_measurement"("in_measurement" "public"."measurements", "in_measurement_data" "public"."upload_download_data", "in_latency_data" "public"."latency_data", "in_locations" "public"."locations"[], "in_cells" "public"."cells"[]) TO "anon";
GRANT ALL ON FUNCTION "public"."insert_measurement"("in_measurement" "public"."measurements", "in_measurement_data" "public"."upload_download_data", "in_latency_data" "public"."latency_data", "in_locations" "public"."locations"[], "in_cells" "public"."cells"[]) TO "authenticated";
GRANT ALL ON FUNCTION "public"."insert_measurement"("in_measurement" "public"."measurements", "in_measurement_data" "public"."upload_download_data", "in_latency_data" "public"."latency_data", "in_locations" "public"."locations"[], "in_cells" "public"."cells"[]) TO "service_role";



GRANT ALL ON FUNCTION "public"."log_user_data"() TO "anon";
GRANT ALL ON FUNCTION "public"."log_user_data"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."log_user_data"() TO "service_role";



GRANT ALL ON FUNCTION "public"."publish_data"("in_measurements" "public"."publishable_measurement"[], "in_cells" "public"."publishable_cell"[], OUT "measurement_ids" "uuid"[], OUT "cell_ids" "uuid"[]) TO "anon";
GRANT ALL ON FUNCTION "public"."publish_data"("in_measurements" "public"."publishable_measurement"[], "in_cells" "public"."publishable_cell"[], OUT "measurement_ids" "uuid"[], OUT "cell_ids" "uuid"[]) TO "authenticated";
GRANT ALL ON FUNCTION "public"."publish_data"("in_measurements" "public"."publishable_measurement"[], "in_cells" "public"."publishable_cell"[], OUT "measurement_ids" "uuid"[], OUT "cell_ids" "uuid"[]) TO "service_role";



GRANT ALL ON FUNCTION "public"."register_device"("device_id" "uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."register_device"("device_id" "uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."register_device"("device_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."rpc_group_detail"("group_id" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."rpc_group_detail"("group_id" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."rpc_group_detail"("group_id" "text") TO "service_role";



GRANT ALL ON FUNCTION "public"."rpc_group_summaries"("min_lng" double precision, "min_lat" double precision, "max_lng" double precision, "max_lat" double precision, "t_from" timestamp with time zone, "t_to" timestamp with time zone, "providers" "text"[], "conn" "text"[], "dl_min" double precision, "dl_max" double precision, "ul_min" double precision, "ul_max" double precision, "lat_min" double precision, "lat_max" double precision, "lim" integer) TO "anon";
GRANT ALL ON FUNCTION "public"."rpc_group_summaries"("min_lng" double precision, "min_lat" double precision, "max_lng" double precision, "max_lat" double precision, "t_from" timestamp with time zone, "t_to" timestamp with time zone, "providers" "text"[], "conn" "text"[], "dl_min" double precision, "dl_max" double precision, "ul_min" double precision, "ul_max" double precision, "lat_min" double precision, "lat_max" double precision, "lim" integer) TO "authenticated";
GRANT ALL ON FUNCTION "public"."rpc_group_summaries"("min_lng" double precision, "min_lat" double precision, "max_lng" double precision, "max_lat" double precision, "t_from" timestamp with time zone, "t_to" timestamp with time zone, "providers" "text"[], "conn" "text"[], "dl_min" double precision, "dl_max" double precision, "ul_min" double precision, "ul_max" double precision, "lat_min" double precision, "lat_max" double precision, "lim" integer) TO "service_role";



GRANT ALL ON FUNCTION "public"."rpc_map_points"("min_lng" double precision, "min_lat" double precision, "max_lng" double precision, "max_lat" double precision, "t_from" timestamp with time zone, "t_to" timestamp with time zone, "providers" "text"[], "conn" "text"[], "dl_min" double precision, "dl_max" double precision, "ul_min" double precision, "ul_max" double precision, "lat_min" double precision, "lat_max" double precision, "lim" integer) TO "anon";
GRANT ALL ON FUNCTION "public"."rpc_map_points"("min_lng" double precision, "min_lat" double precision, "max_lng" double precision, "max_lat" double precision, "t_from" timestamp with time zone, "t_to" timestamp with time zone, "providers" "text"[], "conn" "text"[], "dl_min" double precision, "dl_max" double precision, "ul_min" double precision, "ul_max" double precision, "lat_min" double precision, "lat_max" double precision, "lim" integer) TO "authenticated";
GRANT ALL ON FUNCTION "public"."rpc_map_points"("min_lng" double precision, "min_lat" double precision, "max_lng" double precision, "max_lat" double precision, "t_from" timestamp with time zone, "t_to" timestamp with time zone, "providers" "text"[], "conn" "text"[], "dl_min" double precision, "dl_max" double precision, "ul_min" double precision, "ul_max" double precision, "lat_min" double precision, "lat_max" double precision, "lim" integer) TO "service_role";



GRANT ALL ON FUNCTION "public"."set_updated_on"() TO "anon";
GRANT ALL ON FUNCTION "public"."set_updated_on"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."set_updated_on"() TO "service_role";



GRANT ALL ON FUNCTION "public"."any_uuid"("uuid") TO "anon";
GRANT ALL ON FUNCTION "public"."any_uuid"("uuid") TO "authenticated";
GRANT ALL ON FUNCTION "public"."any_uuid"("uuid") TO "service_role";



GRANT SELECT,INSERT,REFERENCES,DELETE,UPDATE ON TABLE "published"."measurements" TO "service_role";
GRANT SELECT ON TABLE "published"."measurements" TO PUBLIC;



GRANT SELECT ON TABLE "published"."measurement_groups" TO PUBLIC;





























































































GRANT SELECT ON TABLE "private"."dashboard_settings" TO "readonly_user";



GRANT SELECT ON TABLE "private"."devices" TO "readonly_user";



GRANT SELECT ON TABLE "private"."fcc_submission_failures" TO "readonly_user";



GRANT SELECT ON TABLE "private"."fcc_submission_info" TO "readonly_user";



GRANT SELECT ON TABLE "private"."fcc_submission_retry_queue" TO "readonly_user";



GRANT SELECT,INSERT,REFERENCES,DELETE,TRIGGER,TRUNCATE,UPDATE ON TABLE "public"."fcc_submissions" TO "anon";
GRANT SELECT,INSERT,REFERENCES,DELETE,TRIGGER,TRUNCATE,UPDATE ON TABLE "public"."fcc_submissions" TO "authenticated";
GRANT SELECT,INSERT,REFERENCES,DELETE,TRIGGER,TRUNCATE,UPDATE ON TABLE "public"."fcc_submissions" TO "service_role";
GRANT SELECT ON TABLE "public"."fcc_submissions" TO "readonly_user";



GRANT SELECT ON TABLE "private"."fcc_submissions_view" TO "readonly_user";



GRANT SELECT,INSERT,REFERENCES,DELETE,UPDATE ON TABLE "published"."cells" TO "service_role";
GRANT SELECT ON TABLE "published"."cells" TO PUBLIC;



GRANT SELECT,INSERT,REFERENCES,DELETE,UPDATE ON TABLE "published"."latency_data" TO "service_role";
GRANT SELECT ON TABLE "published"."latency_data" TO PUBLIC;



GRANT SELECT ON TABLE "published"."providers" TO PUBLIC;



GRANT SELECT,INSERT,REFERENCES,DELETE,UPDATE ON TABLE "published"."throughput_data" TO "service_role";
GRANT SELECT ON TABLE "published"."throughput_data" TO PUBLIC;









ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "private" GRANT SELECT ON SEQUENCES TO "readonly_user";



ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "private" GRANT SELECT ON TABLES TO "readonly_user";



ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES TO "postgres";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES TO "service_role";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT SELECT ON SEQUENCES TO "readonly_user";






ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS TO "postgres";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS TO "service_role";






ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT SELECT,INSERT,REFERENCES,DELETE,TRIGGER,TRUNCATE,UPDATE ON TABLES TO "postgres";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT SELECT,INSERT,REFERENCES,DELETE,TRIGGER,TRUNCATE,UPDATE ON TABLES TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT SELECT,INSERT,REFERENCES,DELETE,TRIGGER,TRUNCATE,UPDATE ON TABLES TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT SELECT,INSERT,REFERENCES,DELETE,TRIGGER,TRUNCATE,UPDATE ON TABLES TO "service_role";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT SELECT ON TABLES TO "readonly_user";






























\unrestrict KpPrRLmO8zOBLYEivKIhhwf6ZVSHohppE19ggB9HFlZXJcseXxvBha2XeySQDu8

RESET ALL;
