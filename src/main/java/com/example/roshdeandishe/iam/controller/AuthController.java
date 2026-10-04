package com.example.roshdeandishe.iam.controller;

import com.example.roshdeandishe.iam.IamFacade;
import com.example.roshdeandishe.iam.config.JwtTokenProvider;
import com.example.roshdeandishe.iam.dto.request.ActivateAccountRequest;
import com.example.roshdeandishe.iam.dto.response.ActivateAccountResponse;
import com.example.roshdeandishe.iam.dto.request.LoginRequest;
import com.example.roshdeandishe.iam.dto.response.LoginResponse;
import com.example.roshdeandishe.iam.dto.request.OtpRequestRequest;
import com.example.roshdeandishe.iam.dto.response.OtpRequestResponse;
import com.example.roshdeandishe.iam.dto.request.OtpVerifyRequest;
import com.example.roshdeandishe.iam.dto.request.RegisterRequest;
import com.example.roshdeandishe.iam.dto.response.RegisterResponse;
import com.example.roshdeandishe.iam.dto.response.TokenResponse;
import com.example.roshdeandishe.iam.shared.exception.IamError;
import com.example.roshdeandishe.iam.shared.exception.IamException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;

import java.util.Collections;

/**
 * لاگین/لاگ‌اوت صریح از طریق JSON، به‌جای فرم HTML پیش‌فرض Spring Security
 * (که برای یک بک‌اند API-first معنا ندارد).
 * <p>
 * بعد از authenticate موفق، Authentication باید صراحتاً هم در
 * SecurityContextHolder (برای همین Request) و هم در HttpSession (تا
 * Requestهای بعدی همان کاربر را بشناسند) ذخیره شود - این دو کار خودکار
 * انجام نمی‌شوند چون از AuthenticationManager مستقیم استفاده شده، نه از
 * فیلتر پیش‌فرض Spring Security.
 * <p>
 * {@code /api/auth/token} همان اعتبارسنجی {@code login} را (از طریق
 * {@link #authenticate}) به‌اشتراک می‌گذارد، اما به‌جای session cookie یک
 * JWT برمی‌گرداند - مسیر احراز هویت اپ موبایل (additive، نه جایگزین session).
 */
@RestController
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final IamFacade iamFacade;
    private final JwtTokenProvider jwtTokenProvider;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public AuthController(AuthenticationManager authenticationManager, IamFacade iamFacade, JwtTokenProvider jwtTokenProvider) {
        this.authenticationManager = authenticationManager;
        this.iamFacade = iamFacade;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @PostMapping("/api/auth/login")
    public LoginResponse login(@RequestBody LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        Authentication authenticationResult = authenticate(request);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authenticationResult);
        SecurityContextHolder.setContext(context);
        // ذخیره در Session، تا Requestهای بعدی همین Context را بازیابی کنند
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        return new LoginResponse(authenticationResult.getName());
    }

    /**
     * صدور JWT برای اپ موبایل - همان اعتبارسنجی credential که login دارد،
     * بدون هیچ HttpSession ای (stateless؛ کلاینت توکن را خودش نگه می‌دارد).
     */
    @PostMapping("/api/auth/token")
    public TokenResponse issueToken(@RequestBody LoginRequest request) {
        Authentication authenticationResult = authenticate(request);
        String token = jwtTokenProvider.generateToken(authenticationResult.getName());
        return new TokenResponse(token, "Bearer", jwtTokenProvider.expirationSeconds());
    }

    /**
     * اعتبارسنجی مشترک login/token. قبلاً یک AuthenticationException ناموفق
     * بدون catch از این‌جا رد می‌شد و به IamExceptionHandler/IamProblemResolver
     * نمی‌رسید - یعنی PlatformExceptionHandler آن را ۵۰۰ عمومی می‌کرد،
     * به‌جای ۴۰۱ صحیح. اکنون به IamError.InvalidCredentials نگاشت می‌شود.
     */
    private Authentication authenticate(LoginRequest request) {
        try {
            Authentication authenticationRequest =
                    new UsernamePasswordAuthenticationToken(request.email(), request.password());
            return authenticationManager.authenticate(authenticationRequest);
        } catch (AuthenticationException e) {
            throw new IamException(new IamError.InvalidCredentials("ایمیل یا رمز عبور نادرست است."));
        }
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

    /**
     * درخواست کد تأیید پیامکی - فاکتور دوم ورود برای کاربری که قبلاً (با
     * سشن معتبر) شماره تلفنش را ثبت کرده. عمداً بدون احراز هویت، همان مدل
     * اعتماد /api/auth/login: خودِ کد پیامکی اثبات مالکیت شماره است.
     */
    @PostMapping("/api/auth/otp/request")
    public OtpRequestResponse requestOtp(@RequestBody OtpRequestRequest request) {
        iamFacade.requestOtp(request.phoneNumber());
        return new OtpRequestResponse("کد تأیید ارسال شد.");
    }

    /**
     * تکمیل ورود با کد تأیید. برخلاف login (که credential را از طریق
     * AuthenticationManager اعتبارسنجی می‌کند)، اینجا IamFacade.verifyOtp
     * خودِ اعتبارسنجی دامنه را انجام می‌دهد؛ این متد فقط - دقیقاً مثل login -
     * یک Authentication معتبر می‌سازد و در Session ذخیره می‌کند. بدون رمز
     * عبور، چون کد پیامکی خودش اثبات هویت است - AuthenticationManager هیچ
     * نقشی در این مسیر ندارد.
     */
    @PostMapping("/api/auth/otp/verify")
    public LoginResponse verifyOtp(@RequestBody OtpVerifyRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        IamFacade.UserProfile profile = iamFacade.verifyOtp(request.phoneNumber(), request.code());

        Authentication authenticationResult =
                new UsernamePasswordAuthenticationToken(profile.email(), null, Collections.emptyList());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authenticationResult);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        return new LoginResponse(authenticationResult.getName());
    }
}
