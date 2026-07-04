package com.example.roshdeandishe.finance.shared;

/**
 * مدل خطای دامنه‌محور مختص ماژول finance.
 */
public sealed interface FinanceError {

    record TuitionFeeNotSet(Long schoolId) implements FinanceError {
    }

    record InvalidTuitionFee(String reason) implements FinanceError {
    }
}
