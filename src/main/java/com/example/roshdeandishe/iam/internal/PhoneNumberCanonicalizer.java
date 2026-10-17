package com.example.roshdeandishe.iam.internal;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Canonicalizes user-entered phone numbers at the IAM boundary. The product
 * accepts Persian and Arabic-Indic digits in RTL forms, while persistence and
 * external providers use ASCII digits.
 *
 * <p>Iranian mobile numbers have one canonical form, {@code 09XXXXXXXXX}
 * (product decision, 2026-10-17): after digit conversion and boundary trim,
 * spaces, {@code -}, {@code (}, {@code )} and {@code .} are removed, and
 * {@code 09XXXXXXXXX}, {@code 9XXXXXXXXX}, {@code 989XXXXXXXXX},
 * {@code +989XXXXXXXXX} and {@code 00989XXXXXXXXX} all map to
 * {@code 09XXXXXXXXX}. Bale shares contacts in the international form, so
 * this is what lets a {@code 98…} contact match a {@code 09…} account.
 * Any other number (landline, foreign) keeps its punctuation and
 * country-code formatting unchanged. {@code V32} applies the same rule to
 * existing rows.
 */
final class PhoneNumberCanonicalizer {

    private static final Pattern MOBILE_SEPARATORS = Pattern.compile("[ \\-().]");
    private static final Pattern IRANIAN_MOBILE = Pattern.compile("(?:0|98|\\+98|0098)?(9\\d{9})");

    private PhoneNumberCanonicalizer() {
    }

    static String canonicalize(String value) {
        if (value == null) {
            return null;
        }
        String stripped = stripBoundaryWhitespace(value);
        StringBuilder canonical = new StringBuilder(stripped.length());
        for (int index = 0; index < stripped.length(); index++) {
            char character = stripped.charAt(index);
            if (character >= '۰' && character <= '۹') {
                canonical.append((char) ('0' + character - '۰'));
            } else if (character >= '٠' && character <= '٩') {
                canonical.append((char) ('0' + character - '٠'));
            } else {
                canonical.append(character);
            }
        }
        return canonicalizeIranianMobile(canonical.toString());
    }

    private static String canonicalizeIranianMobile(String value) {
        Matcher mobile = IRANIAN_MOBILE.matcher(MOBILE_SEPARATORS.matcher(value).replaceAll(""));
        return mobile.matches() ? "0" + mobile.group(1) : value;
    }

    private static String stripBoundaryWhitespace(String value) {
        int start = 0;
        int end = value.length();

        while (start < end) {
            int codePoint = value.codePointAt(start);
            if (!isBoundaryWhitespace(codePoint)) {
                break;
            }
            start += Character.charCount(codePoint);
        }
        while (start < end) {
            int codePoint = value.codePointBefore(end);
            if (!isBoundaryWhitespace(codePoint)) {
                break;
            }
            end -= Character.charCount(codePoint);
        }
        return value.substring(start, end);
    }

    private static boolean isBoundaryWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }
}
