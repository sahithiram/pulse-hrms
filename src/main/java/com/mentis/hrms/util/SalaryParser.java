package com.mentis.hrms.util;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Parses legacy salary text into DECIMAL(15,2).\n * Accepts: "50000", "50,000", "₹50,000.00", "$1,25,000", "USD 50000", "INR 90000".
 * Rejects: empty, "abc", parentheses, accounting negatives, "10L", stray letters
 * (only known currency tokens are stripped before checking for leftover letters).
 * REQ-1-03: anything rejected goes to data_migration_issues, never guessed.
 */
public final class SalaryParser {

    private static final Pattern CURRENCY_TOKENS = Pattern.compile(
            "(?i)\\b(USD|INR|EUR|GBP|AUD|CAD|SGD|AED|RS|RUPEES?|DOLLARS?|EUROS?|POUNDS?)\\b");
    private static final Pattern SYMBOLS = Pattern.compile("[₹$€£]");
    private static final Pattern NON_NUMERIC = Pattern.compile("[^0-9.]");
    private static final Pattern HAS_LETTER = Pattern.compile("[A-Za-z]");

    private SalaryParser() {}

    public static Optional<BigDecimal> parse(String raw) {
        if (raw == null) return Optional.empty();
        String t = raw.trim();
        if (t.isEmpty()) return Optional.empty();
        if (t.contains("(") || t.contains(")") || t.contains("-") || t.contains("+")) return Optional.empty();

        // Strip known currency tokens/symbols; if LETTERS remain, the value is not a plain salary.
        String stripped = CURRENCY_TOKENS.matcher(t).replaceAll(" ");
        stripped = SYMBOLS.matcher(stripped).replaceAll(" ");
        if (HAS_LETTER.matcher(stripped).find()) return Optional.empty();

        String digits = NON_NUMERIC.matcher(stripped).replaceAll("");
        if (digits.isEmpty()) return Optional.empty();

        int dot = digits.indexOf('.');
        if (dot >= 0 && digits.indexOf('.', dot + 1) >= 0) return Optional.empty();

        try {
            BigDecimal v = new BigDecimal(digits);
            if (v.compareTo(BigDecimal.ZERO) < 0) return Optional.empty();
            if (v.precision() > 15) return Optional.empty();
            return Optional.of(v.setScale(2, java.math.RoundingMode.UNNECESSARY));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
