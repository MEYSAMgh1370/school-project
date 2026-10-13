package com.example.roshdeandishe.iam.dto.response;

import com.example.roshdeandishe.iam.IamFacade.MembershipInfo;

/**
 * پاسخِ «افزودن عضو»: همان {@link MembershipResponse} به‌علاوه‌ی {@code notified}، یعنی آیا به کاربرِ
 * از قبل فعال ایمیلِ «اضافه شدید» رفت. برای کاربرِ تازه (بدونِ رمز) همیشه false است و دعوت‌نامه‌ی
 * فعال‌سازی جدا صادر می‌شود.
 */
public record AddedMembershipResponse(
        Long membershipId, Long userId, String email, String fullName,
        Long schoolId, String roleCode, boolean active, boolean hasPassword, boolean notified
) {
    public static AddedMembershipResponse from(MembershipInfo info, boolean notified) {
        return new AddedMembershipResponse(
                info.membershipId(), info.userId().value(), info.email(), info.fullName(),
                info.schoolId(), info.roleCode(), info.active(), info.hasPassword(), notified
        );
    }
}
