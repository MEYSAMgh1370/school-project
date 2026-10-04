package com.example.roshdeandishe.iam.controller;

import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.IamFacade.UserId;
import com.example.roshdeandishe.iam.IamFacade.UserProfile;
import com.example.roshdeandishe.iam.dto.response.MeResponse;
import com.example.roshdeandishe.iam.dto.response.MyMembershipResponse;
import com.example.roshdeandishe.iam.dto.request.SetPhoneRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * «من کی هستم و کجا عضوم» - برای داشبورد و سوییچر آینده‌ی مدرسه/نقش
 * (Open Question 2 در CLAUDE.md همین endpoint را پیش‌بینی کرده بود).
 * <p>
 * عمداً هیچ {@code @RequiresPermission}/{@code @RequiresModule} ندارد:
 * این داده‌ها درباره‌ی خودِ کاربر احراز هویت‌شده است، نه درباره‌ی یک
 * مدرسه‌ی خاص - به همین دلیل هم به هدر X-School-ID نیازی ندارد
 * (مسیر /api/me در TenantResolverFilter مستثنی شده). احراز هویت توسط
 * SecurityConfig (anyRequest().authenticated()) الزامی می‌ماند.
 */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final IamFacade iamFacade;

    public MeController(IamFacade iamFacade) {
        this.iamFacade = iamFacade;
    }

    @GetMapping
    public MeResponse me() {
        UserId userId = iamFacade.currentUserId();
        UserProfile profile = iamFacade.findUser(userId).orElseThrow();
        return new MeResponse(profile.id().value(), profile.email(), profile.fullName());
    }

    @GetMapping("/memberships")
    public List<MyMembershipResponse> myMemberships() {
        UserId userId = iamFacade.currentUserId();
        return iamFacade.findMembershipsByUser(userId).stream()
                .map(MyMembershipResponse::from)
                .toList();
    }

    /**
     * ثبت شماره تلفن کاربر فعلی - قدم اول برای اتصال به ربات بله (ماژول
     * notification). عمداً همان‌جا (نه در ماژول notification) قرار دارد چون این یک تغییر
     * روی خودِ حساب کاربری است، نه چیزی مختص کانال بله.
     */
    @PutMapping("/phone")
    public void setPhoneNumber(@RequestBody SetPhoneRequest request) {
        UserId userId = iamFacade.currentUserId();
        iamFacade.setPhoneNumber(userId, request.phoneNumber());
    }
}
