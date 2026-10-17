package com.example.roshdeandishe.iam.internal;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneNumberCanonicalizerTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "09335551287",
            "9335551287",
            "989335551287",
            "+989335551287",
            "00989335551287",
            "۰۹۳۳۵۵۵۱۲۸۷",
            "۹۸۹۳۳۵۵۵۱۲۸۷",
            "+۹۸۹۳۳۵۵۵۱۲۸۷",
            "٩٨٩٣٣٥٥٥١٢٨٧",
            "+98 933 555 1287",
            "0933-555-1287",
            "(0933) 555 1287",
            "0933.555.1287",
            "  +98 (933) 555-12-87 ",
    })
    void everyIranianMobileFormMapsToTheNationalForm(String input) {
        assertThat(PhoneNumberCanonicalizer.canonicalize(input)).isEqualTo("09335551287");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "021-88776655",
            "02188776655",
            "+98 21 8877 6655",
            "+1 (555) 123-4567",
            "+447911123456",
            "0093551234567",
            "98933555128",
            "0989335551287",
            "123",
    })
    void nonMobileNumbersKeepTheirFormatting(String input) {
        assertThat(PhoneNumberCanonicalizer.canonicalize(input)).isEqualTo(input);
    }

    @Test
    void nonMobileNumbersStillGetDigitConversionAndBoundaryTrim() {
        assertThat(PhoneNumberCanonicalizer.canonicalize(" ۰۲۱-۸۸۷۷۶۶۵۵ ")).isEqualTo("021-88776655");
    }

    @Test
    void nullAndBlankArePreserved() {
        assertThat(PhoneNumberCanonicalizer.canonicalize(null)).isNull();
        assertThat(PhoneNumberCanonicalizer.canonicalize("   ")).isEmpty();
    }
}
