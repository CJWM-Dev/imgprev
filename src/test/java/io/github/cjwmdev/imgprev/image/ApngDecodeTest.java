package io.github.cjwmdev.imgprev.image;

import static io.github.cjwmdev.imgprev.image.FrameDecoderTest.solid;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.cjwmdev.imgprev.image.TestApng.Frame;
import java.io.IOException;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ApngDecodeTest {
    static final int RED = 0xFFFF0000, BLUE = 0xFF0000FF, GREEN = 0xFF00FF00, HALF_GREEN = 0x8000FF00, CLEAR = 0;

    static int px(FrameDecoder.Frames<int[]> f, int frame, int x, int y) { return f.frames().get(frame)[y * f.w() + x]; }

    @Test void animationChunksAreRead() throws IOException {
        ApngAnimation a = ApngAnimation.parse(TestApng.animated(
            new Frame(solid(6, 4, RED), 0, 0, 7, 100, 0, 0), new Frame(solid(2, 2, BLUE), 3, 1, 1, 0, 1, 1)));
        assertNotNull(a);
        assertEquals(6, a.canvasW());
        assertEquals(4, a.canvasH());
        assertEquals(2, a.frames().size());
        ApngAnimation.Frame f = a.frames().get(1);
        assertEquals(java.util.List.of(3, 1, 2, 2, 10, 1, 1), java.util.List.of(f.x(), f.y(), f.w(), f.h(), f.delayMs(), f.dispose(), f.blend()));
        assertEquals(70, a.frames().getFirst().delayMs());
    }

    @Test void plainPngsAreNotAnimations() throws IOException {
        assertNull(ApngAnimation.parse(FrameDecoderTest.png(solid(4, 4, RED))));
        assertNull(ApngAnimation.parse("GIF89a......".getBytes()));
    }

    @Test void eachFrameBecomesAPngOfItsOwn() throws IOException {
        ApngAnimation a = ApngAnimation.parse(TestApng.animated(
            new Frame(solid(6, 4, RED), 0, 0, 1, 10, 0, 0), new Frame(solid(2, 3, BLUE), 0, 0, 1, 10, 0, 0)));
        var img = TestApng.read(a.png(1));
        assertEquals(2, img.getWidth());
        assertEquals(3, img.getHeight());
        assertEquals(BLUE, img.getRGB(1, 2));
    }

    @Test void compositesOffsetsDelaysAndDisposal() throws IOException {
        FrameDecoder.Frames<int[]> f = FrameDecoder.decode(TestApng.animated(
            new Frame(solid(4, 4, RED), 0, 0, 1, 20, 0, 0),
            new Frame(solid(2, 2, BLUE), 2, 2, 1, 100, 1, 0),      // 10 ms is too fast; cleared after it shows
            new Frame(solid(1, 1, GREEN), 0, 0, 2, 10, 0, 1)), 256, 256);
        assertEquals(3, f.count());
        assertEquals(4, f.w());
        assertArrayEquals(new int[] {50, 100, 200}, f.delaysMs());
        assertEquals(RED, px(f, 0, 3, 3));
        assertEquals(BLUE, px(f, 1, 3, 3));
        assertEquals(RED, px(f, 1, 1, 1));
        assertEquals(CLEAR, px(f, 2, 3, 3));
        assertEquals(GREEN, px(f, 2, 0, 0));
        assertEquals(RED, px(f, 2, 1, 1));
    }

    @Test void disposeToPrevious() throws IOException {
        FrameDecoder.Frames<int[]> f = FrameDecoder.decode(TestApng.animated(
            new Frame(solid(4, 4, RED), 0, 0, 1, 10, 0, 0),
            new Frame(solid(4, 4, BLUE), 0, 0, 1, 10, 2, 0),
            new Frame(solid(1, 1, GREEN), 0, 0, 1, 10, 0, 0)), 256, 256);
        assertEquals(BLUE, px(f, 1, 2, 2));
        assertEquals(RED, px(f, 2, 2, 2));
        assertEquals(GREEN, px(f, 2, 0, 0));
    }

    @Test void firstFramePreviousActsAsBackground() throws IOException {
        FrameDecoder.Frames<int[]> f = FrameDecoder.decode(TestApng.animated(
            new Frame(solid(4, 4, RED), 0, 0, 1, 10, 2, 0),
            new Frame(solid(1, 1, GREEN), 0, 0, 1, 10, 0, 0)), 256, 256);
        assertEquals(CLEAR, px(f, 1, 2, 2));
        assertEquals(GREEN, px(f, 1, 0, 0));
    }

    @Test void blendSourceReplacesAndBlendOverMixes() throws IOException {
        FrameDecoder.Frames<int[]> f = FrameDecoder.decode(TestApng.animated(
            new Frame(solid(4, 4, RED), 0, 0, 1, 10, 0, 0),
            new Frame(solid(2, 2, HALF_GREEN), 0, 0, 1, 10, 0, 0),    // source: the half-green replaces red
            new Frame(solid(2, 2, HALF_GREEN), 2, 2, 1, 10, 0, 1)), 256, 256);   // over: mixed with red
        assertEquals(HALF_GREEN, px(f, 1, 0, 0));
        assertEquals(FrameDecoder.over(HALF_GREEN, RED), px(f, 2, 3, 3));
        assertEquals(HALF_GREEN, px(f, 2, 0, 0));
    }

    @Test void hiddenDefaultImageIsNotAFrame() throws IOException {
        FrameDecoder.Frames<int[]> f = FrameDecoder.decode(TestApng.animated(solid(4, 4, GREEN),
            new Frame(solid(4, 4, RED), 0, 0, 1, 10, 0, 0), new Frame(solid(4, 4, BLUE), 0, 0, 1, 10, 0, 0)), 256, 256);
        assertEquals(2, f.count());
        assertEquals(RED, px(f, 0, 0, 0));
        assertEquals(BLUE, px(f, 1, 0, 0));
    }

    @Test void scaledToFit() throws IOException {
        FrameDecoder.Frames<int[]> f = FrameDecoder.decode(TestApng.animated(
            new Frame(solid(320, 320, RED), 0, 0, 1, 10, 0, 0), new Frame(solid(320, 320, BLUE), 0, 0, 1, 10, 0, 0)), 128, 128);
        assertEquals(128, f.w());
        assertEquals(320, f.srcW());
        assertTrue(Arrays.stream(f.frames().get(1)).allMatch(p -> p == BLUE));
    }

    @Test void truncatedAnimationsKeepTheFramesThatDecoded() throws IOException {
        byte[] full = TestApng.animated(new Frame(solid(8, 8, RED), 0, 0, 1, 10, 0, 0),
            new Frame(solid(8, 8, BLUE), 0, 0, 1, 10, 0, 0), new Frame(solid(8, 8, GREEN), 0, 0, 1, 10, 0, 0));
        byte[] cut = Arrays.copyOf(full, full.length - 20);   // partway through the last frame's data
        FrameDecoder.Frames<int[]> f = FrameDecoder.decode(cut, 256, 256);
        assertEquals(2, f.count());
        assertEquals(BLUE, px(f, 1, 0, 0));
    }
}
