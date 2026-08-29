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

    private static String scaled(int value, int divisor) {
        double v = value / (double) divisor;
        if (v >= 100.0) {
            return String.valueOf(Math.round(v));
        }
        return String.format(Locale.ROOT, "%.1f", v);
    }
}
