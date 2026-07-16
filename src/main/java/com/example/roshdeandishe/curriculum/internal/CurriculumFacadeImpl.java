package com.example.roshdeandishe.curriculum.internal;

import com.example.roshdeandishe.curriculum.CurriculumFacade;
import com.example.roshdeandishe.curriculum.shared.CurriculumError;
import com.example.roshdeandishe.curriculum.shared.CurriculumException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
class CurriculumFacadeImpl implements CurriculumFacade {

    private final TopicRepository topicRepository;
    private final QuestionRepository questionRepository;

    CurriculumFacadeImpl(TopicRepository topicRepository, QuestionRepository questionRepository) {
        this.topicRepository = topicRepository;
        this.questionRepository = questionRepository;
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
    public List<TopicInfo> listTopics(Long schoolId) {
        return topicRepository.findBySchoolIdOrderBySortOrderAscIdAsc(schoolId).stream()
                .map(CurriculumFacadeImpl::toInfo)
                .toList();
    }

    @Override
    public List<TopicTreeNode> getTopicTree(Long schoolId) {
        List<TopicEntity> topics = topicRepository.findBySchoolIdOrderBySortOrderAscIdAsc(schoolId);
        Map<Long, List<TopicEntity>> byParent = topics.stream()
                .filter(t -> t.getParentId() != null)
                .collect(Collectors.groupingBy(TopicEntity::getParentId));

        return topics.stream()
                .filter(t -> t.getParentId() == null)
                .map(root -> toTreeNode(root, byParent))
                .toList();
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
            questions = questionRepository.findBySchoolIdOrderByIdAsc(schoolId);
        } else {
            requireTopicInSchool(schoolId, effective.topicId());
            // سمنتیک پورت‌شده از moshavere: فیلترِ سرفصل یعنی کل زیر درخت آن
            questions = questionRepository.findBySchoolIdAndTopicIdInOrderByIdAsc(
                    schoolId, subtreeTopicIds(schoolId, effective.topicId())
            );
        }

        var difficulties = effective.difficulties();
        return questions.stream()
                .filter(q -> difficulties == null || difficulties.isEmpty() || difficulties.contains(q.getDifficulty()))
                .map(CurriculumFacadeImpl::toInfo)
                .toList();
    }

    /**
     * شناسه‌ی سرفصل + همه‌ی نوادگانش - جایگزین value.descendants() در
     * QuestionFilter منبع. یک بار همه‌ی سرفصل‌های مدرسه بارگذاری و در
     * حافظه پیمایش می‌شود؛ اگر روزی خواندن آن واقعاً گلوگاه شد، جدول
     * closure مادی‌شده گزینه‌ی بعدی است (MOSHAVERE_PORT_SPEC.md §4).
     */
    private Set<Long> subtreeTopicIds(Long schoolId, Long rootTopicId) {
        Map<Long, List<Long>> childrenByParent = topicRepository
                .findBySchoolIdOrderBySortOrderAscIdAsc(schoolId).stream()
                .filter(t -> t.getParentId() != null)
                .collect(Collectors.groupingBy(
                        TopicEntity::getParentId,
                        Collectors.mapping(TopicEntity::getId, Collectors.toList())
                ));

        Set<Long> ids = new HashSet<>();
        Deque<Long> pending = new ArrayDeque<>(List.of(rootTopicId));
        while (!pending.isEmpty()) {
            Long current = pending.pop();
            if (ids.add(current)) {
                pending.addAll(childrenByParent.getOrDefault(current, List.of()));
            }
        }
        return ids;
    }

    private TopicTreeNode toTreeNode(TopicEntity topic, Map<Long, List<TopicEntity>> byParent) {
        List<TopicTreeNode> children = byParent.getOrDefault(topic.getId(), List.of()).stream()
                .sorted(Comparator.comparingInt(TopicEntity::getSortOrder).thenComparing(TopicEntity::getId))
                .map(child -> toTreeNode(child, byParent))
                .collect(Collectors.toCollection(ArrayList::new));
        return new TopicTreeNode(new TopicId(topic.getId()), topic.getName(), topic.getSortOrder(), children);
    }

    private void requireTopicInSchool(Long schoolId, Long topicId) {
        TopicEntity topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new CurriculumException(new CurriculumError.TopicNotFound(topicId)));
        if (!topic.getSchoolId().equals(schoolId)) {
            // سرفصل متعلق به مدرسه‌ی دیگری است - از دید این مدرسه «یافت نشد»
            throw new CurriculumException(new CurriculumError.TopicNotFound(topicId));
        }
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
