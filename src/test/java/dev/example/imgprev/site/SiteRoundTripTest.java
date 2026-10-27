package dev.example.imgprev.site;

import static dev.example.imgprev.site.TestTokens.assertLinkFree;
import static dev.example.imgprev.site.TestTokens.media;
import static dev.example.imgprev.site.TestTokens.rebuilt;
import static dev.example.imgprev.site.TestTokens.token;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SiteRoundTripTest {
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        # Giphy
        https://media4.giphy.com/media/kj8RVuDxh4x7W/giphy.gif | https://i.giphy.com/media/kj8RVuDxh4x7W/200.gif
        https://giphy.com/gifs/cat-funny-kj8RVuDxh4x7W | https://i.giphy.com/media/kj8RVuDxh4x7W/200.gif
        https://giphy.com/gifs/kj8RVuDxh4x7W | https://i.giphy.com/media/kj8RVuDxh4x7W/200.gif
        https://media.giphy.com/media/v1.Y2lkPTc5MGI3NjEx/XJtM2nNFCzT3etvzOB/giphy.gif?cid=790b7611&rid=giphy.gif | https://i.giphy.com/media/XJtM2nNFCzT3etvzOB/200.gif
        https://i.giphy.com/media/XJtM2nNFCzT3etvzOB/200.gif | https://i.giphy.com/media/XJtM2nNFCzT3etvzOB/200.gif
        # Imgur
        https://i.imgur.com/AbC12xY.png | https://i.imgur.com/AbC12xY.png
        https://i.imgur.com/AbC12xY.jpeg | https://i.imgur.com/AbC12xY.jpg
        https://i.imgur.com/AbC12xY.JPG | https://i.imgur.com/AbC12xY.jpg
        https://i.imgur.com/AbC12xY.gifv | https://i.imgur.com/AbC12xY.gif
        https://i.imgur.com/AbC12xY.mp4 | https://i.imgur.com/AbC12xY.gif
        https://imgur.com/AbC12xY | https://i.imgur.com/AbC12xY.png
        https://i.imgur.com/abcde.webp | https://i.imgur.com/abcde.png
        # Tenor (filename is ignored by the CDN; the key suffix picks the rendition)
        https://media.tenor.com/1S3s5iesWX0AAAAM/despierto-gato.gif | https://media.tenor.com/1S3s5iesWX0AAAAM/tenor.gif
        https://media.tenor.com/6n0lkxsBEQkAAAAC/cat.gif | https://media.tenor.com/6n0lkxsBEQkAAAAC/tenor.gif
        # Discord emoji
        https://cdn.discordapp.com/emojis/1234567890123456789.gif | https://cdn.discordapp.com/emojis/1234567890123456789.gif?size=128
        https://cdn.discordapp.com/emojis/1234567890123456789.png?size=48 | https://cdn.discordapp.com/emojis/1234567890123456789.png?size=128
        https://cdn.discordapp.com/emojis/1234567890123456789.webp?size=48&animated=true | https://cdn.discordapp.com/emojis/1234567890123456789.gif?size=128
        https://media.discordapp.net/emojis/123456789012345678.webp?size=96&quality=lossless | https://cdn.discordapp.com/emojis/123456789012345678.png?size=128
        # Gyazo (32-hex id is left-padded on decode)
        https://gyazo.com/0123456789abcdef0123456789abcdef | https://i.gyazo.com/0123456789abcdef0123456789abcdef.png
        https://i.gyazo.com/00000000000000000000000000000abc.jpg | https://i.gyazo.com/00000000000000000000000000000abc.jpg
        https://i.gyazo.com/ffffffffffffffffffffffffffffffff.gif | https://i.gyazo.com/ffffffffffffffffffffffffffffffff.gif
        https://i.gyazo.com/0123456789ABCDEF0123456789abcdef.png | https://i.gyazo.com/0123456789abcdef0123456789abcdef.png
        """)
    void roundTripsToCanonicalUrl(String url, String expected) {
        assertLinkFree(token(url));
        assertEquals(expected, rebuilt(url));
    }

    @Test void handoffExamples() {
        String imgur = token("https://i.imgur.com/AbC12xY.png");
        assertTrue(imgur.startsWith("<~1ipAbC12xY") && imgur.endsWith("~>") && imgur.length() == 15, imgur);
        String giphy = token("https://media4.giphy.com/media/kj8RVuDxh4x7W/giphy.gif");
        assertTrue(giphy.startsWith("<~1gkj8RVuDxh4x7W") && giphy.length() == 20, giphy);
    }

    @Test void downloadsStayOnSiteHosts() {
        for (String url : new String[] {"https://media4.giphy.com/media/kj8RVuDxh4x7W/giphy.gif", "https://imgur.com/AbC12xY",
                "https://media.tenor.com/1S3s5iesWX0AAAAM/x.gif", "https://media.discordapp.net/emojis/123456789012345678.png",
                "https://gyazo.com/0123456789abcdef0123456789abcdef"}) {
            var m = media(url);
            assertTrue(m.codec().allowedDownloadHosts().contains(m.codec().mediaUri(m.payload()).join().getHost()), url);
        }
    }

    @Test void siteCodesAreUniqueLowercaseLetters() {
        Set<Character> seen = new HashSet<>();
        for (SiteCodec c : SiteRegistry.all()) {
            assertTrue(c.code() >= 'a' && c.code() <= 'z', String.valueOf(c.code()));
            assertTrue(seen.add(c.code()), "duplicate code " + c.code());
        }
    }
}
