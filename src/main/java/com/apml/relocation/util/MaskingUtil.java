package com.apml.relocation.util;

public final class MaskingUtil {
    private MaskingUtil() {}

    /** Masks all but the last four characters, e.g. 123456789012 -> ********9012. */
    public static String mask(String value) {
        if (value == null || value.isBlank()) return null;
        if (value.length() <= 4) return "*".repeat(value.length());
        return "*".repeat(value.length() - 4) + value.substring(value.length() - 4);
    }
}
