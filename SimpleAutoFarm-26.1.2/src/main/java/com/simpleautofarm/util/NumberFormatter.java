package com.simpleautofarm.util;

import java.util.Locale;

/**
 * Formats large numbers with a compact unit suffix (k / m / b) so they stay short in the GUI.
 */
public final class NumberFormatter {

    private NumberFormatter() {
    }

    /** 250 -&gt; "250", 2500 -&gt; "2.5k", 2000000 -&gt; "2.0m", 2147483647 -&gt; "2.1b". */
    public static String abbreviate(int value) {
        if (value >= 1_000_000_000) {
            return scaled(value, 1_000_000_000) + "b";
        }
        if (value >= 1_000_000) {
            return scaled(value, 1_000_000) + "m";
        }
        if (value >= 1_000) {
            return scaled(value, 1_000) + "k";
        }
        return String.valueOf(value);
    }

    /** Energy values use Minecraft's uppercase K / M / G convention: 2500 -&gt; "2.5K", 2147483647 -&gt; "2.1G". */
    public static String abbreviateEnergy(int value) {
        if (value >= 1_000_000_000) {
            return scaled(value, 1_000_000_000) + "G";
        }
        if (value >= 1_000_000) {
            return scaled(value, 1_000_000) + "M";
        }
        if (value >= 1_000) {
            return scaled(value, 1_000) + "K";
        }
        return String.valueOf(value);
    }

    /**
     * Unit a fluid level is shown in, chosen from a reference level (normally the tank's capacity).
     * The tiers step by 1000: mB, B (1000 mB), kB (1000 B) and MB (1000 kB).
     */
    public static String fluidUnit(int reference) {
        if (reference < 1_000) {
            return "mB";
        }
        if (reference < 1_000_000) {
            return "B";
        }
        if (reference < 1_000_000_000) {
            return "kB";
        }
        return "MB";
    }

    /**
     * Fluid level in the unit {@link #fluidUnit(int)} picks for {@code reference}: 250 mB -> "0.25"
     * (B), 16000 mB -> "16" (B), 2147483647 mB -> "2.14" (MB). Both the amount and the capacity are
     * passed with the same reference, so the pair never mixes units and the unit never jumps while the
     * tank fills. Decimals are truncated (never rounded) and trailing zeros are dropped.
     */
    public static String abbreviateFluid(int value, int reference) {
        if (reference < 1_000) {
            return String.valueOf(value);
        }
        if (reference < 1_000_000) {
            return fluidNumber(value, 1_000);
        }
        if (reference < 1_000_000_000) {
            return fluidNumber(value, 1_000_000);
        }
        return fluidNumber(value, 1_000_000_000);
    }

    /**
     * Truncates {@code value / divisor} to one (at 10 and above) or two decimals, in integer maths so
     * no float rounding can push the last digit up.
     */
    private static String fluidNumber(int value, int divisor) {
        boolean singleDecimal = value >= 10L * divisor;
        long scale = singleDecimal ? 10L : 100L;
        long units = (long) value * scale / divisor;
        long whole = units / scale;
        long fraction = units % scale;
        if (fraction == 0) {
            return String.valueOf(whole);
        }
        StringBuilder text = new StringBuilder().append(whole).append('.');
        if (!singleDecimal && fraction < 10) {
            text.append('0');
        }
        text.append(fraction);
        while (text.charAt(text.length() - 1) == '0') {
            text.setLength(text.length() - 1);
        }
        return text.toString();
    }

    private static String scaled(int value, int divisor) {
        double v = value / (double) divisor;
        if (v >= 100.0) {
            return String.valueOf(Math.round(v));
        }
        return String.format(Locale.ROOT, "%.1f", v);
    }
}
