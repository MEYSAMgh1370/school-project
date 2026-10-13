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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
class QuestionBoxFacadeImpl implements QuestionBoxFacade {

    private static final String ADMIN_ROLE = "SCHOOL_ADMIN";
    private static final String TEACHER_ROLE = "TEACHER";

    private final BoxQuestionRepository questionRepository;
    private final IamFacade iamFacade;

    QuestionBoxFacadeImpl(BoxQuestionRepository questionRepository, IamFacade iamFacade) {
        this.questionRepository = questionRepository;
        this.iamFacade = iamFacade;
    }

    @Override
    @Transactional
    public QuestionInfo createQuestion(Long schoolId, Long actorUserId, NewQuestion question) {
        validate(question);
        BoxQuestionEntity entity = new BoxQuestionEntity(schoolId, actorUserId, Instant.now());
        apply(entity, question);
        questionRepository.save(entity);
        return toInfo(entity, new EditRule(schoolId, actorUserId));
    }

    @Override
    @Transactional
    public QuestionInfo updateQuestion(Long schoolId, Long actorUserId, Long questionId, NewQuestion question) {
        BoxQuestionEntity entity = requireVisible(schoolId, questionId);
        EditRule rule = new EditRule(schoolId, actorUserId);
        requireEditable(rule, entity);
        validate(question);
        apply(entity, question);
        entity.setUpdatedAt(Instant.now());
        return toInfo(entity, rule);
    }

    @Override
    @Transactional
    public void deleteQuestion(Long schoolId, Long actorUserId, Long questionId) {
        BoxQuestionEntity entity = requireVisible(schoolId, questionId);
        requireEditable(new EditRule(schoolId, actorUserId), entity);
        Instant now = Instant.now();
        entity.setDeletedAt(now);
        entity.setUpdatedAt(now);
    }

    @Override
    @Transactional(readOnly = true)
    public QuestionInfo getQuestion(Long schoolId, Long actorUserId, Long questionId) {
        return toInfo(requireVisible(schoolId, questionId), new EditRule(schoolId, actorUserId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuestionInfo> listQuestions(Long schoolId, Long actorUserId, QuestionListFilter filter) {
        EditRule rule = new EditRule(schoolId, actorUserId);
        QuestionListFilter f = filter == null ? QuestionListFilter.none() : filter;
        String needle = f.search() == null || f.search().isBlank() ? null : f.search().strip().toLowerCase();
        List<QuestionInfo> result = new ArrayList<>();
        for (BoxQuestionEntity e : questionRepository.findBySchoolIdAndDeletedAtIsNullOrderByIdDesc(schoolId)) {
            if (f.type() != null && e.getType() != f.type()) {
                continue;
            }
            if (f.topicId() != null && !f.topicId().equals(e.getTopicId())) {
                continue;
            }
            if (needle != null && !matches(e, needle)) {
                continue;
            }
            result.add(toInfo(e, rule));
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

    private QuestionInfo toInfo(BoxQuestionEntity e, EditRule rule) {
        List<String> choices = e.getChoice1() == null
                ? List.of()
                : List.of(e.getChoice1(), e.getChoice2(), e.getChoice3(), e.getChoice4());
        return new QuestionInfo(e.getId(), e.getSchoolId(), e.getCreatedByUserId(),
                rule.canEdit(e.getCreatedByUserId()), e.getContentMode() == ContentMode.TEXT,
                e.getType(), e.getContentMode(), e.getTopicId(), e.getText(), choices, e.getCorrectChoice(),
                e.getAnswer(), e.getSuggestedScore(), List.of());
    }

    /**
     * قاعده‌ی ویرایش برای یک درخواست: نقش‌های فعالِ کاربر یک بار خوانده می‌شود و نقش‌های هر سازنده یک بار (cache
     * درون همین شیء، نه بین درخواست‌ها، پس غیرفعال‌شدنِ عضویت در درخواست بعدی دیده می‌شود).
     */
    private final class EditRule {

        private final Long schoolId;
        private final Long actorUserId;
        private final boolean actorIsAdmin;
        private final Map<Long, Boolean> orphanByCreator = new HashMap<>();

        EditRule(Long schoolId, Long actorUserId) {
            this.schoolId = schoolId;
            this.actorUserId = actorUserId;
            this.actorIsAdmin = activeRoles(actorUserId).contains(ADMIN_ROLE);
        }

        boolean canEdit(Long creatorUserId) {
            return actorIsAdmin
                    || actorUserId.equals(creatorUserId)
                    || orphanByCreator.computeIfAbsent(creatorUserId, this::isOrphan);
        }

        /** سازنده نه معلمِ فعال است نه مدیرِ فعالِ این مدرسه. */
        private boolean isOrphan(Long creatorUserId) {
            Set<String> roles = activeRoles(creatorUserId);
            return !roles.contains(TEACHER_ROLE) && !roles.contains(ADMIN_ROLE);
        }

        private Set<String> activeRoles(Long userId) {
            return iamFacade.findActiveRoleCodes(new UserId(userId), schoolId);
        }
    }
}
