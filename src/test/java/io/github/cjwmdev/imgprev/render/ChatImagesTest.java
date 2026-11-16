package io.github.cjwmdev.imgprev.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.cjwmdev.imgprev.chat.TokenStyler;
import io.github.cjwmdev.imgprev.config.ImgPrevConfig;
import io.github.cjwmdev.imgprev.token.TokenCodec;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class ChatImagesTest {
    static final String GIF = TokenCodec.encode("https://media4.giphy.com/media/kj8RVuDxh4x7W/giphy.gif").orElseThrow();
    static final String IMG = TokenCodec.encode("https://i.imgur.com/AbC12xY.png").orElseThrow();
    static final long NOW = 1_800_000_000L;

    static ImgPrevConfig on() {
        ImgPrevConfig cfg = new ImgPrevConfig();
        cfg.imagesInChat = true;
        return cfg;
    }

    /** As a message reaches chat: labels made, the token kept in each label's hover. */
    static Component received(String text) { return TokenStyler.apply(Component.literal(text), m -> true); }

    @Test void offByDefault() {
        ImgPrevConfig cfg = new ImgPrevConfig();
        assertFalse(cfg.imagesInChat);
        assertTrue(ChatImages.rowsFor(received("look " + GIF), cfg, NOW).isEmpty());
    }

    @Test void reservesTheConfiguredLinesPerImage() {
        ImgPrevConfig cfg = on();
        cfg.chatImageLines = 4;
        List<ChatImages.Row> rows = ChatImages.rowsFor(received("look " + GIF + " and " + IMG), cfg, NOW);
        assertEquals(8, rows.size());
        assertEquals(List.of(0, 1, 2, 3, 0, 1, 2, 3), rows.stream().map(ChatImages.Row::index).toList());
        assertEquals(GIF, rows.get(0).token());
        assertEquals(IMG, rows.get(4).token());
        assertTrue(rows.stream().allMatch(r -> r.rows() == 4));
    }

    @Test void theSameImageTwiceShowsOnce() {
        assertEquals(5, ChatImages.rowsFor(received(GIF + " " + GIF), on(), NOW).size());
    }

    @Test void atMostFourImagesPerMessage() {
        String many = String.join(" ", GIF, IMG,
            TokenCodec.encode("https://i.imgur.com/AbC12x1.png").orElseThrow(),
            TokenCodec.encode("https://i.imgur.com/AbC12x2.png").orElseThrow(),
            TokenCodec.encode("https://i.imgur.com/AbC12x3.png").orElseThrow());
        assertEquals(ChatImages.MAX_PER_MESSAGE * 5, ChatImages.rowsFor(received(many), on(), NOW).size());
    }

    @Test void nothingWithoutLabelsOrWithTheSiteOff() {
        ImgPrevConfig cfg = on();
        assertTrue(ChatImages.rowsFor(Component.literal("plain text"), cfg, NOW).isEmpty());
        assertTrue(ChatImages.rowsFor(Component.literal(GIF), cfg, NOW).isEmpty(), "a raw token isn't a label");
        cfg.setSiteEnabled(TokenCodec.decode(GIF).orElseThrow().codec().code(), false);
        assertTrue(ChatImages.rowsFor(received(GIF), cfg, NOW).isEmpty());
        cfg.renderEnabled = false;
        assertTrue(ChatImages.rowsFor(received(IMG), cfg, NOW).isEmpty(), "previews off turns these off too");
    }

    @Test void rowsDrawNoText() {
        ChatImages.Row row = ChatImages.rowsFor(received(GIF), on(), NOW).getFirst();
        StringBuilder sb = new StringBuilder();
        assertTrue(row.accept((i, style, cp) -> { sb.appendCodePoint(cp); return true; }));
        assertEquals("", sb.toString());
    }

    @Test void eachRowDrawsItsBandOfTheImage() {
        // 9-unit rows, image 40 tall: four full bands, a partial fifth, nothing on the sixth.
        assertArrayEquals(new int[] {0, 9}, ChatImages.band(0, 9, 40));
        assertArrayEquals(new int[] {27, 36}, ChatImages.band(3, 9, 40));
        assertArrayEquals(new int[] {36, 40}, ChatImages.band(4, 9, 40));
        assertNull(ChatImages.band(5, 9, 40));
        assertNull(ChatImages.band(1, 9, 9), "an image exactly one row tall");
    }
}
