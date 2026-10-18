package com.example.roshdeandishe.school.controller;

import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.school.shared.SchoolError;
import com.example.roshdeandishe.school.shared.SchoolException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * نقش‌هایی که در تنظیماتِ مدرسه (یادآوری رویداد، ربات) قابل‌انتخاب‌اند و اعتبارسنجیِ کدها؛ بین
 * EventReminderController و SchoolSettingsController مشترک است.
 */
final class SchoolRoleCodes {

    private static final String PLATFORM_ROLE_PREFIX = "PLATFORM_";

    private SchoolRoleCodes() {
    }

    /** همان فهرستِ ویرایشگر نقش (listRoles: سیستمی + سفارشیِ همین مدرسه)؛ نقش پلتفرم هرگز. */
    static List<IamFacade.RoleInfo> selectable(IamFacade iamFacade, Long schoolId) {
        return iamFacade.listRoles(schoolId).stream()
                .filter(role -> role.code() != null && !role.code().startsWith(PLATFORM_ROLE_PREFIX))
                .toList();
    }

    /** هر کدِ null یا خارج از نقش‌های مدرسه SchoolError.InvalidSetting است. */
    static Set<String> validate(IamFacade iamFacade, Long schoolId, Iterable<String> codes) {
        Set<String> allowed = new HashSet<>();
        selectable(iamFacade, schoolId).forEach(role -> allowed.add(role.code()));
        Set<String> wanted = new HashSet<>();
        for (String code : codes) {
            if (code == null || !allowed.contains(code)) {
                throw new SchoolException(new SchoolError.InvalidSetting(
                        "نقش «" + code + "» جزو نقش‌های این مدرسه نیست."));
            }
            wanted.add(code);
        }
        return wanted;
    }
}
