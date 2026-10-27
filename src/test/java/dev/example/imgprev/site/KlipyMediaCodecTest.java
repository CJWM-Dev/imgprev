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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class KlipyMediaCodecTest {
    static final String PATH = "/ii/35ccce3d852f7995dd2da910f2abd795/2c/59/NiBtvUhr";

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        https://static.klipy.com/ii/35ccce3d852f7995dd2da910f2abd795/2c/59/NiBtvUhr.webp | https://static.klipy.com/ii/35ccce3d852f7995dd2da910f2abd795/2c/59/NiBtvUhr.webp
        https://static2.klipy.com/ii/35ccce3d852f7995dd2da910f2abd795/2c/59/NiBtvUhr.webp | https://static.klipy.com/ii/35ccce3d852f7995dd2da910f2abd795/2c/59/NiBtvUhr.webp
        https://static1.klipy.com/ii/935d7ab9d8c6202580a668421940ec81/14/af/y6iepZM7.gif | https://static.klipy.com/ii/935d7ab9d8c6202580a668421940ec81/14/af/y6iepZM7.gif
        https://static.klipy.com/ii/00000000000000000000000000000abc/00/0f/AbCd1234.png | https://static.klipy.com/ii/00000000000000000000000000000abc/00/0f/AbCd1234.png
        https://static.klipy.com/ii/935d7ab9d8c6202580a668421940ec81/14/af/LyWpim71.JPG | https://static.klipy.com/ii/935d7ab9d8c6202580a668421940ec81/14/af/LyWpim71.jpg
        https://static.klipy.com/ii/935d7ab9d8c6202580a668421940ec81/14/af/KkjHgST0WkvqPhrQBEj.webp?w=200 | https://static.klipy.com/ii/935d7ab9d8c6202580a668421940ec81/14/af/KkjHgST0WkvqPhrQBEj.webp
        """)
    void roundTripsToStaticHost(String url, String expected) {
        assertLinkFree(token(url));
        assertEquals('y', media(url).codec().code());
        assertEquals(expected, rebuilt(url));
    }

    @Test void tokensStayShort() {
        assertTrue(token("https://static.klipy.com" + PATH + ".webp").length() < 50, token("https://static.klipy.com" + PATH + ".webp"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://static.klipy.com" + PATH + ".mp4",
        "https://static.klipy.com" + PATH + ".webm",
        "https://static.klipy.com/ii/35CCCE3D852F7995DD2DA910F2ABD795/2c/59/NiBtvUhr.webp",   // hashes are lowercase
        "https://static.klipy.com/ii/35ccce3d852f7995/2c/59/NiBtvUhr.webp",
        "https://static.klipy.com/ii/35ccce3d852f7995dd2da910f2abd795/2c/59/x/NiBtvUhr.webp",
        "https://klipy.com" + PATH + ".webp",
        "https://evil.example" + PATH + ".webp",
    })
    void otherShapesRejected(String url) {
        assertTrue(TokenCodec.encode(url).isEmpty(), url);
    }

    @Test void craftedOversizeHashFails() {
        KlipyMediaCodec c = new KlipyMediaCodec();
        String crafted = "w" + "z".repeat(22) + "-1-NiBtvUhr";
        assertTrue(c.payloadPattern().matcher(crafted).matches());
        assertTrue(c.mediaUri(crafted).isCompletedExceptionally());
    }
}
