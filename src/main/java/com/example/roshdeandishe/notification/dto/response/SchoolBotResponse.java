package com.example.roshdeandishe.notification.dto.response;

import com.example.roshdeandishe.notification.SchoolBotFacade.SchoolBotInfo;

import java.time.Instant;

/** وضعیتِ رباتِ مدرسه برای پنل مدیر؛ شاملِ توکن یا راز نیست. */
public record SchoolBotResponse(String username, String link, String status, long linkedMembers,
                                Instant lastUpdateAt, String lastError, Instant lastErrorAt) {

    public static SchoolBotResponse from(SchoolBotInfo info) {
        return new SchoolBotResponse(info.username(), "https://ble.ir/" + info.username(), info.status().name(),
                info.linkedMembers(), info.lastUpdateAt(), info.lastError(), info.lastErrorAt());
    }
}
