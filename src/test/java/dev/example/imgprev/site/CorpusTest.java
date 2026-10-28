package dev.example.imgprev.site;

import static dev.example.imgprev.site.TestTokens.assertLinkFree;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.example.imgprev.token.ResolvedMedia;
import dev.example.imgprev.token.TokenCodec;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Real links from public sources: Giphy from the Library of Congress GIPHY dataset (2018), the rest from GitHub code
 * search (October 2026). Discord attachment links among them have long expired, which doesn't matter offline.
 */
class CorpusTest {
    static List<String> links(String site) throws IOException {
        try (InputStream in = CorpusTest.class.getResourceAsStream("/corpus/" + site + ".txt")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().filter(l -> !l.isBlank()).toList();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"catbox", "discord", "emoji", "giphy", "gyazo", "imgbb", "imgur", "klipy", "postimages", "tenor"})
    void everyLinkBecomesAShortLinkFreeToken(String site) throws IOException {
        List<String> links = links(site);
        assertFalse(links.isEmpty(), site);
        for (String url : links) {
            String token = TokenCodec.encode(url).orElseThrow(() -> new AssertionError("not encodable: " + url));
            assertLinkFree(token);
            ResolvedMedia m = TokenCodec.decode(token).orElseThrow(() -> new AssertionError("didn't decode: " + url));
            assertEquals(site, m.codec().name(), url);
            assertFalse(m.codec().mediaUri(m.payload()).isCompletedExceptionally(), url);
            // A v1 token is the payload plus 7 characters; v2 is never longer.
            assertTrue(token.length() <= m.payload().length() + 7, url + " -> " + token);
        }
    }
}
