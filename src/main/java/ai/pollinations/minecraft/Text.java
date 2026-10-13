package ai.pollinations.minecraft;

import java.util.regex.Pattern;

/** Cleanup and validation for text typed by players and admins. */
final class Text {

    private static final Pattern NPC_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private Text() {}

    static boolean validNpcName(String name) {
        return NPC_NAME.matcher(name).matches();
    }

    /** Collapses whitespace and control characters, then truncates to {@code max} characters. */
    static String clean(String text, int max) {
        String flat = text.replaceAll("[\\p{Cntrl}\\s]+", " ").trim();
        if (flat.length() <= max) {
            return flat;
        }
        return flat.substring(0, Math.max(0, max - 3)).stripTrailing() + "...";
    }
}
