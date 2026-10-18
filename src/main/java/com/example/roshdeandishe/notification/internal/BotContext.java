package com.example.roshdeandishe.notification.internal;

import com.example.roshdeandishe.iam.IamFacade.MembershipInfo;
import com.example.roshdeandishe.iam.IamFacade.UserId;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * تصویر «این کاربرِ لینک‌شده کیست و به چه چیزی دسترسی دارد» برای یک پیام
 * بات. فقط از داده‌ی سرور ساخته می‌شود (BotContextLoader، از روی
 * BaleLinkEntity)؛ هیچ‌چیز از محتوای Update به‌جز chat.id در آن راه ندارد.
 * intentهای بعدی userId و نقش‌ها را فقط از همین‌جا می‌گیرند؛ فرزندان و ثبت‌نام‌ها تنبل و فقط وقتی لازم شوند
 * از {@link SubjectResolver} می‌آیند.
 *
 * @param memberships فقط عضویت‌های فعال که تنظیمات بات مدرسه اجازه می‌دهد
 * @param blockedBySchoolSettings کاربر عضویت فعال داشته ولی هیچ‌کدام بات را مجاز نکرده‌اند
 *                                (با «عضویت فعالی ندارد» فرق دارد)
 */
record BotContext(
        Long chatId,
        UserId userId,
        String fullName,
        List<MembershipInfo> memberships,
        boolean blockedBySchoolSettings
) {

    BotContext(Long chatId, UserId userId, String fullName, List<MembershipInfo> memberships) {
        this(chatId, userId, fullName, memberships, false);
    }

    /** کدِ نقش‌های فعال این کاربر در یک مدرسه. */
    Set<String> rolesIn(Long schoolId) {
        return memberships.stream()
                .filter(m -> schoolId.equals(m.schoolId()))
                .map(MembershipInfo::roleCode)
                .collect(Collectors.toSet());
    }

    /** آیا کاربر در هر مدرسه‌ای این نقش را دارد. */
    boolean hasRole(String roleCode) {
        return memberships.stream().anyMatch(m -> roleCode.equals(m.roleCode()));
    }
}
