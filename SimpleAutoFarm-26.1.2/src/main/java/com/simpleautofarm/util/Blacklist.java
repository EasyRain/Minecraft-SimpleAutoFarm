package com.simpleautofarm.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Matches ids against a user supplied blacklist.
 *
 * <p>Every entry is trimmed and lower-cased; {@code modid:*} blocks a whole namespace and anything else
 * must equal the id exactly. This is shared by the random entity and material card pools, so both accept
 * the same syntax.
 */
public final class Blacklist {

    /** A normalised snapshot of one blacklist, cheap to compare for "the player edited the config" checks. */
    public record Entries(List<String> exact, List<String> namespaces) {

        public static Entries of(List<? extends String> raw) {
            List<String> exact = new ArrayList<>();
            List<String> namespaces = new ArrayList<>();
            for (String entry : raw) {
                if (entry == null) {
                    continue;
                }
                String value = entry.trim().toLowerCase(Locale.ROOT);
                if (value.isEmpty()) {
                    continue;
                }
                if (value.endsWith(":*")) {
                    String namespace = value.substring(0, value.length() - 2);
                    if (!namespace.isEmpty()) {
                        namespaces.add(namespace);
                    }
                } else if (!value.contains("*")) {
                    exact.add(value);
                }
            }
            return new Entries(List.copyOf(exact), List.copyOf(namespaces));
        }

        /** Whether the id (namespace:path) is blocked. */
        public boolean blocks(String id) {
            if (id == null || id.isEmpty()) {
                return false;
            }
            String value = id.toLowerCase(Locale.ROOT);
            return exact.contains(value) || namespaces.contains(value.substring(0, value.indexOf(':')));
        }

        public boolean isEmpty() {
            return exact.isEmpty() && namespaces.isEmpty();
        }
    }

    private Blacklist() {
    }
}
