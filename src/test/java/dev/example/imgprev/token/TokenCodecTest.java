package dev.example.imgprev.token;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TokenCodecTest {
    /** Builds a token with a correct checksum around an arbitrary body. */
    static String craft(String body) { return "<~" + body + TokenCodec.check(body) + "~>"; }

    @Test void craftedValidTokenDecodes() {
        assertTrue(TokenCodec.decode(craft("1gkj8RVuDxh4x7W")).isPresent());
    }

    @Test void wrongChecksumRejected() {
        String good = craft("1gkj8RVuDxh4x7W");
        char c = good.charAt(good.length() - 3);
        String bad = good.substring(0, good.length() - 3) + (c == 'A' ? 'B' : 'A') + "~>";
        assertTrue(TokenCodec.decode(bad).isEmpty());
    }

    @Test void otherVersionRejected() {
        assertTrue(TokenCodec.decode(craft("2gkj8RVuDxh4x7W")).isEmpty());
    }

    @Test void unknownSiteCodeRejected() {
        assertTrue(TokenCodec.decode(craft("1qkj8RVuDxh4x7W")).isEmpty());
        assertTrue(TokenCodec.decode(craft("Qkj8RVuDxh4x7W")).isEmpty());
    }

    @Test void headerCaseMustMatchVersion() {
        assertTrue(TokenCodec.decode(craft("Gkj8RVuDxh4x7W")).isPresent());
        assertTrue(TokenCodec.decode(craft("gkj8RVuDxh4x7W")).isEmpty());
        assertTrue(TokenCodec.decode(craft("1Gkj8RVuDxh4x7W")).isEmpty());
    }

    @Test void v2PayloadsHaveNoDashes() {
        assertTrue(TokenCodec.decode(craft("Gkj8RV-uDxh4x7W")).isEmpty());
    }

    /** Tokens from 0.1.x stay readable and share the cache entry of the same image's new token. */
    @ParameterizedTest
    @ValueSource(strings = {
        "<~1gkj8RVuDxh4x7W%s~>|https://i.giphy.com/media/kj8RVuDxh4x7W/giphy.gif",
        "<~1t6n0lkxsBEQkAAAAC%s~>|https://media.tenor.com/6n0lkxsBEQkAAAAC/x.gif",
        "<~1cpsa4ue3%s~>|https://files.catbox.moe/sa4ue3.png",
    })
    void v1TokensStillDecode(String row) {
        String[] f = row.split("\\|");
        String body = f[0].substring(2, f[0].indexOf('%'));
        ResolvedMedia v1 = TokenCodec.decode(craft(body)).orElseThrow(() -> new AssertionError(body));
        ResolvedMedia v2 = TokenCodec.decode(TokenCodec.encode(f[1]).orElseThrow()).orElseThrow();
        assertEquals(v2.key(), v1.key());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "1gabc",            // giphy id < 5
        "1ixAbC12xY",       // imgur ext code not p/j/g
        "1ipAbC",           // imgur hash too short
        "1tshort",          // tenor key < 8
        "1es",              // emoji without snowflake
        "1d1-2-3",          // discord attachment needs 6 fields
        "1zx123",           // gyazo ext code not p/j/g
        "1pAbC",            // postimages id < 6
        "1bAbC12-xY",       // imgbb id with '-'
        "1cxsa4ue3",        // catbox ext code not p/j/e/g/w
        "1cpSA4UE3",        // catbox ids are lowercase
    })
    void payloadPatternMismatchRejected(String body) {
        assertTrue(TokenCodec.decode(craft(body)).isEmpty(), body);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "http://i.imgur.com/AbC12xY.png",
        "ftp://i.imgur.com/AbC12xY.png",
        "https://example.com/AbC12xY.png",
        "https://evil.com/i.imgur.com/AbC12xY.png",
        "https://i.imgur.com.evil.com/AbC12xY.png",
        "https://imgur.com/a/AbC12xY",
        "https://imgur.com/gallery/AbC12xY",
        "https://tenor.com/view/cat-gif-123456",
        "https://media.tenor.com/1S3s5iesWX0AAAPo/x.mp4",
        "https://giphy.com/explore/cat",
        "https://cdn.discordapp.com/attachments/1100000000000000001/1300000000000000002/image.png",
        "https://i.gyazo.com/0123.png",
        "https://postimg.cc/PCDc5McC",               // page links need a lookup
        "https://ibb.co/S7csSRwd",
        "https://i.ibb.co/M5f2dMkG/",
        "https://i.ibb.co/M5f2dMkG/a/b.png",
        "https://files.catbox.moe/sa4ue3.PNG",       // catbox 404s on a different case
        "https://files.catbox.moe/SA4UE3.png",
        "https://files.catbox.moe/sa4ue3.mp4",
        "https://files.catbox.moe/sa4ue3",
        "https://litter.catbox.moe/sa4ue3.png",
        "javascript:alert(1)",
        "not a url",
        "https://",
        "",
    })
    void encodeRejects(String url) {
        assertTrue(TokenCodec.encode(url).isEmpty(), url);
    }

    @Test void encodeIgnoresSchemeAndHostCase() {
        assertEquals(TokenCodec.encode("https://i.imgur.com/AbC12xY.png"), TokenCodec.encode("HTTPS://I.IMGUR.COM/AbC12xY.png"));
        assertTrue(TokenCodec.encode("https://i.imgur.com/AbC12xY.png").isPresent());
    }

    @Test void findsEveryTokenInSurroundingText() {
        String a = TokenCodec.encode("https://i.imgur.com/AbC12xY.png").orElseThrow();
        String b = TokenCodec.encode("https://media4.giphy.com/media/kj8RVuDxh4x7W/giphy.gif").orElseThrow();
        Matcher m = TokenCodec.TOKEN.matcher("look " + a + "and" + b + " ok");
        List<String> found = new ArrayList<>();
        while (m.find()) { found.add(m.group()); assertTrue(TokenCodec.decode(m).isPresent()); }
        assertEquals(List.of(a, b), found);
    }
}
