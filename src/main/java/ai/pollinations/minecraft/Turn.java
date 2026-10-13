package ai.pollinations.minecraft;

import java.util.List;

/** One message in an NPC conversation. Role is "user" or "assistant". */
record Turn(String role, String content) {

    /** Returns the newest {@code max} turns, oldest first. */
    static List<Turn> trim(List<Turn> turns, int max) {
        int keep = Math.max(0, max);
        if (turns.size() <= keep) {
            return List.copyOf(turns);
        }
        return List.copyOf(turns.subList(turns.size() - keep, turns.size()));
    }
}
