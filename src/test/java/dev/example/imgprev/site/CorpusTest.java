package dev.example.imgprev.site;

import static dev.example.imgprev.site.TestTokens.assertLinkFree;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.example.imgprev.token.ResolvedMedia;
import dev.example.imgprev.token.TokenCodec;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Real links from public sources: Giphy from the Library of Congress GIPHY dataset (2018), the rest from GitHub code
 * search (October 2026). Discord attachment links among them have long expired, which doesn't matter offline.
 * Each link is its own test, so one bad link doesn't hide the rest.
 */
class CorpusTest {
    static final List<String> SITES = List.of("catbox", "discord", "emoji", "giphy", "gyazo", "imgbb", "imgur", "klipy", "postimages", "tenor");

    static List<String> links(String site) throws IOException {
        String path = "/corpus/" + site + ".txt";
        try (InputStream in = Objects.requireNonNull(CorpusTest.class.getResourceAsStream(path), "missing test resource " + path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().filter(l -> !l.isBlank()).toList();
        }
    }

    @TestFactory Stream<DynamicContainer> everyLinkBecomesAShortLinkFreeToken() {
        return SITES.stream().map(site -> {
            List<String> links = assertDoesNotThrow(() -> links(site));
            assertFalse(links.isEmpty(), site);
            return DynamicContainer.dynamicContainer(site, links.stream().map(url -> DynamicTest.dynamicTest(url, () -> {
                String token = TokenCodec.encode(url).orElseThrow(() -> new AssertionError("not encodable: " + url));
                assertLinkFree(token);
                ResolvedMedia m = TokenCodec.decode(token).orElseThrow(() -> new AssertionError("didn't decode: " + url));
                assertEquals(site, m.codec().name(), url);
                assertDoesNotThrow(() -> m.codec().mediaUri(m.payload()), url);
                // A v1 token is the payload plus 7 characters; v2 is never longer.
                assertTrue(token.length() <= m.payload().length() + 7, url + " -> " + token);
            })));
        });
    }
}
