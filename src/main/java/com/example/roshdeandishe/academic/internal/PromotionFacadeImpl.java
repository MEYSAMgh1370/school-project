package com.example.roshdeandishe.academic.internal;

import com.example.roshdeandishe.academic.PromotionFacade;
import com.example.roshdeandishe.academic.PromotionOutcome;
import com.example.roshdeandishe.academic.StudentStatus;
import com.example.roshdeandishe.academic.shared.AcademicError;
import com.example.roshdeandishe.academic.shared.AcademicException;
import com.example.roshdeandishe.academic.shared.AcademicYearCodes;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.shared.exception.IamException;
import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.AcademicYearInfo;
import com.example.roshdeandishe.school.SchoolFacade.SchoolId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * انتساب، ارتقا، برگرداندن و جا‌دادن (openspec/changes/academic-year.md، «Phase 3 design»). قاعده‌های مهم:
 * ارتقا همیشه به سالِ جاریِ مدرسه است؛ هیچ چیزی حذف نمی‌شود؛ هر اجرا وضعیتِ قبلیِ دانش‌آموزان را نگه می‌دارد تا
 * برگشت‌پذیر باشد؛ ارتقا فاکتور صادر نمی‌کند (صدورِ فاکتورِ سال یک اقدامِ جدا در finance است).
 */
@Service
class PromotionFacadeImpl implements PromotionFacade {

    private static final String STUDENT_ROLE = "STUDENT";

    private final StudentRepository studentRepository;
    private final ClassRepository classRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final PromotionRunRepository runRepository;
    private final PromotionRunStudentRepository runStudentRepository;
    private final EnrollmentLedger enrollmentLedger;
    private final SchoolFacade schoolFacade;
    private final IamFacade iamFacade;

    PromotionFacadeImpl(StudentRepository studentRepository, ClassRepository classRepository,
                        EnrollmentRepository enrollmentRepository, PromotionRunRepository runRepository,
                        PromotionRunStudentRepository runStudentRepository, EnrollmentLedger enrollmentLedger,
                        SchoolFacade schoolFacade, IamFacade iamFacade) {
        this.studentRepository = studentRepository;
        this.classRepository = classRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.runRepository = runRepository;
        this.runStudentRepository = runStudentRepository;
        this.enrollmentLedger = enrollmentLedger;
        this.schoolFacade = schoolFacade;
        this.iamFacade = iamFacade;
    }

    // ---- انتساب ----

    @Override
    public UnattributedSummary unattributedSummary(Long schoolId) {
        String current = enrollmentLedger.currentYear(schoolId).map(AcademicYearInfo::code).orElse(null);
        return new UnattributedSummary(current, classRepository.countBySchoolIdAndAcademicYearIsNull(schoolId),
                studentRepository.countWithoutEnrollment(schoolId));
    }

    @Override
    @Transactional
    public AttributionResult attributeExistingData(Long schoolId, String academicYear) {
        AcademicYearInfo current = currentOrThrow(schoolId);
        AcademicYearInfo year = listedYear(academicYear);
        if (year.startYear() > current.startYear()) {
            throw new AcademicException(new AcademicError.InvalidRollover(
                    "سالِ انتساب نباید بعد از سالِ جاریِ مدرسه («" + current.code() + "») باشد."));
        }

        List<ClassEntity> classes = classRepository.findBySchoolId(schoolId);
        int classCount = 0;
        for (ClassEntity c : classes) {
            if (c.getAcademicYear() == null) {
                c.setAcademicYear(year.code());
                classRepository.save(c);
                classCount++;
            }
        }
        Map<Long, ClassEntity> classesById = classes.stream().collect(Collectors.toMap(ClassEntity::getId, Function.identity()));

        int studentCount = 0;
        for (StudentEntity student : studentRepository.findWithoutEnrollment(schoolId)) {
            ClassEntity c = student.getClassId() == null ? null : classesById.get(student.getClassId());
            if (c == null) {
                continue;
            }
            // کلاسی که از قبل سالِ خودش را دارد سالِ ردیف را تعیین می‌کند؛ وگرنه سالِ انتخاب‌شده
            String rowYear = c.getAcademicYear() != null ? c.getAcademicYear() : year.code();
            enrollmentRepository.save(new EnrollmentEntity(schoolId, student.getId(), rowYear, c.getId(),
                    c.getGradeLevel(), EnrollmentEntity.Status.ENROLLED, null));
            studentCount++;
        }
        return new AttributionResult(year.code(), classCount, studentCount);
    }

    // ---- ارتقا ----

    @Override
    public List<PlanItem> preview(Long schoolId, String fromYear) {
        AcademicYearInfo current = currentOrThrow(schoolId);
        AcademicYearInfo from = requireEarlier(fromYear, current);
        int maxGrade = maxGrade(schoolId);
        return candidates(schoolId, from, current).stream()
                .map(c -> new PlanItem(c.student().getId(), c.student().getUserId(), c.row().getClassId(),
                        c.row().getGradeLevel(), defaultOutcome(c.row().getGradeLevel(), maxGrade)))
                .toList();
    }

    @Override
    @Transactional
    public RunInfo run(Long schoolId, String fromYear, Map<Long, PromotionOutcome> decisions) {
        AcademicYearInfo current = currentOrThrow(schoolId);
        AcademicYearInfo from = requireEarlier(fromYear, current);
        List<Candidate> candidates = candidates(schoolId, from, current);
        if (candidates.isEmpty()) {
            throw new AcademicException(new AcademicError.InvalidRollover(
                    "دانش‌آموزی برای ارتقا از سال «" + from.code() + "» باقی نمانده است."));
        }
        return execute(schoolId, from, current, candidates, decisions == null ? Map.of() : decisions,
                PromotionRunEntity.Mode.MANUAL);
    }

    @Override
    @Transactional
    public Optional<RunInfo> runAutomatic(Long schoolId, String fromYear) {
        if (!schoolFacade.isAutoPromotion(new SchoolId(schoolId))) {
            return Optional.empty();
        }
        Optional<AcademicYearInfo> current = enrollmentLedger.currentYear(schoolId);
        Optional<AcademicYearInfo> from = schoolFacade.listAcademicYears().stream()
                .filter(y -> y.code().equals(AcademicYearCodes.canonical(fromYear)))
                .findFirst();
        if (current.isEmpty() || from.isEmpty() || from.get().startYear() >= current.get().startYear()) {
            return Optional.empty();
        }
        List<Candidate> candidates = candidates(schoolId, from.get(), current.get());
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(execute(schoolId, from.get(), current.get(), candidates, Map.of(),
                PromotionRunEntity.Mode.AUTO));
    }

    private RunInfo execute(Long schoolId, AcademicYearInfo from, AcademicYearInfo current, List<Candidate> candidates,
                            Map<Long, PromotionOutcome> decisions, PromotionRunEntity.Mode mode) {
        int maxGrade = maxGrade(schoolId);
        Set<Long> candidateIds = candidates.stream().map(c -> c.student().getId()).collect(Collectors.toSet());
        for (Long decided : decisions.keySet()) {
            if (!candidateIds.contains(decided)) {
                throw new AcademicException(new AcademicError.InvalidRollover(
                        "دانش‌آموز " + decided + " در سال «" + from.code() + "» ارتقاپذیر نیست."));
            }
        }
        // همه‌ی نتیجه‌ها پیش از نوشتن سنجیده می‌شوند تا اجرا یا کامل باشد یا هیچ
        Map<Long, PromotionOutcome> outcomes = new HashMap<>();
        for (Candidate c : candidates) {
            int level = c.row().getGradeLevel();
            PromotionOutcome outcome = decisions.getOrDefault(c.student().getId(), defaultOutcome(level, maxGrade));
            if (outcome == PromotionOutcome.PROMOTE && level >= maxGrade) {
                throw new AcademicException(new AcademicError.InvalidRollover(
                        "دانش‌آموز " + c.student().getId() + " در آخرین پایه‌ی مدرسه است؛ فارغ‌التحصیل یا تکرار پایه را انتخاب کنید."));
            }
            if (outcome == PromotionOutcome.GRADUATE && level < maxGrade) {
                throw new AcademicException(new AcademicError.InvalidRollover(
                        "دانش‌آموز " + c.student().getId() + " هنوز در آخرین پایه‌ی مدرسه نیست و فارغ‌التحصیل نمی‌شود."));
            }
            outcomes.put(c.student().getId(), outcome);
        }

        PromotionRunEntity run = runRepository.save(new PromotionRunEntity(
                schoolId, from.code(), current.code(), mode, currentActorOrNull()));
        Map<PromotionOutcome, Integer> counts = new EnumMap<>(PromotionOutcome.class);
        for (Candidate c : candidates) {
            StudentEntity student = c.student();
            PromotionOutcome outcome = outcomes.get(student.getId());
            boolean deactivated = false;
            switch (outcome) {
                case PROMOTE, REPEAT -> {
                    int target = c.row().getGradeLevel() + (outcome == PromotionOutcome.PROMOTE ? 1 : 0);
                    enrollmentRepository.save(new EnrollmentEntity(schoolId, student.getId(), current.code(), null,
                            target, EnrollmentEntity.Status.PENDING, run.getId()));
                }
                case GRADUATE -> {
                }
                case LEAVE -> deactivated = setStudentMembershipsActive(schoolId, student.getUserId(), false);
            }
            runStudentRepository.save(new PromotionRunStudentEntity(run.getId(), student.getId(), outcome,
                    student.getStatus(), student.getClassId(), deactivated));
            student.setClassId(null);
            student.setStatus(switch (outcome) {
                case PROMOTE, REPEAT -> StudentStatus.PENDING;
                case GRADUATE -> StudentStatus.GRADUATED;
                case LEAVE -> StudentStatus.LEFT;
            });
            studentRepository.save(student);
            counts.merge(outcome, 1, Integer::sum);
        }
        // یک اجرای تازه‌ساخته تازه‌ترین است و هنوز کسی را جا نداده، پس برگشت‌پذیر است
        return toRunInfo(run, counts, true);
    }

    @Override
    public List<RunInfo> listRuns(Long schoolId) {
        List<PromotionRunEntity> runs = runRepository.findBySchoolIdOrderByIdDesc(schoolId);
        Long latestActive = runs.stream().filter(r -> r.getUndoneAt() == null).map(PromotionRunEntity::getId)
                .findFirst().orElse(null);
        return runs.stream()
                .map(r -> toRunInfo(r, countsOf(r.getId()),
                        r.getUndoneAt() == null && r.getId().equals(latestActive) && !anyPlaced(r.getId())))
                .toList();
    }

    @Override
    @Transactional
    public void undo(Long schoolId, Long runId) {
        PromotionRunEntity run = runRepository.findById(runId)
                .filter(r -> r.getSchoolId().equals(schoolId))
                .orElseThrow(() -> new AcademicException(new AcademicError.PromotionRunNotFound(runId)));
        if (run.getUndoneAt() != null) {
            throw new AcademicException(new AcademicError.PromotionRunNotUndoable(runId,
                    "این اجرا قبلاً برگردانده شده است."));
        }
        if (runRepository.existsBySchoolIdAndIdGreaterThanAndUndoneAtIsNull(schoolId, runId)) {
            throw new AcademicException(new AcademicError.PromotionRunNotUndoable(runId,
                    "اجرای جدیدتری هست؛ اول آن را برگردانید."));
        }
        if (anyPlaced(runId)) {
            throw new AcademicException(new AcademicError.PromotionRunNotUndoable(runId,
                    "دانش‌آموزی از این اجرا در کلاس جا داده شده است؛ با برگرداندن، کارِ جا‌دادن از بین می‌رفت. "
                            + "آن دانش‌آموزان را دستی جابه‌جا کنید."));
        }
        for (PromotionRunStudentEntity rs : runStudentRepository.findByRunId(runId)) {
            studentRepository.findById(rs.getStudentId()).ifPresent(student -> {
                student.setStatus(rs.getPreviousStatus());
                student.setClassId(rs.getPreviousClassId());
                studentRepository.save(student);
                if (rs.isMembershipDeactivated()) {
                    setStudentMembershipsActive(schoolId, student.getUserId(), true);
                }
            });
        }
        enrollmentRepository.deleteAll(enrollmentRepository.findByPromotionRunId(runId));
        run.setUndoneAt(Instant.now());
        runRepository.save(run);
    }

    @Override
    @Transactional
    public void changeOutcome(Long schoolId, Long studentId, PromotionOutcome outcome) {
        if (outcome != PromotionOutcome.REPEAT && outcome != PromotionOutcome.PROMOTE) {
            throw new AcademicException(new AcademicError.InvalidRollover(
                    "فقط «تکرار پایه» یا «ارتقا» را می‌شود دستی عوض کرد."));
        }
        AcademicYearInfo current = currentOrThrow(schoolId);
        PromotionRunEntity run = runRepository
                .findFirstBySchoolIdAndToYearAndUndoneAtIsNullOrderByIdDesc(schoolId, current.code())
                .orElseThrow(() -> new AcademicException(new AcademicError.InvalidRollover(
                        "برای سالِ جاری اجرای ارتقایی نیست.")));
        PromotionRunStudentEntity runStudent = runStudentRepository.findByRunIdAndStudentId(run.getId(), studentId)
                .orElseThrow(() -> new AcademicException(new AcademicError.InvalidRollover(
                        "دانش‌آموز " + studentId + " در اجرای ارتقای امسال نیست.")));
        StudentEntity student = studentRepository.findById(studentId)
                .filter(s -> s.getSchoolId().equals(schoolId))
                .orElseThrow(() -> new AcademicException(new AcademicError.StudentNotFound(studentId)));
        if (student.getStatus() != StudentStatus.PENDING && student.getStatus() != StudentStatus.GRADUATED) {
            throw new AcademicException(new AcademicError.InvalidRollover(
                    "فقط دانش‌آموزِ جا‌نشده یا فارغ‌التحصیل را می‌شود دستی عوض کرد؛ دانش‌آموزِ جا‌شده را با جابه‌جایی کلاس جابه‌جا کنید."));
        }
        EnrollmentEntity fromRow = enrollmentRepository.findByStudentIdAndAcademicYear(studentId, run.getFromYear())
                .orElseThrow(() -> new AcademicException(new AcademicError.InvalidRollover(
                        "ثبت‌نامِ سالِ قبلِ دانش‌آموز " + studentId + " پیدا نشد.")));
        int level = fromRow.getGradeLevel();
        if (outcome == PromotionOutcome.PROMOTE && level >= maxGrade(schoolId)) {
            throw new AcademicException(new AcademicError.InvalidRollover(
                    "دانش‌آموز در آخرین پایه‌ی مدرسه است و پایه‌ی بعدی ندارد."));
        }
        int target = level + (outcome == PromotionOutcome.PROMOTE ? 1 : 0);
        EnrollmentEntity row = enrollmentRepository.findByStudentIdAndAcademicYear(studentId, current.code())
                .orElseGet(() -> new EnrollmentEntity(schoolId, studentId, current.code(), null, target,
                        EnrollmentEntity.Status.PENDING, run.getId()));
        row.setGradeLevel(target);
        enrollmentRepository.save(row);
        student.setStatus(StudentStatus.PENDING);
        studentRepository.save(student);
        runStudent.setOutcome(outcome);
        runStudentRepository.save(runStudent);
    }

    // ---- جا‌دادن ----

    @Override
    public List<PendingStudent> listPending(Long schoolId) {
        Optional<AcademicYearInfo> current = enrollmentLedger.currentYear(schoolId);
        if (current.isEmpty()) {
            return List.of();
        }
        return enrollmentRepository.findBySchoolIdAndAcademicYearAndStatus(
                        schoolId, current.get().code(), EnrollmentEntity.Status.PENDING).stream()
                .map(row -> studentRepository.findById(row.getStudentId())
                        .map(s -> new PendingStudent(s.getId(), s.getUserId(), row.getGradeLevel(),
                                previousLevel(s.getId(), current.get().code()))))
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    public List<PendingStudent> listGraduates(Long schoolId) {
        Optional<AcademicYearInfo> current = enrollmentLedger.currentYear(schoolId);
        if (current.isEmpty()) {
            return List.of();
        }
        return runRepository.findFirstBySchoolIdAndToYearAndUndoneAtIsNullOrderByIdDesc(schoolId, current.get().code())
                .map(run -> runStudentRepository.findByRunId(run.getId()).stream()
                        .filter(rs -> rs.getOutcome() == PromotionOutcome.GRADUATE)
                        .map(rs -> studentRepository.findById(rs.getStudentId())
                                .filter(s -> s.getStatus() == StudentStatus.GRADUATED)
                                .map(s -> {
                                    Integer level = previousLevel(s.getId(), current.get().code());
                                    return new PendingStudent(s.getId(), s.getUserId(), level == null ? 0 : level, level);
                                }))
                        .flatMap(Optional::stream)
                        .toList())
                .orElse(List.of());
    }

    /** پایه‌ی آخرین ثبت‌نامِ قبل از سالِ جاری. */
    private Integer previousLevel(Long studentId, String currentYear) {
        return enrollmentRepository.findByStudentIdIn(List.of(studentId)).stream()
                .filter(r -> r.getAcademicYear().compareTo(currentYear) < 0)
                .max(java.util.Comparator.comparing(EnrollmentEntity::getAcademicYear))
                .map(EnrollmentEntity::getGradeLevel)
                .orElse(null);
    }

    @Override
    @Transactional
    public int place(Long schoolId, List<Placement> placements) {
        AcademicYearInfo current = currentOrThrow(schoolId);
        int placed = 0;
        for (Placement placement : placements) {
            StudentEntity student = studentRepository.findById(placement.studentId())
                    .filter(s -> s.getSchoolId().equals(schoolId))
                    .orElseThrow(() -> new AcademicException(new AcademicError.StudentNotFound(placement.studentId())));
            if (student.getStatus() != StudentStatus.PENDING) {
                throw new AcademicException(new AcademicError.InvalidRollover(
                        "دانش‌آموز " + student.getId() + " در انتظارِ جا‌دادن نیست."));
            }
            ClassEntity classEntity = classRepository.findById(placement.classId())
                    .filter(c -> c.getSchoolId().equals(schoolId))
                    .orElseThrow(() -> new AcademicException(new AcademicError.ClassNotFound(placement.classId())));
            enrollmentLedger.place(student, classEntity, current.code(), placement.track());
            placed++;
        }
        return placed;
    }

    // ---- کمکی ----

    /** دانش‌آموزِ ACTIVEی که در {@code from} ثبت‌نام (ENROLLED) داشته و هنوز ردیفی در سالِ جاری ندارد. */
    private List<Candidate> candidates(Long schoolId, AcademicYearInfo from, AcademicYearInfo current) {
        Set<Long> alreadyInCurrent = new HashSet<>();
        enrollmentRepository.findBySchoolIdAndAcademicYear(schoolId, current.code())
                .forEach(r -> alreadyInCurrent.add(r.getStudentId()));
        List<EnrollmentEntity> rows = enrollmentRepository.findBySchoolIdAndAcademicYearAndStatus(
                schoolId, from.code(), EnrollmentEntity.Status.ENROLLED).stream()
                .filter(r -> !alreadyInCurrent.contains(r.getStudentId()))
                .toList();
        Map<Long, StudentEntity> students = studentRepository.findAllById(
                        rows.stream().map(EnrollmentEntity::getStudentId).toList()).stream()
                .collect(Collectors.toMap(StudentEntity::getId, Function.identity()));
        return rows.stream()
                .filter(r -> students.containsKey(r.getStudentId())
                        && students.get(r.getStudentId()).getStatus() == StudentStatus.ACTIVE)
                .map(r -> new Candidate(students.get(r.getStudentId()), r))
                .sorted(java.util.Comparator.comparing(c -> c.student().getId()))
                .toList();
    }

    private record Candidate(StudentEntity student, EnrollmentEntity row) {
    }

    private static PromotionOutcome defaultOutcome(int gradeLevel, int maxGrade) {
        return gradeLevel >= maxGrade ? PromotionOutcome.GRADUATE : PromotionOutcome.PROMOTE;
    }

    /** آخرین پایه‌ی مدرسه: با مقطعِ تکِ مدرسه (سؤال ۱۰ برای چند مقطع به تعویق افتاده). */
    private int maxGrade(Long schoolId) {
        return schoolFacade.findSchool(new SchoolId(schoolId))
                .orElseThrow(() -> new AcademicException(new AcademicError.SchoolNotFound(schoolId)))
                .level().maxGrade();
    }

    private AcademicYearInfo currentOrThrow(Long schoolId) {
        return enrollmentLedger.currentYear(schoolId)
                .orElseThrow(() -> new AcademicException(new AcademicError.YearNotChosen(schoolId)));
    }

    private AcademicYearInfo listedYear(String academicYear) {
        String code = AcademicYearCodes.canonical(academicYear);
        return schoolFacade.listAcademicYears().stream()
                .filter(y -> y.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new AcademicException(new AcademicError.InvalidAcademicYear(academicYear)));
    }

    private AcademicYearInfo requireEarlier(String fromYear, AcademicYearInfo current) {
        AcademicYearInfo from = listedYear(fromYear);
        if (from.startYear() >= current.startYear()) {
            throw new AcademicException(new AcademicError.InvalidRollover(
                    "سالِ مبدأ («" + from.code() + "») باید قبل از سالِ جاری («" + current.code() + "») باشد."));
        }
        return from;
    }

    /** @return آیا حداقل یک عضویتِ STUDENT تغییر کرد */
    private boolean setStudentMembershipsActive(Long schoolId, Long userId, boolean active) {
        boolean changed = false;
        for (IamFacade.MembershipInfo m : iamFacade.findMembershipsByUser(new IamFacade.UserId(userId))) {
            if (schoolId.equals(m.schoolId()) && STUDENT_ROLE.equals(m.roleCode()) && m.active() != active) {
                iamFacade.setMembershipActive(schoolId, m.membershipId(), active);
                changed = true;
            }
        }
        return changed;
    }

    private boolean anyPlaced(Long runId) {
        return enrollmentRepository.findByPromotionRunId(runId).stream()
                .anyMatch(r -> r.getStatus() == EnrollmentEntity.Status.ENROLLED);
    }

    private Map<PromotionOutcome, Integer> countsOf(Long runId) {
        Map<PromotionOutcome, Integer> counts = new EnumMap<>(PromotionOutcome.class);
        runStudentRepository.findByRunId(runId).forEach(rs -> counts.merge(rs.getOutcome(), 1, Integer::sum));
        return counts;
    }

    private static RunInfo toRunInfo(PromotionRunEntity run, Map<PromotionOutcome, Integer> counts, boolean undoable) {
        return new RunInfo(run.getId(), run.getFromYear(), run.getToYear(), run.getMode().name(), run.getCreatedAt(),
                run.getUndoneAt(), counts, undoable);
    }

    private Long currentActorOrNull() {
        try {
            return iamFacade.currentUserId().value();
        } catch (IamException e) {
            return null;
        }
    }
}
