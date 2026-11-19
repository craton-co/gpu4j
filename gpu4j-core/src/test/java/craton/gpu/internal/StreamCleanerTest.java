// SPDX-License-Identifier: Apache-2.0
// Copyright 2025-2026 Craton Software Company (https://craton.com.ar)

package craton.gpu.internal;

import craton.gpu.GpuException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Verifies the deterministic-clean and idempotency contracts of
 * {@link StreamCleaner}. The explicit-close path is exercised via
 * {@link StreamCleaner.Registration#cleanThrowing()}; the GC-driven
 * cleanup path is covered by a separate {@link Disabled} test (see bottom
 * of this file) because {@link java.lang.ref.Cleaner} firing is inherently
 * non-deterministic.
 */
class StreamCleanerTest {

    private NativeBridge bridge;

    @BeforeEach
    void installMockBridge() {
        bridge = mock(NativeBridge.class);
        Native.setBridge(bridge);
    }

    @AfterEach
    void restoreDefaultBridge() {
        Native.setBridge(null);
    }

    @Test
    void zeroHandleRegistersANoOpCleanable() {
        Object owner = new Object();
        StreamCleaner.Registration c =
            StreamCleaner.register(owner, 0L, StreamCleaner.Kind.EXECUTOR);

        c.cleanThrowing();
        c.cleanThrowing();

        verifyNoInteractions(bridge);
    }

    @Test
    void executorKindReleasesViaReleaseExecutor() {
        Object owner = new Object();
        StreamCleaner.Registration c =
            StreamCleaner.register(owner, 42L, StreamCleaner.Kind.EXECUTOR);

        c.cleanThrowing();

        verify(bridge, times(1)).releaseExecutor(42L);
        verify(bridge, never()).closeStream(42L);
        verify(bridge, never()).releaseFuture(42L);
        verify(bridge, never()).releaseArray(42L);
    }

    @Test
    void streamKindReleasesViaCloseStream() {
        Object owner = new Object();
        StreamCleaner.Registration c =
            StreamCleaner.register(owner, 43L, StreamCleaner.Kind.STREAM);

        c.cleanThrowing();

        verify(bridge, times(1)).closeStream(43L);
        verify(bridge, never()).releaseExecutor(43L);
        verify(bridge, never()).releaseFuture(43L);
        verify(bridge, never()).releaseArray(43L);
    }

    @Test
    void futureKindReleasesViaReleaseFuture() {
        Object owner = new Object();
        StreamCleaner.Registration c =
            StreamCleaner.register(owner, 44L, StreamCleaner.Kind.FUTURE);

        c.cleanThrowing();

        verify(bridge, times(1)).releaseFuture(44L);
        verify(bridge, never()).releaseExecutor(44L);
        verify(bridge, never()).closeStream(44L);
        verify(bridge, never()).releaseArray(44L);
    }

    @Test
    void arrayKindReleasesViaReleaseArray() {
        Object owner = new Object();
        StreamCleaner.Registration c =
            StreamCleaner.register(owner, 45L, StreamCleaner.Kind.ARRAY);

        c.cleanThrowing();

        verify(bridge, times(1)).releaseArray(45L);
        verify(bridge, never()).releaseExecutor(45L);
        verify(bridge, never()).closeStream(45L);
        verify(bridge, never()).releaseFuture(45L);
    }

    @Test
    void graphKindReleasesViaReleaseGraph() {
        Object owner = new Object();
        StreamCleaner.Registration c =
            StreamCleaner.register(owner, 46L, StreamCleaner.Kind.GRAPH);

        c.cleanThrowing();

        verify(bridge, times(1)).releaseGraph(46L);
        verify(bridge, never()).releaseExecutor(46L);
        verify(bridge, never()).closeStream(46L);
        verify(bridge, never()).releaseFuture(46L);
        verify(bridge, never()).releaseArray(46L);
    }

    @Test
    void cleanIsIdempotent() {
        Object owner = new Object();
        StreamCleaner.Registration c =
            StreamCleaner.register(owner, 99L, StreamCleaner.Kind.EXECUTOR);

        c.cleanThrowing();
        c.cleanThrowing();
        c.cleanThrowing();

        verify(bridge, times(1)).releaseExecutor(99L);
    }

    @Test
    @Disabled("flaky in CI; java.lang.ref.Cleaner firing is non-deterministic, "
        + "manual verification only")
    void gcDrivenCleanupEventuallyReleasesHandle() throws InterruptedException {
        CountDownLatch released = new CountDownLatch(1);
        doAnswer(inv -> {
            released.countDown();
            return null;
        }).when(bridge).releaseExecutor(100L);

        Object owner = new Object();
        StreamCleaner.register(owner, 100L, StreamCleaner.Kind.EXECUTOR);
        // Drop the strong reference so the cleaner can fire.
        owner = null;

        // Best-effort: prod the GC and wait briefly.
        for (int i = 0; i < 40 && released.getCount() > 0; i++) {
            System.gc();
            Thread.sleep(50);
        }

        assertThat(released.await(0, TimeUnit.MILLISECONDS)).isTrue();
        verify(bridge, times(1)).releaseExecutor(100L);
    }

    // ------------------------------------------------------------------
    // Explicit-close (cleanThrowing) failure path: PROPAGATES GpuException.
    // ------------------------------------------------------------------

    @Test
    void cleanThrowing_propagatesGpuException_whenBridgeThrows() {
        // EXECUTOR path: releaseExecutor throws a plain RuntimeException —
        // cleanThrowing() must wrap and propagate it as a GpuException.
        doThrow(new RuntimeException("boom-executor"))
            .when(bridge).releaseExecutor(201L);

        StreamCleaner.Registration execReg =
            StreamCleaner.register(new Object(), 201L, StreamCleaner.Kind.EXECUTOR);

        assertThatThrownBy(execReg::cleanThrowing)
            .isInstanceOf(GpuException.class)
            .hasMessageContaining("201");
        verify(bridge, times(1)).releaseExecutor(201L);

        // STREAM path: closeStream throws — cleanThrowing() must propagate.
        doThrow(new RuntimeException("boom-stream"))
            .when(bridge).closeStream(202L);

        StreamCleaner.Registration streamReg =
            StreamCleaner.register(new Object(), 202L, StreamCleaner.Kind.STREAM);

        assertThatThrownBy(streamReg::cleanThrowing)
            .isInstanceOf(GpuException.class)
            .hasMessageContaining("202");
        verify(bridge, times(1)).closeStream(202L);
    }

    @Test
    void cleanThrowing_rethrowsGpuExceptionDirectly_whenBridgeThrowsGpuException() {
        // A bridge that already throws GpuException: cleanThrowing() must
        // surface that exact instance rather than double-wrapping it.
        GpuException original = new GpuException("native-array-failure");
        doThrow(original).when(bridge).releaseArray(205L);

        StreamCleaner.Registration reg =
            StreamCleaner.register(new Object(), 205L, StreamCleaner.Kind.ARRAY);

        assertThatThrownBy(reg::cleanThrowing).isSameAs(original);
    }

    // ------------------------------------------------------------------
    // Safety-net (Cleaner-worker) failure path: SWALLOWS-and-logs.
    // ------------------------------------------------------------------

    @Test
    void cleanerSafetyNet_swallowsBridgeException() throws InterruptedException {
        // When the registration's owner becomes unreachable, the Cleaner
        // worker thread runs the cleanup action with propagate == false:
        // a native release failure must be swallowed (logged at WARNING),
        // never escaping into the Cleaner worker.
        CountDownLatch released = new CountDownLatch(1);
        doAnswer(inv -> {
            released.countDown();
            throw new RuntimeException("boom-cleaner-worker");
        }).when(bridge).releaseFuture(206L);

        Object owner = new Object();
        StreamCleaner.register(owner, 206L, StreamCleaner.Kind.FUTURE);
        // Drop the strong reference so the Cleaner can fire on its worker.
        owner = null;

        // Best-effort: prod the GC; the test passes as long as the worker
        // does not crash the JVM / surface the exception to us.
        for (int i = 0; i < 60 && released.getCount() > 0; i++) {
            System.gc();
            Thread.sleep(25);
        }

        assertThat(released.await(2, TimeUnit.SECONDS))
            .as("Cleaner worker should have invoked releaseFuture")
            .isTrue();
        // No exception reached this thread — the safety net swallowed it.
        verify(bridge, times(1)).releaseFuture(206L);
    }

    @Test
    void concurrentCleanThrowing_callsBridgeExactlyOnce() throws InterruptedException {
        StreamCleaner.Registration c =
            StreamCleaner.register(new Object(), 303L, StreamCleaner.Kind.EXECUTOR);

        final int threadCount = 4;
        CountDownLatch startBarrier = new CountDownLatch(1);
        CountDownLatch doneBarrier = new CountDownLatch(threadCount);
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                try {
                    startBarrier.await();
                    c.cleanThrowing();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneBarrier.countDown();
                }
            }, "stream-cleaner-test-" + i);
            threads[i].start();
        }

        // Release all threads simultaneously to maximise contention on the
        // AtomicBoolean once-guard inside the cleanup action.
        startBarrier.countDown();
        assertThat(doneBarrier.await(5, TimeUnit.SECONDS)).isTrue();
        for (Thread t : threads) {
            t.join();
        }

        verify(bridge, times(1)).releaseExecutor(303L);
        verify(bridge, never()).closeStream(303L);
        verify(bridge, never()).releaseFuture(303L);
        verify(bridge, never()).releaseArray(303L);
    }

    @Test
    void cleanThrowing_quietNoOp_whenBridgeReturnsNormally() {
        // A bridge whose release method does nothing (no return, no throw) —
        // cleanThrowing() must still be a quiet no-op that hits the bridge.
        NativeBridge silentBridge = mock(NativeBridge.class);
        doNothing().when(silentBridge).releaseArray(anyLong());
        Native.setBridge(silentBridge);

        StreamCleaner.Registration c =
            StreamCleaner.register(new Object(), 404L, StreamCleaner.Kind.ARRAY);

        assertThatCode(c::cleanThrowing).doesNotThrowAnyException();
        verify(silentBridge, times(1)).releaseArray(404L);
    }
}
