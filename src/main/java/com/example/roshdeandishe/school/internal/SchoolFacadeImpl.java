package com.example.roshdeandishe.school.internal;

import com.example.roshdeandishe.school.CurrentAcademicYearChangedEvent;
import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.shared.SchoolError;
import com.example.roshdeandishe.school.shared.SchoolException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
class SchoolFacadeImpl implements SchoolFacade {

    /**
     * قالب یک DNS label (بدون خط تیره در ابتدا/انتها) - عمداً، تا slug در
     * فاز ساب‌دامین (Tier 2 دامنه‌ی اختصاصی، CLAUDE.md §11) بدون migration
     * قابل استفاده باشد. طول ۳ تا ۶۳ جداگانه چک می‌شود.
     */
    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$");

    /**
     * نام‌هایی که در فاز ساب‌دامین معنای زیرساختی دارند و هیچ مدرسه‌ای
     * نباید بتواند آن‌ها را بگیرد - الان رزرو می‌شوند، نه بعداً که یک
     * مدرسه‌ی واقعی آن‌ها را گرفته باشد.
     */
    private static final Set<String> RESERVED_SLUGS = Set.of(
            "www", "api", "app", "admin", "mail", "smtp", "static", "assets",
            "cdn", "dev", "test", "staging", "demo", "login", "register",
            "dashboard", "activate", "roshdeandishe"
    );

    private static final int MIN_REMIND_DAYS = 0;
    private static final int MAX_REMIND_DAYS = 30;

    private final SchoolRepository schoolRepository;
    private final AcademicYearRepository academicYearRepository;
    private final CalendarEntryRepository calendarEntryRepository;
    private final PlatformHolidayRepository platformHolidayRepository;
    private final SchoolYearRangeRepository schoolYearRangeRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ReminderRoleRepository reminderRoleRepository;
    private final SchoolSettingsRepository schoolSettingsRepository;
    private final BotRoleRepository botRoleRepository;

    SchoolFacadeImpl(SchoolRepository schoolRepository, AcademicYearRepository academicYearRepository,
                     CalendarEntryRepository calendarEntryRepository,
                     PlatformHolidayRepository platformHolidayRepository,
                     SchoolYearRangeRepository schoolYearRangeRepository, ApplicationEventPublisher eventPublisher,
                     ReminderRoleRepository reminderRoleRepository,
                     SchoolSettingsRepository schoolSettingsRepository, BotRoleRepository botRoleRepository) {
        this.reminderRoleRepository = reminderRoleRepository;
        this.schoolSettingsRepository = schoolSettingsRepository;
        this.botRoleRepository = botRoleRepository;
        this.schoolYearRangeRepository = schoolYearRangeRepository;
        this.schoolRepository = schoolRepository;
        this.academicYearRepository = academicYearRepository;
        this.calendarEntryRepository = calendarEntryRepository;
        this.platformHolidayRepository = platformHolidayRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public SchoolId createSchool(String name, String timezone, String locale, SchoolLevel level, String slug) {
        return createSchool(name, timezone, locale, level, slug, WeekStart.SATURDAY);
    }

    @Override
    @Transactional
    public SchoolId createSchool(String name, String timezone, String locale, SchoolLevel level, String slug,
                                 WeekStart weekStart) {
        String normalizedSlug = normalizeAndValidateSlug(slug);
        SchoolEntity entity = new SchoolEntity(name, timezone, locale, level, normalizedSlug);
        entity.setWeekStartDay((weekStart == null ? WeekStart.SATURDAY : weekStart).code());
        return new SchoolId(schoolRepository.save(entity).getId());
    }

    @Override
    public Optional<SchoolInfo> findSchool(SchoolId schoolId) {
        return schoolRepository.findById(schoolId.value()).map(this::toInfo);
    }

    @Override
    public Optional<SchoolInfo> findSchoolBySlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        return schoolRepository.findBySlug(slug.trim().toLowerCase(Locale.ROOT)).map(this::toInfo);
    }

    @Override
    public List<SchoolInfo> listSchools() {
        return schoolRepository.findAll().stream().map(this::toInfo).toList();
    }

    @Override
    @Transactional
    public void setSchoolStatus(SchoolId schoolId, SchoolStatus status) {
        SchoolEntity entity = schoolRepository.findById(schoolId.value())
                .orElseThrow(() -> new SchoolException(new SchoolError.SchoolNotFound(schoolId.value())));
        entity.setStatus(status);
        schoolRepository.save(entity);
    }

    @Override
    public List<AcademicYearInfo> listAcademicYears() {
        return academicYearRepository.findAllByOrderByStartYearAsc().stream()
                .map(SchoolFacadeImpl::toYearInfo)
                .toList();
    }

    @Override
    public Optional<AcademicYearInfo> findCurrentAcademicYear(SchoolId schoolId) {
        SchoolEntity school = schoolRepository.findById(schoolId.value())
                .orElseThrow(() -> new SchoolException(new SchoolError.SchoolNotFound(schoolId.value())));
        if (school.getCurrentAcademicYearId() == null) {
            return Optional.empty();
        }
        return academicYearRepository.findById(school.getCurrentAcademicYearId())
                .map(SchoolFacadeImpl::toYearInfo);
    }

    @Override
    @Transactional
    public void setCurrentAcademicYear(SchoolId schoolId, AcademicYearId academicYearId,
                                       AcademicYearId expectedCurrent) {
        schoolRepository.findById(schoolId.value())
                .orElseThrow(() -> new SchoolException(new SchoolError.SchoolNotFound(schoolId.value())));
        AcademicYearEntity year = academicYearRepository.findById(academicYearId.value())
                .orElseThrow(() -> new SchoolException(new SchoolError.AcademicYearNotFound(academicYearId.value())));
        Long expected = expectedCurrent == null ? null : expectedCurrent.value();
        int updated = schoolRepository.compareAndSetCurrentAcademicYear(schoolId.value(), expected, year.getId());
        if (updated == 0) {
            throw new SchoolException(new SchoolError.AcademicYearChanged(expected));
        }
        // رویداد فقط وقتی که سالِ قبلی وجود داشته و واقعاً عوض شده؛ اولین انتخاب و انتخابِ دوباره‌ی همان سال نه
        if (expected != null && !expected.equals(year.getId())) {
            academicYearRepository.findById(expected).ifPresent(previous -> eventPublisher.publishEvent(
                    new CurrentAcademicYearChangedEvent(schoolId.value(), previous.getCode(), previous.getStartYear(),
                            year.getCode(), year.getStartYear())));
        }
    }

    /**
     * ردیفِ تنظیماتِ مدرسه؛ اگر نباشد از ستون‌های قدیمیِ schools ساخته می‌شود (insert ... on conflict do nothing،
     * پس خواندنِ هم‌زمانِ اول فقط یک ردیف می‌سازد). SchoolNotFound اگر مدرسه نباشد.
     */
    private SchoolSettingsEntity loadOrCreateSettings(SchoolId schoolId) {
        Optional<SchoolSettingsEntity> existing = schoolSettingsRepository.findById(schoolId.value());
        if (existing.isPresent()) {
            return existing.get();
        }
        schoolSettingsRepository.createIfMissing(schoolId.value());
        return schoolSettingsRepository.findById(schoolId.value())
                .orElseThrow(() -> new SchoolException(new SchoolError.SchoolNotFound(schoolId.value())));
    }

    @Override
    @Transactional
    public boolean isAutoPromotion(SchoolId schoolId) {
        return loadOrCreateSettings(schoolId).isAutoPromotion();
    }

    @Override
    @Transactional
    public void setAutoPromotion(SchoolId schoolId, boolean enabled) {
        SchoolSettingsEntity settings = loadOrCreateSettings(schoolId);
        settings.setAutoPromotion(enabled);
        schoolSettingsRepository.save(settings);
        // نوشتنِ دوگانه: ستونِ قدیمیِ schools تا migrationِ حذفِ آن
        SchoolEntity school = schoolRepository.findById(schoolId.value())
                .orElseThrow(() -> new SchoolException(new SchoolError.SchoolNotFound(schoolId.value())));
        school.setAutoPromotion(enabled);
        schoolRepository.save(school);
    }

    @Override
    @Transactional
    public boolean isInstallmentsEnabled(SchoolId schoolId) {
        return loadOrCreateSettings(schoolId).isInstallmentsEnabled();
    }

    @Override
    @Transactional
    public void setInstallmentsEnabled(SchoolId schoolId, boolean enabled) {
        SchoolSettingsEntity settings = loadOrCreateSettings(schoolId);
        settings.setInstallmentsEnabled(enabled);
        schoolSettingsRepository.save(settings);
        // نوشتنِ دوگانه: ستونِ قدیمیِ schools تا migrationِ حذفِ آن
        SchoolEntity school = schoolRepository.findById(schoolId.value())
                .orElseThrow(() -> new SchoolException(new SchoolError.SchoolNotFound(schoolId.value())));
        school.setInstallmentsEnabled(enabled);
        schoolRepository.save(school);
    }

    @Override
    @Transactional
    public BotSettings getBotSettings(SchoolId schoolId) {
        SchoolSettingsEntity settings = loadOrCreateSettings(schoolId);
        Set<String> roles = new java.util.HashSet<>();
        botRoleRepository.findBySchoolId(schoolId.value()).forEach(role -> roles.add(role.getRoleCode()));
        return new BotSettings(settings.isBotEnabled(), Set.copyOf(roles));
    }

    @Override
    @Transactional
    public void setBotSettings(SchoolId schoolId, boolean enabled, Set<String> roles) {
        Set<String> wanted = new java.util.HashSet<>();
        if (roles != null) {
            for (String code : roles) {
                if (code == null || code.isBlank() || code.trim().length() > 64) {
                    throw new SchoolException(new SchoolError.InvalidSetting("کد نقش نامعتبر است."));
                }
                wanted.add(code.trim());
            }
        }
        SchoolSettingsEntity settings = loadOrCreateSettings(schoolId);
        settings.setBotEnabled(enabled);
        schoolSettingsRepository.save(settings);
        List<BotRoleEntity> existing = botRoleRepository.findBySchoolId(schoolId.value());
        Set<String> existingCodes = new java.util.HashSet<>();
        List<BotRoleEntity> toRemove = new ArrayList<>();
        for (BotRoleEntity role : existing) {
            existingCodes.add(role.getRoleCode());
            if (!wanted.contains(role.getRoleCode())) {
                toRemove.add(role);
            }
        }
        botRoleRepository.deleteAll(toRemove);
        List<BotRoleEntity> toAdd = new ArrayList<>();
        for (String code : wanted) {
            if (!existingCodes.contains(code)) {
                toAdd.add(new BotRoleEntity(schoolId.value(), code));
            }
        }
        botRoleRepository.saveAll(toAdd);
    }

    @Override
    @Transactional
    public boolean isNotificationsEnabled(SchoolId schoolId) {
        return loadOrCreateSettings(schoolId).isNotificationsEnabled();
    }

    @Override
    @Transactional
    public void setNotificationsEnabled(SchoolId schoolId, boolean enabled) {
        SchoolSettingsEntity settings = loadOrCreateSettings(schoolId);
        settings.setNotificationsEnabled(enabled);
        schoolSettingsRepository.save(settings);
    }

    @Override
    @Transactional
    public boolean isBotAllowed(SchoolId schoolId, Set<String> roleCodes) {
        BotSettings settings = getBotSettings(schoolId);
        if (!settings.enabled()) {
            return false;
        }
        if (settings.roles().isEmpty()) {
            return true;
        }
        return roleCodes != null && roleCodes.stream().anyMatch(settings.roles()::contains);
    }

    @Override
    public List<CalendarEntryInfo> listCalendar(SchoolId schoolId, LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new SchoolException(new SchoolError.InvalidCalendarEntry(
                    "بازه‌ی تقویم باید با «از» و «تا» مشخص شود و «از» بعد از «تا» نباشد."));
        }
        requireSchool(schoolId);
        return calendarEntryRepository.findBySchoolIdAndDayBetweenOrderByDayAscIdAsc(schoolId.value(), from, to)
                .stream()
                .map(SchoolFacadeImpl::toCalendarInfo)
                .toList();
    }

    @Override
    @Transactional
    public CalendarEntryInfo addCalendarEntry(SchoolId schoolId, LocalDate day, CalendarKind kind, String title) {
        if (day == null || kind == null) {
            throw new SchoolException(new SchoolError.InvalidCalendarEntry("روز و نوع ورودی تقویم الزامی است."));
        }
        String normalizedTitle = normalizeTitle(title);
        requireSchool(schoolId);
        return toCalendarInfo(calendarEntryRepository.save(
                new CalendarEntryEntity(schoolId.value(), day, kind, normalizedTitle)));
    }

    @Override
    @Transactional
    public void removeCalendarEntry(SchoolId schoolId, Long entryId) {
        CalendarEntryEntity entry = calendarEntryRepository.findByIdAndSchoolId(entryId, schoolId.value())
                .orElseThrow(() -> new SchoolException(new SchoolError.CalendarEntryNotFound(entryId)));
        calendarEntryRepository.delete(entry);
    }

    @Override
    public boolean isClosedDay(SchoolId schoolId, LocalDate day) {
        // تعطیلیِ سراسری روز را در همه‌ی مدرسه‌ها می‌بندد و مدرسه نمی‌تواند لغوش کند
        return calendarEntryRepository.existsBySchoolIdAndDayAndKind(schoolId.value(), day, CalendarKind.CLOSED)
                || platformHolidayRepository.existsByDay(day);
    }

    @Override
    public YearlyPlan yearlyPlan(SchoolId schoolId, AcademicYearId academicYearId) {
        requireSchool(schoolId);
        AcademicYearInfo year = requireYear(academicYearId);
        List<YearlyPlanItem> items = new ArrayList<>();
        YearlyPlanRange range = effectiveRange(schoolId, year);
        platformHolidayRepository.findByAcademicYearIdOrderByDayAscIdAsc(year.id().value()).forEach(holiday ->
                items.add(new YearlyPlanItem(holiday.getId(), holiday.getDay(), holiday.getTitle(),
                        YearlyPlanSource.PLATFORM, null)));
        calendarEntryRepository.findBySchoolIdAndKindAndDayBetweenOrderByDayAscIdAsc(
                        schoolId.value(), CalendarKind.EVENT, range.startDate(), range.endDate())
                .forEach(entry -> items.add(toPlanItem(entry)));
        // مرتب‌سازیِ پایدار: در یک روز، تعطیلیِ سراسری پیش از رویدادِ مدرسه می‌آید
        items.sort(Comparator.comparing(YearlyPlanItem::day));
        return new YearlyPlan(year, range, List.copyOf(items));
    }

    @Override
    @Transactional
    public YearlyPlanRange setYearlyPlanRange(SchoolId schoolId, AcademicYearId academicYearId, LocalDate startDate,
                                              LocalDate endDate) {
        requireSchool(schoolId);
        AcademicYearInfo year = requireYear(academicYearId);
        if (startDate == null || endDate == null) {
            throw new SchoolException(new SchoolError.InvalidYearRange("تاریخ شروع و پایان بازه الزامی است."));
        }
        if (endDate.isBefore(startDate)) {
            throw new SchoolException(new SchoolError.InvalidYearRange("تاریخ پایان نباید پیش از تاریخ شروع باشد."));
        }
        if (startDate.isAfter(year.startDate()) || endDate.isBefore(year.endDate())) {
            throw new SchoolException(new SchoolError.InvalidYearRange(
                    "بازه‌ی پیش‌فرضِ سال «" + year.code() + "» از " + SolarCalendar.format(year.startDate()) + " تا "
                            + SolarCalendar.format(year.endDate()) + " است و حداقلِ بازه‌ی برنامه‌ی سالانه است؛ "
                            + "فقط می‌توان آن را گسترش داد (شروع در یا پیش از شروعِ پیش‌فرض و پایان در یا پس از "
                            + "پایانِ پیش‌فرض)."));
        }
        // سقفِ گسترش: حداکثر یک ماهِ جلالی از هر طرف، یعنی از ۱ شهریور تا ۳۱ تیر (بازه‌ی پیش‌فرض ۱ مهر تا ۳۱ خرداد)
        LocalDate earliestStart = SolarCalendar.toGregorian(year.startYear(), 6, 1);
        LocalDate latestEnd = SolarCalendar.toGregorian(year.startYear() + 1, 4, 31);
        if (startDate.isBefore(earliestStart) || endDate.isAfter(latestEnd)) {
            throw new SchoolException(new SchoolError.InvalidYearRange(
                    "بازه را فقط تا یک ماه از هر طرف می‌توان گسترش داد: شروع نه پیش از "
                            + SolarCalendar.format(earliestStart) + " و پایان نه پس از "
                            + SolarCalendar.format(latestEnd) + " (بازه‌ی پیش‌فرض از "
                            + SolarCalendar.format(year.startDate()) + " تا " + SolarCalendar.format(year.endDate()) + ")."));
        }
        Optional<SchoolYearRangeEntity> existing = schoolYearRangeRepository
                .findBySchoolIdAndAcademicYearId(schoolId.value(), year.id().value());
        if (startDate.equals(year.startDate()) && endDate.equals(year.endDate())) {
            existing.ifPresent(schoolYearRangeRepository::delete);
            return new YearlyPlanRange(year.startDate(), year.endDate(), false);
        }
        SchoolYearRangeEntity entity = existing.orElseGet(() ->
                new SchoolYearRangeEntity(schoolId.value(), year.id().value(), startDate, endDate));
        entity.setRange(startDate, endDate);
        schoolYearRangeRepository.save(entity);
        return new YearlyPlanRange(startDate, endDate, true);
    }

    private YearlyPlanRange effectiveRange(SchoolId schoolId, AcademicYearInfo year) {
        return schoolYearRangeRepository.findBySchoolIdAndAcademicYearId(schoolId.value(), year.id().value())
                .map(range -> new YearlyPlanRange(range.getStartDate(), range.getEndDate(), true))
                .orElseGet(() -> new YearlyPlanRange(year.startDate(), year.endDate(), false));
    }

    @Override
    @Transactional
    public YearlyPlanItem addYearlyPlanEvent(SchoolId schoolId, AcademicYearId academicYearId, LocalDate day,
                                             String title, Integer remindDaysBefore) {
        if (day == null || academicYearId == null) {
            throw new SchoolException(new SchoolError.InvalidCalendarEntry("روز و سال تحصیلی الزامی است."));
        }
        String normalizedTitle = normalizeTitle(title);
        if (remindDaysBefore != null && (remindDaysBefore < MIN_REMIND_DAYS || remindDaysBefore > MAX_REMIND_DAYS)) {
            throw new SchoolException(new SchoolError.InvalidCalendarEntry(
                    "زمان یادآوری باید بین ۰ تا ۳۰ روز پیش از رویداد باشد."));
        }
        requireSchool(schoolId);
        AcademicYearInfo year = requireYear(academicYearId);
        YearlyPlanRange range = effectiveRange(schoolId, year);
        if (day.isBefore(range.startDate()) || day.isAfter(range.endDate())) {
            throw new SchoolException(new SchoolError.InvalidCalendarEntry(
                    "روز باید در بازه‌ی برنامه‌ی سالانه‌ی سال «" + year.code() + "» (از "
                            + SolarCalendar.format(range.startDate()) + " تا "
                            + SolarCalendar.format(range.endDate()) + ") باشد."));
        }
        Short lead = remindDaysBefore == null ? null : remindDaysBefore.shortValue();
        return toPlanItem(calendarEntryRepository.save(
                new CalendarEntryEntity(schoolId.value(), day, CalendarKind.EVENT, normalizedTitle, lead)));
    }

    @Override
    public List<ReminderEvent> dueReminderEvents(SchoolId schoolId, LocalDate today) {
        requireSchool(schoolId);
        // پنجره‌ی هر رویداد حداکثر ۳۰ روز است؛ پس فقط رویدادهای [today, today+30] کاندیدند و فیلتر دقیق اینجاست
        return calendarEntryRepository
                .findBySchoolIdAndKindAndRemindDaysBeforeIsNotNullAndDayBetweenOrderByDayAscIdAsc(
                        schoolId.value(), CalendarKind.EVENT, today, today.plusDays(MAX_REMIND_DAYS))
                .stream()
                .filter(entry -> entry.getKind() == CalendarKind.EVENT && entry.getRemindDaysBefore() != null
                        && !entry.getDay().minusDays(entry.getRemindDaysBefore()).isAfter(today)
                        && !today.isAfter(entry.getDay()))
                .map(entry -> new ReminderEvent(entry.getId(), entry.getDay(), entry.getTitle(),
                        entry.getRemindDaysBefore()))
                .toList();
    }

    @Override
    public Optional<Set<String>> reminderRoleCodes(SchoolId schoolId) {
        requireSchool(schoolId);
        Set<String> codes = new java.util.TreeSet<>();
        reminderRoleRepository.findBySchoolId(schoolId.value()).forEach(role -> codes.add(role.getRoleCode()));
        return codes.isEmpty() ? Optional.empty() : Optional.of(Set.copyOf(codes));
    }

    @Override
    @Transactional
    public void setReminderRoleCodes(SchoolId schoolId, Set<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            throw new SchoolException(new SchoolError.InvalidSetting(
                    "دست‌کم یک نقش برای دریافت یادآوری باید انتخاب شود."));
        }
        Set<String> wanted = new java.util.HashSet<>();
        for (String code : roleCodes) {
            if (code == null || code.isBlank() || code.trim().length() > 64) {
                throw new SchoolException(new SchoolError.InvalidSetting("کد نقش نامعتبر است."));
            }
            wanted.add(code.trim());
        }
        requireSchool(schoolId);
        // جایگزینی با تفاضل (نه حذف‌همه و درج دوباره): Hibernate درج را پیش از حذف flush می‌کند و کلید تکراری می‌شد
        List<ReminderRoleEntity> existing = reminderRoleRepository.findBySchoolId(schoolId.value());
        Set<String> existingCodes = new java.util.HashSet<>();
        List<ReminderRoleEntity> toRemove = new ArrayList<>();
        for (ReminderRoleEntity role : existing) {
            existingCodes.add(role.getRoleCode());
            if (!wanted.contains(role.getRoleCode())) {
                toRemove.add(role);
            }
        }
        reminderRoleRepository.deleteAll(toRemove);
        List<ReminderRoleEntity> toAdd = new ArrayList<>();
        for (String code : wanted) {
            if (!existingCodes.contains(code)) {
                toAdd.add(new ReminderRoleEntity(schoolId.value(), code));
            }
        }
        reminderRoleRepository.saveAll(toAdd);
    }

    private static YearlyPlanItem toPlanItem(CalendarEntryEntity entry) {
        return new YearlyPlanItem(entry.getId(), entry.getDay(), entry.getTitle(), YearlyPlanSource.SCHOOL,
                entry.getRemindDaysBefore() == null ? null : entry.getRemindDaysBefore().intValue());
    }

    @Override
    @Transactional
    public void removeYearlyPlanEvent(SchoolId schoolId, Long entryId) {
        // فقط EVENT: ورودیِ CLOSED مال صفحه‌ی تعطیلی‌هاست و از اینجا حذف نمی‌شود
        CalendarEntryEntity entry = calendarEntryRepository
                .findByIdAndSchoolIdAndKind(entryId, schoolId.value(), CalendarKind.EVENT)
                .orElseThrow(() -> new SchoolException(new SchoolError.CalendarEntryNotFound(entryId)));
        calendarEntryRepository.delete(entry);
    }

    @Override
    @Transactional
    public PlatformHolidayInfo addPlatformHoliday(AcademicYearId academicYearId, LocalDate day, String title) {
        if (day == null || academicYearId == null) {
            throw new SchoolException(new SchoolError.InvalidCalendarEntry("روز و سال تحصیلی الزامی است."));
        }
        String normalizedTitle = normalizeTitle(title);
        requireDayInYear(requireYear(academicYearId), day);
        return toHolidayInfo(platformHolidayRepository.save(
                new PlatformHolidayEntity(academicYearId.value(), day, normalizedTitle)));
    }

    @Override
    @Transactional
    public void removePlatformHoliday(Long id) {
        PlatformHolidayEntity holiday = (id == null ? Optional.<PlatformHolidayEntity>empty()
                : platformHolidayRepository.findById(id))
                .orElseThrow(() -> new SchoolException(new SchoolError.PlatformHolidayNotFound(id)));
        platformHolidayRepository.delete(holiday);
    }

    @Override
    public List<PlatformHolidayInfo> listPlatformHolidays(AcademicYearId academicYearId) {
        AcademicYearInfo year = requireYear(academicYearId);
        return platformHolidayRepository.findByAcademicYearIdOrderByDayAscIdAsc(year.id().value()).stream()
                .map(SchoolFacadeImpl::toHolidayInfo)
                .toList();
    }

    private static String normalizeTitle(String title) {
        String normalizedTitle = title == null ? "" : title.trim();
        if (normalizedTitle.isEmpty() || normalizedTitle.length() > 128) {
            throw new SchoolException(new SchoolError.InvalidCalendarEntry(
                    "عنوان ورودی تقویم الزامی است و حداکثر ۱۲۸ نویسه می‌تواند باشد."));
        }
        return normalizedTitle;
    }

    private AcademicYearInfo requireYear(AcademicYearId academicYearId) {
        Long id = academicYearId == null ? null : academicYearId.value();
        return (id == null ? Optional.<AcademicYearEntity>empty() : academicYearRepository.findById(id))
                .map(SchoolFacadeImpl::toYearInfo)
                .orElseThrow(() -> new SchoolException(new SchoolError.AcademicYearNotFound(id)));
    }

    private static void requireDayInYear(AcademicYearInfo year, LocalDate day) {
        if (day.isBefore(year.startDate()) || day.isAfter(year.endDate())) {
            throw new SchoolException(new SchoolError.InvalidCalendarEntry(
                    "روز باید در بازه‌ی سال تحصیلی «" + year.code() + "» (۱ مهر تا ۳۱ خرداد) باشد."));
        }
    }

    private static PlatformHolidayInfo toHolidayInfo(PlatformHolidayEntity entity) {
        return new PlatformHolidayInfo(entity.getId(), new AcademicYearId(entity.getAcademicYearId()),
                entity.getDay(), entity.getTitle());
    }

    private void requireSchool(SchoolId schoolId) {
        if (!schoolRepository.existsById(schoolId.value())) {
            throw new SchoolException(new SchoolError.SchoolNotFound(schoolId.value()));
        }
    }

    private static CalendarEntryInfo toCalendarInfo(CalendarEntryEntity entity) {
        return new CalendarEntryInfo(entity.getId(), entity.getDay(), entity.getKind(), entity.getTitle());
    }

    private static AcademicYearInfo toYearInfo(AcademicYearEntity entity) {
        int startYear = entity.getStartYear();
        return new AcademicYearInfo(new AcademicYearId(entity.getId()), entity.getCode(), startYear,
                SolarCalendar.toGregorian(startYear, 7, 1), SolarCalendar.toGregorian(startYear + 1, 3, 31));
    }

    /**
     * null/خالی مجاز است (مدرسه بدون نشانی اختصاصی)؛ در غیر این صورت
     * lowercase می‌شود و باید قالب/طول/رزرونبودن/یکتایی را پاس کند.
     */
    private String normalizeAndValidateSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return null;
        }
        String normalized = slug.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() < 3 || normalized.length() > 63 || !SLUG_PATTERN.matcher(normalized).matches()
                || RESERVED_SLUGS.contains(normalized)) {
            throw new SchoolException(new SchoolError.InvalidSlug(normalized));
        }
        if (schoolRepository.existsBySlug(normalized)) {
            throw new SchoolException(new SchoolError.SlugAlreadyTaken(normalized));
        }
        return normalized;
    }

    private SchoolInfo toInfo(SchoolEntity entity) {
        return new SchoolInfo(
                new SchoolId(entity.getId()),
                entity.getName(),
                entity.getStatus(),
                entity.getTimezone(),
                entity.getLocale(),
                entity.getSchoolLevel(),
                entity.getSlug(),
                WeekStart.fromCode(entity.getWeekStartDay())
        );
    }

    @Override
    public String formatSolarDate(java.time.LocalDate date) {
        return SolarCalendar.format(date);
    }

    @Override
    public LocalDate addSolarMonths(LocalDate gregorian, int months) {
        return SolarCalendar.addMonths(gregorian, months);
    }
}
