begin;

-- Prevent incomplete measurement groups from being posted to the FCC. A failed
-- measurement is valid challenge data; readiness requires presence, not success.
create or replace function private.fcc_submission_is_ready(sub_id uuid)
returns boolean
language sql
stable
set search_path = public, private, extensions
as
$$
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

create or replace function private.post_fcc_submission(sub_id uuid, fcc_env text)
returns bool
language plpgsql
set search_path = public, private, extensions
as
$$
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

create or replace function private.auto_post_fcc_submission()
returns trigger
language plpgsql
security definer
set search_path = public, private, extensions
as
$$
declare
    env text := 'prod';
    post_succeeded boolean;
    response_text text;
begin
    if coalesce(new.submitted, false) = false then
        begin
            post_succeeded := private.post_fcc_submission(new.id, env);

            if post_succeeded is distinct from true then
                raise exception 'FCC submission function returned false for submission_id=%', new.id;
            end if;

            select s.submission_response
            into response_text
            from public.fcc_submissions s
            where s.id = new.id;

            insert into private.fcc_submission_failures (
                submission_id,
                measurement_group_id,
                error_message,
                succeeded,
                fcc_env,
                response_snippet
            )
            values (
                new.id,
                new.id,
                null,
                true,
                env,
                left(coalesce(response_text, ''), 500)
            );

            raise info 'FCC auto-submit succeeded for submission_id=%', new.id;

        exception when others then
            insert into private.fcc_submission_failures (
                submission_id,
                measurement_group_id,
                error_message,
                succeeded,
                fcc_env,
                response_snippet
            )
            values (
                new.id,
                new.id,
                sqlerrm,
                false,
                env,
                left(sqlerrm, 500)
            );

            raise warning 'FCC auto-submit failed for submission_id=%; see private.fcc_submission_failures', new.id;
        end;
    end if;

    return new;
end;
$$;

commit;
