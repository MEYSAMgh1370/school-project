package com.example.roshdeandishe.curriculum.internal;

import com.example.roshdeandishe.curriculum.CurriculumFacade;
import com.example.roshdeandishe.curriculum.shared.CurriculumError;
import com.example.roshdeandishe.curriculum.shared.CurriculumException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
class CurriculumFacadeImpl implements CurriculumFacade {

    private final TopicRepository topicRepository;
    private final QuestionRepository questionRepository;

    private final QuestionReportService reportService;

    CurriculumFacadeImpl(TopicRepository topicRepository, QuestionRepository questionRepository,
                         QuestionReportService reportService) {
        this.topicRepository = topicRepository;
        this.questionRepository = questionRepository;
        this.reportService = reportService;
    }

    @Override
    public ReportId createReport(Long schoolId, Long reporterUserId, Long questionId, ReportType reportType,
                                 String comment) {
        return reportService.create(schoolId, reporterUserId, questionId, reportType, comment);
    }

    @Override
    public List<QuestionReportInfo> listReportsForSchool(Long schoolId, ReportStatus status) {
        return reportService.listForSchool(schoolId, status);
    }

    @Override
    public List<QuestionReportInfo> listReportsByReporter(Long schoolId, Long userId) {
        return reportService.listByReporter(schoolId, userId);
    }

    @Override
    public QuestionReportInfo updateSchoolReport(Long schoolId, Long reportId, ReportStatus newStatus, String note) {
        return reportService.updateForSchool(schoolId, reportId, newStatus, note);
    }

    @Override
    public List<QuestionReportInfo> listPlatformReports(ReportStatus status) {
        return reportService.listPlatform(status);
    }

    @Override
    public QuestionReportInfo updatePlatformReport(Long reportId, ReportStatus newStatus, String note) {
        return reportService.updatePlatform(reportId, newStatus, note);
    }

    @Override
    @Transactional
    public TopicId createTopic(Long schoolId, Long parentId, String name, Integer sortOrder) {
        if (name == null || name.isBlank()) {
            throw new CurriculumException(new CurriculumError.InvalidTopic("نام سرفصل نمی‌تواند خالی باشد."));
        }
        if (parentId != null) {
            requireTopicInSchool(schoolId, parentId);
        }

        TopicEntity saved = topicRepository.save(
                new TopicEntity(schoolId, parentId, name.strip(), sortOrder == null ? 0 : sortOrder)
        );
        return new TopicId(saved.getId());
    }

    @Override
    @Transactional
    public TopicInfo upsertPlatformTopic(String sourceCode, String parentSourceCode, String name, int sortOrder) {
        if (sourceCode == null || sourceCode.isBlank() || sourceCode.strip().length() > 32) {
            throw new CurriculumException(new CurriculumError.InvalidTopic("کد منبع سرفصل باید بین ۱ تا ۳۲ نویسه باشد."));
        }
        if (name == null || name.isBlank()) {
            throw new CurriculumException(new CurriculumError.InvalidTopic("نام سرفصل نمی‌تواند خالی باشد."));
        }
        String code = sourceCode.strip();
        TopicEntity existing = topicRepository.findBySourceCodeAndSchoolIdIsNull(code).orElse(null);

        Long parentId = null;
        if (parentSourceCode != null && !parentSourceCode.isBlank()) {
            String parentCode = parentSourceCode.strip();
            TopicEntity parent = topicRepository.findBySourceCodeAndSchoolIdIsNull(parentCode)
                    .orElseThrow(() -> new CurriculumException(new CurriculumError.ParentTopicNotFound(parentCode)));
            if (existing != null) {
                requireNotInSubtree(existing, parent);
            }
            parentId = parent.getId();
        }

        TopicEntity topic = existing != null ? existing : new TopicEntity(null, parentId, name.strip(), sortOrder);
        topic.setSourceCode(code);
        topic.setParentId(parentId);
        topic.setName(name.strip());
        topic.setSortOrder(sortOrder);
        return toInfo(topicRepository.save(topic));
    }

    @Override
    public boolean upsertPlatformQuestion(String sourceCode, String topicSourceCode, String text,
                                          List<String> choices, int correctChoice, String answer) {
        if (sourceCode == null || sourceCode.isBlank() || sourceCode.strip().length() > 64) {
            throw new CurriculumException(new CurriculumError.InvalidQuestion("کد منبع سؤال باید بین ۱ تا ۶۴ نویسه باشد."));
        }
        if (text == null || text.isBlank()) {
            throw new CurriculumException(new CurriculumError.InvalidQuestion("متن سؤال نمی‌تواند خالی باشد."));
        }
        if (choices == null || choices.size() != 4 || choices.stream().anyMatch(c -> c == null || c.isBlank())) {
            throw new CurriculumException(new CurriculumError.InvalidQuestion("هر سؤال باید چهار گزینه‌ی غیرخالی داشته باشد."));
        }
        if (correctChoice < 1 || correctChoice > 4) {
            throw new CurriculumException(new CurriculumError.InvalidQuestion("گزینه‌ی درست باید ۱ تا ۴ باشد."));
        }
        String topicCode = topicSourceCode == null ? "" : topicSourceCode.strip();
        TopicEntity topic = topicRepository.findBySourceCodeAndSchoolIdIsNull(topicCode)
                .orElseThrow(() -> new CurriculumException(new CurriculumError.InvalidQuestion(
                        "سرفصل «" + topicCode + "» پلتفرمی نیست.")));

        String code = sourceCode.strip();
        QuestionEntity existing = questionRepository.findBySourceCodeAndSchoolIdIsNull(code).orElse(null);
        QuestionEntity question = existing != null ? existing : new QuestionEntity();
        question.setSourceCode(code);
        question.setSchoolId(null);
        question.setTopicId(topic.getId());
        question.setText(text.strip());
        question.setChoice1(choices.get(0).strip());
        question.setChoice2(choices.get(1).strip());
        question.setChoice3(choices.get(2).strip());
        question.setChoice4(choices.get(3).strip());
        question.setCorrectChoice(correctChoice);
        question.setAnswer(answer == null || answer.isBlank() ? null : answer.strip());
        questionRepository.save(question);
        return existing == null;
    }

    // والد نباید خودِ سرفصل یا یکی از نوادگانش باشد (حلقه): از والد به بالا می‌رویم
    private void requireNotInSubtree(TopicEntity topic, TopicEntity newParent) {
        TopicEntity cursor = newParent;
        int guard = 0;
        while (cursor != null && guard++ < 1000) {
            if (cursor.getId().equals(topic.getId())) {
                throw new CurriculumException(new CurriculumError.InvalidTopic(
                        "سرفصل نمی‌تواند والدِ خودش یا یکی از نوادگانش باشد."));
            }
            cursor = cursor.getParentId() == null ? null : topicRepository.findById(cursor.getParentId()).orElse(null);
        }
    }

    @Override
    public List<TopicInfo> listTopics(Long schoolId) {
        return topicRepository.findVisibleToSchool(schoolId).stream()
                .map(CurriculumFacadeImpl::toInfo)
                .toList();
    }

    @Override
    public Map<String, Long> resolveTopicIdsBySourceCode(Long schoolId, java.util.Collection<String> sourceCodes) {
        if (sourceCodes == null || sourceCodes.isEmpty()) {
            return Map.of();
        }
        List<String> codes = sourceCodes.stream().filter(c -> c != null && !c.isBlank()).map(String::strip).distinct().toList();
        if (codes.isEmpty()) {
            return Map.of();
        }
        return topicRepository.findVisibleBySourceCodes(schoolId, codes).stream()
                .collect(Collectors.toMap(TopicEntity::getSourceCode, TopicEntity::getId, (a, b) -> a));
    }

    @Override
    public List<TopicTreeNode> getTopicTree(Long schoolId) {
        List<TopicEntity> topics = topicRepository.findVisibleToSchool(schoolId);
        Map<Long, List<TopicEntity>> byParent = topics.stream()
                .filter(t -> t.getParentId() != null)
                .collect(Collectors.groupingBy(TopicEntity::getParentId));

        return topics.stream()
                .filter(t -> t.getParentId() == null)
                .map(root -> toTreeNode(root, byParent))
                .toList();
    }

    @Override
    public TopicTreeNode getTopicSubtree(Long schoolId, Long topicId) {
        TopicEntity root = requireTopicInSchool(schoolId, topicId);
        List<TopicEntity> topics = topicRepository.findAllById(topicRepository.findSubtreeIds(topicId, schoolId));
        Map<Long, List<TopicEntity>> byParent = topics.stream()
                .filter(t -> t.getParentId() != null)
                .collect(Collectors.groupingBy(TopicEntity::getParentId));
        return toTreeNode(root, byParent);
    }

    @Override
    @Transactional
    public QuestionId createQuestion(Long schoolId, NewQuestion question) {
        if (question.topicId() == null) {
            throw new CurriculumException(new CurriculumError.InvalidQuestion("سؤال باید به یک سرفصل متصل باشد."));
        }
        requireTopicInSchool(schoolId, question.topicId());
        requireNonBlank(question.text(), "متن سؤال نمی‌تواند خالی باشد.");
        requireNonBlank(question.choice1(), "هر چهار گزینه باید پر باشند.");
        requireNonBlank(question.choice2(), "هر چهار گزینه باید پر باشند.");
        requireNonBlank(question.choice3(), "هر چهار گزینه باید پر باشند.");
        requireNonBlank(question.choice4(), "هر چهار گزینه باید پر باشند.");
        if (question.correctChoice() < 1 || question.correctChoice() > 4) {
            throw new CurriculumException(
                    new CurriculumError.InvalidQuestion("گزینه‌ی درست باید عددی بین ۱ تا ۴ باشد.")
            );
        }

        QuestionEntity saved = questionRepository.save(new QuestionEntity(
                schoolId,
                question.topicId(),
                question.text(),
                question.choice1(),
                question.choice2(),
                question.choice3(),
                question.choice4(),
                question.correctChoice(),
                question.answer(),
                question.difficulty(),
                question.discriminationIndex()
        ));
        return new QuestionId(saved.getId());
    }

    @Override
    public List<QuestionInfo> listQuestions(Long schoolId, QuestionFilter filter) {
        QuestionFilter effective = filter == null ? QuestionFilter.none() : filter;

        List<QuestionEntity> questions;
        if (effective.topicId() == null) {
            questions = questionRepository.findVisibleToSchool(schoolId);
        } else {
            requireTopicInSchool(schoolId, effective.topicId());
            // سمنتیک پورت‌شده از moshavere: فیلترِ سرفصل یعنی کل زیر درخت آن
            questions = questionRepository.findVisibleToSchoolByTopicIds(
                    schoolId, topicRepository.findSubtreeIds(effective.topicId(), schoolId)
            );
        }

        var difficulties = effective.difficulties();
        return questions.stream()
                .filter(q -> difficulties == null || difficulties.isEmpty() || difficulties.contains(q.getDifficulty()))
                .map(CurriculumFacadeImpl::toInfo)
                .toList();
    }

    @Override
    public List<QuestionInfo> findQuestionsVisibleToSchool(Long schoolId, java.util.Collection<Long> questionIds) {
        if (questionIds == null || questionIds.isEmpty()) {
            return List.of();
        }
        return questionRepository.findVisibleToSchoolByIds(schoolId, questionIds).stream()
                .map(CurriculumFacadeImpl::toInfo)
                .toList();
    }

    private TopicTreeNode toTreeNode(TopicEntity topic, Map<Long, List<TopicEntity>> byParent) {
        List<TopicTreeNode> children = byParent.getOrDefault(topic.getId(), List.of()).stream()
                .sorted(Comparator.comparingInt(TopicEntity::getSortOrder).thenComparing(TopicEntity::getId))
                .map(child -> toTreeNode(child, byParent))
                .collect(Collectors.toCollection(ArrayList::new));
        return new TopicTreeNode(new TopicId(topic.getId()), topic.getName(), topic.getSortOrder(), children);
    }

    private TopicEntity requireTopicInSchool(Long schoolId, Long topicId) {
        TopicEntity topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new CurriculumException(new CurriculumError.TopicNotFound(topicId)));
        // school_id خالی = سرفصل پلتفرمی، برای همه‌ی مدرسه‌ها قابل‌مشاهده
        if (topic.getSchoolId() != null && !topic.getSchoolId().equals(schoolId)) {
            // سرفصل متعلق به مدرسه‌ی دیگری است - از دید این مدرسه «یافت نشد»
            throw new CurriculumException(new CurriculumError.TopicNotFound(topicId));
        }
        return topic;
    }

    private void requireNonBlank(String value, String reason) {
        if (value == null || value.isBlank()) {
            throw new CurriculumException(new CurriculumError.InvalidQuestion(reason));
        }
    }

    private static TopicInfo toInfo(TopicEntity t) {
        return new TopicInfo(new TopicId(t.getId()), t.getSchoolId(), t.getParentId(), t.getName(), t.getSortOrder());
    }

    private static QuestionInfo toInfo(QuestionEntity q) {
        return new QuestionInfo(
                new QuestionId(q.getId()),
                q.getSchoolId(),
                q.getTopicId(),
                q.getText(),
                q.getChoice1(),
                q.getChoice2(),
                q.getChoice3(),
                q.getChoice4(),
                q.getCorrectChoice(),
                q.getAnswer(),
                q.getDifficulty(),
                q.getDiscriminationIndex()
        );
    }
}
