package com.example.roshdeandishe.questionbox.internal;

import com.example.roshdeandishe.curriculum.CurriculumFacade;
import com.example.roshdeandishe.curriculum.CurriculumFacade.QuestionInfo;
import com.example.roshdeandishe.iam.AccessContext;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.questionbox.QuestionBoxFacade.*;
import com.example.roshdeandishe.questionbox.shared.QuestionBoxError;
import com.example.roshdeandishe.questionbox.shared.QuestionBoxException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * برگه‌های ذخیره‌شده: ساخت/ذخیره (جایگزینیِ کامل بخش‌ها)، فهرست و نمای نهایی برای چاپ. سؤال‌های بانک فقط از راه
 * {@code CurriculumFacade} خوانده می‌شوند. آیتمی که از قبل در برگه است دوباره اعتبارسنجی نمی‌شود، تا ذخیره‌ی
 * دوباره‌ی برگه با سؤالِ بعداً حذف‌شده شکست نخورد.
 */
@Service
class SheetService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<EnumMap<HeaderField, String>> VALUES_TYPE = new TypeReference<>() {
    };
    private static final int MAX_TITLE = 255;
    private static final int MAX_VALUE = 255;

    private final SheetRepository sheetRepository;
    private final SheetSectionRepository sectionRepository;
    private final SheetItemRepository itemRepository;
    private final BoxQuestionRepository questionRepository;
    private final QuestionImageRepository imageRepository;
    private final HeaderRepository headerRepository;
    private final CurriculumFacade curriculumFacade;
    private final IamFacade iamFacade;

    SheetService(SheetRepository sheetRepository, SheetSectionRepository sectionRepository,
                 SheetItemRepository itemRepository, BoxQuestionRepository questionRepository,
                 QuestionImageRepository imageRepository, HeaderRepository headerRepository,
                 CurriculumFacade curriculumFacade, IamFacade iamFacade) {
        this.sheetRepository = sheetRepository;
        this.sectionRepository = sectionRepository;
        this.itemRepository = itemRepository;
        this.questionRepository = questionRepository;
        this.imageRepository = imageRepository;
        this.headerRepository = headerRepository;
        this.curriculumFacade = curriculumFacade;
        this.iamFacade = iamFacade;
    }

    @Transactional
    SheetInfo create(Long schoolId, Long actorUserId, SheetDraft draft) {
        validateShape(draft);
        SheetEntity sheet = new SheetEntity(schoolId, actorUserId, Instant.now());
        validateReferences(schoolId, actorUserId, draft, Set.of(), null);
        apply(sheet, draft);
        sheetRepository.save(sheet);
        replaceSections(sheet.getId(), draft);
        return info(sheet, true);
    }

    @Transactional
    SheetInfo save(Long schoolId, Long actorUserId, Long sheetId, SheetDraft draft) {
        SheetEntity sheet = require(schoolId, sheetId);
        requireEditable(new EditRule(iamFacade, schoolId, actorUserId), sheet);
        validateShape(draft);
        validateReferences(schoolId, actorUserId, draft, existingRefs(sheetId), sheet.getHeaderId());
        apply(sheet, draft);
        sheet.setUpdatedAt(Instant.now());
        sectionRepository.deleteBySheetId(sheetId);
        replaceSections(sheetId, draft);
        return info(sheet, true);
    }

    @Transactional
    void delete(Long schoolId, Long actorUserId, Long sheetId) {
        SheetEntity sheet = require(schoolId, sheetId);
        requireEditable(new EditRule(iamFacade, schoolId, actorUserId), sheet);
        sectionRepository.deleteBySheetId(sheetId);
        sheetRepository.delete(sheet);
    }

    @Transactional(readOnly = true)
    SheetInfo get(Long schoolId, Long actorUserId, Long sheetId) {
        SheetEntity sheet = require(schoolId, sheetId);
        return info(sheet, new EditRule(iamFacade, schoolId, actorUserId).canEdit(sheet.getCreatedByUserId()));
    }

    @Transactional(readOnly = true)
    List<SheetSummary> list(Long schoolId, Long actorUserId) {
        EditRule rule = new EditRule(iamFacade, schoolId, actorUserId);
        List<SheetEntity> sheets = sheetRepository.findBySchoolIdOrderByIdDesc(schoolId);
        if (sheets.isEmpty()) {
            return List.of();
        }
        List<SheetSectionEntity> sections = sectionRepository.findBySheetIdInOrderByPositionAsc(
                sheets.stream().map(SheetEntity::getId).toList());
        Map<Long, Long> sheetBySection = sections.stream()
                .collect(Collectors.toMap(SheetSectionEntity::getId, SheetSectionEntity::getSheetId));
        Map<Long, Integer> counts = new HashMap<>();
        if (!sections.isEmpty()) {
            for (SheetItemEntity item : itemRepository.findBySectionIdInOrderByPositionAsc(sheetBySection.keySet())) {
                counts.merge(sheetBySection.get(item.getSectionId()), 1, Integer::sum);
            }
        }
        return sheets.stream()
                .map(s -> new SheetSummary(s.getId(), s.getTitle(), s.getCreatedByUserId(),
                        rule.canEdit(s.getCreatedByUserId()), counts.getOrDefault(s.getId(), 0), s.getUpdatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    ResolvedSheet resolve(Long schoolId, Long sheetId) {
        SheetEntity sheet = require(schoolId, sheetId);
        List<SheetSectionEntity> sections = sectionRepository.findBySheetIdOrderByPositionAsc(sheetId);
        List<SheetItemEntity> items = sections.isEmpty() ? List.of()
                : itemRepository.findBySectionIdInOrderByPositionAsc(
                sections.stream().map(SheetSectionEntity::getId).toList());

        Set<Long> boxIds = items.stream().map(SheetItemEntity::getBoxQuestionId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> bankIds = items.stream().map(SheetItemEntity::getBankQuestionId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, BoxQuestionEntity> boxById = boxIds.isEmpty() ? Map.of()
                : questionRepository.findByIdInAndSchoolId(boxIds, schoolId).stream()
                .collect(Collectors.toMap(BoxQuestionEntity::getId, q -> q));
        Map<Long, List<QuestionImageRepository.ImageMeta>> imagesByQuestion = boxById.isEmpty() ? Map.of()
                : imageRepository.findMetaByQuestionIdIn(boxById.keySet()).stream()
                .collect(Collectors.groupingBy(QuestionImageRepository.ImageMeta::getQuestionId));
        Map<Long, QuestionInfo> bankById = curriculumFacade.findQuestionsVisibleToSchool(schoolId, bankIds).stream()
                .collect(Collectors.toMap(q -> q.id().value(), q -> q));

        Map<Long, List<SheetItemEntity>> itemsBySection = items.stream()
                .collect(Collectors.groupingBy(SheetItemEntity::getSectionId));
        List<ResolvedSection> resolvedSections = new ArrayList<>();
        int number = 0;
        for (SheetSectionEntity section : sections) {
            List<ResolvedItem> resolvedItems = new ArrayList<>();
            for (SheetItemEntity item : itemsBySection.getOrDefault(section.getId(), List.of())) {
                number++;
                resolvedItems.add(item.getBoxQuestionId() != null
                        ? resolveBox(number, item.getBoxQuestionId(), boxById.get(item.getBoxQuestionId()), imagesByQuestion)
                        : resolveBank(number, item.getBankQuestionId(), bankById.get(item.getBankQuestionId())));
            }
            resolvedSections.add(new ResolvedSection(section.getTitle(), section.getInstruction(), resolvedItems));
        }
        HeaderInfo header = sheet.getHeaderId() == null ? null
                : headerRepository.findRowByIdAndSchoolId(sheet.getHeaderId(), schoolId)
                .map(h -> HeaderService.toInfo(h, false)).orElse(null);
        return new ResolvedSheet(sheet.getId(), sheet.getTitle(), header, readValues(sheet.getHeaderValues()),
                sheet.isShowEssayAnswerSpace(), sheet.isIncludeAnswerKey(), resolvedSections);
    }

    // ---- ساخت و اعتبارسنجی ----

    private static void validateShape(SheetDraft d) {
        if (d == null || d.title() == null || d.title().isBlank()) {
            throw invalid("عنوان برگه نباید خالی باشد.");
        }
        if (d.title().strip().length() > MAX_TITLE) {
            throw invalid("عنوان برگه حداکثر " + MAX_TITLE + " نویسه است.");
        }
        if (d.sections() == null || d.sections().isEmpty()) {
            throw invalid("برگه باید دست‌کم یک بخش داشته باشد.");
        }
        for (SectionDraft s : d.sections()) {
            if (s == null || s.title() == null || s.title().isBlank()) {
                throw invalid("هر بخش باید عنوان داشته باشد.");
            }
            if (s.title().strip().length() > MAX_TITLE) {
                throw invalid("عنوان بخش حداکثر " + MAX_TITLE + " نویسه است.");
            }
            for (ItemRef item : s.items() == null ? List.<ItemRef>of() : s.items()) {
                if (item == null || item.source() == null || item.questionId() == null) {
                    throw invalid("هر آیتم باید منبع و شناسه‌ی سؤال داشته باشد.");
                }
            }
        }
        if (d.headerValues() != null && d.headerValues().values().stream()
                .anyMatch(v -> v != null && v.strip().length() > MAX_VALUE)) {
            throw invalid("مقدار فیلدهای سربرگ حداکثر " + MAX_VALUE + " نویسه است.");
        }
    }

    /** آیتم‌هایی که از قبل در برگه‌اند معاف از بررسی‌اند؛ سربرگِ فعلیِ برگه هم. */
    private void validateReferences(Long schoolId, Long actorUserId, SheetDraft d, Set<ItemRef> existing,
                                   Long currentHeaderId) {
        Set<Long> newBox = new HashSet<>();
        Set<Long> newBank = new HashSet<>();
        for (SectionDraft s : d.sections()) {
            for (ItemRef item : s.items() == null ? List.<ItemRef>of() : s.items()) {
                if (existing.contains(item)) {
                    continue;
                }
                (item.source() == ItemSource.BOX ? newBox : newBank).add(item.questionId());
            }
        }
        if (!newBox.isEmpty()) {
            Map<Long, BoxQuestionEntity> found = questionRepository.findByIdInAndSchoolId(newBox, schoolId).stream()
                    .filter(q -> q.getDeletedAt() == null)
                    .collect(Collectors.toMap(BoxQuestionEntity::getId, q -> q));
            Set<Long> needStem = found.values().stream().filter(q -> q.getContentMode() == ContentMode.IMAGE_ONLY)
                    .map(BoxQuestionEntity::getId).collect(Collectors.toSet());
            Set<Long> withStem = needStem.isEmpty() ? Set.of()
                    : imageRepository.findMetaByQuestionIdIn(needStem).stream()
                    .filter(i -> i.getRole() == ImageRole.STEM)
                    .map(QuestionImageRepository.ImageMeta::getQuestionId).collect(Collectors.toSet());
            for (Long id : newBox) {
                if (!found.containsKey(id)) {
                    throw invalid("سؤال " + id + " در باکس این مدرسه نیست یا حذف شده است.");
                }
                if (needStem.contains(id) && !withStem.contains(id)) {
                    throw invalid("سؤال فقط‌تصویر " + id + " هنوز تصویر ندارد.");
                }
            }
        }
        if (!newBank.isEmpty()) {
            // بانک سؤال (CURRICULUM) ماژولِ پولی است؛ باکس رایگان نباید راهِ دور زدنِ آن باشد
            boolean bankEntitled = iamFacade.checkAccess(new IamFacade.UserId(actorUserId),
                    new IamFacade.ModuleCode("CURRICULUM"), new AccessContext.SchoolContext(schoolId)).isGranted();
            if (!bankEntitled) {
                throw invalid("بانک سؤال برای این مدرسه فعال نیست؛ فقط سؤال‌های باکس را می‌توانید به برگه اضافه کنید.");
            }
            Set<Long> visible = curriculumFacade.findQuestionsVisibleToSchool(schoolId, newBank).stream()
                    .map(q -> q.id().value()).collect(Collectors.toSet());
            for (Long id : newBank) {
                if (!visible.contains(id)) {
                    throw invalid("سؤال بانک " + id + " برای این مدرسه در دسترس نیست.");
                }
            }
        }
        Long headerId = d.headerId();
        if (headerId != null && !headerId.equals(currentHeaderId)) {
            HeaderRow header = headerRepository.findRowByIdAndSchoolId(headerId, schoolId)
                    .orElseThrow(() -> invalid("سربرگ " + headerId + " در این مدرسه نیست."));
            if (!HeaderService.toInfo(header, false).complete()) {
                throw invalid("سربرگ «" + header.name() + "» هنوز تصویر ندارد.");
            }
        }
    }

    private Set<ItemRef> existingRefs(Long sheetId) {
        List<SheetSectionEntity> sections = sectionRepository.findBySheetIdOrderByPositionAsc(sheetId);
        if (sections.isEmpty()) {
            return Set.of();
        }
        return itemRepository.findBySectionIdInOrderByPositionAsc(
                        sections.stream().map(SheetSectionEntity::getId).toList()).stream()
                .map(i -> i.getBoxQuestionId() != null
                        ? new ItemRef(ItemSource.BOX, i.getBoxQuestionId())
                        : new ItemRef(ItemSource.BANK, i.getBankQuestionId()))
                .collect(Collectors.toSet());
    }

    private void apply(SheetEntity sheet, SheetDraft d) {
        sheet.setTitle(d.title().strip());
        sheet.setHeaderId(d.headerId());
        sheet.setShowEssayAnswerSpace(d.showEssayAnswerSpace());
        sheet.setIncludeAnswerKey(d.includeAnswerKey());
        Map<HeaderField, String> values = new EnumMap<>(HeaderField.class);
        if (d.headerValues() != null) {
            d.headerValues().forEach((k, v) -> {
                if (k != null && v != null && !v.isBlank()) {
                    values.put(k, v.strip());
                }
            });
        }
        try {
            sheet.setHeaderValues(JSON.writeValueAsString(values));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private void replaceSections(Long sheetId, SheetDraft d) {
        int sectionPosition = 1;
        for (SectionDraft s : d.sections()) {
            SheetSectionEntity section = sectionRepository.save(new SheetSectionEntity(sheetId, sectionPosition++,
                    s.title().strip(), s.instruction() == null || s.instruction().isBlank() ? null : s.instruction().strip()));
            int itemPosition = 1;
            for (ItemRef item : s.items() == null ? List.<ItemRef>of() : s.items()) {
                itemRepository.save(new SheetItemEntity(section.getId(), itemPosition++,
                        item.source() == ItemSource.BOX ? item.questionId() : null,
                        item.source() == ItemSource.BANK ? item.questionId() : null));
            }
        }
    }

    // ---- خواندن ----

    private SheetInfo info(SheetEntity sheet, boolean editable) {
        List<SheetSectionEntity> sections = sectionRepository.findBySheetIdOrderByPositionAsc(sheet.getId());
        Map<Long, List<SheetItemEntity>> bySection = sections.isEmpty() ? Map.of()
                : itemRepository.findBySectionIdInOrderByPositionAsc(
                        sections.stream().map(SheetSectionEntity::getId).toList()).stream()
                .collect(Collectors.groupingBy(SheetItemEntity::getSectionId));
        List<SectionDraft> drafts = sections.stream().map(s -> new SectionDraft(s.getTitle(), s.getInstruction(),
                bySection.getOrDefault(s.getId(), List.of()).stream()
                        .map(i -> i.getBoxQuestionId() != null
                                ? new ItemRef(ItemSource.BOX, i.getBoxQuestionId())
                                : new ItemRef(ItemSource.BANK, i.getBankQuestionId()))
                        .toList())).toList();
        return new SheetInfo(sheet.getId(), sheet.getSchoolId(), sheet.getCreatedByUserId(), editable, sheet.getTitle(),
                sheet.getHeaderId(), readValues(sheet.getHeaderValues()), sheet.isShowEssayAnswerSpace(),
                sheet.isIncludeAnswerKey(), drafts);
    }

    private static Map<HeaderField, String> readValues(String json) {
        try {
            return JSON.readValue(json, VALUES_TYPE);
        } catch (JsonProcessingException e) {
            return new EnumMap<>(HeaderField.class);
        }
    }

    private static ResolvedItem resolveBox(int number, Long id, BoxQuestionEntity q,
                                           Map<Long, List<QuestionImageRepository.ImageMeta>> imagesByQuestion) {
        if (q == null) {
            return unavailable(number, ItemSource.BOX, id);
        }
        // سؤالِ فقط‌تصویری که تصویرِ صورتش برداشته شده چیزی برای چاپ ندارد؛ شماره‌ی خالی چاپ نمی‌شود
        if (q.getContentMode() == ContentMode.IMAGE_ONLY && imagesByQuestion.getOrDefault(id, List.of()).stream()
                .noneMatch(i -> i.getRole() == ImageRole.STEM)) {
            return unavailable(number, ItemSource.BOX, id);
        }
        List<String> choices = q.getChoice1() == null ? List.of()
                : List.of(q.getChoice1(), q.getChoice2(), q.getChoice3(), q.getChoice4());
        List<ImageRef> images = imagesByQuestion.getOrDefault(id, List.of()).stream()
                .map(i -> new ImageRef(i.getId(), i.getRole())).toList();
        return new ResolvedItem(number, ItemSource.BOX, id, true, q.getType(), q.getContentMode(), q.getText(),
                choices, q.getCorrectChoice(), q.getAnswer(), q.getSuggestedScore(), images);
    }

    private static ResolvedItem resolveBank(int number, Long id, QuestionInfo q) {
        if (q == null) {
            return unavailable(number, ItemSource.BANK, id);
        }
        return new ResolvedItem(number, ItemSource.BANK, id, true, QuestionType.MULTIPLE_CHOICE, ContentMode.TEXT,
                q.text(), List.of(q.choice1(), q.choice2(), q.choice3(), q.choice4()), q.correctChoice(), q.answer(),
                null, List.of());
    }

    private static ResolvedItem unavailable(int number, ItemSource source, Long id) {
        return new ResolvedItem(number, source, id, false, null, null, null, List.of(), null, null, null, List.of());
    }

    private SheetEntity require(Long schoolId, Long sheetId) {
        return sheetRepository.findByIdAndSchoolId(sheetId, schoolId)
                .orElseThrow(() -> new QuestionBoxException(new QuestionBoxError.SheetNotFound(sheetId)));
    }

    private static void requireEditable(EditRule rule, SheetEntity sheet) {
        if (!rule.canEdit(sheet.getCreatedByUserId())) {
            throw new QuestionBoxException(new QuestionBoxError.NotAllowed(sheet.getId()));
        }
    }

    private static QuestionBoxException invalid(String reason) {
        return new QuestionBoxException(new QuestionBoxError.InvalidSheet(reason));
    }
}
