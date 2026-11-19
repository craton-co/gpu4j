// SPDX-License-Identifier: Apache-2.0
// Copyright 2025-2026 Craton Software Company (https://craton.com.ar)

package craton.gpu.internal;

import craton.gpu.testing.GpuBackendSpi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Sanity-checks the {@link Native#setBridge(NativeBridge)} test seam:
 * installing a mock makes {@link Native#bridge()} return that mock, and
 * passing {@code null} restores the production {@link NativeImpl}.
 */
class NativeSwapTest {

    @AfterEach
    void restoreDefaultBridge() {
        Native.setBridge(null);
    }

    @Test
    void setBridgeInstallsMockAndBridgeReturnsIt() {
        NativeBridge mockBridge = mock(NativeBridge.class);
        Native.setBridge(mockBridge);

        assertThat(Native.bridge()).isSameAs(mockBridge);
    }

    @Test
    void setBridgeNullRestoresNativeImpl() {
        NativeBridge mockBridge = mock(NativeBridge.class);
        Native.setBridge(mockBridge);
        assertThat(Native.bridge()).isSameAs(mockBridge);

        Native.setBridge(null);

        assertThat(Native.bridge()).isInstanceOf(NativeImpl.class);
    }

    @Test
    void defaultBridgeIsNativeImpl() {
        // No swap performed - the static initializer should give us the
        // production implementation.
        assertThat(Native.bridge()).isInstanceOf(NativeImpl.class);
    }

    @Test
    void setBridgeNull_restoresFreshNativeImpl() {
        // After a setBridge(null), capture bridge(); call setBridge(null)
        // again, capture again. Lock the class — not identity — because
        // setBridge(null) may legitimately allocate a fresh NativeImpl.
        Native.setBridge(null);
        GpuBackendSpi ref1 = Native.bridge();

        Native.setBridge(null);
        GpuBackendSpi ref2 = Native.bridge();

        assertThat(ref1).isInstanceOf(NativeImpl.class);
        assertThat(ref2).isInstanceOf(NativeImpl.class);
    }

    @Test
    void concurrentBridgeReads_neverNull() throws InterruptedException {
        // Exercise the volatile publication invariant: while one thread
        // alternates setBridge(mock)/setBridge(null), other threads should
        // never see a null bridge reference.
        final int readerCount = 3;          // threads 1..3 read
        final int readsPerThread = 1000;
        final int swapIterations = 100;
        final int totalThreads = readerCount + 1; // +1 swapper (thread 0)

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(totalThreads);
        AtomicBoolean observedNull = new AtomicBoolean(false);
        AtomicInteger readCount = new AtomicInteger(0);
        NativeBridge mockBridge = mock(NativeBridge.class);

        Thread swapper = new Thread(() -> {
            try {
                startGate.await();
                for (int i = 0; i < swapIterations; i++) {
                    Native.setBridge(mockBridge);
                    Native.setBridge(null);
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } finally {
                doneGate.countDown();
            }
        }, "native-swap-test-swapper");
        swapper.start();

        Thread[] readers = new Thread[readerCount];
        for (int t = 0; t < readerCount; t++) {
            readers[t] = new Thread(() -> {
                try {
                    startGate.await();
                    for (int i = 0; i < readsPerThread; i++) {
                        GpuBackendSpi b = Native.bridge();
                        if (b == null) {
                            observedNull.set(true);
                        }
                        readCount.incrementAndGet();
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneGate.countDown();
                }
            }, "native-swap-test-reader-" + t);
            readers[t].start();
        }

        startGate.countDown();
        boolean finished = doneGate.await(30, TimeUnit.SECONDS);

        assertThat(finished).as("all threads must finish within timeout").isTrue();
        assertThat(observedNull.get())
            .as("no reader thread should ever observe a null bridge")
            .isFalse();
        assertThat(readCount.get()).isEqualTo(readerCount * readsPerThread);
    }

    @Test
    void setBridgeMock_thenInvokeForwardsCorrectly() {
        // Lock the test seam: installing a mock and invoking a method on
        // Native.bridge() must reach the mock (not the underlying native).
        NativeBridge mockBridge = mock(NativeBridge.class);
        Native.setBridge(mockBridge);

        Native.bridge().openExecutor(0);

        verify(mockBridge).openExecutor(0);
    }
}
