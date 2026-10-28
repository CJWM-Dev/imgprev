package io.github.cjwmdev.imgprev.image;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.jspecify.annotations.Nullable;

/**
 * Decides which downloads run, so one slow site can't hold up the others. While more than one site has downloads
 * waiting or running, each site gets one at a time and the oldest download from an idle site goes next: with Discord
 * running and both Discord and Tenor waiting, Tenor starts first. A site loading on its own may run two at once;
 * more would invite the burst limits some hosts have (ImgBB answers bursts with 503).
 */
final class DownloadQueue {
    static final int MAX_RUNNING = 4;
    static final int MAX_PER_SITE_ALONE = 2;

    private record Job(String host, Runnable run) {}

    private final Executor executor;
    private final Deque<Job> waiting = new ArrayDeque<>();   // oldest first
    private final Map<String, Integer> running = new HashMap<>();
    private int total;

    DownloadQueue(Executor executor) { this.executor = executor; }

    /** Runs {@code task} when {@code host}'s turn comes; the future completes with its result or exception. */
    <T> CompletableFuture<T> submit(String host, Callable<T> task) {
        CompletableFuture<T> f = new CompletableFuture<>();
        Job job = new Job(host, () -> {
            try {
                f.complete(task.call());
            } catch (Throwable t) {
                if (t instanceof InterruptedException) Thread.currentThread().interrupt();
                f.completeExceptionally(t);
            } finally {
                finished(host);
            }
        });
        synchronized (this) { waiting.add(job); }
        startWhatCan();
        return f;
    }

    synchronized int running() { return total; }
    synchronized int waiting() { return waiting.size(); }

    private void finished(String host) {
        synchronized (this) {
            total--;
            running.computeIfPresent(host, (h, n) -> n > 1 ? n - 1 : null);
        }
        startWhatCan();
    }

    private void startWhatCan() {
        List<Job> start = new ArrayList<>();
        synchronized (this) {
            for (Job j; total < MAX_RUNNING && (j = next()) != null; ) {
                waiting.remove(j);
                running.merge(j.host(), 1, Integer::sum);
                total++;
                start.add(j);
            }
        }
        start.forEach(j -> executor.execute(j.run()));
    }

    /** The oldest waiting download whose site is idle; or, when only one site is loading at all, its next one. */
    private @Nullable Job next() {
        for (Job j : waiting) if (!running.containsKey(j.host())) return j;
        Job first = waiting.peekFirst();
        if (first == null) return null;
        boolean alone = running.keySet().stream().allMatch(first.host()::equals) && waiting.stream().allMatch(j -> j.host().equals(first.host()));
        return alone && running.getOrDefault(first.host(), 0) < MAX_PER_SITE_ALONE ? first : null;
    }
}
