package io.github.cjwmdev.imgprev.site;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.cjwmdev.imgprev.token.TokenCodec;
import java.util.List;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LinkConverterTest {
    static final String GIPHY = "https://media.giphy.com/media/XJtM2nNFCzT3etvzOB/giphy.gif";
    static final String IMGUR = "https://i.imgur.com/AbC12xY.png";
    static final String KLIPY_PAGE = "https://klipy.com/gifs/red-moon-flying";

    static String tok(String url) { return TokenCodec.encode(url).orElseThrow(); }

    @Test void replacesLinksInsideText() {
        assertEquals("look " + tok(GIPHY) + " and " + tok(IMGUR), LinkConverter.convertNow("look " + GIPHY + " and " + IMGUR));
    }

    @Test void keepsTrailingPunctuation() {
        assertEquals("(see " + tok(IMGUR) + ").", LinkConverter.convertNow("(see " + IMGUR + ")."));
        assertEquals(tok(GIPHY) + "!!", LinkConverter.convertNow(GIPHY + "!!"));
    }

    @Test void leavesEverythingElseAlone() {
        String text = "http://i.imgur.com/AbC12xY.png https://example.com/x.png " + KLIPY_PAGE + " no links <~here~>";
        assertEquals(text, LinkConverter.convertNow(text));
    }

    @Test void findsEachKlipyPageOnce() {
        assertEquals(List.of(KLIPY_PAGE), LinkConverter.lookupsIn("a " + KLIPY_PAGE + " b " + KLIPY_PAGE + ". " + GIPHY));
    }

    @Test void pageLinksBeforePunctuationAreFoundOnce() {
        assertEquals(List.of("https://postimg.cc/PCDc5McC"), LinkConverter.lookupsIn("see https://postimg.cc/PCDc5McC, https://postimg.cc/PCDc5McC!"));
    }

    @Test void upperCaseSchemesConvertToo() {
        assertEquals("look " + tok(IMGUR), LinkConverter.convertNow("look HTTPS://I.IMGUR.COM/AbC12xY.png"));
    }

    @Test void convertAllWithoutLookupsMatchesConvertNow() {
        String text = "hi " + GIPHY;
        assertEquals(LinkConverter.convertNow(text), LinkConverter.convertAll(text).join());
    }

    @Test void convertAllFailsWhenALookupCannotRun() {
        CompletionException e = assertThrows(CompletionException.class, () -> LinkConverter.convertAll("hi " + KLIPY_PAGE).join());
        assertEquals(LinkConverter.KLIPY_PAGE_LINK, LinkConverter.describe(e));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://klipy.com/gifs/hello-hi-662", "https://www.klipy.com/gifs/hello-hi-662/", "https://KLIPY.com/gifs/hello-hi-662?ref=x"})
    void klipyPageLinksAreRefusedWithAHint(String url) {
        assertTrue(TokenCodec.encode(url).isEmpty(), url);
        assertTrue(LinkConverter.needsLookup(url), url);
        assertFalse(LinkConverter.canLookUp(url), url);
        CompletionException e = assertThrows(CompletionException.class, () -> LinkConverter.convert(url).join());
        assertEquals(LinkConverter.KLIPY_PAGE_LINK, LinkConverter.describe(e));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://klipy.com/clips/a-few-moments-later", "https://klipy.com/stickers/hello-hi-662",
        "https://klipy.com/gifs/hi_there", "https://klipy.com/gifs/x", "https://evil.example/gifs/hello-hi-662"})
    void otherKlipyLinksAreNotPageLinks(String url) {
        assertFalse(LinkConverter.needsLookup(url), url);
    }

    @Test void oldKlipySlugTokensNoLongerDecode() {
        assertTrue(TokenCodec.decode("<~1kred-moon-flyinga~>").isEmpty(), "made by 0.1.0; viewing one needed an API key");
    }

    @Test void singleLinkConvert() {
        assertEquals(tok(GIPHY), LinkConverter.convert(GIPHY).join());
        CompletionException e = assertThrows(CompletionException.class, () -> LinkConverter.convert("https://example.com/a.png").join());
        assertEquals("Not a supported image link", LinkConverter.describe(e));
    }
}
