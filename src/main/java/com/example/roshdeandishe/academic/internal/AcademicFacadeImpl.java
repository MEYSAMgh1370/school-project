package com.example.roshdeandishe.academic.internal;

import com.example.roshdeandishe.academic.AcademicFacade;
import com.example.roshdeandishe.academic.DescriptiveGradeLevel;
import com.example.roshdeandishe.academic.GradingScheme;
import com.example.roshdeandishe.academic.GradingTerm;
import com.example.roshdeandishe.academic.StudentEnrolledEvent;
import com.example.roshdeandishe.academic.StudentStatus;
import com.example.roshdeandishe.academic.shared.AcademicError;
import com.example.roshdeandishe.academic.shared.AcademicYearCodes;
import com.example.roshdeandishe.academic.shared.AcademicException;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.shared.exception.IamException;
import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.AcademicYearInfo;
import com.example.roshdeandishe.school.SchoolFacade.SchoolInfo;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
class AcademicFacadeImpl implements AcademicFacade {

    private static final BigDecimal MAX_NUMERIC_SCORE = new BigDecimal("20");

    private final StudentRepository studentRepository;
    private final ClassRepository classRepository;
    private final StudentGuardianRepository studentGuardianRepository;
    private final GradeRepository gradeRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentLedger enrollmentLedger;
    private final IamFacade iamFacade;
    private final SchoolFacade schoolFacade;
    private final ApplicationEventPublisher eventPublisher;

    AcademicFacadeImpl(
            StudentRepository studentRepository,
            ClassRepository classRepository,
            StudentGuardianRepository studentGuardianRepository,
            GradeRepository gradeRepository,
            CourseRepository courseRepository,
            EnrollmentRepository enrollmentRepository,
            EnrollmentLedger enrollmentLedger,
            IamFacade iamFacade,
            SchoolFacade schoolFacade,
            ApplicationEventPublisher eventPublisher
    ) {
        this.studentRepository = studentRepository;
        this.classRepository = classRepository;
        this.studentGuardianRepository = studentGuardianRepository;
        this.gradeRepository = gradeRepository;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.enrollmentLedger = enrollmentLedger;
        this.iamFacade = iamFacade;
        this.schoolFacade = schoolFacade;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public StudentId enrollStudent(String email, String fullName, Long schoolId, Long classId) {
        ClassEntity classEntity = requireClassInSchool(schoolId, classId);

        // «ردیاب‌شده» بودنِ مدرسه پیش از افزودنِ این دانش‌آموز سنجیده می‌شود (academic-year.md فاز ۳)
        Optional<AcademicYearInfo> currentYear = enrollmentLedger.currentYear(schoolId);
        boolean tracked = enrollmentLedger.isTracked(schoolId);
        if (tracked && classEntity.getAcademicYear() != null
                && !classEntity.getAcademicYear().equals(currentYear.get().code())) {
            throw new AcademicException(new AcademicError.ClassNotInYear(
                    classId, classEntity.getAcademicYear(), currentYear.get().code()));
        }

        // همان الگوی SchoolController: پیدا/ساخت کاربر در iam، سپس Membership
        // نقش STUDENT برای او در این مدرسه (idempotent در هر دو مرحله؛ Membershipِ غیرفعال دوباره فعال می‌شود)
        IamFacade.UserId userId = iamFacade.findOrCreateUser(email, fullName);
        iamFacade.createMembership(userId, schoolId, "STUDENT");

        Optional<StudentEntity> existingStudent = studentRepository.findByUserIdAndSchoolId(userId.value(), schoolId);
        StudentEntity student = existingStudent
                .orElseGet(() -> new StudentEntity(userId.value(), schoolId, classId, Instant.now()));
        // ثبت‌نامِ دوباره‌ی کسی که فارغ‌التحصیل شده یا مدرسه را ترک کرده یک ثبت‌نامِ تازه است (فاکتور و ...)
        boolean returning = existingStudent.isPresent()
                && (student.getStatus() == StudentStatus.GRADUATED || student.getStatus() == StudentStatus.LEFT);
        student.setClassId(classId); // پشتیبانی از جابه‌جایی کلاس در فراخوانی دوباره
        student.setStatus(StudentStatus.ACTIVE);
        StudentEntity saved = studentRepository.save(student);

        if (tracked) {
            enrollmentLedger.place(saved, classEntity, currentYear.get().code());
        }

        if (existingStudent.isEmpty() || returning) {
            eventPublisher.publishEvent(new StudentEnrolledEvent(schoolId, saved.getId(), classId));
        }

        return new StudentId(saved.getId());
    }

    @Override
    public StudentInfo getStudentInfo(Long studentId) {
        return findStudentInfo(studentId)
                .orElseThrow(() -> new AcademicException(new AcademicError.StudentNotFound(studentId)));
    }

    @Override
    public StudentInfo getStudentInfo(Long schoolId, Long studentId) {
        StudentInfo info = getStudentInfo(studentId);
        if (!info.schoolId().equals(schoolId)) {
            throw new AcademicException(new AcademicError.StudentNotFound(studentId));
        }
        return info;
    }

    @Override
    public Optional<StudentInfo> findStudentInfo(Long studentId) {
        return studentRepository.findById(studentId)
                .map(AcademicFacadeImpl::toStudentInfo);
    }

    @Override
    public List<StudentInfo> listStudents(Long schoolId) {
        return studentRepository.findBySchoolIdAndStatusIn(schoolId, List.of(StudentStatus.ACTIVE, StudentStatus.PENDING))
                .stream()
                .map(AcademicFacadeImpl::toStudentInfo)
                .toList();
    }

    @Override
    public List<StudentInfo> listStudentsInClass(Long schoolId, Long classId) {
        requireClassInSchool(schoolId, classId);
        Set<Long> ids = new LinkedHashSet<>();
        studentRepository.findBySchoolId(schoolId).stream()
                .filter(s -> classId.equals(s.getClassId()))
                .forEach(s -> ids.add(s.getId()));
        enrollmentRepository.findByClassId(classId).forEach(e -> ids.add(e.getStudentId()));
        return studentRepository.findAllById(ids).stream()
                .filter(s -> s.getSchoolId().equals(schoolId))
                .map(AcademicFacadeImpl::toStudentInfo)
                .toList();
    }

    @Override
    public List<StudentInfo> listStudentsEnrolledIn(Long schoolId, String academicYear) {
        String year = AcademicYearCodes.canonical(academicYear);
        requireListedYear(year);
        if (!enrollmentLedger.isTracked(schoolId)) {
            return listStudents(schoolId);
        }
        List<Long> studentIds = enrollmentRepository.findBySchoolIdAndAcademicYear(schoolId, year).stream()
                .map(EnrollmentEntity::getStudentId)
                .toList();
        return studentRepository.findAllById(studentIds).stream()
                .map(AcademicFacadeImpl::toStudentInfo)
                .toList();
    }

    @Override
    @Transactional
    public ClassId createClass(Long schoolId, String name, Long teacherId, String section, int gradeLevel, GradingScheme gradingScheme) {
        return createClass(schoolId, name, teacherId, section, gradeLevel, gradingScheme, null);
    }

    @Override
    @Transactional
    public ClassId createClass(Long schoolId, String name, Long teacherId, String section, int gradeLevel,
                               GradingScheme gradingScheme, String academicYear) {
        requireValidGradeLevel(schoolId, gradeLevel);
        String year = AcademicYearCodes.canonical(academicYear);
        if (year != null && !year.isEmpty()) {
            requireListedYear(year);
        } else {
            year = enrollmentLedger.currentYear(schoolId).map(AcademicYearInfo::code).orElse(null);
        }
        ClassEntity saved = classRepository.save(
                new ClassEntity(schoolId, name, teacherId, section, gradeLevel, gradingScheme, year));
        return new ClassId(saved.getId());
    }

    @Override
    public List<ClassInfo> listClasses(Long schoolId) {
        return classRepository.findBySchoolId(schoolId).stream()
                .map(AcademicFacadeImpl::toClassInfo)
                .toList();
    }

    @Override
    public List<ClassInfo> listClasses(Long schoolId, String academicYear) {
        String year = AcademicYearCodes.canonical(academicYear);
        if (year == null || year.isEmpty()) {
            return listClasses(schoolId);
        }
        requireListedYear(year);
        return classRepository.findBySchoolIdAndYearOrUnattributed(schoolId, year).stream()
                .map(AcademicFacadeImpl::toClassInfo)
                .toList();
    }

    @Override
    @Transactional
    public GradeId recordGrade(Long schoolId, Long studentId, Long classId, String academicYear, GradingTerm term,
                                DescriptiveGradeLevel descriptiveLevel, BigDecimal numericScore, String comment,
                                Long courseId) {
        ClassEntity classEntity = requireClassInSchool(schoolId, classId);
        if (courseId != null) {
            // درس باید در فهرست سراسری باشد و هم‌پایه‌ی کلاس (فهرست به‌تفکیک پایه است)
            CourseEntity course = courseRepository.findById(courseId)
                    .orElseThrow(() -> new AcademicException(new AcademicError.CourseNotFound(courseId)));
            if (course.getGradeLevel() != classEntity.getGradeLevel()) {
                throw new AcademicException(new AcademicError.CourseNotForClass(courseId, classId));
            }
            if (!course.isGraded()) {
                throw new AcademicException(new AcademicError.CourseNotGradable(courseId));
            }
        }
        StudentEntity student = studentRepository.findById(studentId)
                .orElseThrow(() -> new AcademicException(new AcademicError.StudentNotFound(studentId)));
        // عضوِ کلاسِ جاری است، یا در آن کلاس ثبت‌نامِ سالی را داشته (اصلاحِ نمره‌ی سالِ قبل توسط مدیر)
        if (!student.getSchoolId().equals(schoolId)
                || !(classId.equals(student.getClassId())
                        || enrollmentRepository.existsByStudentIdAndClassId(studentId, classId))) {
            throw new AcademicException(new AcademicError.StudentNotInClass(studentId, classId));
        }
        if (academicYear == null || academicYear.isBlank()) {
            throw new AcademicException(new AcademicError.InvalidGrade("سال تحصیلی الزامی است"));
        }
        // سال کلید است: یک شکل canonical و حتماً از فهرستِ سال‌ها (academic-year.md فاز ۱)
        academicYear = AcademicYearCodes.canonical(academicYear);
        int startYear = requireListedYear(academicYear);
        // کلاس به یک سال تعلق دارد (academic-year.md فاز ۳): نمره‌ی سالِ دیگری در آن ثبت نمی‌شود؛ کلاسِ بدون سال مثل قبل
        if (classEntity.getAcademicYear() != null && !classEntity.getAcademicYear().equals(academicYear)) {
            throw new AcademicException(new AcademicError.GradeYearNotClassYear(
                    classId, classEntity.getAcademicYear(), academicYear));
        }
        if (courseId != null) {
            // بازه‌ی اعتبارِ درس به سالِ نمره (academic-year.md فاز ۱)؛ وجودِ درس پیش‌تر بررسی شده
            CourseEntity course = courseRepository.findById(courseId).orElseThrow();
            if (!course.isOfferedIn(startYear)) {
                throw new AcademicException(new AcademicError.CourseNotValidForYear(courseId, academicYear));
            }
        }

        GradingScheme scheme = classEntity.getGradingScheme() != null ? classEntity.getGradingScheme() : GradingScheme.NUMERIC;
        switch (scheme) {
            case DESCRIPTIVE -> {
                if (descriptiveLevel == null || numericScore != null) {
                    throw new AcademicException(new AcademicError.GradingSchemeMismatch(classId, scheme));
                }
            }
            case NUMERIC -> {
                if (numericScore == null || descriptiveLevel != null) {
                    throw new AcademicException(new AcademicError.GradingSchemeMismatch(classId, scheme));
                }
                if (numericScore.compareTo(BigDecimal.ZERO) < 0 || numericScore.compareTo(MAX_NUMERIC_SCORE) > 0) {
                    throw new AcademicException(new AcademicError.InvalidGrade("نمره باید بین ۰ تا ۲۰ باشد"));
                }
            }
        }

        // نمرهٔ کل و نمره‌ی هر درس ردیف‌های جدا هستند (دو ایندکس جزئیِ V5)
        GradeEntity grade = (courseId == null
                ? gradeRepository.findByStudentIdAndClassIdAndAcademicYearAndTermAndCourseIdIsNull(
                        studentId, classId, academicYear, term)
                : gradeRepository.findByStudentIdAndClassIdAndAcademicYearAndTermAndCourseId(
                        studentId, classId, academicYear, term, courseId))
                .orElseGet(GradeEntity::new);
        grade.setSchoolId(schoolId);
        grade.setStudentId(studentId);
        grade.setClassId(classId);
        grade.setCourseId(courseId);
        grade.setAcademicYear(academicYear);
        grade.setTerm(term);
        grade.setScheme(scheme);
        grade.setDescriptiveLevel(descriptiveLevel);
        grade.setNumericScore(numericScore);
        grade.setComment(comment);
        grade.setRecordedByUserId(currentActorOrNull());
        grade.setRecordedAt(Instant.now());
        GradeEntity saved = gradeRepository.save(grade);
        return new GradeId(saved.getId());
    }

    /** سالِ شروعِ یک سالِ فهرست‌شده؛ سالِ ناشناخته InvalidAcademicYear. */
    private int requireListedYear(String academicYear) {
        return schoolFacade.listAcademicYears().stream()
                .filter(y -> y.code().equals(academicYear))
                .findFirst()
                .orElseThrow(() -> new AcademicException(new AcademicError.InvalidAcademicYear(academicYear)))
                .startYear();
    }

    @Override
    public GradeInfo getGrade(Long gradeId) {
        GradeEntity grade = gradeRepository.findById(gradeId)
                .orElseThrow(() -> new AcademicException(new AcademicError.GradeNotFound(gradeId)));
        return toGradeInfos(List.of(grade)).get(0);
    }

    @Override
    public List<GradeInfo> listGradesForClass(Long schoolId, Long classId, String academicYear, GradingTerm term,
                                              Long courseId) {
        academicYear = AcademicYearCodes.canonical(academicYear);
        requireClassInSchool(schoolId, classId);
        List<GradeEntity> grades = courseId == null
                ? gradeRepository.findByClassIdAndAcademicYearAndTermAndCourseIdIsNull(classId, academicYear, term)
                : gradeRepository.findByClassIdAndAcademicYearAndTermAndCourseId(classId, academicYear, term, courseId);
        return toGradeInfos(grades);
    }

    @Override
    public List<CourseInfo> listCoursesForClass(Long schoolId, Long classId, String academicYear) {
        ClassEntity classEntity = requireClassInSchool(schoolId, classId);
        String year = AcademicYearCodes.canonical(academicYear);
        Integer startYear = year == null ? null : requireListedYear(year);
        return courseRepository.findByGradeLevelAndGradedTrueOrderByNameAsc(classEntity.getGradeLevel()).stream()
                .filter(c -> startYear == null || c.isOfferedIn(startYear))
                .map(c -> new CourseInfo(new CourseId(c.getId()), c.getGradeLevel(), c.getName()))
                .toList();
    }

    @Override
    public List<GradeInfo> listGradesForStudents(Long schoolId, List<Long> studentIds) {
        if (studentIds.isEmpty()) {
            return List.of();
        }
        List<GradeEntity> grades = gradeRepository.findByStudentIdIn(studentIds).stream()
                .filter(g -> g.getSchoolId().equals(schoolId))
                .toList();
        // درسی که بعد از ثبتِ نمره «غیرنمره‌پذیر» شده (graded = false) از کارنامه‌ی دانش‌آموز/ولی
        // پنهان است؛ ردیف‌ها حذف نمی‌شوند (اگر دوباره نمره‌پذیر شود برمی‌گردند) و فهرستِ مدیر که
        // صریحاً با courseId می‌پرسد همچنان آن‌ها را می‌دهد.
        Map<Long, CourseEntity> coursesById = coursesOf(grades);
        return toGradeInfos(grades.stream()
                .filter(g -> g.getCourseId() == null || coursesById.get(g.getCourseId()) == null
                        || coursesById.get(g.getCourseId()).isGraded())
                .toList(), coursesById);
    }

    @Override
    @Transactional
    public GuardianLinkId linkGuardian(Long schoolId, Long studentId, Long guardianUserId) {
        requireStudentInSchool(schoolId, studentId);
        requireActiveParentMembership(schoolId, guardianUserId);

        // idempotent: همان (student, guardian) دوباره لینک نمی‌شود
        StudentGuardianEntity link = studentGuardianRepository
                .findByStudentIdAndGuardianUserId(studentId, guardianUserId)
                .orElseGet(() -> studentGuardianRepository.save(
                        new StudentGuardianEntity(schoolId, studentId, guardianUserId, Instant.now())
                ));

        return new GuardianLinkId(link.getId());
    }

    @Override
    public List<Long> listGuardians(Long schoolId, Long studentId) {
        requireStudentInSchool(schoolId, studentId);
        return studentGuardianRepository.findByStudentId(studentId).stream()
                .map(StudentGuardianEntity::getGuardianUserId)
                .toList();
    }

    @Override
    public List<StudentInfo> listWards(Long guardianUserId, Long schoolId) {
        return studentGuardianRepository.findByGuardianUserIdAndSchoolId(guardianUserId, schoolId).stream()
                .map(link -> getStudentInfo(link.getStudentId()))
                .toList();
    }

    @Override
    public Optional<EnrollmentInfo> findMyEnrollment(Long userId, Long schoolId) {
        return studentRepository.findByUserIdAndSchoolId(userId, schoolId)
                .filter(student -> student.getStatus() != StudentStatus.LEFT)
                .flatMap(student -> {
                    if (student.getClassId() == null) {
                        // PENDING/GRADUATED: دانش‌آموز هست ولی کلاسی ندارد
                        return Optional.of(new EnrollmentInfo(new StudentId(student.getId()), schoolId, null, null, null,
                                student.getStatus()));
                    }
                    return classRepository.findById(student.getClassId())
                            .map(classEntity -> new EnrollmentInfo(
                                    new StudentId(student.getId()),
                                    schoolId,
                                    new ClassId(classEntity.getId()),
                                    classEntity.getName(),
                                    classEntity.getSection(),
                                    student.getStatus()
                            ));
                });
    }

    private void requireStudentInSchool(Long schoolId, Long studentId) {
        StudentEntity student = studentRepository.findById(studentId)
                .orElseThrow(() -> new AcademicException(new AcademicError.StudentNotFound(studentId)));
        if (!student.getSchoolId().equals(schoolId)) {
            throw new AcademicException(new AcademicError.StudentNotFound(studentId));
        }
    }

    // یک User فقط زمانی می‌تواند به یک دانش‌آموز لینک شود که Membership
    // فعالِ نقش PARENT در همان مدرسه داشته باشد - همان بررسی امنیتی که در
    // Facade توضیح داده شده. findMembershipsByUser (نه findMembership)
    // عمداً استفاده شده چون findMembership فقط اولین Membership را
    // برمی‌گرداند و یک کاربر می‌تواند هم‌زمان چند نقش در همان مدرسه داشته باشد.
    private void requireActiveParentMembership(Long schoolId, Long guardianUserId) {
        boolean hasActiveParentMembership = iamFacade.findMembershipsByUser(new IamFacade.UserId(guardianUserId))
                .stream()
                .anyMatch(m -> m.schoolId().equals(schoolId) && "PARENT".equals(m.roleCode()) && m.active());
        if (!hasActiveParentMembership) {
            throw new AcademicException(new AcademicError.GuardianNotEligible(guardianUserId, schoolId));
        }
    }

    // مقطع تحصیلی مدرسه بازه‌ی مجاز gradeLevel کلاس‌هایش را تعیین می‌کند
    // (school.SchoolFacade.SchoolLevel) - یک ساختمان دبستان فقط پایه‌های
    // ۱ تا ۶ می‌پذیرد. schoolId ای که به هیچ SchoolEntity واقعی‌ای اشاره
    // نکند عملاً نباید رخ دهد (TenantContext از قبل schoolId را تأیید
    // کرده)، ولی این یک بررسی دفاعی است.
    private void requireValidGradeLevel(Long schoolId, int gradeLevel) {
        SchoolInfo school = schoolFacade.findSchool(new SchoolFacade.SchoolId(schoolId))
                .orElseThrow(() -> new AcademicException(new AcademicError.SchoolNotFound(schoolId)));
        if (!school.level().includesGrade(gradeLevel)) {
            throw new AcademicException(new AcademicError.InvalidGradeLevel(
                    gradeLevel, school.level().minGrade(), school.level().maxGrade()));
        }
    }

    private ClassEntity requireClassInSchool(Long schoolId, Long classId) {
        ClassEntity classEntity = classRepository.findById(classId)
                .orElseThrow(() -> new AcademicException(new AcademicError.ClassNotFound(classId)));
        if (!classEntity.getSchoolId().equals(schoolId)) {
            // کلاس متعلق به مدرسه‌ی دیگری است - از دید academic همان "یافت نشد" است
            throw new AcademicException(new AcademicError.ClassNotFound(classId));
        }
        return classEntity;
    }

    private static ClassInfo toClassInfo(ClassEntity c) {
        return new ClassInfo(new ClassId(c.getId()), c.getSchoolId(), c.getName(), c.getTeacherId(), c.getSection(),
                c.getGradeLevel(), c.getGradingScheme(), c.getAcademicYear());
    }

    private static StudentInfo toStudentInfo(StudentEntity s) {
        return new StudentInfo(new StudentId(s.getId()), s.getUserId(), s.getSchoolId(),
                s.getClassId() == null ? null : new ClassId(s.getClassId()), s.getStatus());
    }

    // درس‌های یک فهرست از نمره با یک query برای همه‌ی ردیف‌ها گرفته می‌شود (نه یکی به‌ازای هر نمره).
    private Map<Long, CourseEntity> coursesOf(List<GradeEntity> grades) {
        List<Long> courseIds = grades.stream()
                .map(GradeEntity::getCourseId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, CourseEntity> byId = new HashMap<>();
        if (!courseIds.isEmpty()) {
            courseRepository.findAllById(courseIds).forEach(c -> byId.put(c.getId(), c));
        }
        return byId;
    }

    private List<GradeInfo> toGradeInfos(List<GradeEntity> grades) {
        return toGradeInfos(grades, coursesOf(grades));
    }

    private List<GradeInfo> toGradeInfos(List<GradeEntity> grades, Map<Long, CourseEntity> coursesById) {
        return grades.stream()
                .map(g -> new GradeInfo(
                        new GradeId(g.getId()), g.getSchoolId(), new StudentId(g.getStudentId()),
                        new ClassId(g.getClassId()), g.getAcademicYear(), g.getTerm(), g.getScheme(),
                        g.getDescriptiveLevel(), g.getNumericScore(), g.getComment(), g.getRecordedByUserId(),
                        g.getRecordedAt(), g.getCourseId(),
                        g.getCourseId() == null || coursesById.get(g.getCourseId()) == null
                                ? null : coursesById.get(g.getCourseId()).getName()))
                .toList();
    }

    /**
     * این متد می‌تواند هم از یک درخواست HTTP واقعی (کاربر احراز هویت‌شده
     * موجود است) و هم از یک فراخوانِ بدون کاربر احراز هویت‌شده صدا زده شود؛
     * در حالت دوم actor را null می‌گذاریم - همان الگوی
     * FinanceFacadeImpl.currentActorOrNull.
     */
    private Long currentActorOrNull() {
        try {
            return iamFacade.currentUserId().value();
        } catch (IamException e) {
            return null;
        }
    }
}
