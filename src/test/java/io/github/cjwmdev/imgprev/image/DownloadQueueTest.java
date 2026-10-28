package io.github.cjwmdev.imgprev.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

/** Which downloads start when. The executor only collects them, so each test decides when one finishes. */
class DownloadQueueTest {
    static final String DISCORD = "cdn.discordapp.com", TENOR = "media.tenor.com";

    final List<Runnable> started = new ArrayList<>();
    final DownloadQueue queue = new DownloadQueue(started::add);

    CompletableFuture<String> submit(String host, String name) { return queue.submit(host, () -> name); }

    /** Finishes the {@code i}th download to have started. */
    void finish(int i) { started.get(i).run(); }

    @Test void twoFromOneSiteGoThroughWhenItsTheOnlySite() {
        submit(DISCORD, "d1");
        submit(DISCORD, "d2");
        assertEquals(2, queue.running());
        submit(DISCORD, "d3");
        assertEquals(2, queue.running(), "but not three");
        assertEquals(1, queue.waiting());
    }

    @Test void anotherSiteStopsASecondFromTheSameSite() {
        CompletableFuture<String> d1 = submit(DISCORD, "d1"), tenor = submit(TENOR, "t1"), d2 = submit(DISCORD, "d2");
        assertEquals(2, queue.running(), "Discord and Tenor, one each");
        assertEquals(1, queue.waiting(), "the second Discord waits while Tenor is loading");
        finish(1);
        assertEquals("t1", tenor.join());
        assertEquals(2, queue.running(), "Tenor done: Discord is the only site, so the second one starts");
        finish(0);
        finish(2);
        assertEquals("d1", d1.join());
        assertEquals("d2", d2.join());
    }

    @Test void whenBothAreWaitingTheIdleSiteGoesFirst() {
        for (String host : new String[] {"a", "b", "c"}) submit(host, host);
        CompletableFuture<String> d1 = submit(DISCORD, "d1");
        assertEquals(DownloadQueue.MAX_RUNNING, queue.running(), "every slot taken");
        CompletableFuture<String> d2 = submit(DISCORD, "d2"), tenor = submit(TENOR, "t1");   // d2 asked first
        assertEquals(2, queue.waiting());

        finish(0);   // a frees a slot
        assertEquals(5, started.size());
        started.getLast().run();
        assertEquals("t1", tenor.join(), "Tenor took the slot, ahead of the older second Discord");
        assertFalse(d2.isDone());
        assertEquals(1, queue.waiting());
        assertFalse(d1.isDone());
    }

    @Test void theOldestFromAnIdleSiteGoesNext() {
        CompletableFuture<String> b1 = submit("a", "a1");
        for (String host : new String[] {"b", "c", "d"}) submit(host, host);
        CompletableFuture<String> e1 = submit("e", "e1"), f1 = submit("f", "f1");
        finish(0);
        started.getLast().run();
        assertEquals("e1", e1.join(), "e asked before f");
        assertFalse(f1.isDone());
        assertTrue(b1.isDone());
    }

    @Test void atMostFourRunAtOnce() {
        for (String host : new String[] {"a", "b", "c", "d", "e", "f"}) submit(host, host);
        assertEquals(DownloadQueue.MAX_RUNNING, queue.running());
        assertEquals(2, queue.waiting());
    }

    @Test void aFailedDownloadFreesItsTurn() {
        CompletableFuture<String> bad = queue.submit("a", () -> { throw new IOException("connection reset"); });
        submit("b", "b1");
        submit("a", "a2");
        assertEquals(1, queue.waiting(), "a2 waits behind a1 while b is loading");
        finish(0);
        ExecutionException e = assertThrows(ExecutionException.class, bad::get);
        assertEquals("connection reset", e.getCause().getMessage());
        assertEquals(0, queue.waiting(), "a2 took a1's turn");
        assertEquals(2, queue.running());
    }
}
