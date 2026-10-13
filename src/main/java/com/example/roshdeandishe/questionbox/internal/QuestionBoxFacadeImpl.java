package com.example.roshdeandishe.questionbox.internal;

import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.IamFacade.UserId;
import com.example.roshdeandishe.questionbox.QuestionBoxFacade;
import com.example.roshdeandishe.questionbox.shared.QuestionBoxError;
import com.example.roshdeandishe.questionbox.shared.QuestionBoxException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
class QuestionBoxFacadeImpl implements QuestionBoxFacade {

    /** سقفِ تعداد تصویر برای هر سؤال (فرضِ ثبت‌شده در برنامه، منتظر تأیید). */
    private static final int MAX_IMAGES_PER_QUESTION = 6;

    private final BoxQuestionRepository questionRepository;
    private final QuestionImageRepository imageRepository;
    private final IamFacade iamFacade;
    private final HeaderService headerService;

    QuestionBoxFacadeImpl(BoxQuestionRepository questionRepository, QuestionImageRepository imageRepository,
                          IamFacade iamFacade, HeaderService headerService) {
        this.questionRepository = questionRepository;
        this.imageRepository = imageRepository;
        this.iamFacade = iamFacade;
        this.headerService = headerService;
    }

    @Override
    public HeaderInfo createHeader(Long schoolId, Long actorUserId, NewHeader header) {
        return headerService.create(schoolId, actorUserId, header);
    }

    @Override
    public HeaderInfo updateHeader(Long schoolId, Long actorUserId, Long headerId, NewHeader header) {
        return headerService.update(schoolId, actorUserId, headerId, header);
    }

    @Override
    public void deleteHeader(Long schoolId, Long actorUserId, Long headerId) {
        headerService.delete(schoolId, actorUserId, headerId);
    }

    @Override
    public List<HeaderInfo> listHeaders(Long schoolId, Long actorUserId) {
        return headerService.list(schoolId, actorUserId);
    }

    @Override
    public HeaderInfo setHeaderImage(Long schoolId, Long actorUserId, Long headerId, byte[] content) {
        return headerService.setImage(schoolId, actorUserId, headerId, content);
    }

    @Override
    public ImageContent getHeaderImage(Long schoolId, Long headerId) {
        return headerService.getImage(schoolId, headerId);
    }

    @Override
    @Transactional
    public QuestionInfo createQuestion(Long schoolId, Long actorUserId, NewQuestion question) {
        validate(question);
        BoxQuestionEntity entity = new BoxQuestionEntity(schoolId, actorUserId, Instant.now());
        apply(entity, question);
        questionRepository.save(entity);
        return toInfo(entity, new EditRule(iamFacade, schoolId, actorUserId), List.of());
    }

    @Override
    @Transactional
    public QuestionInfo updateQuestion(Long schoolId, Long actorUserId, Long questionId, NewQuestion question) {
        BoxQuestionEntity entity = requireVisible(schoolId, questionId);
        EditRule rule = new EditRule(iamFacade, schoolId, actorUserId);
        requireEditable(rule, entity);
        validate(question);
        apply(entity, question);
        entity.setUpdatedAt(Instant.now());
        return toInfo(entity, rule, imageRepository.findByQuestionIdOrderByIdAsc(entity.getId()));
    }

    @Override
    @Transactional
    public void deleteQuestion(Long schoolId, Long actorUserId, Long questionId) {
        BoxQuestionEntity entity = requireVisible(schoolId, questionId);
        requireEditable(new EditRule(iamFacade, schoolId, actorUserId), entity);
        Instant now = Instant.now();
        entity.setDeletedAt(now);
        entity.setUpdatedAt(now);
    }

    @Override
    @Transactional(readOnly = true)
    public QuestionInfo getQuestion(Long schoolId, Long actorUserId, Long questionId) {
        BoxQuestionEntity entity = requireVisible(schoolId, questionId);
        return toInfo(entity, new EditRule(iamFacade, schoolId, actorUserId),
                imageRepository.findByQuestionIdOrderByIdAsc(entity.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuestionInfo> listQuestions(Long schoolId, Long actorUserId, QuestionListFilter filter) {
        EditRule rule = new EditRule(iamFacade, schoolId, actorUserId);
        QuestionListFilter f = filter == null ? QuestionListFilter.none() : filter;
        String needle = f.search() == null || f.search().isBlank() ? null : f.search().strip().toLowerCase();
        List<QuestionInfo> result = new ArrayList<>();
        List<BoxQuestionEntity> all = questionRepository.findBySchoolIdAndDeletedAtIsNullOrderByIdDesc(schoolId);
        Map<Long, List<QuestionImageEntity>> imagesByQuestion = all.isEmpty() ? Map.of()
                : imageRepository.findByQuestionIdInOrderByIdAsc(all.stream().map(BoxQuestionEntity::getId).toList())
                .stream().collect(java.util.stream.Collectors.groupingBy(QuestionImageEntity::getQuestionId));
        for (BoxQuestionEntity e : all) {
            if (f.type() != null && e.getType() != f.type()) {
                continue;
            }
            if (f.topicId() != null && !f.topicId().equals(e.getTopicId())) {
                continue;
            }
            if (needle != null && !matches(e, needle)) {
                continue;
            }
            result.add(toInfo(e, rule, imagesByQuestion.getOrDefault(e.getId(), List.of())));
        }
        return result;
    }

    // ---- کمکی‌ها ----

    private static boolean matches(BoxQuestionEntity e, String needle) {
        for (String part : new String[]{e.getText(), e.getChoice1(), e.getChoice2(), e.getChoice3(), e.getChoice4(),
                e.getAnswer()}) {
            if (part != null && part.toLowerCase().contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private BoxQuestionEntity requireVisible(Long schoolId, Long questionId) {
        return questionRepository.findByIdAndSchoolIdAndDeletedAtIsNull(questionId, schoolId)
                .orElseThrow(() -> new QuestionBoxException(new QuestionBoxError.QuestionNotFound(questionId)));
    }

    private void requireEditable(EditRule rule, BoxQuestionEntity entity) {
        if (!rule.canEdit(entity.getCreatedByUserId())) {
            throw new QuestionBoxException(new QuestionBoxError.NotAllowed(entity.getId()));
        }
    }

    private static void validate(NewQuestion q) {
        if (q == null || q.type() == null || q.contentMode() == null) {
            throw invalid("نوع و حالت محتوای سؤال را مشخص کنید.");
        }
        boolean hasChoices = q.choices() != null && !q.choices().isEmpty();
        if (q.contentMode() == ContentMode.TEXT && (q.text() == null || q.text().isBlank())) {
            throw invalid("متن سؤال نباید خالی باشد.");
        }
        if (q.suggestedScore() != null && q.suggestedScore() <= 0) {
            throw invalid("نمره‌ی پیشنهادی باید مثبت باشد.");
        }
        if (q.type() == QuestionType.ESSAY) {
            if (hasChoices || q.correctChoice() != null) {
                throw invalid("سؤال تشریحی گزینه و گزینه‌ی صحیح ندارد.");
            }
            return;
        }
        if (q.correctChoice() == null || q.correctChoice() < 1 || q.correctChoice() > 4) {
            throw invalid("گزینه‌ی صحیح باید عددی از ۱ تا ۴ باشد.");
        }
        if (q.contentMode() == ContentMode.IMAGE_ONLY) {
            if (hasChoices) {
                throw invalid("در سؤال فقط‌تصویر، گزینه‌ها در خودِ تصویرند و متن گزینه ثبت نمی‌شود.");
            }
            return;
        }
        if (!hasChoices || q.choices().size() != 4 || q.choices().stream().anyMatch(c -> c == null || c.isBlank())) {
            throw invalid("سؤال چهارگزینه‌ای باید دقیقاً چهار گزینه‌ی غیرخالی داشته باشد.");
        }
    }

    private static QuestionBoxException invalid(String reason) {
        return new QuestionBoxException(new QuestionBoxError.InvalidQuestion(reason));
    }

    private static void apply(BoxQuestionEntity e, NewQuestion q) {
        e.setType(q.type());
        e.setContentMode(q.contentMode());
        e.setTopicId(q.topicId());
        e.setText(q.text() == null || q.text().isBlank() ? null : q.text().strip());
        List<String> c = q.choices() == null ? List.of() : q.choices();
        e.setChoice1(c.size() > 0 ? c.get(0).strip() : null);
        e.setChoice2(c.size() > 1 ? c.get(1).strip() : null);
        e.setChoice3(c.size() > 2 ? c.get(2).strip() : null);
        e.setChoice4(c.size() > 3 ? c.get(3).strip() : null);
        e.setCorrectChoice(q.correctChoice());
        e.setAnswer(q.answer() == null || q.answer().isBlank() ? null : q.answer().strip());
        e.setSuggestedScore(q.suggestedScore());
    }

    @Override
    @Transactional
    public ImageRef addImage(Long schoolId, Long actorUserId, Long questionId, ImageRole role, byte[] content) {
        BoxQuestionEntity question = requireVisible(schoolId, questionId);
        requireEditable(new EditRule(iamFacade, schoolId, actorUserId), question);
        if (role == null) {
            throw new QuestionBoxException(new QuestionBoxError.InvalidImage("نقش تصویر را مشخص کنید."));
        }
        if (question.getType() == QuestionType.ESSAY
                && (role == ImageRole.CHOICE_1 || role == ImageRole.CHOICE_2
                || role == ImageRole.CHOICE_3 || role == ImageRole.CHOICE_4)) {
            throw new QuestionBoxException(new QuestionBoxError.InvalidImage("سؤال تشریحی گزینه ندارد."));
        }
        String contentType = ImageValidator.validate(content);
        if (imageRepository.countByQuestionId(questionId) >= MAX_IMAGES_PER_QUESTION) {
            throw new QuestionBoxException(new QuestionBoxError.InvalidImage(
                    "هر سؤال حداکثر " + MAX_IMAGES_PER_QUESTION + " تصویر می‌تواند داشته باشد."));
        }
        QuestionImageEntity saved = imageRepository.save(
                new QuestionImageEntity(questionId, role, contentType, content, Instant.now()));
        return new ImageRef(saved.getId(), saved.getRole());
    }

    @Override
    @Transactional
    public void removeImage(Long schoolId, Long actorUserId, Long questionId, Long imageId) {
        BoxQuestionEntity question = requireVisible(schoolId, questionId);
        requireEditable(new EditRule(iamFacade, schoolId, actorUserId), question);
        QuestionImageEntity image = imageRepository.findById(imageId)
                .filter(i -> i.getQuestionId().equals(questionId))
                .orElseThrow(() -> new QuestionBoxException(new QuestionBoxError.ImageNotFound(imageId)));
        imageRepository.delete(image);
    }

    @Override
    @Transactional(readOnly = true)
    public ImageContent getImage(Long schoolId, Long imageId) {
        QuestionImageEntity image = imageRepository.findById(imageId)
                .orElseThrow(() -> new QuestionBoxException(new QuestionBoxError.ImageNotFound(imageId)));
        boolean sameSchool = questionRepository.findById(image.getQuestionId())
                .map(q -> q.getSchoolId().equals(schoolId)).orElse(false);
        if (!sameSchool) {
            throw new QuestionBoxException(new QuestionBoxError.ImageNotFound(imageId));
        }
        return new ImageContent(image.getContentType(), image.getData());
    }

    private QuestionInfo toInfo(BoxQuestionEntity e, EditRule rule, List<QuestionImageEntity> images) {
        List<String> choices = e.getChoice1() == null
                ? List.of()
                : List.of(e.getChoice1(), e.getChoice2(), e.getChoice3(), e.getChoice4());
        return new QuestionInfo(e.getId(), e.getSchoolId(), e.getCreatedByUserId(),
                rule.canEdit(e.getCreatedByUserId()),
                e.getContentMode() == ContentMode.TEXT
                        || images.stream().anyMatch(i -> i.getRole() == ImageRole.STEM),
                e.getType(), e.getContentMode(), e.getTopicId(), e.getText(), choices, e.getCorrectChoice(),
                e.getAnswer(), e.getSuggestedScore(),
                images.stream().map(i -> new ImageRef(i.getId(), i.getRole())).toList());
    }
}
