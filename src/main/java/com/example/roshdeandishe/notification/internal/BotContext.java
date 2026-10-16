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
 * intentهای بعدی userId، نقش‌ها و فرزندان را فقط از همین‌جا می‌گیرند.
 *
 * @param memberships فقط عضویت‌های فعال
 * @param wards       فقط از عضویت‌های فعالِ نقش PARENT
 */
record BotContext(
        Long chatId,
        UserId userId,
        String fullName,
        List<MembershipInfo> memberships,
        List<Ward> wards
) {

    /** فرزندِ لینک‌شده؛ classId وقتی دانش‌آموز کلاس ندارد null است. */
    record Ward(Long schoolId, Long studentId, Long studentUserId, Long classId, String fullName) {
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

    List<Ward> wardsIn(Long schoolId) {
        return wards.stream().filter(w -> schoolId.equals(w.schoolId())).toList();
    }
}
