package com.example.roshdeandishe.exam.internal;

import com.example.roshdeandishe.academic.AcademicFacade;
import com.example.roshdeandishe.curriculum.CurriculumFacade;
import com.example.roshdeandishe.exam.ExamFacade;
import com.example.roshdeandishe.exam.ExamType;
import com.example.roshdeandishe.exam.shared.ExamError;
import com.example.roshdeandishe.exam.shared.ExamException;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.shared.exception.IamException;
import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.WeekStart;
import com.example.roshdeandishe.studyplan.StudyPlanFacade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
class ExamFacadeImpl implements ExamFacade {

    private static final int WEEKLY_QUESTIONS_PER_TOPIC = 5;

    private final ExamRepository examRepository;
    private final SectionRepository sectionRepository;
    private final SectionQuestionRepository sectionQuestionRepository;
    private final ExamAssignmentRepository examAssignmentRepository;
    private final AttemptRepository attemptRepository;
    private final SubmitRepository submitRepository;
    private final ExamTokenService tokenService;
    private final CurriculumFacade curriculumFacade;
    private final AcademicFacade academicFacade;
    private final StudyPlanFacade studyPlanFacade;
    private final IamFacade iamFacade;
    private final SchoolFacade schoolFacade;

    ExamFacadeImpl(ExamRepository examRepository,
                   SectionRepository sectionRepository,
                   SectionQuestionRepository sectionQuestionRepository,
                   ExamAssignmentRepository examAssignmentRepository,
                   AttemptRepository attemptRepository,
                   SubmitRepository submitRepository,
                   ExamTokenService tokenService,
                   CurriculumFacade curriculumFacade,
                   AcademicFacade academicFacade,
                   StudyPlanFacade studyPlanFacade,
                   IamFacade iamFacade,
                   SchoolFacade schoolFacade) {
        this.examRepository = examRepository;
        this.sectionRepository = sectionRepository;
        this.sectionQuestionRepository = sectionQuestionRepository;
        this.examAssignmentRepository = examAssignmentRepository;
        this.attemptRepository = attemptRepository;
        this.submitRepository = submitRepository;
        this.tokenService = tokenService;
        this.curriculumFacade = curriculumFacade;
        this.academicFacade = academicFacade;
        this.studyPlanFacade = studyPlanFacade;
        this.iamFacade = iamFacade;
        this.schoolFacade = schoolFacade;
    }

    // ------------------------------------------------------------ authoring

    @Override
    @Transactional
    public ExamId createCollectiveExam(Long schoolId, String title, LocalDateTime start, LocalDateTime end,
                                       int durationMinutes) {
        if (title == null || title.isBlank()) {
            throw new ExamException(new ExamError.InvalidExam("عنوان آزمون نمی‌تواند خالی باشد."));
        }
        if (start == null || end == null) {
            throw new ExamException(new ExamError.InvalidExam("زمان شروع و پایان الزامی است."));
        }
        if (!end.isAfter(start)) {
            throw new ExamException(new ExamError.InvalidExam("زمان پایان باید بعد از زمان شروع باشد."));
        }
        if (durationMinutes < 1) {
            throw new ExamException(new ExamError.InvalidExam("مدت آزمون باید دست‌کم ۱ دقیقه باشد."));
        }
        if (java.time.Duration.between(start, end).toMinutes() < durationMinutes) {
            throw new ExamException(new ExamError.InvalidExam(
                    "بازه‌ی شروع تا پایان باید حداقل به اندازه‌ی مدت آزمون باشد."));
        }
        ExamEntity exam = examRepository.save(new ExamEntity(schoolId, ExamType.COLLECTIVE, title.trim(),
                currentActorOrNull(), null, start, end, durationMinutes, null));
        return new ExamId(exam.getId());
    }

    @Override
    public List<ExamInfo> listCollectiveExams(Long schoolId) {
        return examRepository.findBySchoolIdAndExamTypeOrderByIdDesc(schoolId, ExamType.COLLECTIVE).stream()
                .map(this::toExamInfo)
                .toList();
    }

    @Override
    public ExamInfo getExam(Long schoolId, Long examId) {
        return toExamInfo(requireExamInSchool(schoolId, examId));
    }

    @Override
    @Transactional
    public void assignStudents(Long schoolId, Long examId, List<Long> studentIds) {
        ExamEntity exam = requireExamInSchool(schoolId, examId);
        // سالِ تحصیلی از تاریخِ خودِ آزمون می‌آید (startAt ستونِ not null است)
        LocalDate examDate = exam.getStartAt().toLocalDate();
        for (Long studentId : studentIds == null ? List.<Long>of() : studentIds) {
            requireStudentInSchool(schoolId, studentId);
            academicFacade.requireEnrolledOn(schoolId, studentId, examDate);
            if (!examAssignmentRepository.existsByExamIdAndStudentId(examId, studentId)) {
                examAssignmentRepository.save(new ExamAssignmentEntity(examId, studentId));
            }
        }
    }

    @Override
    public List<Long> listAssignedStudents(Long schoolId, Long examId) {
        requireExamInSchool(schoolId, examId);
        return examAssignmentRepository.findByExamIdOrderByIdAsc(examId).stream()
                .map(ExamAssignmentEntity::getStudentId)
                .toList();
    }

    @Override
    @Transactional
    public SectionId createSection(Long schoolId, Long examId, Long topicId, String name, List<Long> questionIds) {
        requireExamInSchool(schoolId, examId);
        requireTopicInSchool(schoolId, topicId);
        requireNoDuplicateSection(examId, topicId);
        if (questionIds == null || questionIds.isEmpty()) {
            throw new ExamException(new ExamError.InvalidExam("بخش باید دست‌کم یک سؤال داشته باشد."));
        }
        Set<Long> schoolQuestionIds = questionMap(schoolId).keySet();
        for (Long questionId : questionIds) {
            if (questionId == null || !schoolQuestionIds.contains(questionId)) {
                throw new ExamException(new ExamError.QuestionNotFound(questionId));
            }
        }
        SectionEntity section = sectionRepository.save(new SectionEntity(examId, topicId, blankToNull(name)));
        for (Long questionId : new LinkedHashSet<>(questionIds)) {
            sectionQuestionRepository.save(new SectionQuestionEntity(section.getId(), questionId));
        }
        return new SectionId(section.getId());
    }

    @Override
    @Transactional
    public SectionId createSectionAuto(Long schoolId, Long examId, Long topicId, String name, int count, boolean lazy) {
        requireExamInSchool(schoolId, examId);
        requireTopicInSchool(schoolId, topicId);
        requireNoDuplicateSection(examId, topicId);
        if (count < 1) {
            throw new ExamException(new ExamError.InvalidExam("تعداد سؤال باید دست‌کم ۱ باشد."));
        }
        List<Long> questionIds = pickRandomQuestionIds(schoolId, topicId, count, lazy);
        SectionEntity section = sectionRepository.save(new SectionEntity(examId, topicId, blankToNull(name)));
        for (Long questionId : questionIds) {
            sectionQuestionRepository.save(new SectionQuestionEntity(section.getId(), questionId));
        }
        return new SectionId(section.getId());
    }

    @Override
    public List<SectionInfo> listSections(Long schoolId, Long examId) {
        ExamEntity exam = requireExamInSchool(schoolId, examId);
        return toSectionInfos(exam.getId());
    }

    @Override
    @Transactional
    public ExamId generateWeeklyExam(Long schoolId, Long studentId, LocalDate weekStart) {
        requireStudentInSchool(schoolId, studentId);
        requireWeekStart(schoolId, weekStart);

        Optional<ExamEntity> existing = examRepository
                .findByStudentIdAndExamTypeAndWeekStart(studentId, ExamType.WEEKLY, weekStart);
        if (existing.isPresent()) {
            ExamEntity existingExam = existing.get();
            if (attemptRepository.findByExamIdAndStudentId(existingExam.getId(), studentId).isPresent()) {
                throw new ExamException(new ExamError.InvalidExam(
                        "این آزمون قبلاً شروع شده و دیگر قابل بازتولید نیست."));
            }
            deleteExamCascade(existingExam);
        }

        List<StudyPlanFacade.RecentTopic> recentTopics = studyPlanFacade.getRecentTopics(studentId);
        LocalDateTime start = weekStart.atStartOfDay();
        LocalDateTime end = weekStart.plusDays(7).atStartOfDay();
        ExamEntity exam = examRepository.save(new ExamEntity(
                schoolId, ExamType.WEEKLY, null, null, studentId, start, end, 60, weekStart));

        for (StudyPlanFacade.RecentTopic recentTopic : recentTopics) {
            SectionEntity section = sectionRepository.save(new SectionEntity(exam.getId(), recentTopic.topicId(), null));
            List<Long> pickFrom = recentTopic.subTopicIds().isEmpty()
                    ? List.of(recentTopic.topicId())
                    : recentTopic.subTopicIds();
            LinkedHashSet<Long> chosen = new LinkedHashSet<>();
            for (Long pickTopicId : pickFrom) {
                chosen.addAll(pickRandomQuestionIds(schoolId, pickTopicId, WEEKLY_QUESTIONS_PER_TOPIC, true));
            }
            for (Long questionId : chosen) {
                sectionQuestionRepository.save(new SectionQuestionEntity(section.getId(), questionId));
            }
        }
        return new ExamId(exam.getId());
    }

    // ------------------------------------------------------- student "my"

    @Override
    public List<ExamInfo> listExamsForStudent(Long schoolId, Long studentId) {
        List<ExamEntity> weekly = examRepository
                .findByStudentIdAndExamTypeOrderByWeekStartDesc(studentId, ExamType.WEEKLY).stream()
                .filter(e -> e.getSchoolId().equals(schoolId))
                .toList();
        List<Long> assignedExamIds = examAssignmentRepository.findByStudentIdOrderByIdAsc(studentId).stream()
                .map(ExamAssignmentEntity::getExamId)
                .toList();
        List<ExamEntity> collective = assignedExamIds.isEmpty()
                ? List.of()
                : examRepository.findAllById(assignedExamIds).stream()
                        .filter(e -> e.getSchoolId().equals(schoolId))
                        .toList();
        List<ExamEntity> all = new ArrayList<>(weekly);
        all.addAll(collective);
        return all.stream().map(this::toExamInfo).toList();
    }

    @Override
    @Transactional
    public AttemptResult startAttempt(Long schoolId, Long examId, Long studentId) {
        ExamEntity exam = requireExamInSchool(schoolId, examId);
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(exam.getStartAt())) {
            throw new ExamException(new ExamError.CannotAttempt("آزمون هنوز شروع نشده است."));
        }
        if (now.isAfter(exam.getEndAt())) {
            throw new ExamException(new ExamError.CannotAttempt("آزمون به پایان رسیده است."));
        }
        if (!canAttempt(exam, studentId)) {
            throw new ExamException(new ExamError.CannotAttempt("شما مجاز به شرکت در این آزمون نیستید."));
        }

        Instant nowInstant = Instant.now();
        Optional<AttemptEntity> existing = attemptRepository.findByExamIdAndStudentId(examId, studentId);
        if (existing.isPresent()) {
            AttemptEntity attempt = existing.get();
            if (nowInstant.isBefore(attempt.getExpireAt())) {
                String token = tokenService.generateToken(examId, studentId, attempt.getExpireAt());
                return new AttemptResult(token, attempt.getExpireAt());
            }
            throw new ExamException(new ExamError.AttemptExpired(examId, studentId));
        }

        Instant durationEnd = nowInstant.plus(exam.getDurationMinutes(), ChronoUnit.MINUTES);
        Instant examEndInstant = toInstant(exam.getEndAt());
        Instant expireAt = durationEnd.isBefore(examEndInstant) ? durationEnd : examEndInstant;
        attemptRepository.save(new AttemptEntity(examId, studentId, nowInstant, expireAt));
        String token = tokenService.generateToken(examId, studentId, expireAt);
        return new AttemptResult(token, expireAt);
    }

    // -------------------------------------------------------- exam-take

    @Override
    public ExamTakeInfo getExamForToken(String token) {
        ExamTokenService.TokenData data = requireToken(token);
        ExamEntity exam = requireExamForToken(data);
        return new ExamTakeInfo(new ExamId(exam.getId()), exam.getTitle(), exam.getStartAt(), exam.getEndAt(),
                exam.getDurationMinutes(), toSectionInfos(exam.getId()));
    }

    @Override
    public List<TakeQuestionInfo> getSectionQuestions(String token, Long sectionId) {
        ExamTokenService.TokenData data = requireToken(token);
        ExamEntity exam = requireExamForToken(data);
        SectionEntity section = requireSectionInExam(exam.getId(), sectionId);
        List<SectionQuestionEntity> sectionQuestions = sectionQuestionRepository
                .findBySectionIdOrderByIdAsc(section.getId());
        if (sectionQuestions.isEmpty()) {
            return List.of();
        }
        Map<Long, CurriculumFacade.QuestionInfo> questionsById = questionMap(exam.getSchoolId());
        List<Long> sqIds = sectionQuestions.stream().map(SectionQuestionEntity::getId).toList();
        Map<Long, Integer> choiceBySqId = submitRepository
                .findBySectionQuestionIdInAndStudentId(sqIds, data.studentId()).stream()
                .collect(Collectors.toMap(SubmitEntity::getSectionQuestionId, SubmitEntity::getChoice));

        return sectionQuestions.stream().map(sq -> {
            CurriculumFacade.QuestionInfo q = questionsById.get(sq.getQuestionId());
            Integer selected = choiceBySqId.get(sq.getId());
            return new TakeQuestionInfo(sq.getQuestionId(), q.text(), q.choice1(), q.choice2(), q.choice3(),
                    q.choice4(), selected);
        }).toList();
    }

    @Override
    @Transactional
    public void submitAnswer(String token, Long sectionId, Long questionId, Integer choice) {
        ExamTokenService.TokenData data = requireToken(token);
        ExamEntity exam = requireExamForToken(data);
        SectionEntity section = requireSectionInExam(exam.getId(), sectionId);
        SectionQuestionEntity sectionQuestion = sectionQuestionRepository
                .findBySectionIdAndQuestionId(section.getId(), questionId)
                .orElseThrow(() -> new ExamException(new ExamError.QuestionNotFound(questionId)));

        if (choice == null) {
            submitRepository.deleteBySectionQuestionIdAndStudentId(sectionQuestion.getId(), data.studentId());
            return;
        }
        if (choice < 1 || choice > 4) {
            throw new ExamException(new ExamError.InvalidSubmission("پاسخ باید بین ۱ تا ۴ باشد."));
        }
        SubmitEntity submit = submitRepository
                .findBySectionQuestionIdAndStudentId(sectionQuestion.getId(), data.studentId())
                .orElseGet(() -> new SubmitEntity(sectionQuestion.getId(), data.studentId(), choice));
        submit.setChoice(choice);
        submitRepository.save(submit);
    }

    // ------------------------------------------------------------- results

    @Override
    public ExamResultSummary getMyResult(Long schoolId, Long examId, Long studentId) {
        return computeResult(schoolId, examId, studentId);
    }

    @Override
    public List<AttemptedStudentInfo> listAttemptedStudents(Long schoolId, Long examId) {
        requireExamInSchool(schoolId, examId);
        return attemptRepository.findByExamIdOrderByStartedAtDesc(examId).stream()
                .map(a -> new AttemptedStudentInfo(a.getStudentId(), a.getStartedAt()))
                .toList();
    }

    @Override
    public ExamResultSummary getStudentResult(Long schoolId, Long examId, Long studentId) {
        return computeResult(schoolId, examId, studentId);
    }

    private ExamResultSummary computeResult(Long schoolId, Long examId, Long studentId) {
        ExamEntity exam = requireExamInSchool(schoolId, examId);
        if (!LocalDateTime.now().isAfter(exam.getEndAt())) {
            throw new ExamException(new ExamError.ExamNotEnded(examId));
        }
        attemptRepository.findByExamIdAndStudentId(examId, studentId)
                .orElseThrow(() -> new ExamException(new ExamError.NoAttempt(examId, studentId)));

        List<SectionEntity> sections = sectionRepository.findByExamIdOrderByIdAsc(examId);
        Map<Long, CurriculumFacade.QuestionInfo> questionsById = questionMap(schoolId);
        List<SectionResult> results = new ArrayList<>();
        for (SectionEntity section : sections) {
            List<SectionQuestionEntity> sqs = sectionQuestionRepository.findBySectionIdOrderByIdAsc(section.getId());
            int questionCount = sqs.size();
            int correct = 0;
            int incorrect = 0;
            int unanswered = 0;
            if (!sqs.isEmpty()) {
                List<Long> sqIds = sqs.stream().map(SectionQuestionEntity::getId).toList();
                Map<Long, Integer> choiceBySqId = submitRepository
                        .findBySectionQuestionIdInAndStudentId(sqIds, studentId).stream()
                        .collect(Collectors.toMap(SubmitEntity::getSectionQuestionId, SubmitEntity::getChoice));
                for (SectionQuestionEntity sq : sqs) {
                    Integer choice = choiceBySqId.get(sq.getId());
                    if (choice == null) {
                        unanswered++;
                    } else {
                        CurriculumFacade.QuestionInfo q = questionsById.get(sq.getQuestionId());
                        if (q != null && choice == q.correctChoice()) {
                            correct++;
                        } else {
                            incorrect++;
                        }
                    }
                }
            }
            double score = questionCount == 0 ? 0.0 : (correct * 100.0 / questionCount);
            results.add(new SectionResult(new SectionId(section.getId()), section.getName(), section.getTopicId(),
                    questionCount, correct, incorrect, unanswered, score));
        }
        double overall = results.isEmpty() ? 0.0
                : results.stream().mapToDouble(SectionResult::score).average().orElse(0.0);
        return new ExamResultSummary(new ExamId(examId), studentId, results, overall);
    }

    // ---------------------------------------------------------------- helpers

    private ExamInfo toExamInfo(ExamEntity exam) {
        return new ExamInfo(new ExamId(exam.getId()), exam.getSchoolId(), exam.getExamType(), exam.getTitle(),
                exam.getTeacherId(), exam.getStudentId(), exam.getStartAt(), exam.getEndAt(),
                exam.getDurationMinutes(), exam.getWeekStart(), toSectionInfos(exam.getId()));
    }

    private List<SectionInfo> toSectionInfos(Long examId) {
        List<SectionEntity> sections = sectionRepository.findByExamIdOrderByIdAsc(examId);
        if (sections.isEmpty()) {
            return List.of();
        }
        List<Long> sectionIds = sections.stream().map(SectionEntity::getId).toList();
        Map<Long, Long> countBySection = sectionQuestionRepository.findBySectionIdInOrderByIdAsc(sectionIds).stream()
                .collect(Collectors.groupingBy(SectionQuestionEntity::getSectionId, Collectors.counting()));
        return sections.stream()
                .map(s -> new SectionInfo(new SectionId(s.getId()), new ExamId(s.getExamId()), s.getTopicId(),
                        s.getName(), countBySection.getOrDefault(s.getId(), 0L).intValue()))
                .toList();
    }

    private void deleteExamCascade(ExamEntity exam) {
        List<SectionEntity> sections = sectionRepository.findByExamIdOrderByIdAsc(exam.getId());
        if (!sections.isEmpty()) {
            List<Long> sectionIds = sections.stream().map(SectionEntity::getId).toList();
            sectionQuestionRepository.deleteAll(sectionQuestionRepository.findBySectionIdInOrderByIdAsc(sectionIds));
            sectionRepository.deleteAll(sections);
        }
        examRepository.delete(exam);
    }

    private List<Long> pickRandomQuestionIds(Long schoolId, Long topicId, int count, boolean lazy) {
        List<CurriculumFacade.QuestionInfo> candidates = new ArrayList<>(
                curriculumFacade.listQuestions(schoolId, new CurriculumFacade.QuestionFilter(topicId, null)));
        Collections.shuffle(candidates);
        if (!lazy && candidates.size() < count) {
            throw new ExamException(new ExamError.InsufficientQuestions(topicId, count, candidates.size()));
        }
        return candidates.stream().limit(count).map(q -> q.id().value()).toList();
    }

    private boolean canAttempt(ExamEntity exam, Long studentId) {
        if (exam.getExamType() == ExamType.WEEKLY) {
            return studentId.equals(exam.getStudentId());
        }
        return examAssignmentRepository.existsByExamIdAndStudentId(exam.getId(), studentId);
    }

    private ExamEntity requireExamInSchool(Long schoolId, Long examId) {
        ExamEntity exam = examRepository.findById(examId)
                .orElseThrow(() -> new ExamException(new ExamError.ExamNotFound(examId)));
        if (!exam.getSchoolId().equals(schoolId)) {
            // آزمون مدرسه‌ی دیگر - از دید این ماژول همان «یافت نشد»
            throw new ExamException(new ExamError.ExamNotFound(examId));
        }
        return exam;
    }

    /**
     * آزمونِ توکن، به‌شرطِ این‌که دانش‌آموزِ توکن هنوز نقشِ فعالِ دانش‌آموز در مدرسه‌ی آزمون داشته باشد: توکن
     * امضاشده است و پس از صدور از پایگاه داده باطل‌شدنی نبود، پس عضویتِ غیرفعال‌شده تا انقضای توکن همچنان
     * جلسه را باز نگه می‌داشت. پاسخ همان «توکن نامعتبر» است (نوع و وضعیتِ جدید ندارد).
     */
    private ExamEntity requireExamForToken(ExamTokenService.TokenData data) {
        ExamEntity exam = requireExamById(data.examId());
        boolean activeMember = academicFacade.findStudentInfo(data.studentId())
                .filter(student -> exam.getSchoolId().equals(student.schoolId()))
                .map(student -> iamFacade.findActiveRoleCodes(new IamFacade.UserId(student.userId()), exam.getSchoolId())
                        .contains("STUDENT"))
                .orElse(false);
        if (!activeMember) {
            throw new ExamException(new ExamError.InvalidExamToken("توکن جلسه‌ی آزمون نامعتبر یا منقضی شده است."));
        }
        return exam;
    }

    private ExamEntity requireExamById(Long examId) {
        return examRepository.findById(examId)
                .orElseThrow(() -> new ExamException(new ExamError.InvalidExamToken("آزمون این توکن یافت نشد.")));
    }

    private SectionEntity requireSectionInExam(Long examId, Long sectionId) {
        SectionEntity section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new ExamException(new ExamError.SectionNotFound(sectionId)));
        if (!section.getExamId().equals(examId)) {
            throw new ExamException(new ExamError.SectionNotFound(sectionId));
        }
        return section;
    }

    private void requireNoDuplicateSection(Long examId, Long topicId) {
        if (sectionRepository.findByExamIdAndTopicId(examId, topicId).isPresent()) {
            throw new ExamException(new ExamError.DuplicateSection(examId, topicId));
        }
    }

    private void requireTopicInSchool(Long schoolId, Long topicId) {
        boolean exists = curriculumFacade.listTopics(schoolId).stream()
                .anyMatch(t -> t.id().value().equals(topicId));
        if (!exists) {
            throw new ExamException(new ExamError.TopicNotFound(topicId));
        }
    }

    private void requireStudentInSchool(Long schoolId, Long studentId) {
        boolean inSchool = academicFacade.findStudentInfo(studentId)
                .map(info -> info.schoolId().equals(schoolId))
                .orElse(false);
        if (!inSchool) {
            throw new ExamException(new ExamError.StudentNotFound(studentId));
        }
    }

    private Map<Long, CurriculumFacade.QuestionInfo> questionMap(Long schoolId) {
        return curriculumFacade.listQuestions(schoolId, CurriculumFacade.QuestionFilter.none()).stream()
                .collect(Collectors.toMap(q -> q.id().value(), q -> q));
    }

    private ExamTokenService.TokenData requireToken(String token) {
        return tokenService.decode(token)
                .orElseThrow(() -> new ExamException(
                        new ExamError.InvalidExamToken("توکن جلسه‌ی آزمون نامعتبر یا منقضی شده است.")));
    }

    /** روز شروع هفته را SchoolFacade مالکی می‌کند (W9)؛ این ماژول آن را ثابت نمی‌کند. */
    private void requireWeekStart(Long schoolId, LocalDate weekStart) {
        WeekStart week = schoolFacade.findSchool(new SchoolFacade.SchoolId(schoolId))
                .map(SchoolFacade.SchoolInfo::weekStart)
                .orElseThrow(() -> new IllegalStateException("school not found: " + schoolId));
        if (weekStart == null || weekStart.getDayOfWeek() != week.firstDay()) {
            String day = week == WeekStart.MONDAY ? "دوشنبه" : "شنبه";
            throw new ExamException(new ExamError.InvalidExam("شروع هفته باید " + day + " باشد."));
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static Instant toInstant(LocalDateTime ldt) {
        return ldt.atZone(ZoneId.systemDefault()).toInstant();
    }

    /**
     * همان قرارداد currentActorOrNull در Finance/Expense/StudyPlanFacadeImpl -
     * سازنده ممکن است از مسیر بدون کاربر احرازشده (تست/seed) صدا زده شود.
     */
    private Long currentActorOrNull() {
        try {
            return iamFacade.currentUserId().value();
        } catch (IamException e) {
            return null;
        }
    }
}
