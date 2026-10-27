package dev.example.imgprev.image;

import com.mojang.blaze3d.platform.NativeImage;
import dev.example.imgprev.ImgPrevClient;
import dev.example.imgprev.token.ResolvedMedia;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.IIOException;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * Hover-driven image cache bounded by a byte budget (LRU). Render thread only: downloads and decodes run on
 * background pools and post their results back with {@code Minecraft#execute}.
 */
public final class ImageCache {
    public enum State { LOADING, READY, FAILED }

    static final long RETRY_MS = 60_000;
    static final ExecutorService DECODE = Executors.newSingleThreadExecutor(SafeHttp.daemon("imgprev-decode"));

    public static final class Entry {
        private State state = State.LOADING;
        private @Nullable AnimatedTexture texture;
        private String error = "";
        private long failedAt;

        public State state() { return state; }
        public @Nullable AnimatedTexture texture() { return texture; }
        public String error() { return error; }
    }

    private record Decoded(FrameDecoder.Frames frames, NativeImage sheet) {}

    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);   // access order = LRU
    private long usedBytes;

    /** Called every frame while a preview is hovered. FAILED entries are kept so dead links aren't refetched each frame. */
    public Entry getOrStart(ResolvedMedia m) {
        Entry e = entries.get(m.key());
        if (e != null && !(e.state == State.FAILED && Util.getMillis() - e.failedAt > RETRY_MS)) return e;
        Entry fresh = new Entry();
        entries.put(m.key(), fresh);
        load(m, fresh);
        return fresh;
    }

    public @Nullable Entry peek(String key) { return entries.get(key); }
    public int size() { return entries.size(); }
    public long usedBytes() { return usedBytes; }

    public void clear() {
        for (Entry e : entries.values()) if (e.texture != null) e.texture.release();
        entries.clear();
        usedBytes = 0;
    }

    private void load(ResolvedMedia m, Entry e) {
        int maxBytes = ImgPrevClient.CONFIG.maxDownloadBytes(), maxPx = ImgPrevClient.CONFIG.maxDrawPx;
        CompletableFuture<URI> uri;
        try { uri = m.codec().mediaUri(m.payload()); } catch (RuntimeException ex) { uri = CompletableFuture.failedFuture(ex); }
        uri.thenCompose(u -> SafeHttp.get(u, m.codec().allowedDownloadHosts(), maxBytes, "image/"))
            .thenApplyAsync(bytes -> decode(bytes, maxPx), DECODE)
            .whenComplete((d, err) -> Minecraft.getInstance().execute(() -> finish(m, e, d, err)));
    }

    private static Decoded decode(byte[] bytes, int maxPx) {
        try {
            FrameDecoder.Frames f = FrameDecoder.decode(bytes, maxPx);
            return new Decoded(f, AnimatedTexture.toSheet(f));
        } catch (IOException ex) {
            throw new CompletionException(ex);
        }
    }

    private void finish(ResolvedMedia m, Entry e, @Nullable Decoded d, @Nullable Throwable err) {
        if (entries.get(m.key()) != e) {   // cleared or replaced while loading
            if (d != null) d.sheet().close();
            return;
        }
        if (err != null) {
            e.state = State.FAILED;
            e.error = describe(m, err);
            e.failedAt = Util.getMillis();
            ImgPrevClient.LOG.debug("Preview {} failed: {}", m.key(), err.toString());
            return;
        }
        e.texture = AnimatedTexture.upload(m.key(), d.frames(), d.sheet());
        e.state = State.READY;
        usedBytes += e.texture.bytes();
        evictOverBudget(e);
    }

    /** Releases least-recently-hovered textures until under budget; never the one just loaded. */
    private void evictOverBudget(Entry keep) {
        long budget = ImgPrevClient.CONFIG.cacheBudgetBytes();
        Iterator<Map.Entry<String, Entry>> it = entries.entrySet().iterator();
        while (usedBytes > budget && it.hasNext()) {
            Entry old = it.next().getValue();
            if (old == keep || old.texture == null) continue;
            old.texture.release();
            usedBytes -= old.texture.bytes();
            it.remove();
        }
    }

    static String describe(ResolvedMedia m, Throwable err) {
        Throwable t = err;
        while ((t instanceof CompletionException || t instanceof ExecutionException) && t.getCause() != null) t = t.getCause();
        return switch (t) {
            case MediaException e -> e.getMessage();
            case SafeHttp.HttpStatusException e when m.codec().code() == 'd' && (e.status == 403 || e.status == 404) -> "Discord link expired";
            case SafeHttp.HttpStatusException e when m.codec().code() == 'k' && (e.status == 401 || e.status == 403) -> "Klipy API key rejected";
            case SafeHttp.HttpStatusException e when e.status == 404 || e.status == 410 -> "Image not found";
            case SafeHttp.HttpStatusException e when e.status == 429 -> "Rate limited, try again later";
            case SafeHttp.HttpStatusException e -> "Couldn't load image (HTTP " + e.status + ")";
            case SecurityException e -> "Blocked: download left the site's servers";
            case HttpTimeoutException e -> "Timed out";
            case IIOException e -> "Couldn't decode image";
            default -> "Couldn't load image";
        };
    }
}
