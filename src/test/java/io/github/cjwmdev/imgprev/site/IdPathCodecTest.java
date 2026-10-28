package io.github.cjwmdev.imgprev.site;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.cjwmdev.imgprev.image.SafeHttp;
import io.github.cjwmdev.imgprev.token.TokenCodec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Postimages and ImgBB share pages, whose ids differ from the CDN's. */
class IdPathCodecTest {
    static final IdPathCodec POSTIMAGES = SiteRegistry.POSTIMAGES, IMGBB = SiteRegistry.IMGBB;

    /** Trimmed from postimg.cc/PCDc5McC and ibb.co/S7csSRwd (checked 2026-10-28). */
    static final String POSTIMAGES_PAGE = """
        <meta property="og:type" content="website" />
        <meta property="og:image" content="https://i.postimg.cc/8zXVwKmm/01.png" />
        <textarea>[url=https://postimg.cc/PCDc5McC][img]https://i.postimg.cc/PCDc5McC/01.png[/img][/url]</textarea>
        """;
    static final String IMGBB_PAGE = """
        <meta property="og:image" content="https://i.ibb.co/M5f2dMkG/Full-Moon.webp" />
        <meta property="og:image:width" content="780" />
        <img src="https://i.ibb.co/S7csSRwd/Full-Moon.webp">
        """;

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        https://postimg.cc/PCDc5McC | postimages | PCDc5McC
        https://www.postimg.cc/PCDc5McC/ | postimages | PCDc5McC
        https://ibb.co/S7csSRwd | imgbb | S7csSRwd
        https://IBB.CO/S7csSRwd?ref=x | imgbb | S7csSRwd
        """)
    void pageLinksNeedALookup(String url, String site, String id) {
        assertTrue(TokenCodec.encode(url).isEmpty(), url);
        assertTrue(LinkConverter.needsLookup(url), url);
        assertTrue(LinkConverter.canLookUp(url), "no key needed");
        LinkConverter.Page p = LinkConverter.page(url).orElseThrow();
        assertEquals(site, p.site().name());
        assertEquals(id, p.id());
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://postimg.cc/gallery/PCDc5McC", "https://ibb.co/album/S7csSRwd", "https://ibb.co/abc",
        "http://ibb.co/S7csSRwd", "https://evil.example/S7csSRwd", "https://ibb.co.evil.example/S7csSRwd"})
    void otherLinksAreNotPageLinks(String url) {
        assertFalse(LinkConverter.needsLookup(url), url);
    }

    @Test void pageNamesTheFullSizeImage() {
        assertEquals(URI.create("https://i.postimg.cc/8zXVwKmm/01.png"), POSTIMAGES.ogImage(POSTIMAGES_PAGE).orElseThrow());
        URI imgbb = IMGBB.ogImage(IMGBB_PAGE).orElseThrow();
        assertEquals(URI.create("https://i.ibb.co/M5f2dMkG/Full-Moon.webp"), imgbb);
        assertTrue(TokenCodec.encode(imgbb.toString()).isPresent(), "the page's image converts to a token");
    }

    @Test void imageMustBeOnTheSitesOwnCdn() {
        for (String content : List.of("https://evil.example/M5f2dMkG/x.png", "http://i.ibb.co/M5f2dMkG/x.png",
                "https://i.postimg.cc/M5f2dMkG/x.png", "https://i.ibb.co/x.png", "not a url")) {
            assertTrue(IMGBB.ogImage("<meta property=\"og:image\" content=\"" + content + "\" />").isEmpty(), content);
        }
        assertTrue(IMGBB.ogImage("<html><title>Not found</title></html>").isEmpty());
    }

    // --- Looking up a page, with the network stood in for ---

    static IdPathCodec.PageFetcher serving(String html) {
        return (page, hosts) -> CompletableFuture.completedFuture(html.getBytes(StandardCharsets.UTF_8));
    }

    static IdPathCodec.PageFetcher failing(Throwable t) { return (page, hosts) -> CompletableFuture.failedFuture(t); }

    static String lookUpError(IdPathCodec site, IdPathCodec.PageFetcher fetcher) {
        return LinkConverter.describe(assertThrows(CompletionException.class, () -> site.lookUp("PCDc5McC", fetcher).join()));
    }

    @Test void lookUpAsksOnlyThePageHost() {
        AtomicReference<String> asked = new AtomicReference<>();
        URI found = POSTIMAGES.lookUp("PCDc5McC", (page, hosts) -> {
            asked.set(page + " " + hosts);
            return serving(POSTIMAGES_PAGE).fetch(page, hosts);
        }).join();
        assertEquals("https://postimg.cc/PCDc5McC [postimg.cc]", asked.get());
        assertEquals(URI.create("https://i.postimg.cc/8zXVwKmm/01.png"), found);
    }

    @Test void lookUpFailuresReadNaturally() {
        assertEquals("Postimages page not found", lookUpError(POSTIMAGES, failing(new CompletionException(new SafeHttp.HttpStatusException(404)))));
        assertEquals("Couldn't look up the Postimages image", lookUpError(POSTIMAGES, failing(new SafeHttp.HttpStatusException(503))));
        assertEquals("No image found on that ImgBB page", lookUpError(IMGBB, serving("<html></html>")));
    }

    @Test void convertAllLooksUpPageLinksAndConvertsTheRest() {
        String text = "pic https://postimg.cc/PCDc5McC and https://media.giphy.com/media/XJtM2nNFCzT3etvzOB/giphy.gif!";
        String out = LinkConverter.convertAll(text, serving(POSTIMAGES_PAGE)).join();
        assertEquals("pic " + TestTokens.token("https://i.postimg.cc/8zXVwKmm/01.png") + " and "
            + TestTokens.token("https://media.giphy.com/media/XJtM2nNFCzT3etvzOB/giphy.gif") + "!", out);
    }
}
