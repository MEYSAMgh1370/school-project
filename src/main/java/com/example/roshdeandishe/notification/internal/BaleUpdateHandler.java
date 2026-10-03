package com.example.roshdeandishe.notification.internal;

import com.example.roshdeandishe.academic.AcademicFacade;
import com.example.roshdeandishe.academic.AcademicFacade.StudentInfo;
import com.example.roshdeandishe.notification.internal.BaleUpdate.BaleMessage;
import com.example.roshdeandishe.finance.FinanceFacade;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.IamFacade.MembershipInfo;
import com.example.roshdeandishe.iam.IamFacade.UserId;
import com.example.roshdeandishe.iam.IamFacade.UserProfile;
import com.example.roshdeandishe.school.SchoolFacade;
import com.example.roshdeandishe.school.SchoolFacade.SchoolId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * منطق پردازش یک Update ورودی از وب‌هوک بله. یک بات دستوری ساده است - نه
 * مکالمه‌ی آزاد و نه هیچ مدل زبانی‌ای (CLAUDE.md §10: «هوش مصنوعی نسازید»).
 * <p>
 * چون این کد در پاسخ به یک HTTP Request از سرور بله اجرا می‌شود - نه یک
 * درخواست مرورگر با سشن - هیچ‌کدام از {@code @RequiresModule}/
 * {@code @RequiresPermission} در دسترس/معنادار نیستند (به TenantContext/
 * SecurityContext یک session نیاز دارند که اینجا وجود ندارد). امنیت این
 * ماژول از الگوی دیگری می‌آید: هر پرس‌وجو فقط داده‌ی خودِ کاربرِ لینک‌شده
 * (از روی BaleLinkEntity) یا فرزندان *واقعاً لینک‌شده*‌ی او را می‌خواند -
 * دقیقاً همان الگوی اعتماد MeController («/api/me»، عمداً بدون آن دو
 * annotation، چون درباره‌ی خودِ کاربر است نه یک مدرسه‌ی خاص).
 */
@Component
class BaleUpdateHandler {

    private static final Map<String, String> ROLE_LABELS = Map.of(
            "STUDENT", "دانش‌آموز",
            "TEACHER", "معلم",
            "PARENT", "والد",
            "SCHOOL_ADMIN", "مدیر مدرسه",
            "FINANCE_ADMIN", "مدیر مالی"
    );

    private static final String WELCOME_TEXT = """
            به ربات رشد اندیشه خوش آمدید!

            برای اتصال حساب کاربری‌تان، لطفاً شماره تلفنی که در پروفایل حساب \
            کاربری‌تان (در سایت) ثبت کرده‌اید را با دکمه‌ی زیر به اشتراک بگذارید.""";

    private static final String NOT_LINKED_TEXT = "حساب شما هنوز متصل نشده است. لطفاً ابتدا /start را ارسال کنید.";

    private static final String UNKNOWN_COMMAND_TEXT =
            "دستور نامعتبر است. برای راهنما، «راهنما» را ارسال کنید.";

    private static final String HELP_TEXT = """
            دستورهای قابل استفاده:
            «مدرسه‌های من» - فهرست مدرسه‌ها و نقش‌های شما
            «فرزندان من» - فهرست فرزندان لینک‌شده و وضعیت مالی هرکدام""";

    private final BaleLinkRepository baleLinkRepository;
    private final BaleClient baleClient;
    private final IamFacade iamFacade;
    private final SchoolFacade schoolFacade;
    private final AcademicFacade academicFacade;
    private final FinanceFacade financeFacade;

    BaleUpdateHandler(
            BaleLinkRepository baleLinkRepository,
            BaleClient baleClient,
            IamFacade iamFacade,
            SchoolFacade schoolFacade,
            AcademicFacade academicFacade,
            FinanceFacade financeFacade
    ) {
        this.baleLinkRepository = baleLinkRepository;
        this.baleClient = baleClient;
        this.iamFacade = iamFacade;
        this.schoolFacade = schoolFacade;
        this.academicFacade = academicFacade;
        this.financeFacade = financeFacade;
    }

    @Transactional
    void handle(BaleUpdate update) {
        BaleMessage message = update.message();
        if (message == null || message.chat() == null) {
            return; // آپدیت‌هایی غیر از پیام معمولی (مثلاً edited_message) فعلاً نادیده گرفته می‌شوند
        }

        Long chatId = message.chat().id();

        if (message.contact() != null) {
            handleContactShared(chatId, message.contact().phoneNumber());
            return;
        }

        String text = message.text() == null ? "" : message.text().trim();
        if ("/start".equals(text)) {
            baleClient.sendContactRequest(chatId, WELCOME_TEXT);
            return;
        }

        Optional<Long> linkedUserId = baleLinkRepository.findByChatId(chatId).map(BaleLinkEntity::getUserId);
        if (linkedUserId.isEmpty()) {
            baleClient.sendMessage(chatId, NOT_LINKED_TEXT);
            return;
        }

        UserId userId = new UserId(linkedUserId.get());
        switch (text) {
            case "راهنما" -> baleClient.sendMessage(chatId, HELP_TEXT);
            case "مدرسه‌های من" -> baleClient.sendMessage(chatId, describeMemberships(userId));
            case "فرزندان من" -> baleClient.sendMessage(chatId, describeWards(userId));
            default -> baleClient.sendMessage(chatId, UNKNOWN_COMMAND_TEXT);
        }
    }

    private void handleContactShared(Long chatId, String phoneNumber) {
        Optional<UserProfile> matchedUser = iamFacade.findUserByPhoneNumber(phoneNumber);
        if (matchedUser.isEmpty()) {
            baleClient.sendMessage(chatId,
                    "شماره‌ی به‌اشتراک‌گذاشته‌شده با هیچ حسابی در سامانه مطابقت ندارد. " +
                    "ابتدا از طریق پروفایل حساب کاربری‌تان در سایت، همین شماره را ثبت کنید.");
            return;
        }

        Long userId = matchedUser.get().id().value();
        // upsert بر اساس userId - اگر همین کاربر قبلاً از یک chat دیگر لینک
        // شده بود، آن لینک قدیمی جای‌گزین می‌شود (idempotent روی هر دو طرف)
        BaleLinkEntity link = baleLinkRepository.findByUserId(userId)
                .orElseGet(() -> new BaleLinkEntity(userId, chatId, Instant.now()));
        link.setChatId(chatId);
        link.setUserId(userId);
        baleLinkRepository.save(link);

        baleClient.sendMessage(chatId,
                matchedUser.get().fullName() + " عزیز، حساب شما با موفقیت متصل شد. برای راهنما «راهنما» را ارسال کنید.");
    }

    private String describeMemberships(UserId userId) {
        List<MembershipInfo> memberships = iamFacade.findMembershipsByUser(userId).stream()
                .filter(MembershipInfo::active)
                .toList();

        if (memberships.isEmpty()) {
            return "شما در حال حاضر عضو هیچ مدرسه‌ای نیستید.";
        }

        StringBuilder sb = new StringBuilder("مدرسه‌ها و نقش‌های شما:\n");
        for (MembershipInfo m : memberships) {
            String schoolName = schoolFacade.findSchool(new SchoolId(m.schoolId()))
                    .map(s -> s.name())
                    .orElse("مدرسه #" + m.schoolId());
            sb.append("- ").append(schoolName).append(" (")
                    .append(ROLE_LABELS.getOrDefault(m.roleCode(), m.roleCode())).append(")\n");
        }
        return sb.toString().stripTrailing();
    }

    private String describeWards(UserId guardianUserId) {
        List<MembershipInfo> parentMemberships = iamFacade.findMembershipsByUser(guardianUserId).stream()
                .filter(m -> m.active() && "PARENT".equals(m.roleCode()))
                .toList();

        if (parentMemberships.isEmpty()) {
            return "شما در هیچ مدرسه‌ای نقش والد ندارید.";
        }

        StringBuilder sb = new StringBuilder();
        boolean anyWard = false;
        for (MembershipInfo membership : parentMemberships) {
            Long schoolId = membership.schoolId();
            List<StudentInfo> wards = academicFacade.listWards(guardianUserId.value(), schoolId);
            if (wards.isEmpty()) {
                continue;
            }
            anyWard = true;
            String schoolName = schoolFacade.findSchool(new SchoolId(schoolId)).map(s -> s.name())
                    .orElse("مدرسه #" + schoolId);
            for (StudentInfo ward : wards) {
                String wardName = iamFacade.findUser(new UserId(ward.userId()))
                        .map(UserProfile::fullName)
                        .orElse("دانش‌آموز #" + ward.userId());
                long invoiceCount = financeFacade.listInvoices(schoolId).stream()
                        .filter(inv -> inv.studentId().equals(ward.id().value()))
                        .count();
                sb.append("- ").append(wardName).append(" (").append(schoolName).append("): ")
                        .append(invoiceCount).append(" فاکتور صادرشده\n");
            }
        }

        if (!anyWard) {
            return "هیچ دانش‌آموزی به شما لینک نشده است.";
        }
        return sb.toString().stripTrailing();
    }
}
