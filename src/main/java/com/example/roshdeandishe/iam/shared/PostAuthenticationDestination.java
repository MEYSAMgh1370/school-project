package com.example.roshdeandishe.iam.shared;

/**
 * مقصدهای مجاز پس از ورود از صفحه‌های عمومی. محدود کردن این مقدار به مسیرهای
 * مشخص، از تبدیل شدن پارامتر {@code next} به open redirect جلوگیری می‌کند.
 */
final class PostAuthenticationDestination {

    static final String DASHBOARD = "/dashboard";
    static final String SCHOOL_REGISTRATION = "/schools/new";

    private PostAuthenticationDestination() {
    }

    static String from(String requestedDestination) {
        return SCHOOL_REGISTRATION.equals(requestedDestination)
                ? SCHOOL_REGISTRATION
                : DASHBOARD;
    }
}
