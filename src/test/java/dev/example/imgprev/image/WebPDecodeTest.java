package dev.example.imgprev.image;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.example.imgprev.image.TestWebP.Frame;
import java.io.IOException;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class WebPDecodeTest {
    static final int RED = 0xFFFF0000, BLUE = 0xFF0000FF, HALF_GREEN = 0x8000FF00, CLEAR = 0;

    static int px(FrameDecoder.Frames f, int frame, int x, int y) { return f.argb()[(frame * f.h() + y) * f.w() + x]; }

    static void assertArgbNear(int expected, int actual) {
        for (int shift = 0; shift < 32; shift += 8) {
            int e = (expected >>> shift) & 0xFF, a = (actual >>> shift) & 0xFF;
            assertTrue(Math.abs(e - a) <= 1, String.format("expected %08X, was %08X", expected, actual));
        }
    }

    @Test void stillLosslessIsDecodedAndScaled() throws IOException {
        FrameDecoder.Frames f = FrameDecoder.decode(TestWebP.still(300, 150, RED), 256);
        assertEquals(256, f.w());
        assertEquals(128, f.h());
        assertEquals(1, f.count());
        assertTrue(Arrays.stream(f.argb()).allMatch(p -> p == RED));
    }

    @Test void stillKeepsAlpha() throws IOException {
        FrameDecoder.Frames f = FrameDecoder.decode(TestWebP.still(4, 4, HALF_GREEN), 256);
        assertEquals(HALF_GREEN, f.argb()[0]);
    }

    @Test void animationChunksAreRead() {
        WebPAnimation a = WebPAnimation.parse(TestWebP.animated(6, 4,
            new Frame(0, 0, 6, 4, RED, 70, true, false), new Frame(2, 2, 2, 2, BLUE, 30, false, true)));
        assertNotNull(a);
        assertEquals(6, a.canvasW());
        assertEquals(4, a.canvasH());
        assertEquals(new WebPAnimation.Frame(2, 2, 2, 2, 30, false, true), a.frames().get(1));
        assertNull(WebPAnimation.parse(TestWebP.still(4, 4, RED)), "a still WebP isn't an animation");
        assertNull(WebPAnimation.parse("GIF89a......".getBytes()));
    }

    @Test void animatedCompositesBlendsAndDisposes() throws IOException {
        FrameDecoder.Frames f = FrameDecoder.decode(TestWebP.animated(4, 4,
            new Frame(0, 0, 4, 4, RED, 50, true, false),
            new Frame(2, 2, 2, 2, BLUE, 10, true, true),          // disposed to transparent after it shows
            new Frame(0, 0, 2, 2, HALF_GREEN, 200, true, false)   // alpha-blended over red
        ), 256);
        assertEquals(3, f.count());
        assertEquals(4, f.w());
        assertArrayEquals(new int[] {50, 100, 200}, f.delaysMs());
        assertEquals(RED, px(f, 0, 3, 3));
        assertEquals(BLUE, px(f, 1, 3, 3));
        assertEquals(CLEAR, px(f, 2, 3, 3));
        assertArgbNear(0xFF7F8000, px(f, 2, 0, 0));   // 50% green over red
        assertEquals(RED, px(f, 2, 2, 0));
    }

    @Test void noBlendReplacesPixels() throws IOException {
        FrameDecoder.Frames f = FrameDecoder.decode(TestWebP.animated(4, 4,
            new Frame(0, 0, 4, 4, RED, 100, true, false),
            new Frame(0, 0, 2, 2, HALF_GREEN, 100, false, false)), 256);
        assertEquals(HALF_GREEN, px(f, 1, 0, 0));
        assertEquals(RED, px(f, 1, 3, 3));
    }

    @Test void oversizeCanvasRejected() {
        byte[] huge = TestWebP.animated(4000, 8, new Frame(0, 0, 4, 4, RED, 100, true, false));
        MediaException e = assertThrows(MediaException.class, () -> FrameDecoder.decode(huge, 256));
        assertTrue(e.getMessage().startsWith("Image too large"), e.getMessage());
    }

    @Test void truncatedFileFailsCleanly() {
        byte[] full = TestWebP.animated(4, 4, new Frame(0, 0, 4, 4, RED, 100, true, false));
        byte[] cut = Arrays.copyOf(full, full.length - 6);
        assertThrows(IOException.class, () -> FrameDecoder.decode(cut, 256));
    }
}
