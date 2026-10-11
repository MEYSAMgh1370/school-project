package com.example.roshdeandishe.academic.internal;

import com.example.roshdeandishe.academic.StudentStatus;
import com.example.roshdeandishe.academic.shared.AcademicError;
import com.example.roshdeandishe.academic.shared.AcademicException;
import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.AcademicYearInfo;
import com.example.roshdeandishe.school.SchoolFacade.SchoolId;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/**
 * قاعده‌ی یگانه‌ی «ثبت‌نام در سالِ جاری» (academic-year.md فاز ۳): کدام مدرسه ردیابی می‌شود و جا‌دادنِ یک دانش‌آموز
 * در کلاس چه چیزهایی را می‌نویسد. هم {@code AcademicFacadeImpl} (فرمِ ثبت‌نام) و هم {@code PromotionFacadeImpl}
 * (جا‌دادنِ دانش‌آموزانِ ارتقایافته) از همین استفاده می‌کنند تا دو مسیر دو قاعده نشوند.
 */
@Component
class EnrollmentLedger {

    private static final Set<String> VALID_TRACKS = Set.of("ریاضی", "تجربی", "انسانی");

    private final EnrollmentRepository enrollmentRepository;
    private final StudentRepository studentRepository;
    private final SchoolFacade schoolFacade;

    EnrollmentLedger(EnrollmentRepository enrollmentRepository, StudentRepository studentRepository,
                     SchoolFacade schoolFacade) {
        this.enrollmentRepository = enrollmentRepository;
        this.studentRepository = studentRepository;
        this.schoolFacade = schoolFacade;
    }

    /** سالِ جاریِ مدرسه؛ خالی اگر انتخاب نشده (و برای شناسه‌ای که مدرسه‌ای ندارد: همان «بدون سال»). */
    Optional<AcademicYearInfo> currentYear(Long schoolId) {
        SchoolId id = new SchoolId(schoolId);
        if (schoolFacade.findSchool(id).isEmpty()) {
            return Optional.empty();
        }
        return schoolFacade.findCurrentAcademicYear(id);
    }

    /** سالِ جاری دارد و همه‌ی دانش‌آموزانِ ACTIVE/PENDING اش ردیفِ ثبت‌نام دارند. */
    boolean isTracked(Long schoolId) {
        return currentYear(schoolId).isPresent() && studentRepository.countWithoutEnrollment(schoolId) == 0;
    }

    /** رشته یا تهی است (تعیین نشده) یا دقیقاً یکی از «ریاضی»، «تجربی»، «انسانی»؛ جای یگانه‌ی این قاعده. */
    static void requireValidTrack(String track) {
        if (track != null && !VALID_TRACKS.contains(track)) {
            throw new AcademicException(new AcademicError.InvalidTrack(track));
        }
    }

    /**
     * دانش‌آموز را در کلاس جا می‌دهد: ردیفِ سالِ جاری ENROLLED با همین کلاس و پایه‌ی آن، و خودِ دانش‌آموز ACTIVE با
     * همین کلاس. کلاسِ سالی دیگر ClassNotInYear؛ دانش‌آموزِ PENDING فقط در کلاسِ پایه‌ی هدفش ClassGradeMismatch.
     * {@code track} تهی = رشته‌ی ردیف همان‌طور که هست می‌ماند؛ مقدارِ غیرتهی باید معتبر باشد (InvalidTrack) و روی ردیف
     * نوشته می‌شود.
     */
    void place(StudentEntity student, ClassEntity classEntity, String currentYear, String track) {
        requireValidTrack(track);
        if (classEntity.getAcademicYear() != null && !classEntity.getAcademicYear().equals(currentYear)) {
            throw new AcademicException(new AcademicError.ClassNotInYear(
                    classEntity.getId(), classEntity.getAcademicYear(), currentYear));
        }
        EnrollmentEntity row = enrollmentRepository.findByStudentIdAndAcademicYear(student.getId(), currentYear)
                .orElse(null);
        if (row != null && row.getStatus() == EnrollmentEntity.Status.PENDING
                && row.getGradeLevel() != classEntity.getGradeLevel()) {
            throw new AcademicException(new AcademicError.ClassGradeMismatch(
                    classEntity.getId(), classEntity.getGradeLevel(), row.getGradeLevel()));
        }
        if (row == null) {
            row = new EnrollmentEntity(student.getSchoolId(), student.getId(), currentYear, classEntity.getId(),
                    classEntity.getGradeLevel(), EnrollmentEntity.Status.ENROLLED, null);
        } else {
            row.setClassId(classEntity.getId());
            row.setGradeLevel(classEntity.getGradeLevel());
            row.setStatus(EnrollmentEntity.Status.ENROLLED);
        }
        if (track != null) {
            row.setTrack(track);
        }
        enrollmentRepository.save(row);
        student.setClassId(classEntity.getId());
        student.setStatus(StudentStatus.ACTIVE);
        studentRepository.save(student);
    }
}
