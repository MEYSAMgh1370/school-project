package com.example.roshdeandishe.iam.config;

/**
 * مقصدهای مجاز پس از ورود از صفحه‌های عمومی. محدود کردن این مقدار به مسیرهای
 * مشخص، از تبدیل شدن پارامتر {@code next} به open redirect جلوگیری می‌کند.
 */
public final class PostAuthenticationDestination {

    static final String DASHBOARD = "/dashboard";
    static final String SCHOOL_REGISTRATION = "/schools/new";
    static final String LOGIN = "/login";

    private PostAuthenticationDestination() {
    }

    public static String from(String requestedDestination) {
        return SCHOOL_REGISTRATION.equals(requestedDestination)
                ? SCHOOL_REGISTRATION
                : DASHBOARD;
    }

    /**
     * صفحه‌ی ورودی که یک درخواستِ ناشناس به {@code requestedPath} باید به آن
     * ری‌دایرکت شود. فقط مسیرهای همین allow-list مقصدشان را حفظ می‌کنند؛ بقیه‌ی
     * صفحه‌های محافظت‌شده مثل قبل به {@code /login} خالی می‌روند - رجوع به
     * {@link SecurityConfig}.
     */
    static String loginUrlFor(String requestedPath) {
        return SCHOOL_REGISTRATION.equals(requestedPath)
                ? LOGIN + "?next=" + SCHOOL_REGISTRATION
                : LOGIN;
    }
}
