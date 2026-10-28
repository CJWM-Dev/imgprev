package dev.example.imgprev.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.platform.NativeImage;
import dev.example.imgprev.site.SiteRegistry;
import dev.example.imgprev.token.ResolvedMedia;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class ImageCacheTest {
    /** A load the test finishes by hand. */
    record Load(ResolvedMedia media, int pxW, int pxH, CompletableFuture<ImageCache.Decoded> future) {}

    final List<Load> loads = new ArrayList<>();
    final AtomicLong now = new AtomicLong(1_000_000), budget = new AtomicLong(Long.MAX_VALUE);
    final ImageCache cache = new ImageCache((m, w, h) -> {
        Load l = new Load(m, w, h, new CompletableFuture<>());
        loads.add(l);
        return l.future();
    }, Runnable::run, now::get, budget::get);

    static ResolvedMedia gif(int i) { return new ResolvedMedia(SiteRegistry.byCode('g').orElseThrow(), "media" + i); }

    /** A decoded image of {@code w×h} pixels from a {@code srcW×srcH} source; 4·w·h bytes once loaded. */
    static ImageCache.Decoded decoded(int w, int h, int srcW, int srcH) {
        return new ImageCache.Decoded(w, h, srcW, srcH, new int[] {100}, new NativeImage[1]);
    }

    ImageCache.Entry hover(int i) { return cache.getOrStart(gif(i), 256, 256, 1); }

    void finishLoad(int index, int size) { loads.get(index).future().complete(decoded(size, size, size, size)); }

    @Test void sweepingOverManyLabelsStartsOnlyAFewLoads() {
        for (int i = 0; i < 50; i++) hover(i);
        assertEquals(ImageCache.MAX_LOADING, loads.size(), "loads started while sweeping over 50 labels");
        assertSame(ImageCache.WAITING, hover(49), "the label under the cursor waits for a slot");
        finishLoad(0, 10);
        assertEquals(ImageCache.State.LOADING, hover(49).state());
        assertEquals(ImageCache.MAX_LOADING + 1, loads.size(), "it starts as soon as one finishes");
        assertEquals("media49", loads.getLast().media().payload());
    }

    @Test void evictsTheLeastRecentlyHoveredButNeverTheNewest() {
        budget.set(2 * 4 * 100 * 100);   // room for two 100×100 textures
        hover(1); finishLoad(0, 100);
        hover(2); finishLoad(1, 100);
        hover(1);                         // 1 is now more recent than 2
        hover(3); finishLoad(2, 100);
        assertNull(cache.peek(gif(2).key()), "2 was the least recently hovered");
        assertNotNull(cache.peek(gif(1).key()));
        assertEquals(2 * 4 * 100 * 100, cache.usedBytes());

        budget.set(0);
        hover(4); finishLoad(3, 100);
        assertEquals(1, cache.size(), "everything else went");
        assertEquals(ImageCache.State.READY, cache.peek(gif(4).key()).state(), "but not the image just loaded");
    }

    @Test void peekingDoesNotCountAsAHover() {
        budget.set(2 * 4 * 100 * 100);
        hover(1); finishLoad(0, 100);
        hover(2); finishLoad(1, 100);
        cache.peek(gif(1).key());
        hover(3); finishLoad(2, 100);
        assertNull(cache.peek(gif(1).key()), "1 was still the oldest");
    }

    @Test void failuresAreRetriedOnlyAfterAMinute() {
        hover(1);
        loads.get(0).future().completeExceptionally(new HttpTimeoutException("x"));
        assertEquals("Timed out", hover(1).error());
        now.addAndGet(ImageCache.RETRY_MS);
        hover(1);
        assertEquals(1, loads.size(), "not yet");
        now.addAndGet(1);
        assertEquals(ImageCache.State.LOADING, hover(1).state());
        assertEquals(2, loads.size(), "retried");
    }

    @Test void loadsFinishingAfterAClearFreeTheirImages() {
        hover(1);
        cache.clear();
        NativeImage img = new NativeImage(1, 1, false);
        loads.get(0).future().complete(new ImageCache.Decoded(1, 1, 1, 1, new int[] {100}, new NativeImage[] {img}));
        assertTrue(img.isClosed());
        assertEquals(0, cache.size());
        assertEquals(0, cache.loading(), "its slot is free again");
    }

    @Test void growingTheBoxReloadsAtTheNewSize() {
        hover(1);
        loads.get(0).future().complete(decoded(256, 128, 1000, 500));   // limited by the 256 px box
        ImageCache.Entry small = cache.peek(gif(1).key());
        AnimatedTexture before = small.texture();

        ImageCache.Entry during = cache.getOrStart(gif(1), 256, 256, 2);   // GUI scale 1 → 2
        assertSame(small, during, "the smaller preview keeps showing meanwhile");
        assertEquals(2, loads.size());
        assertEquals(512, loads.get(1).pxW());
        cache.getOrStart(gif(1), 256, 256, 2);
        assertEquals(2, loads.size(), "only one reload at a time");

        loads.get(1).future().complete(decoded(512, 256, 1000, 500));
        ImageCache.Entry big = cache.peek(gif(1).key());
        assertEquals(512, big.texture().textureW());
        assertTrue(before.released(), "the old texture is freed");
        assertEquals(4L * 512 * 256, cache.usedBytes());
        cache.getOrStart(gif(1), 256, 256, 2);
        assertEquals(2, loads.size(), "no reload once it matches");
    }

    @Test void noReloadWhenTheBoxWasntTheLimit() {
        hover(1);
        loads.get(0).future().complete(decoded(200, 100, 200, 100));   // smaller than the box already
        cache.getOrStart(gif(1), 256, 256, 2);
        hover(2);
        loads.get(1).future().complete(decoded(180, 90, 1000, 500));   // the pixel budget, not the box, shrank it
        cache.getOrStart(gif(2), 256, 256, 2);
        assertEquals(2, loads.size());
    }

    @Test void aFailedReloadKeepsTheSmallerPreview() {
        hover(1);
        loads.get(0).future().complete(decoded(256, 128, 1000, 500));
        cache.getOrStart(gif(1), 256, 256, 2);
        loads.get(1).future().completeExceptionally(new HttpTimeoutException("x"));
        ImageCache.Entry e = cache.peek(gif(1).key());
        assertEquals(ImageCache.State.READY, e.state());
        assertFalse(e.texture().released());
        cache.getOrStart(gif(1), 256, 256, 2);
        assertEquals(2, loads.size(), "not retried at that size");
    }

    @Test void failedEntriesAreCapped() {
        for (int i = 0; i < ImageCache.MAX_EMPTY + 20; i++) {
            hover(i);
            loads.get(i).future().completeExceptionally(new HttpTimeoutException("x"));
        }
        assertTrue(cache.size() <= ImageCache.MAX_EMPTY + 1, "entries: " + cache.size());
        assertNull(cache.peek(gif(0).key()), "the oldest went first");
    }

    @Test void errorsReadNaturally() {
        ResolvedMedia discord = new ResolvedMedia(SiteRegistry.byCode('d').orElseThrow(), "x");
        assertEquals("Discord link expired", ImageCache.describe(discord, new SafeHttp.HttpStatusException(403)));
        assertEquals("Image not found", ImageCache.describe(gif(1), new SafeHttp.HttpStatusException(404)));
        assertEquals("Rate limited, try again later", ImageCache.describe(gif(1), new SafeHttp.HttpStatusException(429)));
        assertEquals("Couldn't load image (HTTP 500)", ImageCache.describe(gif(1), new SafeHttp.HttpStatusException(500)));
        assertEquals("File too large (over 2 MB)", ImageCache.describe(gif(1),
            new java.util.concurrent.CompletionException(SafeHttp.tooLarge(2 << 20))));
        assertEquals("Couldn't decode image", ImageCache.describe(gif(1), new javax.imageio.IIOException("bad")));
        assertEquals("Couldn't load image", ImageCache.describe(gif(1), new IllegalStateException()));
    }

    @Test void decodingCopiesPixelsIntoNativeImagesExactly() throws Exception {
        int[] argb = {0xFFFF0000, 0x80FF8800, 0x00000000, 0xFF0000FF, 0x40123456, 0xFFFFFFFF};
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(3, 2, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, 3, 2, argb, 0, 3);
        java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", png);

        ImageCache.Decoded d = ImageCache.decode(png.toByteArray(), 256, 256);
        try {
            int[] expected = FrameDecoder.decode(png.toByteArray(), 256, 256).frames().getFirst();
            for (int i = 0; i < argb.length; i++) assertEquals(expected[i], d.images()[0].getPixel(i % 3, i / 3), "pixel " + i);
        } finally {
            AnimatedTexture.close(d.images());
        }
    }
}
