package dev.example.imgprev.site;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.example.imgprev.token.TokenCodec;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Postimages and ImgBB share pages, whose ids differ from the CDN's. */
class IdPathCodecTest {
    static final IdPathCodec POSTIMAGES = SiteRegistry.PAGES.get(0), IMGBB = SiteRegistry.PAGES.get(1);

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
        assertTrue(!LinkConverter.needsLookup(url), url);
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
}
