package com.example.roshdeandishe.attendance.shared;

import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * نگاشتِ هر AttendanceError به ProblemDetail (وضعیت و پسوندِ kebab-case) بدون MVC؛ هر خطای تازه باید اینجا هم بیاید.
 */
class AttendanceProblemResolverTest {

    private final AttendanceProblemResolver resolver = new AttendanceProblemResolver();

    @Test
    void everyErrorMapsToItsStatusAndTypeSlug() {
        LocalDate day = LocalDate.of(2024, 10, 7);
        List<Expected> expected = List.of(
                new Expected(new AttendanceError.InvalidAttendanceDay(day, "دلیل"), 400, "invalid-attendance-day"),
                new Expected(new AttendanceError.ClassNotFound(1L), 404, "class-not-found"),
                new Expected(new AttendanceError.SchoolNotFound(1L), 404, "school-not-found"),
                new Expected(new AttendanceError.ClassNotInCurrentYear(1L, "1404-1405", "1405-1406"), 409,
                        "class-not-in-current-year"),
                new Expected(new AttendanceError.StudentNotInClass(2L, 1L), 409, "student-not-in-class"),
                new Expected(new AttendanceError.InvalidMark("دلیل"), 400, "invalid-attendance-mark"),
                new Expected(new AttendanceError.InvalidRange("دلیل"), 400, "invalid-attendance-range"),
                new Expected(new AttendanceError.NotYourClass(1L), 403, "attendance-not-your-class"),
                new Expected(new AttendanceError.PastEditNotAllowed(day), 403, "attendance-past-edit-not-allowed"),
                new Expected(new AttendanceError.NotYourChild(2L), 403, "attendance-not-your-child"),
                new Expected(new AttendanceError.InvalidExcuse("دلیل"), 400, "invalid-excuse"),
                new Expected(new AttendanceError.NoAbsenceToExcuse(2L, day), 409, "no-absence-to-excuse"),
                new Expected(new AttendanceError.ExcuseAlreadyDecided(3L), 409, "excuse-already-decided"),
                new Expected(new AttendanceError.ExcuseNotFound(3L), 404, "excuse-not-found"));

        for (Expected e : expected) {
            ProblemDetail problem = resolver.resolve(e.error());

            assertThat(problem.getStatus()).as(e.slug()).isEqualTo(e.status());
            assertThat(problem.getType().toString()).as(e.slug()).endsWith(e.slug());
            assertThat(problem.getDetail()).as(e.slug()).isNotBlank();
            // پیامِ exception هم برای هر خطا ساخته می‌شود (switchِ کامل)
            assertThat(new AttendanceException(e.error()).getMessage()).as(e.slug()).isNotBlank();
        }
    }

    private record Expected(AttendanceError error, int status, String slug) {
    }
}
