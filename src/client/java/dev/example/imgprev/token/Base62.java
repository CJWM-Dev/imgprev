package dev.example.imgprev.token;

import java.math.BigInteger;

public final class Base62 {
    public static final String A ="0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final BigInteger B = BigInteger.valueOf(62);

    private Base62() {}

    public static String encode(BigInteger n) {
        if (n.signum() < 0) throw new IllegalArgumentException("negative");
        if (n.signum() == 0) return "0";
        StringBuilder sb = new StringBuilder();
        while (n.signum() > 0) { BigInteger[] qr = n.divideAndRemainder(B); sb.append(A.charAt(qr[1].intValue())); n = qr[0]; }
        return sb.reverse().toString();
    }

    public static BigInteger decode(String s) {
        BigInteger n = BigInteger.ZERO;
        for (char ch : s.toCharArray()) {
            int v = A.indexOf(ch);
            if (v < 0) throw new IllegalArgumentException("not base62: " + ch);
            n = n.multiply(B).add(BigInteger.valueOf(v));
        }
        return n;
    }

    public static String leftPad(String s, int len) { return "0".repeat(Math.max(0, len - s.length())) + s; }
}
