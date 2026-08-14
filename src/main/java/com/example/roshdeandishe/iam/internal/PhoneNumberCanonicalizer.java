package com.example.roshdeandishe.iam.internal;

/**
 * Canonicalizes user-entered phone numbers at the IAM boundary. The product
 * accepts Persian and Arabic-Indic digits in RTL forms, while persistence and
 * external providers use ASCII digits. Punctuation is deliberately preserved;
 * changing country-code or separator rules requires a product decision.
 */
final class PhoneNumberCanonicalizer {

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
        return canonical.toString();
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
