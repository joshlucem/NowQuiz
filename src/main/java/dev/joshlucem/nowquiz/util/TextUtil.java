package dev.joshlucem.nowquiz.util;

import java.text.DecimalFormat;
import java.util.Collection;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Shared string helpers used by the command and quiz flows.
 */
public final class TextUtil {

    private static final DecimalFormat TWO_DECIMALS = new DecimalFormat("0.##");

    private TextUtil() {
    }

    public static String normalizeAnswer(String raw) {
        if (raw == null) {
            return "";
        }

        return raw.trim()
            .replaceAll("\\s+", " ")
            .toLowerCase(Locale.ROOT);
    }

    public static String formatMillis(long millis) {
        return Long.toString(Math.max(0L, millis));
    }

    public static String formatDouble(double value) {
        return TWO_DECIMALS.format(value);
    }

    public static String joinNames(Collection<String> names) {
        return names.stream()
            .filter(name -> name != null && !name.isBlank())
            .collect(Collectors.joining(", "));
    }
}
