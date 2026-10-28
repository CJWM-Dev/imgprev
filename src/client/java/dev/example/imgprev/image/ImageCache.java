package dev.example.imgprev.image;

import com.mojang.blaze3d.platform.NativeImage;
import dev.example.imgprev.ImgPrevClient;
import dev.example.imgprev.token.ResolvedMedia;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.LongSupplier;
import javax.imageio.IIOException;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * Hover-driven image cache bounded by a byte budget (LRU). Render thread only: downloads and decodes run on
 * background pools and post their results back to the render thread.
 */
public final class ImageCache {
    public enum State { LOADING, READY, FAILED }

    static final long RETRY_MS = 60_000;
    /** A load taking longer than this shows a "still loading" message instead of the plain one. */
    public static final long SLOW_MS = 5_000;
    /** Loads (download + decode) running at once. More hovers wait for a slot, so sweeping the cursor over chat can't queue them all. */
    static final int MAX_LOADING = 4;
    /** Entries kept without a texture (failed links) before the oldest are forgotten. */
    static final int MAX_EMPTY = 256;
    /** A preview is loaded again at the bigger size once its pixel box has grown this much (GUI scale, window size). */
    static final double UPGRADE_RATIO = 1.25;
    static final ExecutorService DECODE = Executors.newSingleThreadExecutor(SafeHttp.daemon("imgprev-decode"));

    public static final class Entry {
        private State state = State.LOADING;
        private @Nullable AnimatedTexture texture;
        private String error = "";
        private long failedAt;
        /** When its load started; -1 for {@link #WAITING}, which hasn't. */
        private long startedAt = -1;
        /** Source size, and the pixel box the texture was decoded to fit. */
        private int srcW, srcH, pxW, pxH;
        private boolean upgrading;

        public State state() { return state; }
        public @Nullable AnimatedTexture texture() { return texture; }
        public String error() { return error; }
    }

    /** Handed out while every load slot is busy; the next frame's hover asks again. */
    static final Entry WAITING = new Entry();

    /** One load's result: frame sizes and timings, with the pixels already in native memory. */
    public record Decoded(int w, int h, int srcW, int srcH, int[] delaysMs, NativeImage[] images) {}

    @FunctionalInterface
    public interface Loader { CompletableFuture<Decoded> load(ResolvedMedia m, int pxW, int pxH); }

    private final Map<String, Entry> entries = new LinkedHashMap<>();   // least recently hovered first
    private final Loader loader;
    private final Executor renderThread;
    private final LongSupplier clock, budget;
    private long usedBytes;
    private int loading;

    public ImageCache() {
        this(ImageCache::download, r -> Minecraft.getInstance().execute(r), Util::getMillis, () -> ImgPrevClient.CONFIG.cacheBudgetBytes());
    }

    ImageCache(Loader loader, Executor renderThread, LongSupplier clock, LongSupplier budget) {
        this.loader = loader;
        this.renderThread = renderThread;
        this.clock = clock;
        this.budget = budget;
    }

    /**
     * Called every frame while a preview is hovered. FAILED entries are kept so dead links aren't refetched each frame.
     * A new load fits the image in {@code boxW×boxH} GUI units, decoded at {@code guiScale} pixels per unit.
     */
    public Entry getOrStart(ResolvedMedia m, int boxW, int boxH, int guiScale) {
        String key = m.key();
        int pxW = boxW * guiScale, pxH = boxH * guiScale;
        Entry e = entries.remove(key);
        if (e != null) {
            entries.put(key, e);   // now the most recently hovered
            boolean retry = e.state == State.FAILED && clock.getAsLong() - e.failedAt > RETRY_MS;
            if (!retry) {
                if (wantsUpgrade(e, pxW, pxH) && loading < MAX_LOADING) {
                    e.upgrading = true;
                    start(m, new Entry(), e, boxW, boxH, pxW, pxH);
                }
                return e;
            }
            if (loading >= MAX_LOADING) return e;
        } else if (loading >= MAX_LOADING) {
            return WAITING;
        }
        Entry fresh = new Entry();
        entries.put(key, fresh);
        start(m, fresh, null, boxW, boxH, pxW, pxH);
        trimEmpty();
        return fresh;
    }

    /** Read-only: unlike {@link #getOrStart}, doesn't count as a hover. */
    public @Nullable Entry peek(String key) { return entries.get(key); }
    public int size() { return entries.size(); }
    public long usedBytes() { return usedBytes; }
    int loading() { return loading; }

    public void clear() {
        for (Entry e : entries.values()) if (e.texture != null) e.texture.release();
        entries.clear();
        usedBytes = 0;
    }

    /** Bigger box than it was decoded for, and the box (not the source size or the pixel budget) was what limited it. */
    static boolean wantsUpgrade(Entry e, int pxW, int pxH) {
        if (e.state != State.READY || e.upgrading || e.texture == null) return false;
        if (pxW < e.pxW * UPGRADE_RATIO && pxH < e.pxH * UPGRADE_RATIO) return false;
        int[] fitted = FrameDecoder.fit(e.srcW, e.srcH, e.pxW, e.pxH);
        boolean boxLimited = fitted[0] < e.srcW || fitted[1] < e.srcH;
        return boxLimited && e.texture.textureW() == fitted[0];
    }

    /** Still loading after {@link #SLOW_MS}: the tooltip says so, so a slow site doesn't look stuck. */
    public boolean isSlow(Entry e) {
        return e.state == State.LOADING && e.startedAt >= 0 && clock.getAsLong() - e.startedAt > SLOW_MS;
    }

    private void start(ResolvedMedia m, Entry target, @Nullable Entry replacing, int boxW, int boxH, int pxW, int pxH) {
        loading++;
        target.startedAt = clock.getAsLong();
        CompletableFuture<Decoded> f;
        try { f = loader.load(m, pxW, pxH); } catch (RuntimeException ex) { f = CompletableFuture.failedFuture(ex); }
        f.whenComplete((d, err) -> renderThread.execute(() -> finish(m, target, replacing, d, err, boxW, boxH, pxW, pxH)));
    }

    private void finish(ResolvedMedia m, Entry target, @Nullable Entry replacing, @Nullable Decoded d, @Nullable Throwable err,
                        int boxW, int boxH, int pxW, int pxH) {
        loading--;
        Entry current = entries.get(m.key());
        if (current != (replacing != null ? replacing : target)) {   // cleared or replaced while loading
            if (d != null) AnimatedTexture.close(d.images());
            return;
        }
        if (err != null || d == null) {
            if (replacing != null) {   // keep the smaller preview; don't try again at this size
                replacing.upgrading = false;
                replacing.pxW = pxW;
                replacing.pxH = pxH;
                return;
            }
            target.state = State.FAILED;
            target.error = describe(m, err);
            target.failedAt = clock.getAsLong();
            ImgPrevClient.LOG.debug("Preview {} failed: {}", m.key(), String.valueOf(err));
            return;
        }
        int[] display = FrameDecoder.fit(d.srcW(), d.srcH(), boxW, boxH);   // GUI units; the texture has up to guiScale× the pixels
        target.texture = new AnimatedTexture(m.key(), d.w(), d.h(), d.delaysMs(), d.images(), display[0], display[1]);
        target.state = State.READY;
        target.srcW = d.srcW();
        target.srcH = d.srcH();
        target.pxW = pxW;
        target.pxH = pxH;
        usedBytes += target.texture.bytes();
        if (replacing != null) {
            entries.replace(m.key(), target);
            AnimatedTexture old = replacing.texture;
            if (old != null) {
                old.release();
                usedBytes -= old.bytes();
            }
        }
        evictOverBudget(target);
    }

    /** Releases least-recently-hovered textures until under budget; never the one just loaded. */
    private void evictOverBudget(Entry keep) {
        long limit = budget.getAsLong();
        Iterator<Entry> it = entries.values().iterator();
        while (usedBytes > limit && it.hasNext()) {
            Entry old = it.next();
            if (old == keep || old.texture == null) continue;
            old.texture.release();
            usedBytes -= old.texture.bytes();
            it.remove();
        }
    }

    /** Forgets the oldest failed entries once there are more than {@link #MAX_EMPTY}. */
    private void trimEmpty() {
        int empty = 0;
        for (Entry e : entries.values()) if (e.state == State.FAILED) empty++;
        Iterator<Entry> it = entries.values().iterator();
        while (empty > MAX_EMPTY && it.hasNext()) {
            if (it.next().state == State.FAILED) {
                it.remove();
                empty--;
            }
        }
    }

    /** Download, decode, and copy into native memory; runs on the download and decode pools. */
    static CompletableFuture<Decoded> download(ResolvedMedia m, int pxW, int pxH) {
        int maxBytes = ImgPrevClient.CONFIG.maxDownloadBytes();
        URI uri;
        try { uri = m.codec().mediaUri(m.payload()); } catch (RuntimeException ex) { return CompletableFuture.failedFuture(ex); }
        return SafeHttp.get(uri, m.codec().allowedDownloadHosts(), maxBytes, "image/").thenApplyAsync(bytes -> decode(bytes, pxW, pxH), DECODE);
    }

    /** Decodes straight into native images, one frame at a time; nothing is left allocated if it fails partway. */
    static Decoded decode(byte[] bytes, int maxW, int maxH) {
        List<NativeImage> made = new ArrayList<>();
        try {
            FrameDecoder.Frames<NativeImage> f = FrameDecoder.decode(bytes, maxW, maxH, (px, w, h) -> {
                NativeImage img = toImage(px, w, h);
                made.add(img);
                return img;
            });
            return new Decoded(f.w(), f.h(), f.srcW(), f.srcH(), f.delaysMs(), f.frames().toArray(NativeImage[]::new));
        } catch (IOException ex) {
            made.forEach(NativeImage::close);
            throw new CompletionException(ex);
        } catch (RuntimeException | Error ex) {
            made.forEach(NativeImage::close);
            throw ex;
        }
    }

    /**
     * ARGB pixels into a native RGBA image. A bulk copy through the raw pointer measured no faster (about 4 ms per
     * 2048×2048 frame either way), so this keeps NativeImage's bounds-checked setPixel.
     */
    static NativeImage toImage(int[] argb, int w, int h) {
        NativeImage img = new NativeImage(w, h, false);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) img.setPixel(x, y, argb[y * w + x]);   // ARGB in
        return img;
    }

    static String describe(ResolvedMedia m, @Nullable Throwable err) {
        return switch (err == null ? null : SafeHttp.unwrap(err)) {
            case MediaException e -> e.getMessage();
            case SafeHttp.HttpStatusException e -> m.codec().statusMessage(e.status).orElseGet(() -> switch (e.status) {
                case 404, 410 -> "Image not found";
                case 429 -> "Rate limited, try again later";
                default -> "Couldn't load image (HTTP " + e.status + ")";
            });
            case HttpTimeoutException e -> "Timed out";
            case IIOException e -> "Couldn't decode image";
            case null, default -> "Couldn't load image";
        };
    }
}
