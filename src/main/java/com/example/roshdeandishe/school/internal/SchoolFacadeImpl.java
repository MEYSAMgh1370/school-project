package com.example.roshdeandishe.school.internal;

import com.example.roshdeandishe.school.CurrentAcademicYearChangedEvent;
import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.shared.SchoolError;
import com.example.roshdeandishe.school.shared.SchoolException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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

    private final SchoolRepository schoolRepository;
    private final AcademicYearRepository academicYearRepository;
    private final CalendarEntryRepository calendarEntryRepository;
    private final ApplicationEventPublisher eventPublisher;

    SchoolFacadeImpl(SchoolRepository schoolRepository, AcademicYearRepository academicYearRepository,
                     CalendarEntryRepository calendarEntryRepository, ApplicationEventPublisher eventPublisher) {
        this.schoolRepository = schoolRepository;
        this.academicYearRepository = academicYearRepository;
        this.calendarEntryRepository = calendarEntryRepository;
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

    @Override
    public boolean isAutoPromotion(SchoolId schoolId) {
        return schoolRepository.findById(schoolId.value())
                .orElseThrow(() -> new SchoolException(new SchoolError.SchoolNotFound(schoolId.value())))
                .isAutoPromotion();
    }

    @Override
    @Transactional
    public void setAutoPromotion(SchoolId schoolId, boolean enabled) {
        SchoolEntity school = schoolRepository.findById(schoolId.value())
                .orElseThrow(() -> new SchoolException(new SchoolError.SchoolNotFound(schoolId.value())));
        school.setAutoPromotion(enabled);
        schoolRepository.save(school);
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
        String normalizedTitle = title == null ? "" : title.trim();
        if (normalizedTitle.isEmpty() || normalizedTitle.length() > 128) {
            throw new SchoolException(new SchoolError.InvalidCalendarEntry(
                    "عنوان ورودی تقویم الزامی است و حداکثر ۱۲۸ نویسه می‌تواند باشد."));
        }
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
        return calendarEntryRepository.existsBySchoolIdAndDayAndKind(schoolId.value(), day, CalendarKind.CLOSED);
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
        return new AcademicYearInfo(new AcademicYearId(entity.getId()), entity.getCode(), entity.getStartYear());
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
}
