package dev.example.imgprev.site;

import static dev.example.imgprev.site.TestTokens.assertLinkFree;
import static dev.example.imgprev.site.TestTokens.media;
import static dev.example.imgprev.site.TestTokens.token;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.example.imgprev.image.MediaException;
import dev.example.imgprev.token.TokenCodec;
import java.net.URI;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class KlipyCodecTest {
    static final String CDN = "https://static.klipy.com/ii/935d7ab9d8c6202580a668421940ec81/14/af/";

    /** Shape of docs.klipy.com/gifs-api/gifs-items-api (trimmed). */
    static String response(String slug, String smGif, String mdGif) {
        return """
            {"result": true, "data": {"data": [{"id": 8041071659142944, "slug": "%s", "title": "Hello",
              "file": {
                "hd": {"gif": {"url": "%sum0L4dFH.gif", "width": 498, "height": 498}},
                "md": {"gif": {"url": "%s", "width": 498, "height": 498}, "webp": {"url": "%sJUYsGsrc.webp"}},
                "sm": {"gif": {"url": "%s", "width": 220, "height": 220}, "mp4": {"url": "%s3c2Tqd1S.mp4"}}
              }}]}}
            """.formatted(slug, CDN, mdGif, CDN, smGif, CDN);
    }

    @AfterEach void resetKey() { SiteRegistry.klipyApiKey(() -> ""); }

    @ParameterizedTest
    @ValueSource(strings = {"https://klipy.com/gifs/hello-hi-662", "https://www.klipy.com/gifs/hello-hi-662/", "https://KLIPY.com/gifs/hello-hi-662?ref=x"})
    void gifPagesEncodeToTheSlug(String url) {
        assertLinkFree(token(url));
        assertEquals("hello-hi-662", media(url).payload());
        assertEquals('k', media(url).codec().code());
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://klipy.com/clips/a-few-moments-later", "https://klipy.com/stickers/hello-hi-662",
        "https://klipy.com/gifs/hi_there", "https://klipy.com/gifs/x", "https://static.klipy.com/ii/abc/14/af/um0L4dFH.gif"})
    void otherKlipyLinksRejected(String url) {
        assertTrue(TokenCodec.encode(url).isEmpty(), url);
    }

    @Test void picksSmallGifFromCdn() throws MediaException {
        URI u = KlipyCodec.pick(response("hello-hi-662", CDN + "y6iepZM7.gif", CDN + "8GCrVAB7.gif"), "hello-hi-662");
        assertEquals(URI.create(CDN + "y6iepZM7.gif"), u);
    }

    @Test void skipsOffCdnUrlsAndFallsBack() throws MediaException {
        URI u = KlipyCodec.pick(response("hello-hi-662", "https://evil.example/y6iepZM7.gif", CDN + "8GCrVAB7.gif"), "hello-hi-662");
        assertEquals(URI.create(CDN + "8GCrVAB7.gif"), u);   // sm was off-CDN, md is next
    }

    @Test void rejectsPlainHttpAndOtherSlugs() {
        String http = "http://static.klipy.com/ii/x.gif";
        assertThrows(MediaException.class, () -> KlipyCodec.pick(response("other-slug", CDN + "a.gif", CDN + "b.gif"), "hello-hi-662"));
        URI fallback = assertDoesNotThrow(() -> KlipyCodec.pick(response("hello-hi-662", http, http), "hello-hi-662"));
        assertEquals("static.klipy.com", fallback.getHost());   // only the https hd entry qualified
        assertEquals("https", fallback.getScheme());
    }

    @Test void garbageResponse() {
        MediaException e = assertThrows(MediaException.class, () -> KlipyCodec.pick("<html>", "hello-hi-662"));
        assertEquals("Unexpected Klipy response", e.getMessage());
    }

    @Test void noKeyFailsWithoutNetwork() {
        var m = media("https://klipy.com/gifs/hello-hi-662");
        CompletionException e = assertThrows(CompletionException.class, () -> m.codec().mediaUri(m.payload()).join());
        assertEquals("Klipy needs an API key (config)", e.getCause().getMessage());
    }

    @Test void keyWithPathCharactersRefused() {
        SiteRegistry.klipyApiKey(() -> "abcdefgh/../../x");
        var m = media("https://klipy.com/gifs/hello-hi-662");
        CompletionException e = assertThrows(CompletionException.class, () -> m.codec().mediaUri(m.payload()).join());
        assertEquals("Klipy API key looks wrong", e.getCause().getMessage());
    }
}
