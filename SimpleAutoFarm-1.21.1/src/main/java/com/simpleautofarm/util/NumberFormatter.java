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
     * (B), 16000 mB -&gt; "16" (B), 2147483647 mB -&gt; "2.15" (MB). Passing the same reference for an
     * amount and the tank's capacity keeps both numbers in one unit, and the unit never changes while
     * the tank fills. Values keep two decimals below 10 (so a nearly empty tank still reads "0.25")
     * and one decimal above it, with trailing zeros dropped, so a full tank reads "16" not "16.0".
     */
    public static String abbreviateFluid(int value, int reference) {
        if (reference < 1_000) {
            return String.valueOf(value);
        }
        if (reference < 1_000_000) {
            return fluidNumber(value / 1_000.0);
        }
        if (reference < 1_000_000_000) {
            return fluidNumber(value / 1_000_000.0);
        }
        return fluidNumber(value / 1_000_000_000.0);
    }

    private static String fluidNumber(double scaled) {
        String text = Math.abs(scaled) >= 10.0
                ? String.format(Locale.ROOT, "%.1f", scaled)
                : String.format(Locale.ROOT, "%.2f", scaled);
        int dot = text.indexOf('.');
        if (dot < 0) {
            return text;
        }
        int end = text.length();
        while (end > dot + 1 && text.charAt(end - 1) == '0') {
            end--;
        }
        if (text.charAt(end - 1) == '.') {
            end--;
        }
        return text.substring(0, end);
    }

    private static String scaled(int value, int divisor) {
        double v = value / (double) divisor;
        if (v >= 100.0) {
            return String.valueOf(Math.round(v));
        }
        return String.format(Locale.ROOT, "%.1f", v);
    }
}
