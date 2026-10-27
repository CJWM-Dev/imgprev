package dev.example.imgprev.token;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class Base62Test {
    @ParameterizedTest
    @ValueSource(strings = {"0", "1", "61", "62", "3843", "3844", "18446744073709551615", "340282366920938463463374607431768211455"})
    void roundTrips(String n) {
        BigInteger v = new BigInteger(n);
        assertEquals(v, Base62.decode(Base62.encode(v)));
    }

    @Test void knownValues() {
        assertEquals("0", Base62.encode(BigInteger.ZERO));
        assertEquals("z", Base62.encode(BigInteger.valueOf(61)));
        assertEquals("10", Base62.encode(BigInteger.valueOf(62)));
    }

    @Test void rejectsBadInput() {
        assertThrows(IllegalArgumentException.class, () -> Base62.decode("ab-c"));
        assertThrows(IllegalArgumentException.class, () -> Base62.encode(BigInteger.valueOf(-1)));
    }

    @Test void leftPad() {
        assertEquals("000abc", Base62.leftPad("abc", 6));
        assertEquals("abcdef", Base62.leftPad("abcdef", 3));
    }
}
