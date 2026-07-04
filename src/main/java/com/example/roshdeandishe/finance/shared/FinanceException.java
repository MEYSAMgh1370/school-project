package com.example.roshdeandishe.finance.shared;

public final class FinanceException extends RuntimeException {

    private final FinanceError error;

    public FinanceException(FinanceError error) {
        super(describe(error));
        this.error = error;
    }

    public FinanceError error() {
        return error;
    }

    private static String describe(FinanceError error) {
        return switch (error) {
            case FinanceError.TuitionFeeNotSet e ->
                    "برای مدرسه‌ی با شناسه " + e.schoolId() + " هنوز شهریه‌ای تنظیم نشده است.";
            case FinanceError.InvalidTuitionFee e -> e.reason();
        };
    }
}
