-- Phone numbers entered through the Persian RTL UI may already be stored with
-- Persian or Arabic-Indic digits. Application lookups now use ASCII digits, so
-- existing rows must be canonicalized in the same deployment.
--
-- Exact-string uniqueness previously allowed two accounts to claim visually
-- equivalent numbers in different digit scripts. Such a collision cannot be
-- resolved safely by choosing one account. Abort with an operationally useful
-- error before changing any row; an operator must resolve the conflicting
-- identities and rerun the migration.
do $$
begin
    if exists (
        select canonical_phone_number
        from (
            select translate(
                       btrim(phone_number),
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
        btrim(phone_number),
        '۰۱۲۳۴۵۶۷۸۹٠١٢٣٤٥٦٧٨٩',
        '01234567890123456789'
    )
where phone_number is not null
  and phone_number is distinct from translate(
        btrim(phone_number),
        '۰۱۲۳۴۵۶۷۸۹٠١٢٣٤٥٦٧٨٩',
        '01234567890123456789'
    );

alter table iam.users
    add constraint ck_users_phone_number_canonical
        check (
            phone_number is null
            or (
                phone_number = btrim(phone_number)
                and phone_number !~ '[۰-۹٠-٩]'
            )
        );
