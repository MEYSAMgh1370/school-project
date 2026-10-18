package com.example.roshdeandishe.notification.controller;

import com.example.roshdeandishe.iam.shared.RequiresPermission;
import com.example.roshdeandishe.notification.SchoolBotFacade;
import com.example.roshdeandishe.notification.dto.request.RegisterSchoolBotRequest;
import com.example.roshdeandishe.notification.dto.response.SchoolBotResponse;
import com.example.roshdeandishe.platform.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * مدیریت رباتِ بله‌ی اختصاصیِ مدرسه توسط مدیر. بدون {@code @RequiresModule}: قابلیتی پلتفرمی است، نه ماژول
 * قابل‌اشتراک. دسترسی با {@code BALE_BOT_MANAGE} (seed برای SCHOOL_ADMIN).
 */
@RestController
@RequestMapping("/api/admin/schools/{schoolId}/bale-bot")
public class SchoolBotController {

    private final SchoolBotFacade schoolBots;

    public SchoolBotController(SchoolBotFacade schoolBots) {
        this.schoolBots = schoolBots;
    }

    @GetMapping
    @RequiresPermission("BALE_BOT_MANAGE")
    public ResponseEntity<SchoolBotResponse> get(@PathVariable Long schoolId) {
        TenantContext.requireMatches(schoolId);

        return schoolBots.find(schoolId)
                .map(info -> ResponseEntity.ok(SchoolBotResponse.from(info)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping
    @RequiresPermission("BALE_BOT_MANAGE")
    public SchoolBotResponse register(@PathVariable Long schoolId, @RequestBody RegisterSchoolBotRequest request) {
        TenantContext.requireMatches(schoolId);

        return SchoolBotResponse.from(schoolBots.register(schoolId, request == null ? null : request.token()));
    }

    @DeleteMapping
    @RequiresPermission("BALE_BOT_MANAGE")
    public ResponseEntity<Void> delete(@PathVariable Long schoolId) {
        TenantContext.requireMatches(schoolId);

        schoolBots.delete(schoolId);
        return ResponseEntity.noContent().build();
    }
}
