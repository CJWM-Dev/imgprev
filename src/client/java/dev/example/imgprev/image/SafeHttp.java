package dev.example.imgprev.image;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/** HTTPS GETs that never leave a site's host allowlist. Pure Java (java.net.http). */
public final class SafeHttp {
    static final String USER_AGENT = "ImgPrev/0.1 (Minecraft mod)";
    static final int MAX_REDIRECTS = 3;
    static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(Duration.ofSeconds(5)).build();
    /** Two downloads at a time. */
    static final ExecutorService POOL = Executors.newFixedThreadPool(2, daemon("imgprev-dl"));

    private SafeHttp() {}

    public static final class HttpStatusException extends IOException {
        public final int status;
        HttpStatusException(int status) { super("HTTP " + status); this.status = status; }
    }

    /** Redirects are followed by hand and every hop must be HTTPS on {@code allowedHosts}. */
    public static CompletableFuture<byte[]> get(URI uri, Set<String> allowedHosts, int maxBytes, String contentTypePrefix) {
        return CompletableFuture.supplyAsync(() -> {
            try { return fetch(uri, allowedHosts, maxBytes, contentTypePrefix); }
            catch (IOException e) { throw new CompletionException(e); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new CompletionException(e); }
        }, POOL);
    }

    static byte[] fetch(URI uri, Set<String> allowedHosts, int maxBytes, String contentTypePrefix) throws IOException, InterruptedException {
        URI cur = uri;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            checkHost(cur, allowedHosts);
            HttpRequest req = HttpRequest.newBuilder(cur).timeout(Duration.ofSeconds(10)).header("User-Agent", USER_AGENT).GET().build();
            HttpResponse<InputStream> r = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = r.body()) {
                int status = r.statusCode();
                if (status / 100 == 3) {
                    cur = cur.resolve(r.headers().firstValue("location").orElseThrow(() -> new MediaException("Bad redirect")));
                    continue;
                }
                if (status != 200) throw new HttpStatusException(status);
                String type = r.headers().firstValue("content-type").orElse("").toLowerCase(Locale.ROOT);
                if (!type.startsWith(contentTypePrefix)) throw new MediaException(contentTypePrefix.equals("image/") ? "Not an image" : "Unexpected response");
                if (r.headers().firstValueAsLong("content-length").orElse(-1) > maxBytes) throw tooLarge(maxBytes);
                return readCapped(in, maxBytes);
            }
        }
        throw new MediaException("Too many redirects");
    }

    static void checkHost(URI u, Set<String> allowedHosts) {
        String host = u.getHost() == null ? null : u.getHost().toLowerCase(Locale.ROOT);
        boolean ok = "https".equalsIgnoreCase(u.getScheme()) && host != null && allowedHosts.contains(host)
            && (u.getPort() == -1 || u.getPort() == 443) && u.getRawUserInfo() == null;
        if (!ok) throw new SecurityException("blocked: " + u.getScheme() + "://" + u.getHost());
    }

    static byte[] readCapped(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(maxBytes, 1 << 16));
        byte[] buf = new byte[8192];
        int total = 0;
        for (int n; (n = in.read(buf)) != -1; ) {
            total += n;
            if (total > maxBytes) throw tooLarge(maxBytes);
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    static MediaException tooLarge(int maxBytes) { return new MediaException("File too large (over " + Math.ceilDiv(maxBytes, 1 << 20) + " MB)"); }

    static ThreadFactory daemon(String name) {
        AtomicInteger n = new AtomicInteger();
        return r -> { Thread t = new Thread(r, name + "-" + n.incrementAndGet()); t.setDaemon(true); return t; };
    }
}
