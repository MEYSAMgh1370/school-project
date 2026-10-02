package com.example.roshdeandishe.academic.internal;

import com.example.roshdeandishe.academic.AcademicFacade.CourseId;
import com.example.roshdeandishe.academic.AcademicFacade.CourseInfo;
import com.example.roshdeandishe.academic.TimetableDay;
import com.example.roshdeandishe.academic.TimetableFacade;
import com.example.roshdeandishe.academic.shared.AcademicError;
import com.example.roshdeandishe.academic.shared.AcademicException;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.school.SchoolFacade;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
class TimetableFacadeImpl implements TimetableFacade {

    private static final int MAX_LABEL_LENGTH = 64;
    private static final int MAX_NAME_LENGTH = 128;
    private static final int PERIOD_LOCK_NAMESPACE = 7401;
    private static final int CELL_LOCK_NAMESPACE = 7402;
    private static final String TEACHER_SLOT_INDEX = "timetable_entries_teacher_slot_unique";
    private static final List<String> ACTIVITY_NAME_INDEXES =
            List.of("activities_school_name_unique", "activities_default_name_unique");

    private final TimetablePeriodRepository periodRepository;
    private final TimetableEntryRepository entryRepository;
    private final ActivityRepository activityRepository;
    private final CourseRepository courseRepository;
    private final ClassRepository classRepository;
    private final IamFacade iamFacade;
    private final SchoolFacade schoolFacade;

    TimetableFacadeImpl(
            TimetablePeriodRepository periodRepository,
            TimetableEntryRepository entryRepository,
            ActivityRepository activityRepository,
            CourseRepository courseRepository,
            ClassRepository classRepository,
            IamFacade iamFacade,
            SchoolFacade schoolFacade
    ) {
        this.periodRepository = periodRepository;
        this.entryRepository = entryRepository;
        this.activityRepository = activityRepository;
        this.courseRepository = courseRepository;
        this.classRepository = classRepository;
        this.iamFacade = iamFacade;
        this.schoolFacade = schoolFacade;
    }

    // ---- زنگ‌ها ----

    @Override
    public List<PeriodInfo> listPeriods(Long schoolId, String academicYear) {
        requireKnownYear(academicYear);
        return periodRepository.findBySchoolIdAndAcademicYearOrderByStartTimeAscPositionAsc(schoolId, academicYear)
                .stream().map(TimetableFacadeImpl::toPeriodInfo).toList();
    }

    @Override
    @Transactional
    public PeriodInfo createPeriod(Long schoolId, String academicYear, String label, LocalTime startTime,
                                   LocalTime endTime) {
        requireKnownYear(academicYear);
        String cleanLabel = requireValidPeriod(label, startTime, endTime);
        lockPeriods(schoolId, academicYear);
        requireNoOverlap(schoolId, academicYear, startTime, endTime, null);
        int position = periodRepository.maxPosition(schoolId, academicYear) + 1;
        return toPeriodInfo(periodRepository.save(
                new TimetablePeriodEntity(schoolId, academicYear, position, cleanLabel, startTime, endTime)));
    }

    @Override
    @Transactional
    public PeriodInfo updatePeriod(Long schoolId, Long periodId, String label, LocalTime startTime,
                                   LocalTime endTime) {
        TimetablePeriodEntity period = requirePeriodInSchool(schoolId, periodId);
        String cleanLabel = requireValidPeriod(label, startTime, endTime);
        lockPeriods(schoolId, period.getAcademicYear());
        requireNoOverlap(schoolId, period.getAcademicYear(), startTime, endTime, periodId);
        period.setLabel(cleanLabel);
        period.setStartTime(startTime);
        period.setEndTime(endTime);
        return toPeriodInfo(periodRepository.save(period));
    }

    @Override
    @Transactional
    public void deletePeriod(Long schoolId, Long periodId) {
        TimetablePeriodEntity period = requirePeriodInSchool(schoolId, periodId);
        if (entryRepository.existsByPeriodId(periodId)) {
            throw new AcademicException(new AcademicError.PeriodHasEntries(periodId));
        }
        periodRepository.delete(period);
    }

    // ---- فعالیت‌ها ----

    @Override
    public List<ActivityInfo> listActivities(Long schoolId) {
        return activityRepository.findVisibleToSchool(schoolId).stream()
                .map(TimetableFacadeImpl::toActivityInfo).toList();
    }

    @Override
    @Transactional
    public ActivityInfo createActivity(Long schoolId, String name) {
        String clean = requireActivityName(name);
        requireNameFree(schoolId, clean);
        return toActivityInfo(saveActivity(new ActivityEntity(schoolId, clean), clean));
    }

    @Override
    @Transactional
    public ActivityInfo renameActivity(Long schoolId, Long activityId, String name) {
        ActivityEntity activity = requireOwnActivity(schoolId, activityId);
        String clean = requireActivityName(name);
        if (!clean.equals(activity.getName())) {
            requireNameFree(schoolId, clean);
        }
        activity.setName(clean);
        return toActivityInfo(saveActivity(activity, clean));
    }

    @Override
    @Transactional
    public void deleteActivity(Long schoolId, Long activityId) {
        ActivityEntity activity = requireOwnActivity(schoolId, activityId);
        if (entryRepository.existsByActivityId(activityId)) {
            throw new AcademicException(new AcademicError.ActivityInUse(activityId));
        }
        activityRepository.delete(activity);
    }

    // ---- خانه‌ها ----

    @Override
    public List<CourseInfo> listCoursesForTimetable(Long schoolId, Long classId) {
        ClassEntity classEntity = requireClassInSchool(schoolId, classId);
        return courseRepository.findByGradeLevelOrderByNameAsc(classEntity.getGradeLevel()).stream()
                .map(c -> new CourseInfo(new CourseId(c.getId()), c.getGradeLevel(), c.getName()))
                .toList();
    }

    @Override
    public List<EntryInfo> listEntriesForClass(Long schoolId, Long classId, String academicYear) {
        requireClassInSchool(schoolId, classId);
        requireKnownYear(academicYear);
        return entriesOf(schoolId, academicYear, List.of(classId));
    }

    @Override
    @Transactional
    public EntryInfo setEntry(Long schoolId, Long classId, Long periodId, TimetableDay weekday,
                              Long courseId, Long activityId, Long teacherUserId) {
        ClassEntity classEntity = requireClassInSchool(schoolId, classId);
        requirePeriodInSchool(schoolId, periodId);
        if (weekday == null) {
            throw new AcademicException(new AcademicError.InvalidTimetableEntry("روز هفته الزامی است"));
        }
        if ((courseId == null) == (activityId == null)) {
            throw new AcademicException(new AcademicError.InvalidTimetableEntry(
                    "دقیقاً یکی از درس یا فعالیت باید انتخاب شود"));
        }
        if (courseId != null) {
            CourseEntity course = courseRepository.findById(courseId)
                    .orElseThrow(() -> new AcademicException(new AcademicError.CourseNotFound(courseId)));
            if (course.getGradeLevel() != classEntity.getGradeLevel()) {
                throw new AcademicException(new AcademicError.CourseNotForClass(courseId, classId));
            }
        } else {
            // پیش‌فرضِ پلتفرم یا فعالیتِ همین مدرسه؛ فعالیتِ مدرسه‌ی دیگر «یافت نشد»
            activityRepository.findById(activityId)
                    .filter(a -> a.getSchoolId() == null || a.getSchoolId().equals(schoolId))
                    .orElseThrow(() -> new AcademicException(new AcademicError.ActivityNotFound(activityId)));
        }
        // خانه را پیش از خواندنِ ردیفِ موجود قفل می‌کنیم تا نوشتنِ هم‌زمانِ یک خانه سریالی شود
        entryRepository.lockCell(CELL_LOCK_NAMESPACE, Objects.hash(classId, weekday.name(), periodId));
        TimetableEntryEntity existing = entryRepository
                .findByClassIdAndWeekdayAndPeriodId(classId, weekday, periodId).orElse(null);
        if (teacherUserId != null) {
            requireActiveTeacher(schoolId, teacherUserId);
            entryRepository.findByTeacherUserIdAndWeekdayAndPeriodId(teacherUserId, weekday, periodId)
                    .filter(other -> existing == null || !other.getId().equals(existing.getId()))
                    .ifPresent(other -> {
                        throw new AcademicException(new AcademicError.TeacherDoubleBooked(
                                teacherUserId, weekday.name(), periodId));
                    });
        }

        TimetableEntryEntity entry = existing != null
                ? existing : new TimetableEntryEntity(schoolId, classId, periodId, weekday);
        entry.setCourseId(courseId);
        entry.setActivityId(activityId);
        entry.setTeacherUserId(teacherUserId);
        TimetableEntryEntity saved;
        try {
            saved = entryRepository.saveAndFlush(entry);
        } catch (DataIntegrityViolationException e) {
            // دو درخواستِ هم‌زمان از بررسیِ برنامه رد شده‌اند؛ ایندکسِ یکتا آخرین سد است
            if (teacherUserId != null && mentionsTeacherSlotIndex(e)) {
                throw new AcademicException(new AcademicError.TeacherDoubleBooked(
                        teacherUserId, weekday.name(), periodId));
            }
            throw e;
        }
        return toEntryInfos(List.of(saved)).get(0);
    }

    @Override
    @Transactional
    public void clearEntry(Long schoolId, Long classId, Long periodId, TimetableDay weekday) {
        requireClassInSchool(schoolId, classId);
        requirePeriodInSchool(schoolId, periodId);
        if (weekday == null) {
            throw new AcademicException(new AcademicError.InvalidTimetableEntry("روز هفته الزامی است"));
        }
        entryRepository.findByClassIdAndWeekdayAndPeriodId(classId, weekday, periodId)
                .ifPresent(entryRepository::delete);
    }

    // ---- خواندن برای خودسرویس ----

    @Override
    public List<EntryInfo> listEntriesForClasses(Long schoolId, String academicYear, Collection<Long> classIds) {
        if (classIds.isEmpty()) {
            return List.of();
        }
        return entriesOf(schoolId, academicYear, classIds);
    }

    @Override
    public List<EntryInfo> listEntriesForTeacher(Long schoolId, String academicYear, Long teacherUserId) {
        List<Long> periodIds = periodIds(schoolId, academicYear);
        if (periodIds.isEmpty()) {
            return List.of();
        }
        return toEntryInfos(entryRepository.findBySchoolIdAndTeacherUserIdAndPeriodIdIn(
                schoolId, teacherUserId, periodIds));
    }

    // ---- کمکی‌ها ----

    private List<EntryInfo> entriesOf(Long schoolId, String academicYear, Collection<Long> classIds) {
        List<Long> periodIds = periodIds(schoolId, academicYear);
        if (periodIds.isEmpty()) {
            return List.of();
        }
        // کلاس‌ها را مدرسه تعیین می‌کند: هر شناسه‌ی کلاسِ مدرسه‌ی دیگر دور ریخته می‌شود
        Set<Long> ownClassIds = classRepository.findAllById(classIds).stream()
                .filter(c -> c.getSchoolId().equals(schoolId))
                .map(ClassEntity::getId)
                .collect(Collectors.toSet());
        if (ownClassIds.isEmpty()) {
            return List.of();
        }
        return toEntryInfos(entryRepository.findByClassIdInAndPeriodIdIn(ownClassIds, periodIds));
    }

    private List<Long> periodIds(Long schoolId, String academicYear) {
        return periodRepository.findBySchoolIdAndAcademicYearOrderByStartTimeAscPositionAsc(schoolId, academicYear)
                .stream().map(TimetablePeriodEntity::getId).toList();
    }

    // نام‌های درس/فعالیت/کلاس با یک query برای هر نوع گرفته می‌شود، نه یکی به‌ازای هر خانه
    private List<EntryInfo> toEntryInfos(List<TimetableEntryEntity> entries) {
        Map<Long, String> courseNames = new HashMap<>();
        Map<Long, String> activityNames = new HashMap<>();
        Map<Long, String> classNames = new HashMap<>();
        List<Long> courseIds = entries.stream().map(TimetableEntryEntity::getCourseId)
                .filter(Objects::nonNull).distinct().toList();
        List<Long> activityIds = entries.stream().map(TimetableEntryEntity::getActivityId)
                .filter(Objects::nonNull).distinct().toList();
        List<Long> classIds = entries.stream().map(TimetableEntryEntity::getClassId).distinct().toList();
        courseRepository.findAllById(courseIds).forEach(c -> courseNames.put(c.getId(), c.getName()));
        activityRepository.findAllById(activityIds).forEach(a -> activityNames.put(a.getId(), a.getName()));
        classRepository.findAllById(classIds).forEach(c -> classNames.put(c.getId(), c.getName()));
        return entries.stream()
                .map(e -> new EntryInfo(e.getId(), e.getClassId(), classNames.get(e.getClassId()), e.getPeriodId(),
                        e.getWeekday(), e.getCourseId(),
                        e.getCourseId() == null ? null : courseNames.get(e.getCourseId()),
                        e.getActivityId(),
                        e.getActivityId() == null ? null : activityNames.get(e.getActivityId()),
                        e.getTeacherUserId()))
                .toList();
    }

    private void requireKnownYear(String academicYear) {
        boolean known = academicYear != null && schoolFacade.listAcademicYears().stream()
                .anyMatch(y -> y.code().equals(academicYear));
        if (!known) {
            throw new AcademicException(new AcademicError.InvalidAcademicYear(academicYear));
        }
    }

    private static String requireValidPeriod(String label, LocalTime startTime, LocalTime endTime) {
        String clean = label == null ? "" : label.strip();
        if (clean.isEmpty() || clean.length() > MAX_LABEL_LENGTH) {
            throw new AcademicException(new AcademicError.InvalidPeriod(
                    "برچسب زنگ الزامی است و حداکثر " + MAX_LABEL_LENGTH + " نویسه"));
        }
        if (startTime == null || endTime == null) {
            throw new AcademicException(new AcademicError.InvalidPeriod("ساعت شروع و پایان الزامی است"));
        }
        if (!endTime.isAfter(startTime)) {
            throw new AcademicException(new AcademicError.InvalidPeriod("ساعت پایان باید بعد از شروع باشد"));
        }
        return clean;
    }

    // بعد از گرفتنِ قفل، زنگ‌ها دوباره خوانده می‌شوند (requireNoOverlap) - پس مقدارِ تازه‌ی
    // تراکنشِ قبلی را می‌بیند. تراکنشِ ویرایش، زنگ را پیش از قفل خوانده است، ولی فقط برچسب/ساعت
    // را عوض می‌کند و سال تغییر نمی‌کند.
    private void lockPeriods(Long schoolId, String academicYear) {
        periodRepository.lockSchoolYear(PERIOD_LOCK_NAMESPACE, Objects.hash(schoolId, academicYear));
    }

    private void requireNoOverlap(Long schoolId, String academicYear, LocalTime start, LocalTime end, Long selfId) {
        periodRepository.findBySchoolIdAndAcademicYearOrderByStartTimeAscPositionAsc(schoolId, academicYear).stream()
                .filter(p -> !p.getId().equals(selfId))
                .filter(p -> p.getStartTime().isBefore(end) && p.getEndTime().isAfter(start))
                .findFirst()
                .ifPresent(p -> {
                    throw new AcademicException(new AcademicError.PeriodOverlaps(p.getId()));
                });
    }

    private TimetablePeriodEntity requirePeriodInSchool(Long schoolId, Long periodId) {
        return periodRepository.findById(periodId)
                .filter(p -> p.getSchoolId().equals(schoolId))
                .orElseThrow(() -> new AcademicException(new AcademicError.PeriodNotFound(periodId)));
    }

    private ClassEntity requireClassInSchool(Long schoolId, Long classId) {
        return classRepository.findById(classId)
                .filter(c -> c.getSchoolId().equals(schoolId))
                .orElseThrow(() -> new AcademicException(new AcademicError.ClassNotFound(classId)));
    }

    private static String requireActivityName(String name) {
        String clean = name == null ? "" : name.strip();
        if (clean.isEmpty() || clean.length() > MAX_NAME_LENGTH) {
            throw new AcademicException(new AcademicError.InvalidTimetableEntry(
                    "نام فعالیت الزامی است و حداکثر " + MAX_NAME_LENGTH + " نویسه"));
        }
        return clean;
    }

    // دو ساختِ هم‌زمان با یک نام از requireNameFree رد می‌شوند؛ ایندکس‌های جزئیِ نام آخرین سدند.
    // flush تا خطا همین‌جا (نه هنگام commit) پدید آید و به همان 409 دامنه ترجمه شود.
    private ActivityEntity saveActivity(ActivityEntity activity, String name) {
        try {
            return activityRepository.saveAndFlush(activity);
        } catch (DataIntegrityViolationException e) {
            if (mentionsConstraint(e, ACTIVITY_NAME_INDEXES)) {
                throw new AcademicException(new AcademicError.ActivityNameTaken(name));
            }
            throw e;
        }
    }

    // نام باید هم در دامنه‌ی مدرسه و هم میان پیش‌فرض‌های پلتفرم یکتا باشد، وگرنه
    // انتخابگر دو گزینه‌ی هم‌نام نشان می‌داد
    private void requireNameFree(Long schoolId, String name) {
        if (activityRepository.existsBySchoolIdAndName(schoolId, name)
                || activityRepository.existsBySchoolIdIsNullAndName(name)) {
            throw new AcademicException(new AcademicError.ActivityNameTaken(name));
        }
    }

    // پیش‌فرضِ پلتفرم برای مدرسه «قابل مشاهده ولی غیرقابل تغییر» است؛ فعالیتِ مدرسه‌ی دیگر یافت‌نشدنی
    private ActivityEntity requireOwnActivity(Long schoolId, Long activityId) {
        ActivityEntity activity = activityRepository.findById(activityId)
                .filter(a -> a.getSchoolId() == null || a.getSchoolId().equals(schoolId))
                .orElseThrow(() -> new AcademicException(new AcademicError.ActivityNotFound(activityId)));
        if (activity.getSchoolId() == null) {
            throw new AcademicException(new AcademicError.ActivityNotEditable(activityId));
        }
        return activity;
    }

    // همان بررسیِ academic.requireActiveParentMembership: findMembershipsByUser چون
    // یک کاربر ممکن است هم‌زمان چند نقش در یک مدرسه داشته باشد
    private void requireActiveTeacher(Long schoolId, Long teacherUserId) {
        boolean eligible = iamFacade.findMembershipsByUser(new IamFacade.UserId(teacherUserId)).stream()
                .anyMatch(m -> m.schoolId().equals(schoolId) && "TEACHER".equals(m.roleCode()) && m.active());
        if (!eligible) {
            throw new AcademicException(new AcademicError.TeacherNotEligible(teacherUserId, schoolId));
        }
    }

    private static boolean mentionsTeacherSlotIndex(Throwable e) {
        return mentionsConstraint(e, List.of(TEACHER_SLOT_INDEX));
    }

    private static boolean mentionsConstraint(Throwable e, List<String> constraintNames) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t.getMessage() != null && constraintNames.stream().anyMatch(t.getMessage()::contains)) {
                return true;
            }
        }
        return false;
    }

    private static PeriodInfo toPeriodInfo(TimetablePeriodEntity p) {
        return new PeriodInfo(p.getId(), p.getSchoolId(), p.getAcademicYear(), p.getPosition(), p.getLabel(),
                p.getStartTime(), p.getEndTime());
    }

    private static ActivityInfo toActivityInfo(ActivityEntity a) {
        return new ActivityInfo(a.getId(), a.getName(), a.getSchoolId() == null);
    }
}
