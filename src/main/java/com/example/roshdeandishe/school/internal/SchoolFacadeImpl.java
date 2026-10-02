package com.example.roshdeandishe.school.internal;

import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.shared.SchoolError;
import com.example.roshdeandishe.school.shared.SchoolException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    SchoolFacadeImpl(SchoolRepository schoolRepository, AcademicYearRepository academicYearRepository) {
        this.schoolRepository = schoolRepository;
        this.academicYearRepository = academicYearRepository;
    }

    @Override
    @Transactional
    public SchoolId createSchool(String name, String timezone, String locale, SchoolLevel level, String slug) {
        String normalizedSlug = normalizeAndValidateSlug(slug);
        SchoolEntity entity = schoolRepository.save(new SchoolEntity(name, timezone, locale, level, normalizedSlug));
        return new SchoolId(entity.getId());
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
                entity.getSlug()
        );
    }
}
