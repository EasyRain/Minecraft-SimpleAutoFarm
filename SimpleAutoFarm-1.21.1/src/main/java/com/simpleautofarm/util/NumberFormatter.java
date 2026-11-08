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
     * The tiers step by 1000: mB, B (1000 mB), kB (1000 B) and MB (1000 kB). "kB" / "MB" only appear
     * when the yield (or the creative) upgrade pushes a tank into the millions, where the bucket
     * reading would grow to seven digits; a normal 16-bucket tank always stays at "B".
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
     * Fluid level in the unit {@link #fluidUnit(int)} picks for {@code reference}: 250 mB -&gt; "0.25"
     * (B), 16000 mB -&gt; "16" (B), 2147483647 mB -&gt; "2.14" (MB). Passing the same reference for an
     * amount and the tank's capacity keeps both numbers in one unit, and the unit never changes while
     * the tank fills. Values keep two decimals below 10 (so a nearly empty tank still reads "0.25")
     * and one decimal above it, with trailing zeros dropped, so a full tank reads "16" not "16.0".
     * The decimals are truncated, not rounded, like most mods do it.
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
     * Truncates {@code value / divisor} to one (at 10 and above) or two decimals. The cut is done in
     * integer maths, so no float rounding can push the last digit up: 2147483647 mB in MB stays
     * "2.14" instead of becoming "2.15", and 4096 mB in B stays "4.09" instead of "4.1".
     */
    private static String fluidNumber(int value, int divisor) {
        boolean singleDecimal = value >= 10L * divisor;
        long scale = singleDecimal ? 10L : 100L;
        long units = (long) value * scale / divisor; // truncated towards zero; values are never negative
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
