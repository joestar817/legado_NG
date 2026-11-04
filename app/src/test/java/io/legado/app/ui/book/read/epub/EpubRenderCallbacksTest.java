package io.legado.app.ui.book.read.epub;

import org.junit.Assume;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.Dispatchers;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

public class EpubRenderCallbacksTest {
    @Test
    public void callbacksRunOutsideTheSubmittingThread() throws Exception {
        Thread submitting = Thread.currentThread();
        CompletableFuture<Thread> completed = new CompletableFuture<>();
        EpubRenderCallbacks.INSTANCE.getExecutor().execute(() -> completed.complete(Thread.currentThread()));
        assertNotSame(submitting, completed.get(3, TimeUnit.SECONDS));
    }

    @Test
    public void completionUnblocksAnOccupiedCallerPool() throws Exception {
        ExecutorService waitingPool = Executors.newSingleThreadExecutor();
        CompletableFuture<Boolean> signal = new CompletableFuture<>();
        CompletableFuture<Boolean> finished = new CompletableFuture<>();
        CountDownLatch waiting = new CountDownLatch(1);
        try {
            waitingPool.execute(() -> {
                waiting.countDown();
                try {
                    finished.complete(signal.get(3, TimeUnit.SECONDS));
                } catch (Exception error) {
                    finished.completeExceptionally(error);
                }
            });
            assertTrue(waiting.await(3, TimeUnit.SECONDS));
            EpubRenderCallbacks.INSTANCE.getExecutor().execute(() -> signal.complete(true));
            assertTrue(finished.get(4, TimeUnit.SECONDS));
        } finally {
            waitingPool.shutdownNow();
            assertTrue(waitingPool.awaitTermination(3, TimeUnit.SECONDS));
        }
    }

    /** Run in an isolated JVM with -Dkotlinx.coroutines.io.parallelism=1. */
    @Test
    public void completionDoesNotNeedAFreeIoWorker() throws Exception {
        Assume.assumeTrue("Requires an isolated one-worker IO dispatcher",
                "1".equals(System.getProperty("kotlinx.coroutines.io.parallelism")));
        CompletableFuture<Boolean> signal = new CompletableFuture<>();
        CompletableFuture<Boolean> finished = new CompletableFuture<>();
        CountDownLatch waiting = new CountDownLatch(1);
        Dispatchers.getIO().dispatch(EmptyCoroutineContext.INSTANCE, () -> {
            waiting.countDown();
            try {
                finished.complete(signal.get(3, TimeUnit.SECONDS));
            } catch (Exception error) {
                finished.completeExceptionally(error);
            }
        });
        assertTrue(waiting.await(3, TimeUnit.SECONDS));
        EpubRenderCallbacks.INSTANCE.getExecutor().execute(() -> signal.complete(true));
        assertTrue(finished.get(4, TimeUnit.SECONDS));
    }
}
