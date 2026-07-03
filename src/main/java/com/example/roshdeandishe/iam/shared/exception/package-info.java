/**
 * مدل خطای دامنه‌محور ماژول iam، جدا از سایر concernهای iam.shared
 * (مثل RequiresModule/RequiresPermission و Aspectهای مربوطه).
 * شامل: {@code IamError} (sealed)، {@code IamException}،
 * {@code IamProblemResolver}، و {@code GlobalExceptionHandler}.
 * <p>
 * این Bean با نام صریح {@code "iamGlobalExceptionHandler"} ثبت می‌شود تا با
 * GlobalExceptionHandlerهای سایر ماژول‌ها (که نام کلاس یکسانی دارند اما در
 * پکیج‌های متفاوت هستند) تداخل نام Bean در Spring پیش نیاید.
 * <p>
 * این پکیج زیرمجموعه‌ی {@code iam.shared} است اما یک پکیج جداست، بنابراین
 * @NamedInterface روی iam.shared را به‌صورت خودکار به ارث نمی‌برد - باید
 * جداگانه علنی شود. لازم شد چون FinanceFacadeImpl حالا برای تشخیص
 * "کاربر احراز هویت‌شده وجود ندارد" به IamException وابسته است (نه برای
 * throw کردنش، فقط برای catch کردن آن هنگام صدا زدن IamFacade از یک
 * event listener داخلی که کاربر احراز هویت‌شده‌ای در کار نیست).
 */
@org.springframework.modulith.NamedInterface("shared.exception")
package com.example.roshdeandishe.iam.shared.exception;
