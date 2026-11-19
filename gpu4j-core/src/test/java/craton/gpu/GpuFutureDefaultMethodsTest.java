// SPDX-License-Identifier: Apache-2.0
// Copyright 2025-2026 Craton Software Company (https://craton.com.ar)

package craton.gpu;

import craton.gpu.testing.GpuBackendSpi;
import craton.gpu.testing.GpuTestBridge;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * Tests for the default methods declared on {@link GpuFuture}.
 */
class GpuFutureDefaultMethodsTest {

    @SuppressWarnings("unchecked")
    private static <T> GpuFuture<T> realDefaultsMock() {
        return (GpuFuture<T>) mock(GpuFuture.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
    }

    @Test
    void defaultGetWithTimeout_throwsWhenStillNotDoneAfterTimeout() throws Exception {
        GpuFuture<String> stub = realDefaultsMock();
        when(stub.isDone()).thenReturn(false);

        long start = System.nanoTime();
        assertThatThrownBy(() -> stub.get(1, TimeUnit.MILLISECONDS))
                .isInstanceOf(TimeoutException.class)
                .hasMessageContaining("not done");
        long elapsedNanos = System.nanoTime() - start;

        assertThat(TimeUnit.NANOSECONDS.toMillis(elapsedNanos)).isLessThan(500L);
    }

    @Test
    void defaultGetWithTimeout_waitsForCompletionWithinDeadline() throws Exception {
        GpuFuture<String> stub = realDefaultsMock();
        AtomicInteger checks = new AtomicInteger();
        when(stub.isDone()).thenAnswer(inv -> checks.incrementAndGet() >= 3);
        when(stub.get()).thenReturn("eventual");

        assertThat(stub.get(100, TimeUnit.MILLISECONDS)).isEqualTo("eventual");
        assertThat(checks.get()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void defaultGetWithTimeout_returnsValueIfDone() throws Exception {
        GpuFuture<String> stub = realDefaultsMock();
        when(stub.isDone()).thenReturn(true);
        when(stub.get()).thenReturn("ready");

        assertThat(stub.get(1, TimeUnit.MILLISECONDS)).isEqualTo("ready");
    }

    @Test
    void defaultGetWithTimeout_propagatesGpuExceptionFromGetWhenDone() throws Exception {
        GpuFuture<String> stub = realDefaultsMock();
        when(stub.isDone()).thenReturn(true);
        when(stub.get()).thenThrow(new GpuException("device failed"));

        assertThatThrownBy(() -> stub.get(1, TimeUnit.MILLISECONDS))
                .isInstanceOf(GpuException.class)
                .hasMessageContaining("device failed");
    }

    @Test
    void defaultGetWithTimeout_propagatesInterruptedExceptionFromGetWhenDone() throws Exception {
        GpuFuture<String> stub = realDefaultsMock();
        when(stub.isDone()).thenReturn(true);
        when(stub.get()).thenThrow(new InterruptedException("interrupted-while-done"));

        assertThatThrownBy(() -> stub.get(5, TimeUnit.MILLISECONDS))
                .isInstanceOf(InterruptedException.class);
    }

    @Test
    void defaultCancel_alwaysReturnsFalse() {
        GpuFuture<Integer> stub = realDefaultsMock();

        assertThat(stub.cancel(true)).isFalse();
        assertThat(stub.cancel(false)).isFalse();
    }

    @Test
    void defaultIsCancelled_returnsFalse() {
        GpuFuture<Integer> stub = realDefaultsMock();

        assertThat(stub.isCancelled()).isFalse();
    }

    @Test
    void defaultGetAsGpuArray_wrapsPrimitiveHostArraysAndRejectsUnsupported() throws Exception {
        GpuBackendSpi backend = mock(GpuBackendSpi.class);
        when(backend.arrayWrapInt(any())).thenReturn(11L);
        when(backend.arrayWrapLong(any())).thenReturn(12L);
        when(backend.arrayWrapShort(any())).thenReturn(13L);
        when(backend.arrayWrapFloat(any())).thenReturn(14L);
        when(backend.arrayWrapDouble(any())).thenReturn(15L);

        try (GpuTestBridge.Installed ignored = GpuTestBridge.install(backend)) {
            GpuFuture<Object> stub = realDefaultsMock();

            when(stub.get()).thenReturn(new int[] {1, 2});
            try (GpuArray<int[]> a = stub.getAsGpuArray()) {
                assertThat(a.elementType()).isEqualTo(int.class);
            }

            when(stub.get()).thenReturn(new long[] {1L});
            try (GpuArray<long[]> a = stub.getAsGpuArray()) {
                assertThat(a.elementType()).isEqualTo(long.class);
            }

            when(stub.get()).thenReturn(new short[] {1});
            try (GpuArray<short[]> a = stub.getAsGpuArray()) {
                assertThat(a.elementType()).isEqualTo(short.class);
            }

            when(stub.get()).thenReturn(new float[] {1f});
            try (GpuArray<float[]> a = stub.getAsGpuArray()) {
                assertThat(a.elementType()).isEqualTo(float.class);
            }

            when(stub.get()).thenReturn(new double[] {1d});
            try (GpuArray<double[]> a = stub.getAsGpuArray()) {
                assertThat(a.elementType()).isEqualTo(double.class);
            }

            when(stub.get()).thenReturn("not-an-array");
            assertThatThrownBy(stub::getAsGpuArray)
                    .isInstanceOf(GpuException.class)
                    .hasMessageContaining("cannot be wrapped");
        }
    }

    @Test
    void defaultNamedThenApplyGpu_throwsUnsupportedOperationException() {
        GpuFuture<String> stub = realDefaultsMock();
        assertThatThrownBy(() -> stub.thenApplyGpu("c/K", "m", "()V"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void defaultToCompletableFutureExecutor_completesWithValue() throws Exception {
        GpuFuture<String> stub = realDefaultsMock();
        when(stub.get()).thenReturn("answer");

        CompletableFuture<String> cf = stub.toCompletableFuture(ForkJoinPool.commonPool());

        assertThat(cf.get(2, TimeUnit.SECONDS)).isEqualTo("answer");
    }

    @Test
    void defaultToCompletableFutureExecutor_propagatesFailureAsExecutionException() throws Exception {
        GpuFuture<String> stub = realDefaultsMock();
        GpuException cause = new GpuException("device boom");
        when(stub.get()).thenThrow(cause);

        CompletableFuture<String> cf = stub.toCompletableFuture(ForkJoinPool.commonPool());

        assertThatThrownBy(() -> cf.get(2, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(GpuException.class)
                .hasRootCauseMessage("device boom");
    }

    @Test
    void defaultToCompletableFutureExecutor_wrapsInterruptedExceptionInGpuException() throws Exception {
        GpuFuture<String> stub = realDefaultsMock();
        when(stub.get()).thenThrow(new InterruptedException("boom-interrupt"));

        ExecutorService exec = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<String> cf = stub.toCompletableFuture(exec);

            assertThatThrownBy(() -> cf.get(2, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(GpuException.class)
                    .hasRootCauseInstanceOf(InterruptedException.class);

            exec.submit(Thread::interrupted).get(2, TimeUnit.SECONDS);
        } finally {
            exec.shutdownNow();
        }
    }

    @Test
    void defaultToCompletableFutureExecutor_runsOnSuppliedExecutor() throws Exception {
        GpuFuture<Thread> stub = realDefaultsMock();
        when(stub.get()).thenAnswer(inv -> Thread.currentThread());

        AtomicReference<Thread> seen = new AtomicReference<>();
        Executor exec = task -> {
            Thread t = new Thread(task, "gpu-future-default-test-worker");
            seen.set(t);
            t.start();
        };

        CompletableFuture<Thread> cf = stub.toCompletableFuture(exec);
        Thread runner = cf.get(2, TimeUnit.SECONDS);

        assertThat(runner).isSameAs(seen.get());
        assertThat(runner.getName()).isEqualTo("gpu-future-default-test-worker");
    }
}
