package io.github.cjwmdev.imgprev.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class AutoConvertTest {
    static final List<String> COMMANDS = List.of("msg", "pc");

    @Test void onlyListedCommandsAreConverted() {
        assertTrue(AutoConvert.appliesTo("pc look at this", COMMANDS));
        assertTrue(AutoConvert.appliesTo("PC look", COMMANDS), "case doesn't matter");
        assertTrue(AutoConvert.appliesTo("pc", COMMANDS), "even with nothing after it");
        assertFalse(AutoConvert.appliesTo("pcx look", COMMANDS));
        assertFalse(AutoConvert.appliesTo("give @s diamond", COMMANDS));
    }

    @Test void aCommandsSlashCountsTowardsTheLimit() {
        String text = "x".repeat(AutoConvert.MAX_LENGTH);
        assertEquals(AutoConvert.MAX_LENGTH, AutoConvert.sentLength(text, false));
        assertEquals(AutoConvert.MAX_LENGTH + 1, AutoConvert.sentLength(text, true));
    }
}
