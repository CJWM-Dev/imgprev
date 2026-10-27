package dev.example.imgprev.site;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.example.imgprev.token.TokenCodec;
import java.util.List;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

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

    @Test void convertAllWithoutLookupsMatchesConvertNow() {
        String text = "hi " + GIPHY;
        assertEquals(LinkConverter.convertNow(text), LinkConverter.convertAll(text).join());
    }

    @Test void convertAllFailsWhenALookupCannotRun() {
        CompletionException e = assertThrows(CompletionException.class, () -> LinkConverter.convertAll("hi " + KLIPY_PAGE).join());
        assertEquals(LinkConverter.KLIPY_NEEDS_KEY, LinkConverter.describe(e));
    }

    @Test void singleLinkConvert() {
        assertEquals(tok(GIPHY), LinkConverter.convert(GIPHY).join());
        CompletionException e = assertThrows(CompletionException.class, () -> LinkConverter.convert("https://example.com/a.png").join());
        assertEquals("Not a supported image link", LinkConverter.describe(e));
    }
}
