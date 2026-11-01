begin;

-- Record the authenticated role's statement timeout, which was set by hand on prod.
--
-- Prod gives authenticated a 2 minute statement_timeout, like anon (supabase/role_settings.sql).
-- It was set by hand at an unrecorded date, and 202609300001 recorded anon's and
-- authenticator's settings but not this one, so a database built from the migrations gave
-- authenticated the stock image's 8 s instead. Nothing uses the role today (the app never signs
-- users in), so on prod this changes nothing; it makes the migrations match prod.
alter role authenticated set statement_timeout = '2min';

commit;
