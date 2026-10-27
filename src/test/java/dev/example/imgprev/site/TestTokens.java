package dev.example.imgprev.site;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.example.imgprev.token.ResolvedMedia;
import dev.example.imgprev.token.TokenCodec;
import java.util.Locale;
import java.util.regex.Matcher;

final class TestTokens {
    private TestTokens() {}

    static String token(String url) {
        return TokenCodec.encode(url).orElseThrow(() -> new AssertionError("not encodable: " + url));
    }

    static ResolvedMedia media(String url) {
        return TokenCodec.decode(token(url)).orElseThrow(() -> new AssertionError("token didn't decode: " + url));
    }

    /** sender URL → token → decoded → rebuilt download URL */
    static String rebuilt(String url) {
        ResolvedMedia m = media(url);
        return m.codec().mediaUri(m.payload()).join().toString();
    }

    /** §11: no '.', '/', ':' or "http"; full regex match; payload ≤ 200. */
    static void assertLinkFree(String token) {
        Matcher m = TokenCodec.TOKEN.matcher(token);
        assertTrue(m.matches(), token);
        assertTrue(m.group(3).length() <= 200, token);
        for (String bad : new String[] {".", "/", ":"}) assertFalse(token.contains(bad), token);
        assertFalse(token.toLowerCase(Locale.ROOT).contains("http"), token);
    }
}
