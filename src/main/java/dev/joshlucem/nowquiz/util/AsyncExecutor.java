package dev.joshlucem.nowquiz.util;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Small dedicated executor used for disk and SQL work.
 *
 * <p>A single-thread executor keeps SQLite access serialized, which avoids
 * unnecessary locking complexity while still keeping blocking work off the main thread.</p>
 */
public final class AsyncExecutor {

    private final ExecutorService executor;

    public AsyncExecutor(String threadName) {
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, threadName);
            thread.setDaemon(true);
            return thread;
        };
        this.executor = Executors.newSingleThreadExecutor(factory);
    }

    public CompletableFuture<Void> run(Runnable runnable) {
        return CompletableFuture.runAsync(runnable, this.executor);
    }

    public <T> CompletableFuture<T> supply(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, this.executor);
    }

    public void shutdown(Duration timeout) {
        this.executor.shutdown();
        try {
            if (!this.executor.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                this.executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            this.executor.shutdownNow();
        }
    }
}
