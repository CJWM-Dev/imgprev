package dev.example.imgprev.image;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;

/**
 * HTTPS GETs that never leave a site's host allowlist. Pure Java (java.net.http). Every download is bounded in time:
 * the request timeout only covers the wait for headers, so the body has its own idle and total limits.
 */
public final class SafeHttp {
    static final String USER_AGENT = "ImgPrev/" + modVersion() + " (Minecraft mod)";
    static final String BLOCKED = "Blocked: download left the site's servers";
    static final int MAX_REDIRECTS = 3;
    /** Bodies of redirects and errors are read up to this much, then dropped. */
    static final int DISCARD_BYTES = 64 << 10;
    static final Timeouts TIMEOUTS = new Timeouts(Duration.ofSeconds(10), Duration.ofSeconds(10), Duration.ofSeconds(120));
    static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(Duration.ofSeconds(5)).build();
    /** Two downloads at a time. */
    static final ExecutorService POOL = Executors.newFixedThreadPool(2, daemon("imgprev-dl"));

    private SafeHttp() {}

    /**
     * {@code response}: from sending the request to its headers. {@code idle}: longest gap while the body arrives.
     * {@code total}: the whole download, redirects included.
     */
    record Timeouts(Duration response, Duration idle, Duration total) {}

    /** Throws when a hop isn't allowed. */
    @FunctionalInterface
    interface HostCheck { void check(URI u) throws MediaException; }

    public static final class HttpStatusException extends IOException {
        public final int status;
        public HttpStatusException(int status) { super("HTTP " + status); this.status = status; }
    }

    /** Redirects are followed by hand and every hop must be HTTPS on {@code allowedHosts}. */
    public static CompletableFuture<byte[]> get(URI uri, Set<String> allowedHosts, int maxBytes, String contentTypePrefix) {
        return CompletableFuture.supplyAsync(() -> {
            try { return fetch(HTTP, u -> checkHost(u, allowedHosts), uri, maxBytes, contentTypePrefix, TIMEOUTS); }
            catch (IOException e) { throw new CompletionException(e); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new CompletionException(e); }
        }, POOL);
    }

    static byte[] fetch(HttpClient client, HostCheck hosts, URI uri, int maxBytes, String contentTypePrefix, Timeouts limits)
            throws IOException, InterruptedException {
        long deadline = System.nanoTime() + limits.total().toNanos();
        URI cur = uri;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            hosts.check(cur);
            HttpRequest req = HttpRequest.newBuilder(cur).timeout(limits.response()).header("User-Agent", USER_AGENT).GET().build();
            AtomicReference<CappedBody> body = new AtomicReference<>();
            HttpResponse<byte[]> r = await(client.sendAsync(req, info -> {
                body.set(CappedBody.forResponse(info, maxBytes, contentTypePrefix));
                return body.get();
            }), body, limits.idle(), deadline);
            int status = r.statusCode();
            if (status / 100 == 3) {
                cur = cur.resolve(r.headers().firstValue("location").orElseThrow(() -> new MediaException("Bad redirect")));
                continue;
            }
            if (status != 200) throw new HttpStatusException(status);
            return r.body();
        }
        throw new MediaException("Too many redirects");
    }

    /** Waits for the response, giving up when its body stalls for {@code idle} or the deadline passes. */
    private static <T> HttpResponse<T> await(CompletableFuture<HttpResponse<T>> f, AtomicReference<CappedBody> body, Duration idle,
                                             long deadline) throws IOException, InterruptedException {
        while (true) {
            try {
                return f.get(100, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                long now = System.nanoTime();
                CappedBody b = body.get();
                boolean stalled = b != null && now - b.lastProgress > idle.toNanos();
                if (stalled || now - deadline > 0) {
                    HttpTimeoutException timeout = new HttpTimeoutException(stalled ? "download stalled" : "download took too long");
                    if (b != null) b.fail(timeout);
                    f.cancel(true);
                    throw timeout;
                }
            } catch (ExecutionException e) {
                Throwable t = unwrap(e);
                throw t instanceof IOException io ? io : new IOException(t);
            }
        }
    }

    /** The cause under any {@link CompletionException}/{@link ExecutionException} wrappers. */
    public static Throwable unwrap(Throwable err) {
        Throwable t = err;
        while ((t instanceof CompletionException || t instanceof ExecutionException) && t.getCause() != null) t = t.getCause();
        return t;
    }

    static void checkHost(URI u, Set<String> allowedHosts) throws MediaException {
        String host = u.getHost() == null ? null : u.getHost().toLowerCase(Locale.ROOT);
        boolean ok = "https".equalsIgnoreCase(u.getScheme()) && host != null && allowedHosts.contains(host)
            && (u.getPort() == -1 || u.getPort() == 443) && u.getRawUserInfo() == null;
        if (!ok) throw new MediaException(BLOCKED);
    }

    static MediaException tooLarge(int maxBytes) { return new MediaException("File too large (over " + Math.ceilDiv(maxBytes, 1 << 20) + " MB)"); }

    static ThreadFactory daemon(String name) {
        AtomicInteger n = new AtomicInteger();
        return r -> { Thread t = new Thread(r, name + "-" + n.incrementAndGet()); t.setDaemon(true); return t; };
    }

    private static String modVersion() {
        try {
            return FabricLoader.getInstance().getModContainer("imgprev").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("dev");
        } catch (RuntimeException | LinkageError e) {
            return "dev";   // unit tests run without a loader
        }
    }

    /**
     * Collects a body up to a cap, noting when bytes last arrived. A 200 with the wrong type or a declared length over
     * the cap fails before any of the body is read; the bodies of redirects and errors are dropped.
     */
    static final class CappedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final int max;
        private final boolean keep;
        private final @Nullable IOException failure;
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream out;
        private volatile Flow.@Nullable Subscription subscription;
        private int total;
        volatile long lastProgress = System.nanoTime();

        private CappedBody(int max, boolean keep, @Nullable IOException failure, long expected) {
            this.max = max;
            this.keep = keep;
            this.failure = failure;
            this.out = new ByteArrayOutputStream(keep ? (int) Math.min(max, expected > 0 ? expected : 1 << 16) : 0);
        }

        static CappedBody forResponse(HttpResponse.ResponseInfo info, int maxBytes, String contentTypePrefix) {
            if (info.statusCode() != 200) return new CappedBody(DISCARD_BYTES, false, null, 0);
            String type = info.headers().firstValue("content-type").orElse("").toLowerCase(Locale.ROOT);
            long length = info.headers().firstValueAsLong("content-length").orElse(-1);
            IOException bad = !type.startsWith(contentTypePrefix) ? new MediaException(contentTypePrefix.equals("image/") ? "Not an image" : "Unexpected response")
                : length > maxBytes ? tooLarge(maxBytes) : null;
            return new CappedBody(maxBytes, true, bad, length);
        }

        @Override public void onSubscribe(Flow.Subscription s) {
            subscription = s;
            if (failure != null) fail(failure);
            else s.request(1);
        }

        @Override public void onNext(List<ByteBuffer> items) {
            lastProgress = System.nanoTime();
            for (ByteBuffer b : items) {
                total += b.remaining();
                if (total > max) {
                    if (keep) fail(tooLarge(max));
                    else discard();
                    return;
                }
                if (keep) {
                    byte[] chunk = new byte[b.remaining()];
                    b.get(chunk);
                    out.writeBytes(chunk);
                }
            }
            Flow.@Nullable Subscription s = subscription;
            if (s != null) s.request(1);
        }

        @Override public void onError(Throwable t) { result.completeExceptionally(t); }
        @Override public void onComplete() { result.complete(keep ? out.toByteArray() : null); }
        @Override public CompletionStage<byte[]> getBody() { return result; }

        void fail(Throwable t) {
            if (result.completeExceptionally(t)) cancel();
        }

        /** Enough of an unwanted body: stop reading it. */
        private void discard() {
            if (result.complete(null)) cancel();
        }

        private void cancel() {
            Flow.@Nullable Subscription s = subscription;
            if (s != null) s.cancel();
        }
    }
}
