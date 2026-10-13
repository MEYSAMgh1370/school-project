package com.example.roshdeandishe.questionbox.internal;

import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.questionbox.QuestionBoxFacade.HeaderField;
import com.example.roshdeandishe.questionbox.QuestionBoxFacade.HeaderInfo;
import com.example.roshdeandishe.questionbox.QuestionBoxFacade.HeaderMode;
import com.example.roshdeandishe.questionbox.QuestionBoxFacade.ImageContent;
import com.example.roshdeandishe.questionbox.QuestionBoxFacade.NewHeader;
import com.example.roshdeandishe.questionbox.shared.QuestionBoxError;
import com.example.roshdeandishe.questionbox.shared.QuestionBoxException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** سربرگ‌های برگه‌ی چاپی؛ قاعده‌ی ویرایش همان {@link EditRule}. */
@Service
class HeaderService {

    private final HeaderRepository headerRepository;
    private final SheetRepository sheetRepository;
    private final IamFacade iamFacade;

    HeaderService(HeaderRepository headerRepository, SheetRepository sheetRepository, IamFacade iamFacade) {
        this.headerRepository = headerRepository;
        this.sheetRepository = sheetRepository;
        this.iamFacade = iamFacade;
    }

    @Transactional
    HeaderInfo create(Long schoolId, Long actorUserId, NewHeader h) {
        validate(h);
        HeaderEntity entity = new HeaderEntity(schoolId, actorUserId, Instant.now());
        apply(entity, h);
        headerRepository.save(entity);
        return toInfo(entity, true);
    }

    @Transactional
    HeaderInfo update(Long schoolId, Long actorUserId, Long headerId, NewHeader h) {
        HeaderEntity entity = require(schoolId, headerId);
        EditRule rule = new EditRule(iamFacade, schoolId, actorUserId);
        requireEditable(rule, entity);
        validate(h);
        apply(entity, h);
        entity.setUpdatedAt(Instant.now());
        return toInfo(entity, true);
    }

    @Transactional
    void delete(Long schoolId, Long actorUserId, Long headerId) {
        HeaderEntity entity = require(schoolId, headerId);
        requireEditable(new EditRule(iamFacade, schoolId, actorUserId), entity);
        if (sheetRepository.existsByHeaderId(headerId)) {
            throw new QuestionBoxException(new QuestionBoxError.InvalidSheet(
                    "این سربرگ در برگه‌ای استفاده شده است؛ اول آن را از برگه‌ها بردارید."));
        }
        headerRepository.delete(entity);
    }

    @Transactional(readOnly = true)
    List<HeaderInfo> list(Long schoolId, Long actorUserId) {
        EditRule rule = new EditRule(iamFacade, schoolId, actorUserId);
        return headerRepository.findBySchoolIdOrderByIdDesc(schoolId).stream().map(e -> toInfo(e, rule.canEdit(e.getCreatedByUserId()))).toList();
    }

    @Transactional
    HeaderInfo setImage(Long schoolId, Long actorUserId, Long headerId, byte[] content) {
        HeaderEntity entity = require(schoolId, headerId);
        EditRule rule = new EditRule(iamFacade, schoolId, actorUserId);
        requireEditable(rule, entity);
        if (entity.getMode() != HeaderMode.IMAGE) {
            throw new QuestionBoxException(new QuestionBoxError.InvalidSheet("فقط سربرگِ تصویری تصویر می‌گیرد."));
        }
        entity.setImageContentType(ImageValidator.validate(content));
        entity.setImage(content);
        entity.setUpdatedAt(Instant.now());
        return toInfo(entity, true);
    }

    @Transactional(readOnly = true)
    ImageContent getImage(Long schoolId, Long headerId) {
        HeaderEntity entity = require(schoolId, headerId);
        if (entity.getImage() == null) {
            throw new QuestionBoxException(new QuestionBoxError.ImageNotFound(headerId));
        }
        return new ImageContent(entity.getImageContentType(), entity.getImage());
    }

    private HeaderEntity require(Long schoolId, Long headerId) {
        return headerRepository.findByIdAndSchoolId(headerId, schoolId)
                .orElseThrow(() -> new QuestionBoxException(new QuestionBoxError.HeaderNotFound(headerId)));
    }

    private static void requireEditable(EditRule rule, HeaderEntity entity) {
        if (!rule.canEdit(entity.getCreatedByUserId())) {
            throw new QuestionBoxException(new QuestionBoxError.NotAllowed(entity.getId()));
        }
    }

    private static void validate(NewHeader h) {
        if (h == null || h.name() == null || h.name().isBlank()) {
            throw new QuestionBoxException(new QuestionBoxError.InvalidSheet("نام سربرگ نباید خالی باشد."));
        }
        if (h.name().strip().length() > 128) {
            throw new QuestionBoxException(new QuestionBoxError.InvalidSheet("نام سربرگ حداکثر ۱۲۸ نویسه است."));
        }
        if (h.mode() == null) {
            throw new QuestionBoxException(new QuestionBoxError.InvalidSheet("حالت سربرگ را مشخص کنید."));
        }
        if (h.mode() == HeaderMode.LAYOUT && h.layout() == null) {
            throw new QuestionBoxException(new QuestionBoxError.InvalidSheet("برای سربرگِ قالبی یک چیدمان انتخاب کنید."));
        }
    }

    private static void apply(HeaderEntity e, NewHeader h) {
        e.setName(h.name().strip());
        e.setMode(h.mode());
        boolean layoutMode = h.mode() == HeaderMode.LAYOUT;
        e.setLayout(layoutMode ? h.layout() : null);
        e.setShowSchoolName(layoutMode && h.showSchoolName());
        e.setShowLogo(layoutMode && h.showLogo());
        Set<HeaderField> fields = layoutMode && h.fields() != null && !h.fields().isEmpty()
                ? EnumSet.copyOf(h.fields()) : EnumSet.noneOf(HeaderField.class);
        e.setFields(fields.stream().map(Enum::name).collect(Collectors.joining(",")));
        if (layoutMode) {
            e.setImage(null);
            e.setImageContentType(null);
        }
    }

    static HeaderInfo toInfo(HeaderEntity e, boolean editable) {
        Set<HeaderField> fields = EnumSet.noneOf(HeaderField.class);
        if (!e.getFields().isBlank()) {
            for (String code : e.getFields().split(",")) {
                fields.add(HeaderField.valueOf(code));
            }
        }
        boolean hasImage = e.getImage() != null;
        return new HeaderInfo(e.getId(), e.getSchoolId(), e.getCreatedByUserId(),
                editable, e.getMode() == HeaderMode.LAYOUT || hasImage, e.getName(),
                e.getMode(), e.getLayout(), e.isShowSchoolName(), e.isShowLogo(), fields, hasImage);
    }
}
