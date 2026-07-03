package com.example.roshdeandishe.iam.shared;

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
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public AuthController(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
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

    public record LoginRequest(String email, String password) {
    }

    public record LoginResponse(String email) {
    }
}
