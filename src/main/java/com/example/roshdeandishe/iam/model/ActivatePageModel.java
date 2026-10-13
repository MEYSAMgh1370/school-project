package com.example.roshdeandishe.iam.model;

import com.example.roshdeandishe.iam.IamFacade.InviteLookup;
import com.example.roshdeandishe.iam.IamFacade.InviteState;

/**
 * {@code token} همان مقدارِ ورودیِ URL (نامطمئن؛ فقط به‌صورت data-attribute رندر می‌شود) و
 * {@code invite} توضیحِ آن توکن (وضعیت و، برای توکنِ معتبر، حساب/مدرسه/نقش).
 */
public record ActivatePageModel(String token, InviteLookup invite) {

    public boolean hasToken() {
        return token != null && !token.isBlank();
    }

    public boolean valid() {
        return invite.valid();
    }

    /** توکنی داده شده ولی کار نمی‌کند (مصرف‌شده، منقضی یا ناشناخته). */
    public boolean broken() {
        return hasToken() && invite.state() != InviteState.VALID;
    }

    public boolean used() {
        return invite.state() == InviteState.USED;
    }

    public boolean expired() {
        return invite.state() == InviteState.EXPIRED;
    }
}
