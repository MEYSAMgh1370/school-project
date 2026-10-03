package com.example.roshdeandishe.studyplan.internal;

import com.example.roshdeandishe.academic.AcademicFacade;
import com.example.roshdeandishe.curriculum.CurriculumFacade;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.shared.exception.IamException;
import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.WeekStart;
import com.example.roshdeandishe.studyplan.StudyPlanFacade;
import com.example.roshdeandishe.studyplan.shared.StudyPlanError;
import com.example.roshdeandishe.studyplan.shared.StudyPlanException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
class StudyPlanFacadeImpl implements StudyPlanFacade {

    private final PlanRepository planRepository;
    private final PlanCourseWeightRepository planCourseWeightRepository;
    private final WeekCourseRepository weekCourseRepository;
    private final WeekCourseTopicRepository weekCourseTopicRepository;
    private final StudentPlanAssignmentRepository assignmentRepository;
    private final StudyIntervalRepository intervalRepository;
    private final StudyIntervalSubTopicRepository subTopicRepository;
    private final IntervalScheduleRepository scheduleRepository;
    private final CurriculumFacade curriculumFacade;
    private final AcademicFacade academicFacade;
    private final IamFacade iamFacade;
    private final SchoolFacade schoolFacade;
    // عمداً مستقل ساخته می‌شود، نه تزریق Spring - همان دلیل مستندشده در
    // notification.internal.BaleWebhookService: spring-modulith-events-jackson یک
    // Jackson 3 (tools.jackson) دیگر هم روی classpath می‌آورد و auto-configuration
    // دیگر یک Bean واحد و بدون‌ابهام از com.fasterxml.jackson.databind.ObjectMapper نمی‌سازد.
    private final ObjectMapper objectMapper = new ObjectMapper();

    StudyPlanFacadeImpl(PlanRepository planRepository,
                        PlanCourseWeightRepository planCourseWeightRepository,
                        WeekCourseRepository weekCourseRepository,
                        WeekCourseTopicRepository weekCourseTopicRepository,
                        StudentPlanAssignmentRepository assignmentRepository,
                        StudyIntervalRepository intervalRepository,
                        StudyIntervalSubTopicRepository subTopicRepository,
                        IntervalScheduleRepository scheduleRepository,
                        CurriculumFacade curriculumFacade,
                        AcademicFacade academicFacade,
                        IamFacade iamFacade,
                        SchoolFacade schoolFacade) {
        this.planRepository = planRepository;
        this.planCourseWeightRepository = planCourseWeightRepository;
        this.weekCourseRepository = weekCourseRepository;
        this.weekCourseTopicRepository = weekCourseTopicRepository;
        this.assignmentRepository = assignmentRepository;
        this.intervalRepository = intervalRepository;
        this.subTopicRepository = subTopicRepository;
        this.scheduleRepository = scheduleRepository;
        this.curriculumFacade = curriculumFacade;
        this.academicFacade = academicFacade;
        this.iamFacade = iamFacade;
        this.schoolFacade = schoolFacade;
    }

    // ---------------------------------------------------------------- plans

    @Override
    @Transactional
    public PlanId createPlan(Long schoolId, String title, List<CourseWeight> weights) {
        if (title == null || title.isBlank()) {
            throw new StudyPlanException(new StudyPlanError.InvalidPlan("عنوان برنامه نمی‌تواند خالی باشد."));
        }
        if (weights == null || weights.isEmpty()) {
            throw new StudyPlanException(new StudyPlanError.InvalidPlan("برنامه باید دست‌کم یک درس وزن‌دهی‌شده داشته باشد."));
        }
        Set<Long> schoolTopicIds = schoolTopicIds(schoolId);
        Set<Long> seen = new LinkedHashSet<>();
        for (CourseWeight weight : weights) {
            // همان قیدهای اسکیمای COURSE_INFO_SCHEMA موshavere: weight >= 0, priority >= 1
            if (weight.topicId() == null || !schoolTopicIds.contains(weight.topicId())) {
                throw new StudyPlanException(new StudyPlanError.TopicNotFound(weight.topicId()));
            }
            if (weight.weight() < 0) {
                throw new StudyPlanException(new StudyPlanError.InvalidPlan("وزن درس نمی‌تواند منفی باشد."));
            }
            if (weight.priority() < 1) {
                throw new StudyPlanException(new StudyPlanError.InvalidPlan("اولویت درس باید دست‌کم ۱ باشد."));
            }
            if (!seen.add(weight.topicId())) {
                throw new StudyPlanException(new StudyPlanError.InvalidPlan(
                        "سرفصل " + weight.topicId() + " بیش از یک بار وزن‌دهی شده است."));
            }
        }

        PlanEntity plan = planRepository.save(
                new PlanEntity(schoolId, currentActorOrNull(), title.trim(), Instant.now()));
        for (CourseWeight weight : weights) {
            planCourseWeightRepository.save(new PlanCourseWeightEntity(
                    plan.getId(), weight.topicId(), weight.weight(), weight.priority()));
        }
        return new PlanId(plan.getId());
    }

    @Override
    public List<PlanInfo> listPlans(Long schoolId) {
        return planRepository.findBySchoolIdOrderByIdAsc(schoolId).stream()
                .map(this::toPlanInfo)
                .toList();
    }

    // ---------------------------------------------------------- week courses

    @Override
    @Transactional
    public WeekCourseId createWeekCourse(Long schoolId, Long planId, LocalDate weekStart,
                                         Long courseTopicId, List<Long> subTopicIds) {
        PlanEntity plan = requirePlanInSchool(schoolId, planId);
        requireWeekStart(schoolId, weekStart);
        Set<Long> schoolTopicIds = schoolTopicIds(schoolId);
        if (courseTopicId == null || !schoolTopicIds.contains(courseTopicId)) {
            throw new StudyPlanException(new StudyPlanError.TopicNotFound(courseTopicId));
        }
        List<Long> subTopics = subTopicIds == null ? List.of() : subTopicIds;
        if (subTopics.size() > 10) {
            // سقف ArrayField(size=10) موshavere
            throw new StudyPlanException(new StudyPlanError.InvalidWeekCourse("حداکثر ۱۰ زیرسرفصل مجاز است."));
        }
        Set<Long> seen = new LinkedHashSet<>();
        for (Long subTopicId : subTopics) {
            if (subTopicId == null || !schoolTopicIds.contains(subTopicId)) {
                throw new StudyPlanException(new StudyPlanError.TopicNotFound(subTopicId));
            }
            if (!seen.add(subTopicId)) {
                throw new StudyPlanException(new StudyPlanError.InvalidWeekCourse(
                        "زیرسرفصل " + subTopicId + " تکراری است."));
            }
        }
        if (weekCourseRepository.findByPlanIdAndWeekStartAndCourseTopicId(plan.getId(), weekStart, courseTopicId)
                .isPresent()) {
            throw new StudyPlanException(new StudyPlanError.DuplicateWeekCourse(plan.getId(), weekStart, courseTopicId));
        }

        WeekCourseEntity weekCourse = weekCourseRepository.save(
                new WeekCourseEntity(plan.getId(), weekStart, courseTopicId));
        for (Long subTopicId : subTopics) {
            weekCourseTopicRepository.save(new WeekCourseTopicEntity(weekCourse.getId(), subTopicId));
        }
        return new WeekCourseId(weekCourse.getId());
    }

    @Override
    public List<WeekCourseInfo> listWeekCourses(Long schoolId, Long planId) {
        PlanEntity plan = requirePlanInSchool(schoolId, planId);
        List<WeekCourseEntity> weekCourses = weekCourseRepository.findByPlanIdOrderByWeekStartAscIdAsc(plan.getId());
        Map<Long, List<Long>> subTopicsByWeekCourse = subTopicsByWeekCourse(weekCourses);
        return weekCourses.stream()
                .map(wc -> new WeekCourseInfo(new WeekCourseId(wc.getId()), new PlanId(wc.getPlanId()),
                        wc.getWeekStart(), wc.getCourseTopicId(),
                        subTopicsByWeekCourse.getOrDefault(wc.getId(), List.of())))
                .toList();
    }

    // ----------------------------------------------------------- assignments

    @Override
    @Transactional
    public void assignPlanToStudent(Long schoolId, Long planId, Long studentId) {
        PlanEntity plan = requirePlanInSchool(schoolId, planId);
        requireStudentInSchool(schoolId, studentId);

        Optional<StudentPlanAssignmentEntity> existing = assignmentRepository.findByStudentId(studentId);
        if (existing.isPresent()) {
            StudentPlanAssignmentEntity assignment = existing.get();
            assignment.setPlanId(plan.getId());
            assignment.setAssignedAt(Instant.now());
            assignmentRepository.save(assignment);
        } else {
            assignmentRepository.save(
                    new StudentPlanAssignmentEntity(schoolId, studentId, plan.getId(), Instant.now()));
        }
    }

    @Override
    public List<AssignmentInfo> listAssignments(Long schoolId, Long planId) {
        PlanEntity plan = requirePlanInSchool(schoolId, planId);
        return assignmentRepository.findByPlanIdOrderByIdAsc(plan.getId()).stream()
                .map(a -> new AssignmentInfo(a.getStudentId(), new PlanId(a.getPlanId()), a.getAssignedAt()))
                .toList();
    }

    // ------------------------------------------------------------- schedules

    @Override
    public ScheduleInfo getSchedule(Long schoolId, Long studentId) {
        requireStudentInSchool(schoolId, studentId);
        Optional<IntervalScheduleEntity> entity = scheduleRepository.findByStudentId(studentId);
        if (entity.isEmpty()) {
            return new ScheduleInfo(studentId, emptySchedule(), null, null, false);
        }
        IntervalScheduleEntity schedule = entity.get();
        Map<Weekday, List<TimeRange>> current = schedule.getCurrentSchedule() == null
                ? null
                : fromJson(schedule.getCurrentSchedule());
        return new ScheduleInfo(studentId, fromJson(schedule.getDefaultSchedule()), current,
                schedule.getLastEditedAt(), currentActive(schedule, weekStartOf(schoolId)));
    }

    @Override
    @Transactional
    public void setDefaultSchedule(Long schoolId, Long studentId, Map<Weekday, List<TimeRange>> schedule) {
        requireStudentInSchool(schoolId, studentId);
        if (schedule == null) {
            throw new StudyPlanException(new StudyPlanError.InvalidSchedule("برنامه‌ی زمانی پیش‌فرض نمی‌تواند خالی باشد."));
        }
        Map<Weekday, List<TimeRange>> normalized = validateAndNormalize(schedule);
        IntervalScheduleEntity entity = scheduleRepository.findByStudentId(studentId)
                .orElseGet(() -> new IntervalScheduleEntity(schoolId, studentId, toJson(emptySchedule())));
        entity.setDefaultSchedule(toJson(normalized));
        scheduleRepository.save(entity);
    }

    @Override
    @Transactional
    public void setCurrentSchedule(Long schoolId, Long studentId, Map<Weekday, List<TimeRange>> schedule) {
        requireStudentInSchool(schoolId, studentId);
        IntervalScheduleEntity entity = scheduleRepository.findByStudentId(studentId)
                .orElseGet(() -> new IntervalScheduleEntity(schoolId, studentId, toJson(emptySchedule())));
        entity.setCurrentSchedule(schedule == null ? null : toJson(validateAndNormalize(schedule)));
        // همان رفتار serializer موshavere: هر بار که current_schedule در payload
        // بیاید (حتی null برای پاک کردن) last_schedule_modify به‌روز می‌شود
        entity.setLastEditedAt(Instant.now());
        scheduleRepository.save(entity);
    }

    // -------------------------------------------------------- study intervals

    @Override
    @Transactional
    public List<IntervalInfo> suggestWeek(Long schoolId, Long studentId, LocalDate weekStart) {
        requireStudentInSchool(schoolId, studentId);
        WeekStart week = requireWeekStart(schoolId, weekStart);
        LocalDateTime start = weekStart.atStartOfDay();
        LocalDateTime end = weekStart.plusDays(7).atStartOfDay();
        if (intervalRepository.existsByStudentIdAndStartTimeGreaterThanEqualAndStartTimeLessThan(
                studentId, start, end)) {
            throw new StudyPlanException(new StudyPlanError.WeekNotEmpty(weekStart));
        }
        StudentPlanAssignmentEntity assignment = assignmentRepository.findByStudentId(studentId)
                .orElseThrow(() -> new StudyPlanException(new StudyPlanError.PlanNotAssigned(studentId)));
        List<CourseWeight> weights = planCourseWeightRepository
                .findByPlanIdOrderByPriorityAscIdAsc(assignment.getPlanId()).stream()
                .map(w -> new CourseWeight(w.getTopicId(), w.getWeight(), w.getPriority()))
                .toList();
        if (weights.isEmpty()) {
            throw new StudyPlanException(new StudyPlanError.PlanNotAssigned(studentId));
        }

        Map<Weekday, List<TimeRange>> schedule = effectiveSchedule(studentId, week);
        List<Suggestor.WeekCourseData> weekCourses = loadWeekCourseData(assignment.getPlanId());

        List<Suggestor.ProposedInterval> proposed = Suggestor.suggest(weekStart, Weekday.weekStartingOn(week.firstDay()), schedule, weights,
                weekCourses);

        List<IntervalInfo> created = new ArrayList<>();
        for (Suggestor.ProposedInterval interval : proposed) {
            StudyIntervalEntity saved = intervalRepository.save(new StudyIntervalEntity(
                    schoolId, studentId, interval.topicId(), interval.start(), interval.end()));
            for (Long subTopicId : interval.subTopicIds()) {
                subTopicRepository.save(new StudyIntervalSubTopicEntity(saved.getId(), subTopicId));
            }
            created.add(new IntervalInfo(new IntervalId(saved.getId()), studentId, saved.getTopicId(),
                    interval.subTopicIds(), saved.getStartTime(), saved.getEndTime(), false, 0));
        }
        return created;
    }

    @Override
    public List<IntervalInfo> getWeek(Long schoolId, Long studentId, LocalDate date) {
        requireStudentInSchool(schoolId, studentId);
        LocalDate weekStart = weekStartOf(schoolId).weekStartOf(date == null ? LocalDate.now() : date);
        List<StudyIntervalEntity> intervals = intervalRepository
                .findByStudentIdAndStartTimeGreaterThanEqualAndEndTimeLessThanEqualOrderByStartTimeAscIdAsc(
                        studentId, weekStart.atStartOfDay(), weekStart.plusDays(7).atStartOfDay());
        return toIntervalInfos(intervals);
    }

    @Override
    @Transactional
    public void clearWeek(Long schoolId, Long studentId, LocalDate weekStart) {
        requireStudentInSchool(schoolId, studentId);
        requireWeekStart(schoolId, weekStart);
        List<StudyIntervalEntity> intervals = intervalRepository
                .findByStudentIdAndStartTimeGreaterThanEqualAndEndTimeLessThanEqualOrderByStartTimeAscIdAsc(
                        studentId, weekStart.atStartOfDay(), weekStart.plusDays(7).atStartOfDay());
        if (intervals.isEmpty()) {
            return;
        }
        List<Long> ids = intervals.stream().map(StudyIntervalEntity::getId).toList();
        subTopicRepository.deleteByIntervalIdIn(ids);
        intervalRepository.deleteAll(intervals);
    }

    @Override
    @Transactional
    public List<CopyResult> copyWeek(Long schoolId, Long sourceStudentId, LocalDate weekStart,
                                     List<Long> targetStudentIds) {
        requireStudentInSchool(schoolId, sourceStudentId);
        requireWeekStart(schoolId, weekStart);
        List<StudyIntervalEntity> source = intervalRepository
                .findByStudentIdAndStartTimeGreaterThanEqualAndEndTimeLessThanEqualOrderByStartTimeAscIdAsc(
                        sourceStudentId, weekStart.atStartOfDay(), weekStart.plusDays(7).atStartOfDay());

        List<CopyResult> results = new ArrayList<>();
        for (Long targetStudentId : targetStudentIds == null ? List.<Long>of() : targetStudentIds) {
            try {
                requireStudentInSchool(schoolId, targetStudentId);
                // اول همه چک، بعد ثبت - کپی نیمه‌کاره برای یک مقصد پیش نیاید
                for (StudyIntervalEntity interval : source) {
                    if (intervalRepository.overlaps(targetStudentId, interval.getStartTime(), interval.getEndTime())) {
                        throw new StudyPlanException(new StudyPlanError.IntervalOverlaps(
                                "بازه‌ی " + interval.getStartTime() + " تا " + interval.getEndTime()
                                        + " با بازه‌های موجود دانش‌آموز " + targetStudentId + " هم‌پوشانی دارد."));
                    }
                }
                for (StudyIntervalEntity interval : source) {
                    intervalRepository.save(new StudyIntervalEntity(schoolId, targetStudentId,
                            interval.getTopicId(), interval.getStartTime(), interval.getEndTime()));
                }
                results.add(new CopyResult(targetStudentId, true, "کپی شد"));
            } catch (StudyPlanException e) {
                results.add(new CopyResult(targetStudentId, false, e.getMessage()));
            }
        }
        return results;
    }

    @Override
    @Transactional
    public IntervalId createInterval(Long schoolId, Long studentId, Long topicId, List<Long> subTopicIds,
                                     LocalDateTime start, LocalDateTime end) {
        requireStudentInSchool(schoolId, studentId);
        Set<Long> schoolTopicIds = schoolTopicIds(schoolId);
        if (topicId == null || !schoolTopicIds.contains(topicId)) {
            throw new StudyPlanException(new StudyPlanError.TopicNotFound(topicId));
        }
        List<Long> subTopics = subTopicIds == null ? List.of() : subTopicIds;
        for (Long subTopicId : subTopics) {
            if (subTopicId == null || !schoolTopicIds.contains(subTopicId)) {
                throw new StudyPlanException(new StudyPlanError.TopicNotFound(subTopicId));
            }
        }
        if (start == null || end == null || !start.isBefore(end)) {
            throw new StudyPlanException(new StudyPlanError.InvalidInterval("شروع بازه باید قبل از پایان آن باشد."));
        }
        if (intervalRepository.overlaps(studentId, start, end)) {
            throw new StudyPlanException(new StudyPlanError.IntervalOverlaps(
                    "بازه‌ی جدید با بازه‌های مطالعه‌ی موجود هم‌پوشانی دارد."));
        }

        StudyIntervalEntity saved = intervalRepository.save(
                new StudyIntervalEntity(schoolId, studentId, topicId, start, end));
        for (Long subTopicId : new LinkedHashSet<>(subTopics)) {
            subTopicRepository.save(new StudyIntervalSubTopicEntity(saved.getId(), subTopicId));
        }
        return new IntervalId(saved.getId());
    }

    @Override
    @Transactional
    public IntervalInfo updateInterval(Long schoolId, Long intervalId, Boolean done, Integer testCount) {
        StudyIntervalEntity interval = requireIntervalInSchool(schoolId, intervalId);
        return applyIntervalUpdate(interval, done, testCount);
    }

    @Override
    @Transactional
    public IntervalInfo updateOwnInterval(Long schoolId, Long studentId, Long intervalId, Boolean done, Integer testCount) {
        StudyIntervalEntity interval = requireIntervalInSchool(schoolId, intervalId);
        if (!interval.getStudentId().equals(studentId)) {
            throw new StudyPlanException(new StudyPlanError.IntervalNotFound(intervalId));
        }
        return applyIntervalUpdate(interval, done, testCount);
    }

    private IntervalInfo applyIntervalUpdate(StudyIntervalEntity interval, Boolean done, Integer testCount) {
        if (testCount != null && testCount < 0) {
            throw new StudyPlanException(new StudyPlanError.InvalidInterval("تعداد تست نمی‌تواند منفی باشد."));
        }
        if (done != null) {
            interval.setDone(done);
        }
        if (testCount != null) {
            interval.setTestCount(testCount);
        }
        intervalRepository.save(interval);
        return toIntervalInfos(List.of(interval)).get(0);
    }

    @Override
    @Transactional
    public void deleteInterval(Long schoolId, Long intervalId) {
        StudyIntervalEntity interval = requireIntervalInSchool(schoolId, intervalId);
        subTopicRepository.deleteByIntervalId(interval.getId());
        intervalRepository.delete(interval);
    }

    @Override
    public List<RecentTopic> getRecentTopics(Long studentId) {
        Optional<AcademicFacade.StudentInfo> student = academicFacade.findStudentInfo(studentId);
        if (student.isEmpty()) {
            return List.of();
        }
        LocalDate weekStart = weekStartOf(student.get().schoolId()).weekStartOf(LocalDate.now());
        List<StudyIntervalEntity> intervals = intervalRepository
                .findByStudentIdAndStartTimeGreaterThanEqualAndEndTimeLessThanEqualOrderByStartTimeAscIdAsc(
                        studentId, weekStart.atStartOfDay(), weekStart.plusDays(7).atStartOfDay());
        if (intervals.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Long>> subTopicsByInterval = subTopicRepository
                .findByIntervalIdInOrderByIdAsc(intervals.stream().map(StudyIntervalEntity::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(StudyIntervalSubTopicEntity::getIntervalId,
                        Collectors.mapping(StudyIntervalSubTopicEntity::getTopicId, Collectors.toList())));

        Map<Long, LinkedHashSet<Long>> byTopic = new LinkedHashMap<>();
        for (StudyIntervalEntity interval : intervals) {
            byTopic.computeIfAbsent(interval.getTopicId(), k -> new LinkedHashSet<>())
                    .addAll(subTopicsByInterval.getOrDefault(interval.getId(), List.of()));
        }
        return byTopic.entrySet().stream()
                .map(e -> new RecentTopic(e.getKey(), List.copyOf(e.getValue())))
                .toList();
    }

    // ---------------------------------------------------------------- helpers

    private PlanInfo toPlanInfo(PlanEntity plan) {
        List<CourseWeight> weights = planCourseWeightRepository
                .findByPlanIdOrderByPriorityAscIdAsc(plan.getId()).stream()
                .map(w -> new CourseWeight(w.getTopicId(), w.getWeight(), w.getPriority()))
                .toList();
        return new PlanInfo(new PlanId(plan.getId()), plan.getSchoolId(), plan.getTeacherId(),
                plan.getTitle(), weights);
    }

    private List<IntervalInfo> toIntervalInfos(List<StudyIntervalEntity> intervals) {
        if (intervals.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Long>> subTopicsByInterval = subTopicRepository
                .findByIntervalIdInOrderByIdAsc(intervals.stream().map(StudyIntervalEntity::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(StudyIntervalSubTopicEntity::getIntervalId,
                        Collectors.mapping(StudyIntervalSubTopicEntity::getTopicId, Collectors.toList())));
        return intervals.stream()
                .map(i -> new IntervalInfo(new IntervalId(i.getId()), i.getStudentId(), i.getTopicId(),
                        subTopicsByInterval.getOrDefault(i.getId(), List.of()),
                        i.getStartTime(), i.getEndTime(), i.isDone(), i.getTestCount()))
                .toList();
    }

    private List<Suggestor.WeekCourseData> loadWeekCourseData(Long planId) {
        List<WeekCourseEntity> weekCourses = weekCourseRepository.findByPlanIdOrderByWeekStartAscIdAsc(planId);
        Map<Long, List<Long>> subTopics = subTopicsByWeekCourse(weekCourses);
        return weekCourses.stream()
                .map(wc -> new Suggestor.WeekCourseData(wc.getCourseTopicId(), wc.getWeekStart(),
                        subTopics.getOrDefault(wc.getId(), List.of())))
                .toList();
    }

    private Map<Long, List<Long>> subTopicsByWeekCourse(List<WeekCourseEntity> weekCourses) {
        if (weekCourses.isEmpty()) {
            return Map.of();
        }
        return weekCourseTopicRepository
                .findByWeekCourseIdInOrderByIdAsc(weekCourses.stream().map(WeekCourseEntity::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(WeekCourseTopicEntity::getWeekCourseId,
                        Collectors.mapping(WeekCourseTopicEntity::getTopicId, Collectors.toList())));
    }

    /**
     * برنامه‌ی مؤثر برای پیشنهاد: currentSchedule فقط اگر در هفته‌ی جاری
     * (از روز شروع هفته‌ی اخیرِ مدرسه) ویرایش شده باشد - پورت property «schedule» موshavere
     * با مقایسه‌ی timestamp ساده (MOSHAVERE_PORT_SPEC.md §6). بدون رکورد -
     * برنامه‌ی خالی، که پایین‌تر در FillSlices به ScheduleTooSmall می‌رسد.
     */
    private Map<Weekday, List<TimeRange>> effectiveSchedule(Long studentId, WeekStart week) {
        Optional<IntervalScheduleEntity> entity = scheduleRepository.findByStudentId(studentId);
        if (entity.isEmpty()) {
            return emptySchedule();
        }
        IntervalScheduleEntity schedule = entity.get();
        if (currentActive(schedule, week)) {
            return fromJson(schedule.getCurrentSchedule());
        }
        return fromJson(schedule.getDefaultSchedule());
    }

    private boolean currentActive(IntervalScheduleEntity schedule, WeekStart week) {
        if (schedule.getCurrentSchedule() == null || schedule.getLastEditedAt() == null) {
            return false;
        }
        LocalDate editedOn = LocalDate.ofInstant(schedule.getLastEditedAt(), java.time.ZoneId.systemDefault());
        return !editedOn.isBefore(week.weekStartOf(LocalDate.now()));
    }

    /** روز شروع هفته‌ی این مدرسه - تنها مالکِ آن SchoolFacade است (W9). */
    private WeekStart weekStartOf(Long schoolId) {
        return schoolFacade.findSchool(new SchoolFacade.SchoolId(schoolId))
                .map(SchoolFacade.SchoolInfo::weekStart)
                .orElseThrow(() -> new IllegalStateException("school not found: " + schoolId));
    }

    private WeekStart requireWeekStart(Long schoolId, LocalDate weekStart) {
        WeekStart week = weekStartOf(schoolId);
        if (weekStart == null || weekStart.getDayOfWeek() != week.firstDay()) {
            String day = week == WeekStart.MONDAY ? "دوشنبه" : "شنبه";
            throw new StudyPlanException(new StudyPlanError.InvalidWeekStart(
                    "شروع هفته باید " + day + " باشد (هفته‌ی درسی این مدرسه از " + day + " آغاز می‌شود)."));
        }
        return week;
    }

    private PlanEntity requirePlanInSchool(Long schoolId, Long planId) {
        PlanEntity plan = planRepository.findById(planId)
                .orElseThrow(() -> new StudyPlanException(new StudyPlanError.PlanNotFound(planId)));
        if (!plan.getSchoolId().equals(schoolId)) {
            // برنامه‌ی مدرسه‌ی دیگر - از دید این ماژول همان «یافت نشد»
            throw new StudyPlanException(new StudyPlanError.PlanNotFound(planId));
        }
        return plan;
    }

    private StudyIntervalEntity requireIntervalInSchool(Long schoolId, Long intervalId) {
        StudyIntervalEntity interval = intervalRepository.findById(intervalId)
                .orElseThrow(() -> new StudyPlanException(new StudyPlanError.IntervalNotFound(intervalId)));
        if (!interval.getSchoolId().equals(schoolId)) {
            throw new StudyPlanException(new StudyPlanError.IntervalNotFound(intervalId));
        }
        return interval;
    }

    private void requireStudentInSchool(Long schoolId, Long studentId) {
        if (studentId == null) {
            throw new StudyPlanException(new StudyPlanError.StudentNotFound(null));
        }
        boolean inSchool = academicFacade.findStudentInfo(studentId)
                .map(info -> info.schoolId().equals(schoolId))
                .orElse(false);
        if (!inSchool) {
            throw new StudyPlanException(new StudyPlanError.StudentNotFound(studentId));
        }
    }

    private Set<Long> schoolTopicIds(Long schoolId) {
        return curriculumFacade.listTopics(schoolId).stream()
                .map(t -> t.id().value())
                .collect(Collectors.toSet());
    }

    /**
     * اعتبارسنجی برنامه‌ی زمانی - پورت schedule_schema_validator +
     * schedule_overlap_validator موshavere؛ روز غایب به‌جای خطا فهرست خالی
     * فرض و خروجی به هر ۷ روز (به ترتیب شنبه تا جمعه) نرمال می‌شود.
     */
    private Map<Weekday, List<TimeRange>> validateAndNormalize(Map<Weekday, List<TimeRange>> schedule) {
        Map<Weekday, List<TimeRange>> normalized = emptySchedule();
        for (Map.Entry<Weekday, List<TimeRange>> entry : schedule.entrySet()) {
            if (entry.getKey() == null) {
                throw new StudyPlanException(new StudyPlanError.InvalidSchedule("روز هفته نامعتبر است."));
            }
            List<TimeRange> ranges = entry.getValue() == null ? List.of() : entry.getValue();
            List<LocalTime[]> parsed = new ArrayList<>();
            for (TimeRange range : ranges) {
                LocalTime start = parseTime(range == null ? null : range.start());
                LocalTime end = parseTime(range == null ? null : range.end());
                if (!start.isBefore(end)) {
                    throw new StudyPlanException(new StudyPlanError.InvalidSchedule(
                            "شروع بازه باید قبل از پایان آن باشد (" + range.start() + " تا " + range.end() + ")."));
                }
                for (LocalTime[] other : parsed) {
                    if (overlap(start, end, other[0], other[1])) {
                        throw new StudyPlanException(new StudyPlanError.InvalidSchedule(
                                "بازه‌های زمانی روز " + entry.getKey() + " هم‌پوشانی دارند."));
                    }
                }
                parsed.add(new LocalTime[]{start, end});
            }
            normalized.put(entry.getKey(), List.copyOf(ranges));
        }
        return normalized;
    }

    private static boolean overlap(LocalTime s1, LocalTime e1, LocalTime s2, LocalTime e2) {
        return s1.isBefore(e2) && e1.isAfter(s2);
    }

    private static LocalTime parseTime(String hhmm) {
        if (hhmm == null || !hhmm.matches("\\d{1,2}:\\d{2}")) {
            throw new StudyPlanException(new StudyPlanError.InvalidSchedule(
                    "قالب ساعت باید HH:mm باشد (مقدار دریافتی: " + hhmm + ")."));
        }
        String[] parts = hhmm.split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);
        if (hour > 23 || minute > 59) {
            throw new StudyPlanException(new StudyPlanError.InvalidSchedule(
                    "ساعت نامعتبر است: " + hhmm));
        }
        return LocalTime.of(hour, minute);
    }

    private static Map<Weekday, List<TimeRange>> emptySchedule() {
        Map<Weekday, List<TimeRange>> schedule = new LinkedHashMap<>();
        for (Weekday day : Weekday.values()) {
            schedule.put(day, List.of());
        }
        return schedule;
    }

    private String toJson(Map<Weekday, List<TimeRange>> schedule) {
        try {
            return objectMapper.writeValueAsString(schedule);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("serialization برنامه‌ی زمانی شکست خورد", e);
        }
    }

    private Map<Weekday, List<TimeRange>> fromJson(String json) {
        try {
            Map<Weekday, List<TimeRange>> parsed = objectMapper.readValue(json, new TypeReference<>() {
            });
            Map<Weekday, List<TimeRange>> normalized = emptySchedule();
            normalized.putAll(parsed);
            return normalized;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("deserialization برنامه‌ی زمانی شکست خورد", e);
        }
    }

    /**
     * همان قرارداد currentActorOrNull در Finance/ExpenseFacadeImpl - سازنده
     * ممکن است از مسیر بدون کاربر احرازشده (تست/seed) صدا زده شود.
     */
    private Long currentActorOrNull() {
        try {
            return iamFacade.currentUserId().value();
        } catch (IamException e) {
            return null;
        }
    }
}
