package com.example.roshdeandishe.curriculum.internal;

import com.example.roshdeandishe.curriculum.CurriculumFacade;
import com.example.roshdeandishe.curriculum.shared.CurriculumError;
import com.example.roshdeandishe.curriculum.shared.CurriculumException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
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
    public TopicId createTopic(Long schoolId, Long parentId, String name, Integer order) {
        if (name == null || name.isBlank()) {
            throw new CurriculumException(new CurriculumError.InvalidTopic("نام سرفصل نمی‌تواند خالی باشد."));
        }
        if (parentId != null) {
            requireTopicInSchool(schoolId, parentId);
        }

        TopicEntity saved = topicRepository.save(
                new TopicEntity(schoolId, parentId, name.trim(), order == null ? 0 : order)
        );
        return new TopicId(saved.getId());
    }

    @Override
    public List<TopicInfo> listTopics(Long schoolId) {
        return topicRepository.findBySchoolIdOrderBySortOrderAscIdAsc(schoolId).stream()
                .map(t -> new TopicInfo(new TopicId(t.getId()), t.getSchoolId(), t.getParentId(), t.getName(), t.getSortOrder()))
                .toList();
    }

    @Override
    public List<TopicNode> getTopicTree(Long schoolId) {
        // یک fetch، ساخت درخت در حافظه: fetch مرتب‌شده است (order سپس id)،
        // پس فرزندانِ هر گره هم به همان ترتیب در groupingBy می‌نشینند
        List<TopicEntity> all = topicRepository.findBySchoolIdOrderBySortOrderAscIdAsc(schoolId);
        Map<Long, List<TopicEntity>> byParent = all.stream()
                .filter(t -> t.getParentId() != null)
                .collect(Collectors.groupingBy(TopicEntity::getParentId));

        return all.stream()
                .filter(t -> t.getParentId() == null)
                .map(t -> toNode(t, byParent))
                .toList();
    }

    @Override
    @Transactional
    public QuestionId createQuestion(Long schoolId, Long topicId, String text,
                                     String choice1, String choice2, String choice3, String choice4,
                                     int correctChoice, Integer difficulty, Double discriminationIndex) {
        requireTopicInSchool(schoolId, topicId);
        if (text == null || text.isBlank()) {
            throw new CurriculumException(new CurriculumError.InvalidQuestion("متن سوال نمی‌تواند خالی باشد."));
        }
        if (isBlank(choice1) || isBlank(choice2) || isBlank(choice3) || isBlank(choice4)) {
            throw new CurriculumException(new CurriculumError.InvalidQuestion("هر چهار گزینه‌ی سوال باید پر شوند."));
        }
        if (correctChoice < 1 || correctChoice > 4) {
            throw new CurriculumException(new CurriculumError.InvalidQuestion("گزینه‌ی صحیح باید بین ۱ تا ۴ باشد."));
        }
        if (difficulty != null && difficulty < 0) {
            throw new CurriculumException(new CurriculumError.InvalidQuestion("سختی سوال نمی‌تواند منفی باشد."));
        }

        QuestionEntity saved = questionRepository.save(new QuestionEntity(
                schoolId, topicId, text.trim(),
                choice1.trim(), choice2.trim(), choice3.trim(), choice4.trim(),
                correctChoice, difficulty, discriminationIndex
        ));
        return new QuestionId(saved.getId());
    }

    @Override
    public List<QuestionInfo> listQuestions(Long schoolId, Long topicId, Integer difficulty) {
        if (topicId != null) {
            requireTopicInSchool(schoolId, topicId);
        }
        return questionRepository.search(schoolId, topicId, difficulty).stream()
                .map(q -> new QuestionInfo(
                        new QuestionId(q.getId()), q.getSchoolId(), new TopicId(q.getTopicId()), q.getText(),
                        q.getChoice1(), q.getChoice2(), q.getChoice3(), q.getChoice4(),
                        q.getCorrectChoice(), q.getDifficulty(), q.getDiscriminationIndex()
                ))
                .toList();
    }

    private TopicNode toNode(TopicEntity topic, Map<Long, List<TopicEntity>> byParent) {
        List<TopicNode> children = byParent.getOrDefault(topic.getId(), List.of()).stream()
                .map(child -> toNode(child, byParent))
                .toList();
        return new TopicNode(new TopicId(topic.getId()), topic.getName(), topic.getSortOrder(), children);
    }

    private void requireTopicInSchool(Long schoolId, Long topicId) {
        TopicEntity topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new CurriculumException(new CurriculumError.TopicNotFound(topicId)));
        if (!topic.getSchoolId().equals(schoolId)) {
            // سرفصل متعلق به مدرسه‌ی دیگری است - از دید این ماژول همان «یافت نشد»
            throw new CurriculumException(new CurriculumError.TopicNotFound(topicId));
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
