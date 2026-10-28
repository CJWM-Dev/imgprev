package io.github.cjwmdev.imgprev.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.platform.NativeImage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Frame timing and cleanup; nothing here touches the GPU. */
class AnimatedTextureTest {
    static AnimatedTexture texture(int... delaysMs) {
        return new AnimatedTexture("t", 2, 2, delaysMs, new NativeImage[delaysMs.length], 2, 2);
    }

    @ParameterizedTest
    @CsvSource({"0, 0", "99, 0", "100, 1", "149, 1", "150, 2", "349, 2", "350, 0", "1049, 2"})
    void picksTheFrameShowingAtThatTime(long millis, int frame) {
        assertEquals(frame, texture(100, 50, 200).frameAt(millis));
    }

    @Test void singleFramesAndZeroLengthAnimationsShowFrameZero() {
        assertEquals(0, texture(0).frameAt(12345));
        assertEquals(0, texture(0, 0).frameAt(12345));
    }

    @Test void longestDelaysDontOverflow() {
        int[] delays = new int[FrameDecoder.MAX_FRAMES];
        java.util.Arrays.fill(delays, FrameDecoder.MAX_DELAY_MS);
        AnimatedTexture t = texture(delays);
        assertEquals(FrameDecoder.MAX_FRAMES - 1, t.frameAt((long) FrameDecoder.MAX_DELAY_MS * FrameDecoder.MAX_FRAMES - 1));
    }

    @Test void releasingTwiceIsHarmless() {
        NativeImage img = new NativeImage(1, 1, false);
        AnimatedTexture t = new AnimatedTexture("t", 1, 1, new int[] {100}, new NativeImage[] {img}, 1, 1);
        t.release();
        t.release();
        assertTrue(img.isClosed() && t.released());
    }

    @Test void bytesCountEveryFrame() {
        assertEquals(2 * 2 * 4 * 3, texture(10, 10, 10).bytes());
    }
}
