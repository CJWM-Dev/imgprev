package io.github.cjwmdev.imgprev.site;

import static io.github.cjwmdev.imgprev.site.TestTokens.assertLinkFree;
import static io.github.cjwmdev.imgprev.site.TestTokens.media;
import static io.github.cjwmdev.imgprev.site.TestTokens.rebuilt;
import static io.github.cjwmdev.imgprev.site.TestTokens.token;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.cjwmdev.imgprev.token.TokenCodec;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SiteRoundTripTest {
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        # Giphy
        https://media4.giphy.com/media/kj8RVuDxh4x7W/giphy.gif | https://i.giphy.com/media/kj8RVuDxh4x7W/giphy.gif
        https://giphy.com/gifs/cat-funny-kj8RVuDxh4x7W | https://i.giphy.com/media/kj8RVuDxh4x7W/giphy.gif
        https://giphy.com/gifs/kj8RVuDxh4x7W | https://i.giphy.com/media/kj8RVuDxh4x7W/giphy.gif
        https://media.giphy.com/media/v1.Y2lkPTc5MGI3NjEx/XJtM2nNFCzT3etvzOB/giphy.gif?cid=790b7611&rid=giphy.gif | https://i.giphy.com/media/XJtM2nNFCzT3etvzOB/giphy.gif
        https://i.giphy.com/media/XJtM2nNFCzT3etvzOB/200.gif | https://i.giphy.com/media/XJtM2nNFCzT3etvzOB/giphy.gif
        https://i.giphy.com/XJtM2nNFCzT3etvzOB.gif | https://i.giphy.com/media/XJtM2nNFCzT3etvzOB/giphy.gif
        https://i.giphy.com/XJtM2nNFCzT3etvzOB.webp | https://i.giphy.com/media/XJtM2nNFCzT3etvzOB/giphy.gif
        # Imgur
        https://i.imgur.com/AbC12xY.png | https://i.imgur.com/AbC12xY.png
        https://i.imgur.com/AbC12xY.jpeg | https://i.imgur.com/AbC12xY.jpg
        https://i.imgur.com/AbC12xY.JPG | https://i.imgur.com/AbC12xY.jpg
        https://i.imgur.com/AbC12xY.gifv | https://i.imgur.com/AbC12xY.gif
        https://i.imgur.com/AbC12xY.mp4 | https://i.imgur.com/AbC12xY.gif
        https://imgur.com/AbC12xY | https://i.imgur.com/AbC12xY.png
        https://i.imgur.com/abcde.webp | https://i.imgur.com/abcde.png
        # Tenor (filename is ignored by the CDN; the key suffix picks the rendition, medium becomes full size)
        https://media.tenor.com/1S3s5iesWX0AAAAM/despierto-gato.gif | https://media.tenor.com/1S3s5iesWX0AAAAC/tenor.gif
        https://media1.tenor.com/m/1S3s5iesWX0AAAAC/despierto-gato.gif | https://media.tenor.com/1S3s5iesWX0AAAAC/tenor.gif
        https://c.tenor.com/6n0lkxsBEQkAAAAM/tenor.gif | https://media.tenor.com/6n0lkxsBEQkAAAAC/tenor.gif
        https://media.tenor.com/6n0lkxsBEQkAAAAC/cat.gif | https://media.tenor.com/6n0lkxsBEQkAAAAC/tenor.gif
        https://media1.tenor.com/m/vrO_dp6Wf9AAAAAd/cat.gif | https://media.tenor.com/vrO_dp6Wf9AAAAAC/tenor.gif
        https://media.tenor.com/abc_defgh123/x.gif | https://media.tenor.com/abc_defgh123/tenor.gif
        # Discord emoji
        https://cdn.discordapp.com/emojis/1234567890123456789.gif | https://cdn.discordapp.com/emojis/1234567890123456789.gif?size=128
        https://cdn.discordapp.com/emojis/1234567890123456789.png?size=48 | https://cdn.discordapp.com/emojis/1234567890123456789.png?size=128
        https://cdn.discordapp.com/emojis/1234567890123456789.webp?size=48&animated=true | https://cdn.discordapp.com/emojis/1234567890123456789.gif?size=128
        https://media.discordapp.net/emojis/123456789012345678.webp?size=96&quality=lossless | https://cdn.discordapp.com/emojis/123456789012345678.png?size=128
        # Discord stickers (APNG and PNG are both .png, the .webp rendition is a still, GIFs only load from media.discordapp.net)
        https://media.discordapp.net/stickers/781291131828699156.png?size=160 | https://media.discordapp.net/stickers/781291131828699156.png?size=320
        https://cdn.discordapp.com/stickers/781291131828699156.png | https://media.discordapp.net/stickers/781291131828699156.png?size=320
        https://media.discordapp.net/stickers/781291131828699156.webp?size=160&quality=lossless | https://media.discordapp.net/stickers/781291131828699156.png?size=320
        https://media.discordapp.net/stickers/1234567890123456789.gif | https://media.discordapp.net/stickers/1234567890123456789.gif?size=320
        https://cdn.discordapp.com/stickers/1234567890123456789.GIF?size=160 | https://media.discordapp.net/stickers/1234567890123456789.gif?size=320
        # Gyazo (32-hex id is left-padded on decode)
        https://gyazo.com/0123456789abcdef0123456789abcdef | https://i.gyazo.com/0123456789abcdef0123456789abcdef.png
        https://i.gyazo.com/00000000000000000000000000000abc.jpg | https://i.gyazo.com/00000000000000000000000000000abc.jpg
        https://i.gyazo.com/ffffffffffffffffffffffffffffffff.gif | https://i.gyazo.com/ffffffffffffffffffffffffffffffff.gif
        https://i.gyazo.com/0123456789ABCDEF0123456789abcdef.png | https://i.gyazo.com/0123456789abcdef0123456789abcdef.png
        # Postimages and ImgBB (the CDN ignores the filename and extension)
        https://i.postimg.cc/XqRyT9nn/Screenshot-20210914-074956.png | https://i.postimg.cc/XqRyT9nn/image.png
        https://i.postimg.cc/xTJgkP7T/PXL-20210912-152307783.jpg?dl=1 | https://i.postimg.cc/xTJgkP7T/image.png
        https://i.ibb.co/M5f2dMkG/Full-Moon.webp | https://i.ibb.co/M5f2dMkG/image.png
        https://I.IBB.CO/nqNMTVJT/IMG-20250831-171039.jpg | https://i.ibb.co/nqNMTVJT/image.png
        # Catbox (case-sensitive; the exact extension is kept)
        https://files.catbox.moe/sa4ue3.png | https://files.catbox.moe/sa4ue3.png
        https://files.catbox.moe/ydqk4x.jpeg | https://files.catbox.moe/ydqk4x.jpeg
        https://files.catbox.moe/q2n358.jpg | https://files.catbox.moe/q2n358.jpg
        https://files.catbox.moe/q2n358.gif | https://files.catbox.moe/q2n358.gif
        https://files.catbox.moe/q2n358.webp | https://files.catbox.moe/q2n358.webp
        """)
    void roundTripsToCanonicalUrl(String url, String expected) {
        assertLinkFree(token(url));
        assertEquals(expected, rebuilt(url));
    }

    @Test void knownTokenShapes() {
        String imgur = token("https://i.imgur.com/AbC12xY.png");
        assertTrue(imgur.startsWith("<~IpAbC12xY") && imgur.endsWith("~>") && imgur.length() == 14, imgur);
        String giphy = token("https://media4.giphy.com/media/kj8RVuDxh4x7W/giphy.gif");
        assertTrue(giphy.startsWith("<~Gkj8RVuDxh4x7W") && giphy.length() == 19, giphy);
    }

    @Test void stickersAreTheirOwnSite() {
        assertEquals("sticker", media("https://media.discordapp.net/stickers/781291131828699156.png").codec().name());
        assertEquals(Label.STICKER, media("https://media.discordapp.net/stickers/781291131828699156.png").codec().label());
        // Lottie stickers are JSON: nothing to preview.
        assertTrue(TokenCodec.encode("https://cdn.discordapp.com/stickers/796140620111544330.json").isEmpty());
        assertTrue(TokenCodec.encode("https://media.discordapp.net/stickers/12345.png").isEmpty());
    }

    @Test void downloadsStayOnSiteHosts() {
        for (String url : new String[] {"https://media4.giphy.com/media/kj8RVuDxh4x7W/giphy.gif", "https://imgur.com/AbC12xY",
                "https://media.tenor.com/1S3s5iesWX0AAAAM/x.gif", "https://media.discordapp.net/emojis/123456789012345678.png",
                "https://cdn.discordapp.com/stickers/781291131828699156.png", "https://cdn.discordapp.com/stickers/1234567890123456789.gif",
                "https://gyazo.com/0123456789abcdef0123456789abcdef", "https://i.postimg.cc/XqRyT9nn/x.png",
                "https://i.ibb.co/M5f2dMkG/Full-Moon.webp", "https://files.catbox.moe/sa4ue3.png"}) {
            var m = media(url);
            assertTrue(m.codec().allowedDownloadHosts().contains(m.codec().mediaUri(m.payload()).getHost()), url);
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
