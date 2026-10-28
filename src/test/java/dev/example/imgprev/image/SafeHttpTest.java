package dev.example.imgprev.image;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SafeHttpTest {
    static final Set<String> HOSTS = Set.of("i.imgur.com");
    static final SafeHttp.Timeouts FAST = new SafeHttp.Timeouts(Duration.ofSeconds(1), Duration.ofMillis(500), Duration.ofMillis(1500));
    static final HttpClient CLIENT = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    HttpServer server;
    final CountDownLatch stop = new CountDownLatch(1);

    @BeforeEach void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool(SafeHttp.daemon("test-http")));
        server.start();
    }

    @AfterEach void shutdown() {
        stop.countDown();
        server.stop(0);
    }

    URI url(String path) { return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path); }

    /** Only this test server is allowed: the same rule as production, but over plain HTTP on 127.0.0.1. */
    SafeHttp.HostCheck local() {
        return u -> {
            if (!"127.0.0.1".equals(u.getHost()) || u.getPort() != server.getAddress().getPort()) throw new MediaException(SafeHttp.BLOCKED);
        };
    }

    byte[] fetch(String path, int maxBytes) throws Exception {
        return SafeHttp.fetch(CLIENT, local(), url(path), maxBytes, "image/", FAST);
    }

    void serve(String path, int status, String type, byte[] body) {
        server.createContext(path, ex -> respond(ex, status, type, body, body.length));
    }

    static void respond(HttpExchange ex, int status, String type, byte[] body, long length) throws IOException {
        if (type != null) ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(status, length);
        try (OutputStream o = ex.getResponseBody()) { o.write(body); }
    }

    void redirect(String path, String to) {
        server.createContext(path, ex -> {
            ex.getResponseHeaders().set("Location", to);
            ex.sendResponseHeaders(302, -1);
            ex.close();
        });
    }

    // --- The production host rule ---

    @ParameterizedTest
    @ValueSource(strings = {"https://i.imgur.com/a.png", "https://I.IMGUR.COM/a.png", "https://i.imgur.com:443/a.png"})
    void allowedHosts(String url) throws MediaException {
        SafeHttp.checkHost(URI.create(url), HOSTS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://i.imgur.com/a.png", "https://i.imgur.com:8443/a.png", "https://user@i.imgur.com/a.png",
        "https://imgur.com/a.png", "https://i.imgur.com.evil.example/a.png", "https:///a.png", "ftp://i.imgur.com/a.png"})
    void blockedHosts(String url) {
        MediaException e = assertThrows(MediaException.class, () -> SafeHttp.checkHost(URI.create(url), HOSTS));
        assertEquals(SafeHttp.BLOCKED, e.getMessage());
    }

    // --- Responses ---

    @Test void returnsTheBody() throws Exception {
        serve("/a.png", 200, "image/png", new byte[] {1, 2, 3});
        assertArrayEquals(new byte[] {1, 2, 3}, fetch("/a.png", 100));
    }

    @Test void errorStatusIsReported() {
        serve("/gone.png", 404, "text/html", "not here".getBytes());
        SafeHttp.HttpStatusException e = assertThrows(SafeHttp.HttpStatusException.class, () -> fetch("/gone.png", 100));
        assertEquals(404, e.status);
    }

    @Test void wrongContentTypeIsRefused() {
        serve("/page", 200, "text/html; charset=utf-8", "<html>".getBytes());
        assertEquals("Not an image", assertThrows(MediaException.class, () -> fetch("/page", 100)).getMessage());
    }

    @Test void declaredLengthOverTheCapIsRefusedBeforeReading() {
        serve("/big.png", 200, "image/png", new byte[2000]);
        assertTrue(assertThrows(MediaException.class, () -> fetch("/big.png", 1000)).getMessage().startsWith("File too large"));
    }

    @Test void streamedBodyOverTheCapIsCutOff() {
        server.createContext("/chunked.png", ex -> respond(ex, 200, "image/png", new byte[5000], 0));   // 0: chunked, no length
        assertTrue(assertThrows(MediaException.class, () -> fetch("/chunked.png", 1000)).getMessage().startsWith("File too large"));
    }

    @Test void bodyOfExactlyTheCapIsFine() throws Exception {
        server.createContext("/exact.png", ex -> respond(ex, 200, "image/png", new byte[1000], 0));
        assertEquals(1000, fetch("/exact.png", 1000).length);
    }

    // --- Redirects ---

    @Test void followsRedirectsOnAllowedHosts() throws Exception {
        serve("/final.png", 200, "image/png", new byte[] {7});
        redirect("/hop", "/final.png");
        assertArrayEquals(new byte[] {7}, fetch("/hop", 100));
    }

    @Test void redirectOffTheAllowlistIsBlocked() {
        redirect("/away", "http://localhost:" + server.getAddress().getPort() + "/final.png");   // same server, different host name
        assertEquals(SafeHttp.BLOCKED, assertThrows(MediaException.class, () -> fetch("/away", 100)).getMessage());
    }

    @Test void redirectChainsAreLimited() throws Exception {
        serve("/r0", 200, "image/png", new byte[] {1});
        for (int i = 1; i <= SafeHttp.MAX_REDIRECTS + 1; i++) redirect("/r" + i, "/r" + (i - 1));
        assertEquals(1, fetch("/r" + SafeHttp.MAX_REDIRECTS, 100).length, "the limit itself is fine");
        assertEquals("Too many redirects", assertThrows(MediaException.class, () -> fetch("/r" + (SafeHttp.MAX_REDIRECTS + 1), 100)).getMessage());
    }

    // --- Stalls ---

    @Test void stalledBodyTimesOutInsteadOfHanging() {
        server.createContext("/stall.png", ex -> {
            ex.getResponseHeaders().set("Content-Type", "image/png");
            ex.sendResponseHeaders(200, 0);
            OutputStream o = ex.getResponseBody();
            o.write(new byte[100]);
            o.flush();
            try { stop.await(30, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}   // then nothing more
        });
        long t0 = System.nanoTime();
        HttpTimeoutException e = assertThrows(HttpTimeoutException.class, () -> fetch("/stall.png", 1 << 20));
        long ms = (System.nanoTime() - t0) / 1_000_000;
        assertEquals("download stalled", e.getMessage());
        assertTrue(ms < 3000, "gave up after " + ms + " ms");
    }

    @Test void slowButSteadyBodyIsCappedByTheTotalLimit() {
        server.createContext("/drip.png", ex -> {
            ex.getResponseHeaders().set("Content-Type", "image/png");
            ex.sendResponseHeaders(200, 0);
            OutputStream o = ex.getResponseBody();
            try {
                while (stop.getCount() > 0) { o.write(1); o.flush(); Thread.sleep(100); }   // never idle long enough to stall
            } catch (InterruptedException | IOException ignored) {}
        });
        HttpTimeoutException e = assertThrows(HttpTimeoutException.class, () -> fetch("/drip.png", 1 << 20));
        assertEquals("download took too long", e.getMessage());
    }

    @Test void unwrapFindsTheCause() {
        IOException cause = new IOException("x");
        assertEquals(cause, SafeHttp.unwrap(new java.util.concurrent.CompletionException(new java.util.concurrent.ExecutionException(cause))));
    }

    @Test void userAgentNamesTheMod() {
        assertTrue(SafeHttp.USER_AGENT.startsWith("ImgPrev/") && !SafeHttp.USER_AGENT.startsWith("ImgPrev/0.1 "), SafeHttp.USER_AGENT);
    }
}
