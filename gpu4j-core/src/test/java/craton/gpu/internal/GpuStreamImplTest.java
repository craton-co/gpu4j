// SPDX-License-Identifier: Apache-2.0
// Copyright 2025-2026 Craton Software Company (https://craton.com.ar)

package craton.gpu.internal;

import craton.gpu.GpuException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Lifecycle and use-after-free behaviour for {@link GpuStreamImpl}.
 */
class GpuStreamImplTest {

    private static final long HANDLE = 0xCAFEBABEL;

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
    void constructionWithZeroHandleThrowsIllegalArgument() {
        assertThatThrownBy(() -> new GpuStreamImpl(0L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("invalid stream handle");
    }

    @Test
    void constructionWithNonZeroHandleSucceedsAndExposesHandle() {
        GpuStreamImpl stream = new GpuStreamImpl(HANDLE);

        assertThat(stream.handle()).isEqualTo(HANDLE);

        stream.close();
    }

    @Test
    void doubleCloseInvokesCloseStreamOnce() {
        GpuStreamImpl stream = new GpuStreamImpl(HANDLE);

        stream.close();
        stream.close();
        stream.close();

        verify(bridge, times(1)).closeStream(HANDLE);
    }

    @Test
    void handleAfterCloseThrowsIllegalState() {
        GpuStreamImpl stream = new GpuStreamImpl(HANDLE);
        stream.close();

        assertThatThrownBy(stream::handle)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("stream closed");
    }

    @Test
    void close_propagatesGpuException_whenCloseStreamFails() {
        // Explicit-close path: the native closeStream fails. close() must
        // surface that as a GpuException per its documented contract.
        doThrow(new RuntimeException("native-close-failed"))
            .when(bridge).closeStream(HANDLE);

        GpuStreamImpl stream = new GpuStreamImpl(HANDLE);

        assertThatThrownBy(stream::close)
            .isInstanceOf(GpuException.class)
            .hasMessageContaining(Long.toString(HANDLE));
        verify(bridge, times(1)).closeStream(HANDLE);
    }

    @Test
    void close_rethrowsGpuExceptionDirectly_whenCloseStreamThrowsGpuException() {
        GpuException original = new GpuException("native-close-failure");
        doThrow(original).when(bridge).closeStream(HANDLE);

        GpuStreamImpl stream = new GpuStreamImpl(HANDLE);

        assertThatThrownBy(stream::close).isSameAs(original);
    }

    @Test
    void doubleClose_afterFailure_isIdempotentAndSafe() {
        // First close() fails and propagates; the once-guard has still been
        // consumed, so a subsequent close() must be a quiet no-op (the
        // bridge is not invoked a second time).
        doThrow(new RuntimeException("native-close-failed"))
            .when(bridge).closeStream(HANDLE);

        GpuStreamImpl stream = new GpuStreamImpl(HANDLE);

        assertThatThrownBy(stream::close).isInstanceOf(GpuException.class);
        // Second call: idempotent, no throw, no extra bridge interaction.
        stream.close();
        stream.close();

        verify(bridge, times(1)).closeStream(HANDLE);
    }

    @Test
    void concurrentCloseInvokesCloseStreamOnce() throws InterruptedException {
        GpuStreamImpl stream = new GpuStreamImpl(HANDLE);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> err1 = new AtomicReference<>();
        AtomicReference<Throwable> err2 = new AtomicReference<>();

        Thread t1 = new Thread(() -> {
            try {
                start.await();
                stream.close();
            } catch (Throwable t) {
                err1.set(t);
            }
        }, "stream-closer-1");
        Thread t2 = new Thread(() -> {
            try {
                start.await();
                stream.close();
            } catch (Throwable t) {
                err2.set(t);
            }
        }, "stream-closer-2");

        t1.start();
        t2.start();
        start.countDown();

        t1.join(2000);
        t2.join(2000);

        assertThat(t1.isAlive()).isFalse();
        assertThat(t2.isAlive()).isFalse();
        assertThat(err1.get()).isNull();
        assertThat(err2.get()).isNull();

        verify(bridge, times(1)).closeStream(HANDLE);
    }

    @Test
    void concurrentCloseAndHandleAccess_completesWithoutDeadlock() throws InterruptedException {
        GpuStreamImpl stream = new GpuStreamImpl(HANDLE);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> readerErr = new AtomicReference<>();
        AtomicReference<Throwable> closerErr = new AtomicReference<>();

        Thread reader = new Thread(() -> {
            try {
                start.await();
                // Tight loop racing close() on the other thread. Both
                // outcomes are documented: handle returns successfully OR
                // throws IllegalStateException after close wins.
                for (int i = 0; i < 100_000; i++) {
                    try {
                        stream.handle();
                    } catch (IllegalStateException expected) {
                        // documented contract once close() has won the race
                        return;
                    }
                }
            } catch (Throwable t) {
                readerErr.set(t);
            }
        }, "stream-handle-reader");
        Thread closer = new Thread(() -> {
            try {
                start.await();
                stream.close();
            } catch (Throwable t) {
                closerErr.set(t);
            }
        }, "stream-closer");

        reader.start();
        closer.start();
        start.countDown();

        reader.join(1000);
        closer.join(1000);

        assertThat(reader.isAlive()).isFalse();
        assertThat(closer.isAlive()).isFalse();
        assertThat(closerErr.get()).isNull();
        // Reader may have completed normally or surfaced IllegalStateException
        // via the inner catch; either way no other exception type is allowed.
        assertThat(readerErr.get()).isNull();
    }

    @Test
    void multipleConcurrentCloses_callBridgeExactlyOnce() throws InterruptedException {
        GpuStreamImpl stream = new GpuStreamImpl(HANDLE);
        CountDownLatch start = new CountDownLatch(1);
        Thread[] threads = new Thread[4];
        java.util.List<AtomicReference<Throwable>> errs = new java.util.ArrayList<>(threads.length);

        for (int i = 0; i < threads.length; i++) {
            final AtomicReference<Throwable> err = new AtomicReference<>();
            errs.add(err);
            threads[i] = new Thread(() -> {
                try {
                    start.await();
                    stream.close();
                } catch (Throwable t) {
                    err.set(t);
                }
            }, "stream-closer-" + i);
            threads[i].start();
        }

        start.countDown();

        for (Thread t : threads) {
            t.join(1000);
        }

        for (Thread t : threads) {
            assertThat(t.isAlive()).isFalse();
        }
        for (AtomicReference<Throwable> err : errs) {
            assertThat(err.get()).isNull();
        }

        verify(bridge, times(1)).closeStream(HANDLE);
    }
}
