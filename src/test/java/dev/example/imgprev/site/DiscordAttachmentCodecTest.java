package dev.example.imgprev.site;

import static dev.example.imgprev.site.TestTokens.assertLinkFree;
import static dev.example.imgprev.site.TestTokens.media;
import static dev.example.imgprev.site.TestTokens.rebuilt;
import static dev.example.imgprev.site.TestTokens.token;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.example.imgprev.token.TokenCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DiscordAttachmentCodecTest {
    static final String CH = "1100000000000000001", ATT = "1300000000000000002";
    static final String EX = "67a1b2c3", IS = "67a06143", HM = "00" + "ab".repeat(31);   // leading zeros must survive
    static final DiscordAttachmentCodec CODEC = new DiscordAttachmentCodec();

    static String url(String host, String name, String query) {
        return "https://" + host + "/attachments/" + CH + "/" + ATT + "/" + name + "?" + query;
    }
    static String signed(String name) { return url("cdn.discordapp.com", name, "ex=" + EX + "&is=" + IS + "&hm=" + HM + "&"); }

    @Test void preservesSignatureExactly() {
        assertEquals(signed("image.png"), rebuilt(signed("image.png")));
    }

    @Test void mediaHostLinksRebuildOnCdnWithoutExtraParams() {
        // Shape of a real media.discordapp.net "Copy Link", including its stray "&=".
        String media = url("media.discordapp.net", "image.png", "ex=" + EX + "&is=" + IS + "&hm=" + HM + "&=&format=webp&width=300&height=200");
        assertEquals(signed("image.png"), rebuilt(media));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0001.png", "1.2.3.png", "007_final.v2.gif", "_x.png", "-dash-.jpeg", "a"})
    void filenamesRoundTrip(String name) {
        assertLinkFree(token(signed(name)));
        assertEquals(signed(name), rebuilt(signed(name)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"../x", "..", "a..b.png", ".hidden.png", "a%2Fb.png", "a/b.png", "sp%20ace.png"})
    void unsafeSenderFilenamesRejected(String name) {
        assertTrue(TokenCodec.encode(signed(name)).isEmpty(), name);
    }

    /** A hand-made token must not smuggle a traversal or slash into the rebuilt path. */
    @ParameterizedTest
    @ValueSource(strings = {"../x", "a/b", "..", ".x", "a\\b"})
    void craftedPayloadWithUnsafeNameFails(String name) {
        String good = media(signed("image.png")).payload();
        String crafted = good.substring(0, good.lastIndexOf('-') + 1) + DiscordAttachmentCodec.nameEnc(name);
        assertTrue(CODEC.payloadPattern().matcher(crafted).matches());
        assertTrue(CODEC.mediaUri(crafted).isCompletedExceptionally(), name);
    }

    @Test void craftedOversizeFieldFails() {
        String[] f = media(signed("image.png")).payload().split("-");
        f[2] = "zzzzzzzzzz";   // ex far beyond 8 hex digits
        assertTrue(CODEC.mediaUri(String.join("-", f)).isCompletedExceptionally());
    }

    @Test void unsignedOrMalformedQueryRejected() {
        for (String query : new String[] {
            "ex=" + EX + "&is=" + IS,                                      // no hm
            "ex=" + EX + "&is=" + IS + "&hm=" + HM.substring(2),           // hm 62 hex
            "ex=" + EX + "&is=" + IS + "&hm=" + HM.toUpperCase(),          // uppercase hex
            "ex=abcd&is=" + IS + "&hm=" + HM,                              // ex 4 hex
            "ex=%zz&is=" + IS + "&hm=" + HM,                               // malformed escape
        }) assertTrue(TokenCodec.encode(url("cdn.discordapp.com", "image.png", query)).isEmpty(), query);
    }

    @Test void expiryComesFromEx() {
        var m = media(signed("image.png"));
        assertEquals(Long.parseLong(EX, 16), m.codec().expiresAtEpochSec(m.payload()).orElseThrow());
    }

    @Test void tokenFitsInOneChatMessage() {
        String typical = token(signed("SPOILER_screenshot_2026-10-27_at_17.42.57.png"));
        assertLinkFree(typical);
        assertTrue(typical.length() < 256, typical.length() + " chars");
    }

    @Test void longestFilenameFits() {
        String token = token(signed("a".repeat(96) + ".png"));   // Discord's limit is 100
        assertTrue(token.length() < 256, token.length() + " chars");
        assertEquals(signed("a".repeat(96) + ".png"), rebuilt(signed("a".repeat(96) + ".png")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"image.png", "unknown.png", "cat.PNG", "photo.jpeg", "archive.tar.gz", "noext"})
    void defaultNamesAndExtensionsRoundTrip(String name) {
        assertEquals(signed(name), rebuilt(signed(name)));
    }

    @Test void unusualLifetimeRoundTrips() {
        String url = url("cdn.discordapp.com", "image.png", "ex=6aaaaaaa&is=" + IS + "&hm=" + HM + "&");
        assertEquals(url, rebuilt(url));
    }

    @Test void defaultNameTokensAreShort() {
        assertTrue(token(signed("image.png")).length() <= 76, token(signed("image.png")));
    }
}
