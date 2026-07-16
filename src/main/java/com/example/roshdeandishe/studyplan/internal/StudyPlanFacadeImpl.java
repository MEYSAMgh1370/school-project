package com.example.roshdeandishe.studyplan.internal;

import com.example.roshdeandishe.academic.AcademicFacade;
import com.example.roshdeandishe.curriculum.CurriculumFacade;
import com.example.roshdeandishe.studyplan.StudyPlanFacade;
import com.example.roshdeandishe.studyplan.internal.WeekSuggester.StudyIntervalCreationCommand;
import com.example.roshdeandishe.studyplan.shared.StudyPlanError;
import com.example.roshdeandishe.studyplan.shared.StudyPlanException;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
class StudyPlanFacadeImpl implements StudyPlanFacade {

    private final PlanRepository planRepository;
    private final WeekCourseRepository weekCourseRepository;
    private final WeekCourseTopicRepository weekCourseTopicRepository;
    private final StudentPlanAssignmentRepository assignmentRepository;
    private final StudyIntervalRepository studyIntervalRepository;
    private final StudyIntervalSubTopicRepository studyIntervalSubTopicRepository;
    private final IntervalScheduleRepository intervalScheduleRepository;
    private final CurriculumFacade curriculumFacade;
    private final AcademicFacade academicFacade;
    private final WeekSuggester weekSuggester = new WeekSuggester();

    StudyPlanFacadeImpl(
            PlanRepository planRepository,
            WeekCourseRepository weekCourseRepository,
            WeekCourseTopicRepository weekCourseTopicRepository,
            StudentPlanAssignmentRepository assignmentRepository,
            StudyIntervalRepository studyIntervalRepository,
            StudyIntervalSubTopicRepository studyIntervalSubTopicRepository,
            IntervalScheduleRepository intervalScheduleRepository,
            CurriculumFacade curriculumFacade,
            AcademicFacade academicFacade
    ) {
        this.planRepository = planRepository;
        this.weekCourseRepository = weekCourseRepository;
        this.weekCourseTopicRepository = weekCourseTopicRepository;
        this.assignmentRepository = assignmentRepository;
        this.studyIntervalRepository = studyIntervalRepository;
        this.studyIntervalSubTopicRepository = studyIntervalSubTopicRepository;
        this.intervalScheduleRepository = intervalScheduleRepository;
        this.curriculumFacade = curriculumFacade;
        this.academicFacade = academicFacade;
    }

    private static final TypeReference<List<CourseWeight>> COURSE_INFO_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, List<DaySlot>>> SCHEDULE_TYPE = new TypeReference<>() {
    };

    @Override
    @Transactional
    public PlanId createPlan(Long schoolId, Long teacherId, String title, List<CourseWeight> courseInfo) {
        if (title == null || title.isBlank()) {
            throw new StudyPlanException(new StudyPlanError.InvalidPlan("عنوان برنامه نمی‌تواند خالی باشد."));
        }
        if (courseInfo == null || courseInfo.isEmpty()) {
            throw new StudyPlanException(new StudyPlanError.InvalidPlan("برنامه باید حداقل یک درس داشته باشد."));
        }
        Set<Long> validTopicIds = validTopicIds(schoolId);
        for (CourseWeight cw : courseInfo) {
            validateCourseWeight(cw, validTopicIds);
        }

        PlanEntity saved = planRepository.save(
                new PlanEntity(schoolId, teacherId, title.strip(), JsonSupport.write(courseInfo))
        );
        return new PlanId(saved.getId());
    }

    @Override
    public PlanInfo getPlan(Long schoolId, Long planId) {
        return toInfo(requirePlanInSchool(schoolId, planId));
    }

    @Override
    public List<PlanInfo> listPlans(Long schoolId) {
        return planRepository.findBySchoolIdOrderByIdAsc(schoolId).stream()
                .map(StudyPlanFacadeImpl::toInfo)
                .toList();
    }

    @Override
    @Transactional
    public StudentPlanAssignmentId assignPlanToStudent(Long schoolId, Long studentId, Long planId) {
        requireStudentInSchool(schoolId, studentId);
        requirePlanInSchool(schoolId, planId);

        StudentPlanAssignmentEntity assignment = assignmentRepository.findBySchoolIdAndStudentId(schoolId, studentId)
                .orElseGet(() -> new StudentPlanAssignmentEntity(schoolId, studentId, planId));
        assignment.setPlanId(planId);
        StudentPlanAssignmentEntity saved = assignmentRepository.save(assignment);
        return new StudentPlanAssignmentId(saved.getId());
    }

    @Override
    public Optional<PlanId> findAssignedPlan(Long schoolId, Long studentId) {
        return assignmentRepository.findBySchoolIdAndStudentId(schoolId, studentId)
                .map(a -> new PlanId(a.getPlanId()));
    }

    @Override
    @Transactional
    public WeekCourseId createWeekCourse(Long schoolId, Long planId, LocalDate weekStartDate, Long topicId, List<Long> subTopicIds) {
        requirePlanInSchool(schoolId, planId);
        requireSaturday(weekStartDate);

        Set<Long> validTopicIds = validTopicIds(schoolId);
        if (!validTopicIds.contains(topicId)) {
            throw new StudyPlanException(new StudyPlanError.TopicNotFound(topicId));
        }
        List<Long> subTopics = subTopicIds == null ? List.of() : subTopicIds;
        if (subTopics.size() > 10) {
            throw new StudyPlanException(new StudyPlanError.InvalidWeekCourse("حداکثر ۱۰ زیر-سرفصل مجاز است."));
        }
        for (Long subTopicId : subTopics) {
            if (!validTopicIds.contains(subTopicId)) {
                throw new StudyPlanException(new StudyPlanError.TopicNotFound(subTopicId));
            }
        }

        WeekCourseEntity saved = weekCourseRepository.save(new WeekCourseEntity(schoolId, planId, weekStartDate, topicId));
        int order = 0;
        for (Long subTopicId : subTopics) {
            weekCourseTopicRepository.save(new WeekCourseTopicEntity(saved.getId(), subTopicId, order++));
        }
        return new WeekCourseId(saved.getId());
    }

    @Override
    public List<WeekCourseInfo> listWeekCourses(Long schoolId, Long planId) {
        requirePlanInSchool(schoolId, planId);
        List<WeekCourseEntity> weekCourses = weekCourseRepository.findByPlanIdOrderByWeekStartDateAscIdAsc(planId);
        return toWeekCourseInfos(weekCourses);
    }

    @Override
    @Transactional
    public ScheduleInfo getOrCreateSchedule(Long schoolId, Long studentId) {
        requireStudentInSchool(schoolId, studentId);
        IntervalScheduleEntity schedule = findOrCreateSchedule(schoolId, studentId);
        return toScheduleInfo(schedule);
    }

    @Override
    @Transactional
    public ScheduleInfo updateCurrentSchedule(Long schoolId, Long studentId, Map<String, List<DaySlot>> currentSchedule) {
        requireStudentInSchool(schoolId, studentId);
        ScheduleValidator.validate(currentSchedule);

        IntervalScheduleEntity schedule = findOrCreateSchedule(schoolId, studentId);
        schedule.setCurrentScheduleJson(JsonSupport.write(currentSchedule));
        schedule.setLastEditedAt(LocalDateTime.now());
        return toScheduleInfo(intervalScheduleRepository.save(schedule));
    }

    @Override
    @Transactional
    public List<StudyIntervalInfo> suggestWeek(Long schoolId, Long studentId, LocalDate weekStartDate) {
        requireStudentInSchool(schoolId, studentId);
        requireSaturday(weekStartDate);
        requireWeekEmpty(schoolId, studentId, weekStartDate);

        Long planId = assignmentRepository.findBySchoolIdAndStudentId(schoolId, studentId)
                .map(StudentPlanAssignmentEntity::getPlanId)
                .orElseThrow(() -> new StudyPlanException(new StudyPlanError.PlanNotAssigned(studentId)));
        PlanEntity plan = requirePlanInSchool(schoolId, planId);
        List<CourseWeight> courseInfo = JsonSupport.read(plan.getCourseInfoJson(), COURSE_INFO_TYPE);

        ScheduleInfo scheduleInfo = toScheduleInfo(findOrCreateSchedule(schoolId, studentId));
        List<WeekCourseInfo> weekCoursesForWeek = toWeekCourseInfos(
                weekCourseRepository.findByPlanIdAndWeekStartDate(planId, weekStartDate)
        );

        List<StudyIntervalCreationCommand> commands = weekSuggester.suggest(
                scheduleInfo.effectiveSchedule(), courseInfo, weekCoursesForWeek, weekStartDate
        );

        List<StudyIntervalInfo> created = new ArrayList<>();
        for (StudyIntervalCreationCommand command : commands) {
            StudyIntervalEntity saved = studyIntervalRepository.save(
                    new StudyIntervalEntity(schoolId, studentId, command.topicId(), command.start(), command.end())
            );
            saveSubTopics(saved.getId(), command.subTopicIds());
            created.add(toInfo(saved, command.subTopicIds()));
        }
        return created;
    }

    @Override
    public List<StudyIntervalInfo> getWeek(Long schoolId, Long studentId, LocalDate weekStartDate) {
        requireSaturday(weekStartDate);
        List<StudyIntervalEntity> intervals = studyIntervalRepository
                .findBySchoolIdAndStudentIdAndStartTimeBetweenOrderByStartTimeAsc(
                        schoolId, studentId, weekStartDate.atStartOfDay(), weekStartDate.plusDays(7).atStartOfDay()
                );
        return toStudyIntervalInfos(intervals);
    }

    @Override
    @Transactional
    public void clearWeek(Long schoolId, Long studentId, LocalDate weekStartDate) {
        requireSaturday(weekStartDate);
        List<StudyIntervalEntity> intervals = studyIntervalRepository
                .findBySchoolIdAndStudentIdAndStartTimeBetweenOrderByStartTimeAsc(
                        schoolId, studentId, weekStartDate.atStartOfDay(), weekStartDate.plusDays(7).atStartOfDay()
                );
        deleteIntervalsWithSubTopics(intervals);
    }

    @Override
    @Transactional
    public List<CopyWeekResult> copyWeek(Long schoolId, Long sourceStudentId, LocalDate weekStartDate, List<Long> targetStudentIds) {
        requireSaturday(weekStartDate);
        List<StudyIntervalEntity> sourceIntervals = studyIntervalRepository
                .findBySchoolIdAndStudentIdAndStartTimeBetweenOrderByStartTimeAsc(
                        schoolId, sourceStudentId, weekStartDate.atStartOfDay(), weekStartDate.plusDays(7).atStartOfDay()
                );
        Map<Long, List<Long>> subTopicsByInterval = subTopicsByIntervalId(sourceIntervals);

        List<CopyWeekResult> results = new ArrayList<>();
        for (Long targetStudentId : targetStudentIds) {
            try {
                requireStudentInSchool(schoolId, targetStudentId);
                for (StudyIntervalEntity source : sourceIntervals) {
                    StudyIntervalEntity copy = studyIntervalRepository.save(new StudyIntervalEntity(
                            schoolId, targetStudentId, source.getTopicId(), source.getStartTime(), source.getEndTime()
                    ));
                    saveSubTopics(copy.getId(), subTopicsByInterval.getOrDefault(source.getId(), List.of()));
                }
                results.add(new CopyWeekResult(targetStudentId, true, null));
            } catch (StudyPlanException e) {
                results.add(new CopyWeekResult(targetStudentId, false, e.getMessage()));
            }
        }
        return results;
    }

    @Override
    @Transactional
    public StudyIntervalId updateStudyInterval(Long schoolId, Long intervalId, Boolean done, Integer testCount) {
        StudyIntervalEntity interval = studyIntervalRepository.findById(intervalId)
                .filter(i -> i.getSchoolId().equals(schoolId))
                .orElseThrow(() -> new StudyPlanException(new StudyPlanError.StudyIntervalNotFound(intervalId)));
        if (done != null) {
            interval.setDone(done);
        }
        if (testCount != null) {
            interval.setTestCount(testCount);
        }
        StudyIntervalEntity saved = studyIntervalRepository.save(interval);
        return new StudyIntervalId(saved.getId());
    }

    @Override
    public List<RecentTopic> getRecentTopics(Long schoolId, Long studentId) {
        requireStudentInSchool(schoolId, studentId);

        LocalDate today = LocalDate.now();
        // معادل ساده‌ترِ get_start_end_week منبع: نزدیک‌ترین شنبه‌ی قبل (یا
        // امروز اگر خودش شنبه است) تا ۷ روز بعد - همان هفته‌ی جاریِ ایرانی.
        int daysSinceSaturday = (today.getDayOfWeek().getValue() - DayOfWeek.SATURDAY.getValue() + 7) % 7;
        LocalDate startOfWeek = today.minusDays(daysSinceSaturday);

        List<StudyIntervalEntity> intervals = studyIntervalRepository
                .findBySchoolIdAndStudentIdAndStartTimeBetweenOrderByStartTimeAsc(
                        schoolId, studentId, startOfWeek.atStartOfDay(), startOfWeek.plusDays(7).atStartOfDay()
                );
        Map<Long, List<Long>> subTopicsByInterval = subTopicsByIntervalId(intervals);

        Map<Long, Set<Long>> subTopicsByTopic = new LinkedHashMap<>();
        for (StudyIntervalEntity interval : intervals) {
            subTopicsByTopic.computeIfAbsent(interval.getTopicId(), k -> new HashSet<>())
                    .addAll(subTopicsByInterval.getOrDefault(interval.getId(), List.of()));
        }
        return subTopicsByTopic.entrySet().stream()
                .map(e -> new RecentTopic(e.getKey(), List.copyOf(e.getValue())))
                .toList();
    }

    // --- کمکی‌ها ---

    private void saveSubTopics(Long studyIntervalId, List<Long> subTopicIds) {
        for (Long subTopicId : subTopicIds) {
            studyIntervalSubTopicRepository.save(new StudyIntervalSubTopicEntity(studyIntervalId, subTopicId));
        }
    }

    private void deleteIntervalsWithSubTopics(List<StudyIntervalEntity> intervals) {
        List<Long> ids = intervals.stream().map(StudyIntervalEntity::getId).toList();
        if (ids.isEmpty()) {
            return;
        }
        studyIntervalSubTopicRepository.deleteAll(studyIntervalSubTopicRepository.findByStudyIntervalIdIn(ids));
        studyIntervalRepository.deleteAll(intervals);
    }

    private Map<Long, List<Long>> subTopicsByIntervalId(List<StudyIntervalEntity> intervals) {
        List<Long> ids = intervals.stream().map(StudyIntervalEntity::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return studyIntervalSubTopicRepository.findByStudyIntervalIdIn(ids).stream()
                .collect(Collectors.groupingBy(
                        StudyIntervalSubTopicEntity::getStudyIntervalId,
                        Collectors.mapping(StudyIntervalSubTopicEntity::getTopicId, Collectors.toList())
                ));
    }

    private void requireWeekEmpty(Long schoolId, Long studentId, LocalDate weekStartDate) {
        List<StudyIntervalEntity> existing = studyIntervalRepository
                .findBySchoolIdAndStudentIdAndStartTimeBetweenOrderByStartTimeAsc(
                        schoolId, studentId, weekStartDate.atStartOfDay(), weekStartDate.plusDays(7).atStartOfDay()
                );
        if (!existing.isEmpty()) {
            throw new StudyPlanException(new StudyPlanError.WeekNotEmpty(weekStartDate));
        }
    }

    private void requireSaturday(LocalDate date) {
        if (date.getDayOfWeek() != DayOfWeek.SATURDAY) {
            throw new StudyPlanException(new StudyPlanError.NotSaturday(date));
        }
    }

    private void requireStudentInSchool(Long schoolId, Long studentId) {
        academicFacade.findStudentInfo(studentId)
                .filter(info -> info.schoolId().equals(schoolId))
                .orElseThrow(() -> new StudyPlanException(new StudyPlanError.StudentNotFound(studentId)));
    }

    private PlanEntity requirePlanInSchool(Long schoolId, Long planId) {
        PlanEntity plan = planRepository.findById(planId)
                .orElseThrow(() -> new StudyPlanException(new StudyPlanError.PlanNotFound(planId)));
        if (!plan.getSchoolId().equals(schoolId)) {
            throw new StudyPlanException(new StudyPlanError.PlanNotFound(planId));
        }
        return plan;
    }

    private Set<Long> validTopicIds(Long schoolId) {
        return curriculumFacade.listTopics(schoolId).stream()
                .map(t -> t.id().value())
                .collect(Collectors.toSet());
    }

    private void validateCourseWeight(CourseWeight cw, Set<Long> validTopicIds) {
        if (cw.topicId() == null || !validTopicIds.contains(cw.topicId())) {
            throw new StudyPlanException(new StudyPlanError.TopicNotFound(cw.topicId()));
        }
        if (cw.weight() < 0) {
            throw new StudyPlanException(new StudyPlanError.InvalidPlan("وزن درس نمی‌تواند منفی باشد."));
        }
        if (cw.priority() < 1) {
            throw new StudyPlanException(new StudyPlanError.InvalidPlan("اولویت درس باید حداقل ۱ باشد."));
        }
    }

    private IntervalScheduleEntity findOrCreateSchedule(Long schoolId, Long studentId) {
        return intervalScheduleRepository.findBySchoolIdAndStudentId(schoolId, studentId)
                .orElseGet(() -> intervalScheduleRepository.save(
                        new IntervalScheduleEntity(schoolId, studentId, JsonSupport.write(emptySchedule()))
                ));
    }

    private static Map<String, List<DaySlot>> emptySchedule() {
        Map<String, List<DaySlot>> schedule = new LinkedHashMap<>();
        for (String day : WeekDays.ORDER) {
            schedule.put(day, List.of());
        }
        return schedule;
    }

    private ScheduleInfo toScheduleInfo(IntervalScheduleEntity entity) {
        Map<String, List<DaySlot>> defaultSchedule = JsonSupport.read(entity.getDefaultScheduleJson(), SCHEDULE_TYPE);
        Map<String, List<DaySlot>> currentSchedule = entity.getCurrentScheduleJson() == null
                ? null : JsonSupport.read(entity.getCurrentScheduleJson(), SCHEDULE_TYPE);

        // معادلِ property محاسباتیِ schedule در منبع: برنامه‌ی «این هفته» فقط
        // اگر در ۷ روز گذشته ویرایش شده باشد جایگزین پیش‌فرض می‌شود - رجوع به
        // توضیح IntervalScheduleEntity برای دلیلِ ساده‌سازیِ عمدی نسبت به منبع.
        boolean useCurrentSchedule = currentSchedule != null
                && entity.getLastEditedAt() != null
                && entity.getLastEditedAt().isAfter(LocalDateTime.now().minusDays(7));
        Map<String, List<DaySlot>> effectiveSchedule = useCurrentSchedule ? currentSchedule : defaultSchedule;

        return new ScheduleInfo(
                entity.getSchoolId(), entity.getStudentId(), defaultSchedule, currentSchedule,
                effectiveSchedule, entity.getLastEditedAt()
        );
    }

    private List<WeekCourseInfo> toWeekCourseInfos(List<WeekCourseEntity> weekCourses) {
        List<Long> ids = weekCourses.stream().map(WeekCourseEntity::getId).toList();
        Map<Long, List<Long>> subTopicsByWeekCourse = ids.isEmpty() ? Map.of()
                : weekCourseTopicRepository.findByWeekCourseIdIn(ids).stream()
                        .collect(Collectors.groupingBy(
                                WeekCourseTopicEntity::getWeekCourseId,
                                Collectors.mapping(WeekCourseTopicEntity::getTopicId, Collectors.toList())
                        ));
        return weekCourses.stream()
                .map(wc -> new WeekCourseInfo(
                        new WeekCourseId(wc.getId()), wc.getSchoolId(), wc.getPlanId(), wc.getWeekStartDate(),
                        wc.getTopicId(), subTopicsByWeekCourse.getOrDefault(wc.getId(), List.of())
                ))
                .toList();
    }

    private List<StudyIntervalInfo> toStudyIntervalInfos(List<StudyIntervalEntity> intervals) {
        Map<Long, List<Long>> subTopicsByInterval = subTopicsByIntervalId(intervals);
        return intervals.stream()
                .map(i -> toInfo(i, subTopicsByInterval.getOrDefault(i.getId(), List.of())))
                .toList();
    }

    private static PlanInfo toInfo(PlanEntity p) {
        return new PlanInfo(
                new PlanId(p.getId()), p.getSchoolId(), p.getTeacherId(), p.getTitle(),
                JsonSupport.read(p.getCourseInfoJson(), COURSE_INFO_TYPE)
        );
    }

    private static StudyIntervalInfo toInfo(StudyIntervalEntity i, List<Long> subTopicIds) {
        return new StudyIntervalInfo(
                new StudyIntervalId(i.getId()), i.getSchoolId(), i.getStudentId(), i.getTopicId(), subTopicIds,
                i.getStartTime(), i.getEndTime(), i.isDone(), i.getTestCount()
        );
    }
}
