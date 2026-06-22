package com.example.roshdeandishe.iam.shared.exeption;

/**
 * زمانی پرتاب می‌شود که مدرسه‌ی جاری (schoolId از TenantContext) به ماژول
 * درخواست‌شده دسترسی ندارد (یا غیرفعال است یا اساساً subscribe نکرده است).
 * <p>
 * این Exception توسط com.example.roshdeandishe.iam.shared.exeption.GlobalExceptionHandler به یک پاسخ HTTP 403 ترجمه می‌شود.
 */
public class ModuleNotEnabledException extends RuntimeException {

    public ModuleNotEnabledException(String moduleName, Long schoolId) {
        super("ماژول '" + moduleName + "' برای مدرسه با شناسه " + schoolId + " فعال نیست.");
    }
}
