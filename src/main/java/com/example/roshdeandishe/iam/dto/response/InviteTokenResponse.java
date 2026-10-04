package com.example.roshdeandishe.iam.dto.response;

/**
 * emailSent=false با issued=true یعنی توکن صادر شد اما ایمیل به مقصد
 * نرسید (مثلاً SMTP در دسترس نبود) - ادمین باید لینک
 * /activate?token=... را دستی به فرد دعوت‌شده برساند.
 */
public record InviteTokenResponse(String inviteToken, boolean issued, boolean emailSent) {
}
