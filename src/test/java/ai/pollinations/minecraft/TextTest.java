package ai.pollinations.minecraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TextTest {

    @Test
    void cleanFlattensWhitespaceAndControlCharacters() {
        assertEquals("a b c", Text.clean("  a\t\tb\r\nc  ", 100));
        assertEquals("", Text.clean("   \n  ", 100));
    }

    @Test
    void cleanTruncatesWithAnEllipsisWithinTheLimit() {
        String clipped = Text.clean("abcdefghijklmnopqrstuvwxyz", 10);

        assertEquals("abcdefg...", clipped);
        assertEquals(10, clipped.length());
    }

    @Test
    void npcNamesAreShortAndSimple() {
        assertTrue(Text.validNpcName("Ada"));
        assertTrue(Text.validNpcName("smith_01"));
        assertTrue(Text.validNpcName("a".repeat(16)));
        assertFalse(Text.validNpcName("a".repeat(17)));
        assertFalse(Text.validNpcName("Ada Lovelace"));
        assertFalse(Text.validNpcName("bad/name"));
        assertFalse(Text.validNpcName(""));
    }

    @Test
    void trimKeepsOnlyTheNewestTurnsInOrder() {
        List<Turn> turns = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            turns.add(new Turn("user", "m" + i));
        }

        List<Turn> kept = Turn.trim(turns, 3);

        assertEquals(List.of(new Turn("user", "m3"), new Turn("user", "m4"), new Turn("user", "m5")), kept);
        assertEquals(List.of(), Turn.trim(turns, 0));
        assertEquals(5, Turn.trim(turns, 50).size());
    }
}
