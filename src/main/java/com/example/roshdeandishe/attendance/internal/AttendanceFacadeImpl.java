package com.example.roshdeandishe.attendance.internal;

import com.example.roshdeandishe.academic.AcademicFacade;
import com.example.roshdeandishe.academic.AcademicFacade.ClassInfo;
import com.example.roshdeandishe.academic.AcademicFacade.StudentInfo;
import com.example.roshdeandishe.academic.TimetableFacade;
import com.example.roshdeandishe.attendance.AbsenceRecordedEvent;
import com.example.roshdeandishe.attendance.AttendanceFacade;
import com.example.roshdeandishe.attendance.AttendanceStatus;
import com.example.roshdeandishe.attendance.ExcuseStatus;
import com.example.roshdeandishe.attendance.shared.AttendanceError;
import com.example.roshdeandishe.attendance.shared.AttendanceException;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.SchoolId;
import com.example.roshdeandishe.school.SchoolFacade.SchoolInfo;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
class AttendanceFacadeImpl implements AttendanceFacade {

    private static final int LOCK_NAMESPACE = 7501;
    private static final int MAX_NOTE_LENGTH = 255;

    private final RollRepository rollRepository;
    private final MarkRepository markRepository;
    private final ExcuseRepository excuseRepository;
    private final AcademicFacade academicFacade;
    private final TimetableFacade timetableFacade;
    private final SchoolFacade schoolFacade;
    private final IamFacade iamFacade;
    private final ApplicationEventPublisher eventPublisher;

    AttendanceFacadeImpl(
            RollRepository rollRepository,
            MarkRepository markRepository,
            ExcuseRepository excuseRepository,
            AcademicFacade academicFacade,
            TimetableFacade timetableFacade,
            SchoolFacade schoolFacade,
            IamFacade iamFacade,
            ApplicationEventPublisher eventPublisher
    ) {
        this.rollRepository = rollRepository;
        this.markRepository = markRepository;
        this.excuseRepository = excuseRepository;
        this.academicFacade = academicFacade;
        this.timetableFacade = timetableFacade;
        this.schoolFacade = schoolFacade;
        this.iamFacade = iamFacade;
        this.eventPublisher = eventPublisher;
    }

    // ---- ثبت ----

    @Override
    @Transactional
    public ClassDay saveRoll(Long schoolId, Long classId, LocalDate day, Long actorUserId, List<MarkInput> marks) {
        SchoolInfo school = requireSchool(schoolId);
        ClassInfo cls = requireClass(schoolId, classId);
        requireCurrentYear(schoolId, cls);
        String dayProblem = AttendanceRules.recordDayProblem(
                school.weekStart(), day, today(school), day != null && schoolFacade.isClosedDay(school.id(), day));
        if (dayProblem != null) {
            throw new AttendanceException(new AttendanceError.InvalidAttendanceDay(day, dayProblem));
        }
        List<MarkInput> clean = validateMarks(marks);

        // عضوِ کلاس: فهرستِ کلاس، یا کسی که همین حالا هم در همین (کلاس، روز) مُهر دارد (ذخیره‌ی دوباره‌ی
        // یک روزِ دارای دانش‌آموزِ ارتقایافته/ترک‌کرده idempotent می‌ماند)
        Set<Long> allowed = new HashSet<>();
        academicFacade.listStudentsByClass(schoolId, List.of(classId)).getOrDefault(classId, List.of())
                .forEach(s -> allowed.add(s.id().value()));
        Set<Long> previouslyAbsent = new HashSet<>();
        markRepository.findByClassIdAndDay(classId, day).forEach(m -> {
            allowed.add(m.getStudentId());
            if (m.getStatus() == AttendanceStatus.ABSENT) {
                previouslyAbsent.add(m.getStudentId());
            }
        });
        for (MarkInput mark : clean) {
            if (!allowed.contains(mark.studentId())) {
                throw new AttendanceException(new AttendanceError.StudentNotInClass(mark.studentId(), classId));
            }
        }

        rollRepository.lockClassDay(LOCK_NAMESPACE, Objects.hash(classId, day));
        markRepository.deleteByClassAndDay(classId, day);
        Instant now = Instant.now();
        RollEntity roll = rollRepository.findByClassIdAndDay(classId, day)
                .orElseGet(() -> new RollEntity(schoolId, classId, day, actorUserId, now));
        roll.setTakenByUserId(actorUserId);
        roll.setTakenAt(now);
        rollRepository.save(roll);

        // عذرها با جایگزینیِ مُهرها نه گم می‌شوند و نه یتیم: عذرِ PENDINGِ دانش‌آموزی که دیگر غایب نیست حذف
        // می‌شود (APPROVED/REJECTED به‌عنوان تاریخچه می‌ماند ولی نادیده است) و غیبتِ دارای عذرِ APPROVED
        // «موجه» می‌ماند، حتی اگر ورودی excused=false بگوید (عذرِ تأییدشده بر پرچمِ ورودی می‌چربد).
        Set<Long> absentNow = clean.stream().filter(m -> m.status() == AttendanceStatus.ABSENT)
                .map(MarkInput::studentId).collect(Collectors.toSet());
        Set<Long> approved = new HashSet<>();
        List<ExcuseEntity> orphaned = new ArrayList<>();
        for (ExcuseEntity excuse : excuseRepository.findByClassIdAndDay(classId, day)) {
            boolean stillAbsent = absentNow.contains(excuse.getStudentId());
            if (stillAbsent && excuse.getStatus() == ExcuseStatus.APPROVED) {
                approved.add(excuse.getStudentId());
            } else if (!stillAbsent && excuse.getStatus() == ExcuseStatus.PENDING) {
                orphaned.add(excuse);
            }
        }
        if (!orphaned.isEmpty()) {
            excuseRepository.deleteAll(orphaned);
        }

        markRepository.saveAll(clean.stream()
                .map(m -> new MarkEntity(schoolId, classId, day, m.studentId(), m.status(),
                        m.excused() || approved.contains(m.studentId()), m.note(), actorUserId, now))
                .toList());

        // پس از موفقیت (Modulith listenerها بعد از commit اجرا می‌شوند): فقط برای غیبتِ تازه
        for (Long studentId : AttendanceRules.newlyAbsent(previouslyAbsent, clean)) {
            eventPublisher.publishEvent(new AbsenceRecordedEvent(schoolId, classId, studentId, day, actorUserId, now));
        }
        return getClassDay(schoolId, classId, day);
    }

    private static List<MarkInput> validateMarks(List<MarkInput> marks) {
        if (marks == null) {
            throw new AttendanceException(new AttendanceError.InvalidMark(
                    "فهرست مُهرها (marks) الزامی است؛ برای «همه حاضر» فهرست خالی بفرستید."));
        }
        Set<Long> seen = new HashSet<>();
        List<MarkInput> clean = new ArrayList<>();
        for (MarkInput mark : marks) {
            if (mark == null || mark.studentId() == null) {
                throw new AttendanceException(new AttendanceError.InvalidMark("شناسه‌ی دانش‌آموز الزامی است."));
            }
            if (mark.status() == null) {
                throw new AttendanceException(new AttendanceError.InvalidMark(
                        "وضعیتِ دانش‌آموز " + mark.studentId() + " باید ABSENT یا LATE باشد؛ حاضر را نفرستید."));
            }
            if (!seen.add(mark.studentId())) {
                throw new AttendanceException(new AttendanceError.InvalidMark(
                        "دانش‌آموز " + mark.studentId() + " بیش از یک‌بار آمده است."));
            }
            String note = mark.note() == null ? null : mark.note().strip();
            if (note != null && note.isEmpty()) {
                note = null;
            }
            if (note != null && note.length() > MAX_NOTE_LENGTH) {
                throw new AttendanceException(new AttendanceError.InvalidMark(
                        "یادداشت حداکثر " + MAX_NOTE_LENGTH + " نویسه می‌تواند باشد."));
            }
            // «موجه» فقط برای غیبت معنا دارد
            clean.add(new MarkInput(mark.studentId(), mark.status(),
                    mark.status() == AttendanceStatus.ABSENT && mark.excused(), note));
        }
        return clean;
    }

    // ---- خواندن: یک کلاس ----

    @Override
    public ClassDay getClassDay(Long schoolId, Long classId, LocalDate day) {
        SchoolInfo school = requireSchool(schoolId);
        ClassInfo cls = requireClass(schoolId, classId);
        if (day == null) {
            throw new AttendanceException(new AttendanceError.InvalidAttendanceDay(null, "روز الزامی است."));
        }
        boolean taken = rollRepository.findByClassIdAndDay(classId, day).isPresent();
        List<MarkEntity> marks = markRepository.findByClassIdAndDay(classId, day);
        Map<Long, MarkEntity> markByStudent = marks.stream()
                .collect(Collectors.toMap(MarkEntity::getStudentId, Function.identity(), (a, b) -> a));
        List<StudentInfo> students = withMarkedStudents(schoolId,
                academicFacade.listStudentsByClass(schoolId, List.of(classId)).getOrDefault(classId, List.of()), marks);
        Map<Long, String> names = namesByUser(schoolId);

        List<StudentMark> rows = new ArrayList<>();
        for (StudentInfo student : students) {
            MarkEntity mark = markByStudent.get(student.id().value());
            rows.add(new StudentMark(student.id().value(), names.get(student.userId()),
                    mark == null ? null : mark.getStatus(), mark != null && mark.isExcused(),
                    mark == null ? null : mark.getNote()));
        }
        rows.sort(Comparator.comparing(StudentMark::studentName, Comparator.nullsLast(Comparator.<String>naturalOrder())));
        boolean recordable = AttendanceRules.recordDayProblem(
                school.weekStart(), day, today(school), schoolFacade.isClosedDay(school.id(), day)) == null;
        return new ClassDay(classId, cls.name(), day, taken, recordable, rows);
    }

    // ---- خواندن: مدرسه ----

    @Override
    public List<ClassAbsentees> absenteesForDay(Long schoolId, LocalDate day) {
        SchoolInfo school = requireSchool(schoolId);
        requireDay(day);
        if (!isSchoolDay(school, day)) {
            return List.of();
        }
        Map<Long, String> names = namesByUser(schoolId);
        List<ClassAbsentees> result = new ArrayList<>();
        for (DayClass dc : loadDay(schoolId, day)) {
            Map<Long, StudentInfo> studentById = dc.students().stream()
                    .collect(Collectors.toMap(s -> s.id().value(), Function.identity(), (a, b) -> a));
            List<Absentee> absentees = new ArrayList<>();
            int absent = 0;
            int late = 0;
            int excused = 0;
            for (MarkEntity mark : dc.marks()) {
                StudentInfo student = studentById.get(mark.getStudentId());
                if (student == null) {
                    continue;
                }
                if (mark.getStatus() == AttendanceStatus.ABSENT) {
                    absent++;
                    if (mark.isExcused()) {
                        excused++;
                    }
                } else {
                    late++;
                }
                absentees.add(new Absentee(mark.getStudentId(), names.get(student.userId()), mark.getStatus(),
                        mark.isExcused(), mark.getNote()));
            }
            absentees.sort(Comparator.comparing(Absentee::studentName, Comparator.nullsLast(Comparator.<String>naturalOrder())));
            result.add(new ClassAbsentees(dc.cls().id().value(), dc.cls().name(), dc.taken(),
                    dc.students().size(), absent, late, excused, absentees));
        }
        result.sort(Comparator.comparing(ClassAbsentees::className, Comparator.nullsLast(Comparator.<String>naturalOrder())));
        return result;
    }

    @Override
    public DaySummary summaryForDay(Long schoolId, LocalDate day) {
        SchoolInfo school = requireSchool(schoolId);
        requireDay(day);
        if (!isSchoolDay(school, day)) {
            return AttendanceMath.summarize(day, false, List.of());
        }
        List<AttendanceMath.ClassTally> tallies = new ArrayList<>();
        for (DayClass dc : loadDay(schoolId, day)) {
            int absent = 0;
            int excused = 0;
            for (MarkEntity mark : dc.marks()) {
                if (mark.getStatus() == AttendanceStatus.ABSENT) {
                    absent++;
                    if (mark.isExcused()) {
                        excused++;
                    }
                }
            }
            tallies.add(new AttendanceMath.ClassTally(dc.taken(), dc.students().size(), absent, excused));
        }
        return AttendanceMath.summarize(day, true, tallies);
    }

    @Override
    public List<ReportRow> report(Long schoolId, LocalDate from, LocalDate to, Long classId, String nameFilter) {
        requireSchool(schoolId);
        String rangeProblem = AttendanceRules.rangeProblem(from, to);
        if (rangeProblem != null) {
            throw new AttendanceException(new AttendanceError.InvalidRange(rangeProblem));
        }
        if (classId != null) {
            requireClass(schoolId, classId);
        }
        List<RollEntity> rolls = rollRepository.findBySchoolIdAndDayBetween(schoolId, from, to).stream()
                .filter(r -> classId == null || classId.equals(r.getClassId()))
                .toList();
        if (rolls.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> rollDaysByClass = rolls.stream()
                .collect(Collectors.groupingBy(RollEntity::getClassId, Collectors.counting()));
        Map<Long, List<MarkEntity>> marksByClass = markRepository.findBySchoolIdAndDayBetween(schoolId, from, to)
                .stream()
                .filter(m -> rollDaysByClass.containsKey(m.getClassId()))
                .collect(Collectors.groupingBy(MarkEntity::getClassId));
        Map<Long, ClassInfo> classes = new LinkedHashMap<>();
        for (Long id : rollDaysByClass.keySet()) {
            academicFacade.findClass(schoolId, id).ifPresent(c -> classes.put(id, c));
        }
        Map<Long, List<StudentInfo>> rosters = academicFacade.listStudentsByClass(schoolId, classes.keySet());
        Map<Long, String> names = namesByUser(schoolId);

        List<ReportRow> rows = new ArrayList<>();
        for (ClassInfo cls : classes.values()) {
            Long id = cls.id().value();
            List<MarkEntity> classMarks = marksByClass.getOrDefault(id, List.of());
            for (StudentInfo student : withMarkedStudents(schoolId, rosters.getOrDefault(id, List.of()), classMarks)) {
                String name = names.get(student.userId());
                if (!AttendanceMath.nameMatches(name, nameFilter)) {
                    continue;
                }
                int absent = 0;
                int late = 0;
                for (MarkEntity mark : classMarks) {
                    if (mark.getStudentId().equals(student.id().value())) {
                        if (mark.getStatus() == AttendanceStatus.ABSENT) {
                            absent++;
                        } else {
                            late++;
                        }
                    }
                }
                rows.add(AttendanceMath.reportRow(student.id().value(), name, id, cls.name(),
                        rollDaysByClass.get(id).intValue(), absent, late));
            }
        }
        rows.sort(Comparator.comparing(ReportRow::className, Comparator.nullsLast(Comparator.<String>naturalOrder()))
                .thenComparing(ReportRow::studentName, Comparator.nullsLast(Comparator.<String>naturalOrder())));
        return rows;
    }

    // ---- کلاس‌های قابل ثبت و مجوزِ «کلاسِ خود» / «روزِ گذشته» ----

    @Override
    public List<RecordableClass> listRecordableClasses(Long schoolId, Long actorUserId, boolean recordAny,
                                                       LocalDate day) {
        SchoolInfo school = requireSchool(schoolId);
        LocalDate on = day == null ? today(school) : day;
        Optional<String> currentYear = currentYearCode(schoolId);
        List<ClassInfo> classes = recordAny
                ? currentYearClasses(schoolId, currentYear)
                : timetableFacade.listClassesTaughtBy(schoolId, actorUserId).stream()
                        .filter(c -> c.academicYear() == null || currentYear.isEmpty()
                                || currentYear.get().equals(c.academicYear()))
                        .toList();
        Set<Long> taken = rollRepository.findBySchoolIdAndDay(schoolId, on).stream()
                .map(RollEntity::getClassId)
                .collect(Collectors.toSet());
        return classes.stream()
                .map(c -> new RecordableClass(c.id().value(), c.name(), c.section(), c.gradeLevel(),
                        taken.contains(c.id().value())))
                .sorted(Comparator.comparing(RecordableClass::name, Comparator.nullsLast(Comparator.<String>naturalOrder())))
                .toList();
    }

    @Override
    public void requireMayAccessClass(Long schoolId, Long classId, Long actorUserId, boolean recordAny) {
        requireClass(schoolId, classId);
        if (recordAny) {
            return;
        }
        boolean mine = timetableFacade.listClassesTaughtBy(schoolId, actorUserId).stream()
                .anyMatch(c -> classId.equals(c.id().value()));
        if (!mine) {
            throw new AttendanceException(new AttendanceError.NotYourClass(classId));
        }
    }

    @Override
    public void requireMayEditDay(Long schoolId, LocalDate day, boolean editPast) {
        if (editPast || day == null) {
            return;
        }
        if (day.isBefore(today(requireSchool(schoolId)))) {
            throw new AttendanceException(new AttendanceError.PastEditNotAllowed(day));
        }
    }

    // ---- عذر ----

    @Override
    @Transactional
    public ExcuseRow submitExcuse(Long schoolId, Long studentId, LocalDate day, Long submitterUserId, String note) {
        requireSchool(schoolId);
        if (studentId == null) {
            throw new AttendanceException(new AttendanceError.InvalidExcuse("شناسه‌ی دانش‌آموز الزامی است."));
        }
        // ولیِ فعالِ همین دانش‌آموز: هم نقشِ فعالِ PARENT و هم لینکِ واقعی
        boolean parent = iamFacade.findActiveRoleCodes(new IamFacade.UserId(submitterUserId), schoolId)
                .contains("PARENT")
                && academicFacade.listWards(submitterUserId, schoolId).stream()
                .anyMatch(w -> studentId.equals(w.id().value()));
        if (!parent) {
            throw new AttendanceException(new AttendanceError.NotYourChild(studentId));
        }
        requireDay(day);
        String noteProblem = AttendanceRules.excuseNoteProblem(note);
        if (noteProblem != null) {
            throw new AttendanceException(new AttendanceError.InvalidExcuse(noteProblem));
        }
        MarkEntity absence = markRepository.findByStudentIdAndDay(studentId, day).stream()
                .filter(m -> schoolId.equals(m.getSchoolId()) && m.getStatus() == AttendanceStatus.ABSENT)
                .findFirst()
                .orElseThrow(() -> new AttendanceException(new AttendanceError.NoAbsenceToExcuse(studentId, day)));

        Instant now = Instant.now();
        String clean = note.strip();
        ExcuseEntity excuse = excuseRepository.findByStudentIdAndDay(studentId, day).orElse(null);
        if (excuse == null) {
            excuse = new ExcuseEntity(schoolId, absence.getClassId(), day, studentId, clean, submitterUserId, now);
        } else if (excuse.getStatus() != ExcuseStatus.PENDING) {
            throw new AttendanceException(new AttendanceError.ExcuseAlreadyDecided(excuse.getId()));
        } else {
            excuse.setNote(clean);
            excuse.setSubmittedByUserId(submitterUserId);
            excuse.setSubmittedAt(now);
        }
        return excuseRows(schoolId, List.of(excuseRepository.save(excuse))).get(0);
    }

    @Override
    public List<MyStudentAttendance> listMyAttendance(Long schoolId, Long userId, LocalDate from, LocalDate to) {
        requireSchool(schoolId);
        String rangeProblem = AttendanceRules.rangeProblem(from, to);
        if (rangeProblem != null) {
            throw new AttendanceException(new AttendanceError.InvalidRange(rangeProblem));
        }
        // هر منبع به نقشِ فعالِ خودش بسته است (عضوِ سابق یا نقشِ دیگر چیزی نمی‌گیرد)
        Set<String> roles = iamFacade.findActiveRoleCodes(new IamFacade.UserId(userId), schoolId);
        Map<Long, Boolean> ownByStudent = new LinkedHashMap<>();
        Map<Long, Long> userByStudent = new HashMap<>();
        if (roles.contains("STUDENT")) {
            academicFacade.findMyEnrollment(userId, schoolId).ifPresent(e -> {
                ownByStudent.put(e.studentId().value(), true);
                userByStudent.put(e.studentId().value(), userId);
            });
        }
        if (roles.contains("PARENT")) {
            for (StudentInfo ward : academicFacade.listWards(userId, schoolId)) {
                if (ownByStudent.putIfAbsent(ward.id().value(), false) == null) {
                    userByStudent.put(ward.id().value(), ward.userId());
                }
            }
        }
        if (ownByStudent.isEmpty()) {
            return List.of();
        }

        Set<Long> ids = ownByStudent.keySet();
        Map<Long, List<MarkEntity>> marksByStudent = markRepository.findByStudentIdInAndDayBetween(ids, from, to)
                .stream()
                .filter(m -> schoolId.equals(m.getSchoolId()))
                .collect(Collectors.groupingBy(MarkEntity::getStudentId));
        Map<String, ExcuseEntity> excuseByStudentDay = new HashMap<>();
        for (ExcuseEntity e : excuseRepository.findByStudentIdInAndDayBetween(ids, from, to)) {
            if (schoolId.equals(e.getSchoolId())) {
                excuseByStudentDay.put(e.getStudentId() + "|" + e.getDay(), e);
            }
        }
        Map<Long, String> names = namesByUser(schoolId);

        List<MyStudentAttendance> result = new ArrayList<>();
        for (Map.Entry<Long, Boolean> entry : ownByStudent.entrySet()) {
            Long studentId = entry.getKey();
            List<MyDayAttendance> days = marksByStudent.getOrDefault(studentId, List.of()).stream()
                    .sorted(Comparator.comparing(MarkEntity::getDay))
                    .map(m -> {
                        // عذر فقط برای غیبتِ فعلی معنا دارد؛ عذرِ باقی‌مانده‌ی روزی که دیگر غایب نیست نادیده است
                        ExcuseEntity e = m.getStatus() == AttendanceStatus.ABSENT
                                ? excuseByStudentDay.get(studentId + "|" + m.getDay()) : null;
                        return new MyDayAttendance(m.getDay(), m.getStatus(), m.isExcused(),
                                e == null ? null : new MyExcuse(e.getId(), e.getStatus(), e.getNote()));
                    })
                    .toList();
            result.add(new MyStudentAttendance(studentId, names.get(userByStudent.get(studentId)), entry.getValue(),
                    days));
        }
        result.sort(Comparator.comparing(MyStudentAttendance::own).reversed()
                .thenComparing(MyStudentAttendance::studentName, Comparator.nullsLast(Comparator.<String>naturalOrder())));
        return result;
    }

    @Override
    public List<ExcuseRow> listExcuses(Long schoolId, ExcuseStatus status, LocalDate from, LocalDate to) {
        requireSchool(schoolId);
        if (from != null && to != null) {
            String rangeProblem = AttendanceRules.rangeProblem(from, to);
            if (rangeProblem != null) {
                throw new AttendanceException(new AttendanceError.InvalidRange(rangeProblem));
            }
        }
        LocalDate lo = from == null ? LocalDate.of(1900, 1, 1) : from;
        LocalDate hi = to == null ? LocalDate.of(9999, 12, 31) : to;
        List<ExcuseEntity> excuses = status == null
                ? excuseRepository.findBySchoolIdAndDayBetweenOrderByDayDescIdDesc(schoolId, lo, hi)
                : excuseRepository.findBySchoolIdAndStatusAndDayBetweenOrderByDayDescIdDesc(schoolId, status, lo, hi);
        return excuseRows(schoolId, excuses);
    }

    @Override
    @Transactional
    public ExcuseRow decideExcuse(Long schoolId, Long excuseId, ExcuseStatus decision, Long deciderUserId) {
        requireSchool(schoolId);
        if (decision != ExcuseStatus.APPROVED && decision != ExcuseStatus.REJECTED) {
            throw new AttendanceException(new AttendanceError.InvalidExcuse(
                    "تصمیم باید APPROVED یا REJECTED باشد."));
        }
        ExcuseEntity found = excuseRepository.findByIdAndSchoolId(excuseId, schoolId)
                .orElseThrow(() -> new AttendanceException(new AttendanceError.ExcuseNotFound(excuseId)));
        // هم‌زمان با ذخیره‌ی حضور و غیابِ همان کلاس-روز پشتِ سر هم اجرا شود (همان قفلِ saveRoll)
        rollRepository.lockClassDay(LOCK_NAMESPACE, Objects.hash(found.getClassId(), found.getDay()));
        ExcuseEntity excuse = excuseRepository.findByIdAndSchoolId(excuseId, schoolId)
                .orElseThrow(() -> new AttendanceException(new AttendanceError.ExcuseNotFound(excuseId)));
        if (excuse.getStatus() != ExcuseStatus.PENDING) {
            throw new AttendanceException(new AttendanceError.ExcuseAlreadyDecided(excuseId));
        }
        excuse.setStatus(decision);
        excuse.setDecidedByUserId(deciderUserId);
        excuse.setDecidedAt(Instant.now());
        if (decision == ExcuseStatus.APPROVED) {
            // تأیید مُهرِ غیبتِ همان روز را در همین تراکنش «موجه» می‌کند؛ ردّ مُهر را دست نمی‌زند
            markRepository.findByClassIdAndDay(excuse.getClassId(), excuse.getDay()).stream()
                    .filter(m -> m.getStudentId().equals(excuse.getStudentId())
                            && m.getStatus() == AttendanceStatus.ABSENT)
                    .forEach(m -> {
                        m.setExcused(true);
                        markRepository.save(m);
                    });
        }
        return excuseRows(schoolId, List.of(excuseRepository.save(excuse))).get(0);
    }

    /** ردیف‌های عذر با نامِ دانش‌آموز و کلاس، با خواندنِ گروهیِ فهرستِ کلاس‌ها. */
    private List<ExcuseRow> excuseRows(Long schoolId, List<ExcuseEntity> excuses) {
        if (excuses.isEmpty()) {
            return List.of();
        }
        Set<Long> classIds = excuses.stream().map(ExcuseEntity::getClassId).collect(Collectors.toCollection(HashSet::new));
        Map<Long, String> classNames = new HashMap<>();
        for (Long id : classIds) {
            academicFacade.findClass(schoolId, id).ifPresent(c -> classNames.put(id, c.name()));
        }
        Map<Long, StudentInfo> students = new HashMap<>();
        academicFacade.listStudentsByClass(schoolId, classIds).values()
                .forEach(list -> list.forEach(s -> students.putIfAbsent(s.id().value(), s)));
        Map<Long, String> names = namesByUser(schoolId);
        List<ExcuseRow> rows = new ArrayList<>();
        for (ExcuseEntity e : excuses) {
            StudentInfo student = students.get(e.getStudentId());
            if (student == null) {
                // دانش‌آموزی که دیگر در فهرستِ کلاس نیست (ارتقا/ترک)
                student = academicFacade.findStudentInfo(e.getStudentId())
                        .filter(s -> schoolId.equals(s.schoolId())).orElse(null);
            }
            rows.add(new ExcuseRow(e.getId(), e.getStudentId(), student == null ? null : names.get(student.userId()),
                    e.getClassId(), classNames.get(e.getClassId()), e.getDay(), e.getNote(), e.getStatus(),
                    e.getSubmittedAt(), e.getDecidedAt()));
        }
        return rows;
    }

    // ---- کمکی‌ها ----

    /** یک کلاسِ روز: خودِ کلاس، گرفته‌شدن، اعضا (فهرست به‌علاوه‌ی مُهرداران) و مُهرهایش. */
    private record DayClass(ClassInfo cls, boolean taken, List<StudentInfo> students, List<MarkEntity> marks) {
    }

    /**
     * کلاس‌های سالِ جاریِ مدرسه (به‌علاوه‌ی کلاسِ هر سالی که همان روز حضور و غیابش گرفته شده) با اعضا و مُهرهای
     * همان روز؛ با یک خواندنِ گروهیِ اعضا.
     */
    private List<DayClass> loadDay(Long schoolId, LocalDate day) {
        Set<Long> takenIds = rollRepository.findBySchoolIdAndDay(schoolId, day).stream()
                .map(RollEntity::getClassId)
                .collect(Collectors.toSet());
        Map<Long, ClassInfo> classes = new LinkedHashMap<>();
        currentYearClasses(schoolId, currentYearCode(schoolId)).forEach(c -> classes.put(c.id().value(), c));
        for (Long id : takenIds) {
            if (!classes.containsKey(id)) {
                academicFacade.findClass(schoolId, id).ifPresent(c -> classes.put(id, c));
            }
        }
        Map<Long, List<MarkEntity>> marksByClass = markRepository.findBySchoolIdAndDay(schoolId, day).stream()
                .collect(Collectors.groupingBy(MarkEntity::getClassId));
        Map<Long, List<StudentInfo>> rosters = academicFacade.listStudentsByClass(schoolId, classes.keySet());
        List<DayClass> result = new ArrayList<>();
        for (ClassInfo cls : classes.values()) {
            Long id = cls.id().value();
            List<MarkEntity> marks = marksByClass.getOrDefault(id, List.of());
            result.add(new DayClass(cls, takenIds.contains(id),
                    withMarkedStudents(schoolId, rosters.getOrDefault(id, List.of()), marks), marks));
        }
        return result;
    }

    /** فهرستِ کلاس به‌علاوه‌ی دانش‌آموزی که مُهر دارد ولی دیگر در فهرست نیست (تاریخچه می‌ماند). */
    private List<StudentInfo> withMarkedStudents(Long schoolId, List<StudentInfo> roster, List<MarkEntity> marks) {
        Map<Long, StudentInfo> byId = new LinkedHashMap<>();
        roster.forEach(s -> byId.put(s.id().value(), s));
        for (MarkEntity mark : marks) {
            if (!byId.containsKey(mark.getStudentId())) {
                academicFacade.findStudentInfo(mark.getStudentId())
                        .filter(s -> schoolId.equals(s.schoolId()))
                        .ifPresent(s -> byId.put(s.id().value(), s));
            }
        }
        return new ArrayList<>(byId.values());
    }

    private Optional<String> currentYearCode(Long schoolId) {
        return schoolFacade.findCurrentAcademicYear(new SchoolId(schoolId)).map(SchoolFacade.AcademicYearInfo::code);
    }

    /** مدرسه‌ی بدون سالِ جاری همه‌ی کلاس‌هایش را می‌دهد (هیچ سالی حدس زده نمی‌شود). */
    private List<ClassInfo> currentYearClasses(Long schoolId, Optional<String> currentYear) {
        return currentYear.isPresent()
                ? academicFacade.listClasses(schoolId, currentYear.get())
                : academicFacade.listClasses(schoolId);
    }

    /** نامِ کاربران با یک query؛ {@code userId ← fullName}. */
    private Map<Long, String> namesByUser(Long schoolId) {
        Map<Long, String> names = new HashMap<>();
        iamFacade.findMembershipsBySchool(schoolId)
                .forEach(m -> names.putIfAbsent(m.userId().value(), m.fullName()));
        return names;
    }

    private SchoolInfo requireSchool(Long schoolId) {
        return schoolFacade.findSchool(new SchoolId(schoolId))
                .orElseThrow(() -> new AttendanceException(new AttendanceError.SchoolNotFound(schoolId)));
    }

    private ClassInfo requireClass(Long schoolId, Long classId) {
        return academicFacade.findClass(schoolId, classId)
                .orElseThrow(() -> new AttendanceException(new AttendanceError.ClassNotFound(classId)));
    }

    /** کلاس باید از سالِ جاری یا «بدون سال» باشد (قاعده ۲)؛ مدرسه‌ی بدون سالِ جاری محدودیتی ندارد. */
    private void requireCurrentYear(Long schoolId, ClassInfo cls) {
        if (cls.academicYear() == null) {
            return;
        }
        currentYearCode(schoolId).ifPresent(current -> {
            if (!current.equals(cls.academicYear())) {
                throw new AttendanceException(new AttendanceError.ClassNotInCurrentYear(
                        cls.id().value(), cls.academicYear(), current));
            }
        });
    }

    private static void requireDay(LocalDate day) {
        if (day == null) {
            throw new AttendanceException(new AttendanceError.InvalidAttendanceDay(null, "روز الزامی است."));
        }
    }

    private boolean isSchoolDay(SchoolInfo school, LocalDate day) {
        return AttendanceRules.isSchoolDay(school.weekStart(), day, schoolFacade.isClosedDay(school.id(), day));
    }

    /** «امروز» به وقتِ منطقه‌ی زمانیِ خودِ مدرسه؛ منطقه‌ی نامعتبر به منطقه‌ی سرور برمی‌گردد. */
    private static LocalDate today(SchoolInfo school) {
        ZoneId zone;
        try {
            zone = ZoneId.of(school.timezone());
        } catch (DateTimeException | NullPointerException e) {
            zone = ZoneId.systemDefault();
        }
        return LocalDate.now(zone);
    }
}
