-- Product decision (2026-10-17): Iranian mobile numbers have one canonical
-- form, 09XXXXXXXXX. V3/V4 already converted digits to ASCII and trimmed
-- boundary whitespace, so this migration only removes the separators
-- (space, '-', '(', ')', '.') and rewrites 09…, 9…, 989…, +989… and 00989…
-- to 09XXXXXXXXX. Every other number (landline, foreign) is left unchanged,
-- matching PhoneNumberCanonicalizer.
--
-- Two accounts may hold the same mobile in different forms (e.g. 0912… and
-- +98912…). Choosing one owner is not safe, so abort before changing any row;
-- an operator must resolve the conflicting identities and rerun Flyway.
create function pg_temp.canonical_phone_number(phone_number text) returns text
    language sql immutable as
$$
    select case
               when regexp_replace(phone_number, '[ ().-]', '', 'g') ~ '^(0|98|\+98|0098)?9[0-9]{9}$'
                   then '0' || right(regexp_replace(phone_number, '[ ().-]', '', 'g'), 10)
               else phone_number
           end
$$;

do $$
begin
    if exists (
        select pg_temp.canonical_phone_number(phone_number)
        from iam.users
        where phone_number is not null
        group by pg_temp.canonical_phone_number(phone_number)
        having count(*) > 1
    ) then
        raise exception using
            message = 'Cannot canonicalize iam.users.phone_number: the same Iranian mobile number belongs to multiple users.',
            hint = 'Resolve the conflicting accounts, keeping one owner per 09XXXXXXXXX mobile number, then rerun Flyway.';
    end if;
end
$$;

update iam.users
set phone_number = pg_temp.canonical_phone_number(phone_number)
where phone_number is not null
  and phone_number is distinct from pg_temp.canonical_phone_number(phone_number);

alter table iam.users
    add constraint ck_users_phone_number_iranian_mobile_canonical
        check (
            phone_number is null
            or phone_number ~ '^09[0-9]{9}$'
            or regexp_replace(phone_number, '[ ().-]', '', 'g') !~ '^(0|98|\+98|0098)?9[0-9]{9}$'
        );
