package io.github.cjwmdev.imgprev.token;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class MixedTest {
    @Test void fieldsComeBackInReverseOrder() {
        String s = new Mixed().put(3, 5).putString("a0Z", Base62.A, 1, 10).putBits(BigInteger.ONE.shiftLeft(100), 128).put(0, 2).toBase62();
        Mixed m = Mixed.fromBase62(s).orElseThrow();
        assertEquals(0, m.take(2));
        assertEquals(BigInteger.ONE.shiftLeft(100), m.takeBits(128));
        assertEquals("a0Z", m.takeString(Base62.A, 1, 10));
        assertEquals(3, m.take(5));
        assertTrue(m.isEmpty());
    }

    @Test void outOfRangeValuesThrow() {
        assertThrows(IllegalArgumentException.class, () -> new Mixed().put(5, 5));
        assertThrows(IllegalArgumentException.class, () -> new Mixed().putString("", Base62.A, 1, 10));
        assertThrows(IllegalArgumentException.class, () -> new Mixed().putString("a-b", Base62.A, 1, 10));
    }

    @Test void nonBase62InputIsEmpty() {
        assertTrue(Mixed.fromBase62("a-b").isEmpty());
    }
}
