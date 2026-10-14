package com.example.roshdeandishe.finance;

/**
 * وضعیتِ یک تخفیف (V30). APPLIED تخفیفِ بی‌قیدوشرط؛ PENDING مشروطِ باز (مهلت نگذشته و فاکتور هنوز کامل تسویه
 * نشده)؛ EARNED شرط برآورده شده و دائمی است؛ LAPSED مهلت بی‌نتیجه گذشته و تخفیف برداشته شده است.
 */
public enum DiscountStatus {
    APPLIED,
    PENDING,
    EARNED,
    LAPSED
}
