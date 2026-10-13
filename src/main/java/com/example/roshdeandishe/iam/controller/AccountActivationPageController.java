package com.example.roshdeandishe.iam.controller;

import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.IamFacade.InviteLookup;
import com.example.roshdeandishe.iam.IamFacade.InviteState;
import com.example.roshdeandishe.iam.model.ActivatePageModel;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * فقط رندر صفحه‌ی "تعیین رمز عبور" - منطق فعال‌سازی واقعی همچنان در
 * {@link AuthController#activate} و {@code IamFacade.redeemInviteToken} باقی
 * می‌ماند. این کنترلر عمداً {@code @Controller} است (نه {@code @RestController})
 * چون تنها Controllerِ رندرکننده‌ی View در کل پروژه است - اولین صفحه‌ی HTML
 * واقعی این بک‌اند.
 * <p>
 * در iam.shared قرار دارد، نه یک پکیج جدید مثل iam.web، چون فعال‌سازی حساب
 * مفهوماً همان دغدغه‌ی AuthController/SecurityConfig است - یک پکیج تک‌فایلی
 * جدید برای یک صفحه توجیه ندارد.
 */
@Controller
public class AccountActivationPageController {

    private final IamFacade iamFacade;

    public AccountActivationPageController(IamFacade iamFacade) {
        this.iamFacade = iamFacade;
    }

    @GetMapping("/activate")
    public String activatePage(@RequestParam(required = false) String token, Model model) {
        String safeToken = token == null ? "" : token;
        InviteLookup invite = safeToken.isBlank() ? InviteLookup.of(InviteState.UNKNOWN)
                : iamFacade.describeInvite(safeToken);
        model.addAttribute("model", new ActivatePageModel(safeToken, invite));
        return "activate";
    }
}
