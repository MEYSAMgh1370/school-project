package com.example.roshdeandishe.questionbox.internal;

import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.IamFacade.UserId;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * قاعده‌ی ویرایش/حذف برای یک درخواست (سؤال، سربرگ، برگه): مدیر مدرسه، یا خودِ سازنده، یا هر دارنده‌ی مجوز اگر
 * سازنده دیگر نه معلمِ فعال است نه مدیرِ فعالِ مدرسه (بی‌صاحب). نقش‌های فعال هر بار از iam خوانده می‌شود و فقط
 * درون همین شیء cache می‌شود، پس غیرفعال‌شدنِ عضویت در درخواست بعدی دیده می‌شود.
 */
final class EditRule {

    private static final String ADMIN_ROLE = "SCHOOL_ADMIN";
    private static final String TEACHER_ROLE = "TEACHER";

    private final IamFacade iamFacade;
    private final Long schoolId;
    private final Long actorUserId;
    private final boolean actorIsAdmin;
    private final Map<Long, Boolean> orphanByCreator = new HashMap<>();

    EditRule(IamFacade iamFacade, Long schoolId, Long actorUserId) {
        this.iamFacade = iamFacade;
        this.schoolId = schoolId;
        this.actorUserId = actorUserId;
        this.actorIsAdmin = activeRoles(actorUserId).contains(ADMIN_ROLE);
    }

    boolean canEdit(Long creatorUserId) {
        return actorIsAdmin
                || actorUserId.equals(creatorUserId)
                || orphanByCreator.computeIfAbsent(creatorUserId, this::isOrphan);
    }

    private boolean isOrphan(Long creatorUserId) {
        Set<String> roles = activeRoles(creatorUserId);
        return !roles.contains(TEACHER_ROLE) && !roles.contains(ADMIN_ROLE);
    }

    private Set<String> activeRoles(Long userId) {
        return iamFacade.findActiveRoleCodes(new UserId(userId), schoolId);
    }
}
