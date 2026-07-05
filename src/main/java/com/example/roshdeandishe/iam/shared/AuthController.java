package com.example.roshdeandishe.iam.shared;

import com.example.roshdeandishe.iam.IamFacade;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;

/**
 * لاگین/لاگ‌اوت صریح از طریق JSON، به‌جای فرم HTML پیش‌فرض Spring Security
 * (که برای یک بک‌اند API-first معنا ندارد).
 * <p>
 * بعد از authenticate موفق، Authentication باید صراحتاً هم در
 * SecurityContextHolder (برای همین Request) و هم در HttpSession (تا
 * Requestهای بعدی همان کاربر را بشناسند) ذخیره شود - این دو کار خودکار
 * انجام نمی‌شوند چون از AuthenticationManager مستقیم استفاده شده، نه از
 * فیلتر پیش‌فرض Spring Security.
 */
@RestController
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final IamFacade iamFacade;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public AuthController(AuthenticationManager authenticationManager, IamFacade iamFacade) {
        this.authenticationManager = authenticationManager;
        this.iamFacade = iamFacade;
    }

    @PostMapping("/api/auth/login")
    public LoginResponse login(@RequestBody LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        Authentication authenticationRequest =
                new UsernamePasswordAuthenticationToken(request.email(), request.password());
        Authentication authenticationResult = authenticationManager.authenticate(authenticationRequest);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authenticationResult);
        SecurityContextHolder.setContext(context);
        // ذخیره در Session، تا Requestهای بعدی همین Context را بازیابی کنند
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        return new LoginResponse(authenticationResult.getName());
    }

    @PostMapping("/api/auth/logout")
    public void logout(HttpServletRequest httpRequest) {
        SecurityContextHolder.clearContext();
        if (httpRequest.getSession(false) != null) {
            httpRequest.getSession(false).invalidate();
        }
    }

    @PostMapping("/api/auth/activate")
    public ActivateAccountResponse activate(@RequestBody ActivateAccountRequest request) {
        iamFacade.redeemInviteToken(request.token(), request.newPassword());
        return new ActivateAccountResponse("حساب کاربری با موفقیت فعال شد. اکنون می‌توانید وارد شوید.");
    }

    /**
     * ثبت‌نام عمومی (self-service) - عمداً بدون احراز هویت، همان مدل اعتماد
     * /api/auth/login و /api/auth/activate. منطق و اعتبارسنجی در
     * {@code IamFacade.registerUser} است؛ صفحه‌ی HTML آن /register است.
     */
    @PostMapping("/api/auth/register")
    public RegisterResponse register(@RequestBody RegisterRequest request) {
        iamFacade.registerUser(request.email(), request.fullName(), request.password());
        return new RegisterResponse("ثبت‌نام با موفقیت انجام شد. اکنون می‌توانید وارد شوید.");
    }

    public record LoginRequest(String email, String password) {
    }

    public record LoginResponse(String email) {
    }

    public record ActivateAccountRequest(String token, String newPassword) {
    }

    public record ActivateAccountResponse(String message) {
    }

    public record RegisterRequest(String email, String fullName, String password) {
    }

    public record RegisterResponse(String message) {
    }
}
