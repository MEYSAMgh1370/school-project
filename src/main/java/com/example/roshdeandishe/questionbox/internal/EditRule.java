package com.example.roshdeandishe.questionbox.internal;

import com.example.roshdeandishe.iam.AccessContext;
import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.IamFacade.PermissionCode;
import com.example.roshdeandishe.iam.IamFacade.UserId;

import java.util.HashMap;
import java.util.Map;

/**
 * قاعده‌ی ویرایش/حذف برای یک درخواست (سؤال، سربرگ، برگه): دارنده‌ی QUESTION_BOX_MANAGE_ANY (مدیر مدرسه، یا نقشِ
 * سفارشیِ دارای آن)، یا خودِ سازنده، یا هر دارنده‌ی مجوز اگر سازنده دیگر QUESTION_BOX_MANAGE ندارد (بی‌صاحب).
 * تصمیم با Permission است نه کدِ نقش (openspec/changes/finance-view-all-permission.md). مجوزها هر بار از iam
 * خوانده می‌شود و فقط درون همین شیء cache می‌شود، پس غیرفعال‌شدنِ عضویت در درخواست بعدی دیده می‌شود.
 */
final class EditRule {

    private static final PermissionCode MANAGE = new PermissionCode("QUESTION_BOX_MANAGE");
    private static final PermissionCode MANAGE_ANY = new PermissionCode("QUESTION_BOX_MANAGE_ANY");

    private final IamFacade iamFacade;
    private final AccessContext school;
    private final Long actorUserId;
    private final boolean actorManagesAny;
    private final Map<Long, Boolean> orphanByCreator = new HashMap<>();

    EditRule(IamFacade iamFacade, Long schoolId, Long actorUserId) {
        this.iamFacade = iamFacade;
        this.school = new AccessContext.SchoolContext(schoolId);
        this.actorUserId = actorUserId;
        this.actorManagesAny = holds(actorUserId, MANAGE_ANY);
    }

    boolean canEdit(Long creatorUserId) {
        return actorManagesAny
                || actorUserId.equals(creatorUserId)
                || orphanByCreator.computeIfAbsent(creatorUserId, creator -> !holds(creator, MANAGE));
    }

    private boolean holds(Long userId, PermissionCode permission) {
        return iamFacade.checkPermission(new UserId(userId), permission, school).isGranted();
    }
}
