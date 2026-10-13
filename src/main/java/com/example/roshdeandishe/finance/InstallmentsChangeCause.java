package com.example.roshdeandishe.finance;

/** علتِ تغییرِ طرحِ اقساط؛ در جزئیاتِ audit می‌آید تا تغییرِ خودکار از دستیِ ادمین جدا باشد. */
public enum InstallmentsChangeCause {
    /** ساخت یا بازشمارشِ طرح ({@code generateInstallments}). */
    GENERATED,
    /** ویرایشِ دستیِ مبلغ/سررسید ({@code updateInstallments}). */
    EDITED,
    /** تقسیمِ دوباره‌ی اقساطِ پرداخت‌نشده پس از ثبتِ تخفیف. */
    DISCOUNT_RESPLIT,
    /** تقسیمِ دوباره‌ی خودکار پس از برداشته شدنِ تخفیفِ مشروط (ویرایش‌های دستی از بین می‌روند). */
    DISCOUNT_LAPSE_RESPLIT
}
