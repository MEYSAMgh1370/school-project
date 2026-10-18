package com.example.roshdeandishe.notification.internal;

/**
 * هویتِ رباتی که یک به‌روزرسانی یا پیوند به آن تعلق دارد. هر دو مقدار null یعنی ربات مشترک.
 */
record BotRef(Long botId, Long schoolId) {

    /** ربات مشترک (بدون ردیف school_bots). */
    static final BotRef SHARED = new BotRef(null, null);

    static BotRef school(long botId, long schoolId) {
        return new BotRef(botId, schoolId);
    }

    boolean isShared() {
        return botId == null;
    }
}
