package dev.example.imgprev.token;

import java.math.BigInteger;
import java.util.Optional;

/**
 * Packs payload fields into one number, each field in its own radix, so a token spends no characters on separators
 * or rounding each field up to whole Base62 digits. Fields come back out in reverse order: the last one put is the
 * first one taken. Taking from a number that wasn't made this way gives garbage, never an exception, so callers
 * validate what they take and check {@link #isEmpty()} at the end.
 */
public final class Mixed {
    private BigInteger n;

    public Mixed() { this(BigInteger.ZERO); }
    private Mixed(BigInteger n) { this.n = n; }

    public static Optional<Mixed> fromBase62(String s) {
        try { return Optional.of(new Mixed(Base62.decode(s))); } catch (IllegalArgumentException e) { return Optional.empty(); }
    }

    public String toBase62() { return Base62.encode(n); }
    public boolean isEmpty() { return n.signum() == 0; }

    public Mixed put(BigInteger v, BigInteger radix) {
        if (v.signum() < 0 || v.compareTo(radix) >= 0) throw new IllegalArgumentException(v + " out of radix " + radix);
        n = n.multiply(radix).add(v);
        return this;
    }
    public Mixed put(long v, long radix) { return put(BigInteger.valueOf(v), BigInteger.valueOf(radix)); }
    public Mixed putBits(BigInteger v, int bits) { return put(v, BigInteger.ONE.shiftLeft(bits)); }

    public BigInteger take(BigInteger radix) {
        BigInteger[] qr = n.divideAndRemainder(radix);
        n = qr[0];
        return qr[1];
    }
    public int take(int radix) { return take(BigInteger.valueOf(radix)).intValue(); }
    public BigInteger takeBits(int bits) { return take(BigInteger.ONE.shiftLeft(bits)); }

    /** Each character as its index in {@code alphabet}, then the length, so {@link #takeString} reads the length first. */
    public Mixed putString(String s, String alphabet, int minLen, int maxLen) {
        if (s.length() < minLen || s.length() > maxLen) throw new IllegalArgumentException("length " + s.length());
        for (int i = 0; i < s.length(); i++) {
            int v = alphabet.indexOf(s.charAt(i));
            if (v < 0) throw new IllegalArgumentException("not in alphabet: " + s.charAt(i));
            put(v, alphabet.length());
        }
        return put(s.length() - minLen, maxLen - minLen + 1);
    }

    public String takeString(String alphabet, int minLen, int maxLen) {
        int len = take(maxLen - minLen + 1) + minLen;
        char[] out = new char[len];
        for (int i = len - 1; i >= 0; i--) out[i] = alphabet.charAt(take(alphabet.length()));
        return new String(out);
    }
}
