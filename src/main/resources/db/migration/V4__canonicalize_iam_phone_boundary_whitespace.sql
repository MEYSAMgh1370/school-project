-- V3 canonicalized Persian and Arabic-Indic digits and trimmed ASCII spaces.
-- Java canonicalization now trims the full boundary-whitespace set accepted by
-- Character.isWhitespace or Character.isSpaceChar, so rows that passed V3 with
-- tabs, NBSP, or other Unicode space separators must be normalized without
-- editing the applied V3 migration checksum.
do $$
begin
    if exists (
        select canonical_phone_number
        from (
            select translate(
                       btrim(
                           phone_number,
                           U&'\0009\000A\000B\000C\000D\001C\001D\001E\001F\0020\00A0\1680\2000\2001\2002\2003\2004\2005\2006\2007\2008\2009\200A\2028\2029\202F\205F\3000'
                       ),
                       '۰۱۲۳۴۵۶۷۸۹٠١٢٣٤٥٦٧٨٩',
                       '01234567890123456789'
                   ) as canonical_phone_number
            from iam.users
            where phone_number is not null
        ) canonicalized
        group by canonical_phone_number
        having count(*) > 1
    ) then
        raise exception using
            message = 'Cannot canonicalize iam.users.phone_number: visually equivalent phone numbers belong to multiple users.',
            hint = 'Resolve the conflicting accounts, keeping one owner per canonical phone number, then rerun Flyway.';
    end if;
end
$$;

update iam.users
set phone_number = translate(
        btrim(
            phone_number,
            U&'\0009\000A\000B\000C\000D\001C\001D\001E\001F\0020\00A0\1680\2000\2001\2002\2003\2004\2005\2006\2007\2008\2009\200A\2028\2029\202F\205F\3000'
        ),
        '۰۱۲۳۴۵۶۷۸۹٠١٢٣٤٥٦٧٨٩',
        '01234567890123456789'
    )
where phone_number is not null
  and phone_number is distinct from translate(
        btrim(
            phone_number,
            U&'\0009\000A\000B\000C\000D\001C\001D\001E\001F\0020\00A0\1680\2000\2001\2002\2003\2004\2005\2006\2007\2008\2009\200A\2028\2029\202F\205F\3000'
        ),
        '۰۱۲۳۴۵۶۷۸۹٠١٢٣٤٥٦٧٨٩',
        '01234567890123456789'
    );

alter table iam.users
    drop constraint if exists ck_users_phone_number_canonical;

alter table iam.users
    add constraint ck_users_phone_number_canonical
        check (
            phone_number is null
            or (
                phone_number = btrim(
                    phone_number,
                    U&'\0009\000A\000B\000C\000D\001C\001D\001E\001F\0020\00A0\1680\2000\2001\2002\2003\2004\2005\2006\2007\2008\2009\200A\2028\2029\202F\205F\3000'
                )
                and phone_number !~ '[۰-۹٠-٩]'
            )
        );
