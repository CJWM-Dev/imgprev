package io.github.cjwmdev.imgprev.site;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.cjwmdev.imgprev.token.ResolvedMedia;
import io.github.cjwmdev.imgprev.token.TokenCodec;
import java.util.Locale;
import java.util.regex.Matcher;

public final class TestTokens {
    private TestTokens() {}

    public static String token(String url) {
        return TokenCodec.encode(url).orElseThrow(() -> new AssertionError("not encodable: " + url));
    }

    public static ResolvedMedia media(String url) {
        return TokenCodec.decode(token(url)).orElseThrow(() -> new AssertionError("token didn't decode: " + url));
    }

    /** sender URL → token → decoded → rebuilt download URL */
    public static String rebuilt(String url) {
        ResolvedMedia m = media(url);
        return m.codec().mediaUri(m.payload()).toString();
    }

    /** {@code token} with its check character changed, so it no longer decodes. */
    public static String withBadCheck(String token) {
        int at = token.length() - 3;
        return token.substring(0, at) + (token.charAt(at) == 'A' ? 'B' : 'A') + "~>";
    }

    /**
     * Tokens must get past chat link filters: no '.', '/', ':' or "http", the whole token matches the pattern, and the
     * payload is at most 200 characters.
     */
    public static void assertLinkFree(String token) {
        Matcher m = TokenCodec.TOKEN.matcher(token);
        assertTrue(m.matches(), token);
        assertTrue(m.group(3).length() <= 200, token);
        for (String bad : new String[] {".", "/", ":"}) assertFalse(token.contains(bad), token);
        assertFalse(token.toLowerCase(Locale.ROOT).contains("http"), token);
    }
}
