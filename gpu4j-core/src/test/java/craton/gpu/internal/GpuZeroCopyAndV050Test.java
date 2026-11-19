// SPDX-License-Identifier: Apache-2.0
// Copyright 2025-2026 Craton Software Company (https://craton.com.ar)

package craton.gpu.internal;

import craton.gpu.GpuArray;
import craton.gpu.GpuCompileException;
import craton.gpu.GpuException;
import craton.gpu.GpuFuture;
import craton.gpu.GpuGraph;
import craton.gpu.GpuLaunchException;
import craton.gpu.GpuOutOfMemoryException;
import craton.gpu.testing.GpuBackendSpi;
import craton.gpu.testing.GpuTestBridge;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for zero-PCIe {@link GpuFutureImpl#thenApplyGpu} chaining,
 * {@link GpuFutureImpl#getAsGpuArray()} single-claim promotion, {@link GpuStreamImpl#dispatch}
 * and {@link GpuStreamImpl#synchronize()}, {@link GpuGraph} capture/replay on
 * {@link GpuExecutorImpl}, and {@link GpuErrors} classification.
 */
class GpuZeroCopyAndV050Test {

    private NativeBridge bridge;
    private GpuTestBridge.Installed installed;

    @BeforeEach
    void setUp() {
        Thread.interrupted();
        bridge = mock(NativeBridge.class);
        installed = GpuTestBridge.install(bridge);
    }

    @AfterEach
    void tearDown() {
        Thread.interrupted();
        if (installed != null) {
            installed.close();
        }
        GpuTestBridge.restoreDefault();
    }

    // ---- Zero-PCIe GpuFuture.thenApplyGpu & device affinity ----------------

    @Test
    void thenApplyGpu_lambda_chainsNonBlockingWithExecutorHandleAffinity() throws Exception {
        long execHandle = 900L;
        long upstreamHandle = 1001L;
        long downstreamHandle = 1002L;

        when(bridge.futureStatus(upstreamHandle)).thenReturn(0);
        when(bridge.futureStatus(downstreamHandle)).thenReturn(1);
        when(bridge.futureGetResult(downstreamHandle)).thenReturn(new float[] {42.0f});

        GpuFutureImpl<float[]> upstream = new GpuFutureImpl<>(execHandle, upstreamHandle);
        GpuFutureImpl<float[]> downstream = new GpuFutureImpl<>(downstreamHandle);

        when(bridge.<float[]>submitWithArg(eq(execHandle), any(), eq(upstream)))
                .thenReturn(downstream);

        GpuFuture<float[]> chained = upstream.thenApplyGpu(arr -> new float[] {arr[0] * 2});
        assertThat(chained).isSameAs(downstream);
        assertThat(downstream.execHandle()).isEqualTo(execHandle);

        verify(bridge, never()).futureSynchronize(upstreamHandle);
        verify(bridge, never()).futureGetResult(upstreamHandle);

        assertThat(chained.get()).containsExactly(42.0f);
    }

    @Test
    void thenApplyGpu_lambda_fallsBackOnCompileRejectionButPropagatesDeviceOom() throws Exception {
        long upstreamHandle = 1101L;
        long rejectedHandle = 1102L;

        when(bridge.futureStatus(rejectedHandle)).thenReturn(2);
        when(bridge.futureErrorKind(rejectedHandle)).thenReturn(GpuBackendSpi.ERROR_KIND_COMPILE);
        GpuFutureImpl<Integer> rejectedProbe = new GpuFutureImpl<>(rejectedHandle);
        GpuFutureImpl<Integer> upstream = new GpuFutureImpl<>(upstreamHandle);

        when(bridge.<Integer>submitWithArg(eq(0L), any(), eq(upstream))).thenReturn(rejectedProbe);
        when(bridge.futureStatus(upstreamHandle)).thenReturn(1);
        when(bridge.futureGetResult(upstreamHandle)).thenReturn(10);

        GpuFuture<Integer> mapped = upstream.thenApplyGpu(v -> v + 5);
        assertThat(mapped.get()).isEqualTo(15);
        verify(bridge, times(1)).releaseFuture(rejectedHandle);
        verify(bridge, times(1)).futureSynchronize(upstreamHandle);

        // Device OOM (ERROR_KIND_OUT_OF_MEMORY) must NOT silently re-run on CPU!
        long oomHandle = 1103L;
        when(bridge.futureStatus(oomHandle)).thenReturn(2);
        when(bridge.futureErrorKind(oomHandle)).thenReturn(GpuBackendSpi.ERROR_KIND_OUT_OF_MEMORY);
        when(bridge.futureGetError(oomHandle)).thenReturn("device oom");
        GpuFutureImpl<Integer> oomFuture = new GpuFutureImpl<>(oomHandle);
        when(bridge.<Integer>submitWithArg(eq(0L), any(), eq(upstream))).thenReturn(oomFuture);

        GpuFuture<Integer> shouldFail = upstream.thenApplyGpu(v -> v + 99);
        assertThatThrownBy(shouldFail::get).isInstanceOf(GpuOutOfMemoryException.class);
    }

    @Test
    void thenApplyGpu_named_invokesFutureThenApplyGpu() throws Exception {
        long upstreamHandle = 1201L;
        long nextHandle = 1202L;

        when(bridge.futureThenApplyGpu(
                eq(upstreamHandle), eq("pkg/K"), eq("stage2"), eq("([F[F)[F"), any(Object[].class)))
                .thenReturn(nextHandle);
        when(bridge.futureStatus(nextHandle)).thenReturn(1);
        when(bridge.futureGetResult(nextHandle)).thenReturn(99L);

        GpuFutureImpl<float[]> upstream = new GpuFutureImpl<>(777L, upstreamHandle);
        GpuFuture<Long> chained = upstream.thenApplyGpu("pkg/K", "stage2", "([F[F)[F", 1.5f);

        verify(bridge, never()).futureSynchronize(upstreamHandle);
        assertThat(chained.get()).isEqualTo(99L);
    }

    // ---- Timed get() synchronization & AWAIT_* handling --------------------

    @Test
    void timedGet_callsFutureSynchronizeAndHandlesNativeAwaitResults() throws Exception {
        long h1 = 1251L;
        when(bridge.futureStatus(h1)).thenReturn(0, 1);
        when(bridge.futureAwait(eq(h1), any(Long.class))).thenReturn(GpuBackendSpi.AWAIT_COMPLETED);
        when(bridge.futureGetResult(h1)).thenReturn("awaited");

        GpuFutureImpl<String> f1 = new GpuFutureImpl<>(h1);
        assertThat(f1.get(50, TimeUnit.MILLISECONDS)).isEqualTo("awaited");
        verify(bridge, times(1)).futureSynchronize(h1);

        long h2 = 1252L;
        when(bridge.futureStatus(h2)).thenReturn(0);
        when(bridge.futureAwait(eq(h2), any(Long.class))).thenReturn(GpuBackendSpi.AWAIT_TIMED_OUT);
        GpuFutureImpl<String> f2 = new GpuFutureImpl<>(h2);
        assertThatThrownBy(() -> f2.get(50, TimeUnit.MILLISECONDS))
                .isInstanceOf(TimeoutException.class);
    }

    // ---- Zero-copy getAsGpuArray & single-claim invariants -----------------

    @Test
    void getAsGpuArray_promotesOnceAndRejectsSecondClaimAndSubsequentGet() throws Exception {
        long futureHandle = 1301L;
        long promotedArrayHandle = 1302L;

        when(bridge.futureStatus(futureHandle)).thenReturn(1);
        GpuArray<float[]> resident =
                GpuArray.fromNativeHandle(promotedArrayHandle, GpuArray.ELEM_FLOAT, 16);
        when(bridge.futureGetResultAsGpuArray(futureHandle)).thenAnswer(inv -> resident);

        GpuFutureImpl<float[]> fut = new GpuFutureImpl<>(futureHandle);
        GpuArray<float[]> claimed = fut.getAsGpuArray();
        assertThat(claimed).isSameAs(resident);
        verify(bridge, never()).futureGetResult(futureHandle);

        assertThatThrownBy(fut::getAsGpuArray)
                .isInstanceOf(GpuException.class)
                .hasMessageContaining("already claimed");
        assertThatThrownBy(fut::get)
                .isInstanceOf(GpuException.class)
                .hasMessageContaining("already claimed");

        claimed.close();
    }

    @Test
    void getAsGpuArray_afterCachedGet_stillEnforcesSingleClaim() throws Exception {
        long futureHandle = 1350L;
        when(bridge.futureStatus(futureHandle)).thenReturn(1);
        when(bridge.futureGetResult(futureHandle)).thenReturn(new int[] {1, 2, 3});
        when(bridge.arrayWrapInt(any(int[].class))).thenReturn(1351L);

        GpuFutureImpl<int[]> fut = new GpuFutureImpl<>(futureHandle);
        assertThat(fut.get()).containsExactly(1, 2, 3);

        try (GpuArray<int[]> arr = fut.getAsGpuArray()) {
            assertThat(arr.length()).isEqualTo(3);
        }
        assertThatThrownBy(fut::getAsGpuArray)
                .isInstanceOf(GpuException.class)
                .hasMessageContaining("already claimed");
    }

    @Test
    void getAsGpuArray_fallsBackToHostWrapWhenUnsupported() throws Exception {
        long futureHandle = 1401L;
        when(bridge.futureStatus(futureHandle)).thenReturn(1);
        when(bridge.futureGetResultAsGpuArray(futureHandle))
                .thenThrow(new UnsupportedOperationException());
        when(bridge.futureGetResult(futureHandle)).thenReturn(new int[] {7, 8, 9});
        when(bridge.arrayWrapInt(any(int[].class))).thenReturn(1402L);

        GpuFutureImpl<int[]> fut = new GpuFutureImpl<>(futureHandle);
        try (GpuArray<int[]> arr = fut.getAsGpuArray()) {
            assertThat(arr.length()).isEqualTo(3);
            assertThat(arr.elementType()).isEqualTo(int.class);
        }
    }

    // ---- GpuStream dispatch, synchronize, interrupt, and concurrent close ---

    @Test
    void stream_dispatchSynchronizeInterruptAndConcurrentClose() throws Exception {
        long streamHandle = 1501L;
        when(bridge.streamSubmitMethod(eq(streamHandle), eq("c/K"), eq("m"), eq("([F)V"), any()))
                .thenReturn(2001L, 2002L, 2003L, 2004L);
        when(bridge.futureStatus(2001L)).thenReturn(1);
        when(bridge.futureStatus(2002L)).thenReturn(1);
        when(bridge.futureStatus(2003L)).thenReturn(1);
        when(bridge.futureStatus(2004L)).thenReturn(2);
        when(bridge.futureGetError(2004L)).thenReturn("stream-kernel-boom");

        try (GpuStreamImpl stream = new GpuStreamImpl(streamHandle)) {
            stream.dispatch("c/K", "m", "([F)V", new float[4]);
            stream.dispatch("c/K", "m", "([F)V", new float[4]);

            stream.synchronize();
            stream.synchronize();

            verify(bridge).futureSynchronize(2001L);
            verify(bridge).futureSynchronize(2002L);
            verify(bridge).releaseFuture(2001L);
            verify(bridge).releaseFuture(2002L);
            verify(bridge, times(2)).streamSynchronize(streamHandle);

            // Interrupt after 2003L must NOT discard 2004L from pending!
            doAnswer(inv -> {
                Thread.currentThread().interrupt();
                return null;
            }).when(bridge).futureSynchronize(2003L);

            stream.dispatch("c/K", "m", "([F)V", new float[4]); // 2003L
            stream.dispatch("c/K", "m", "([F)V", new float[4]); // 2004L

            assertThatThrownBy(stream::synchronize).isInstanceOf(InterruptedException.class);
            verify(bridge, never()).releaseFuture(2004L);

            // Subsequent synchronize reaches 2004L and surfaces its primary futureGetError!
            assertThatThrownBy(stream::synchronize)
                    .isInstanceOf(GpuException.class)
                    .hasMessageContaining("stream-kernel-boom");
            verify(bridge).releaseFuture(2004L);
        }

        // Concurrent dispatch vs close under ReentrantReadWriteLock
        long raceStreamHandle = 1502L;
        when(bridge.streamSubmitMethod(eq(raceStreamHandle), any(), any(), any(), any()))
                .thenReturn(3001L);
        GpuStreamImpl raceStream = new GpuStreamImpl(raceStreamHandle);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> unexpected = new AtomicReference<>();
        Thread dispatcher = new Thread(() -> {
            try {
                start.await();
                for (int i = 0; i < 200; i++) {
                    try {
                        raceStream.dispatch("c/K", "m", "([F)V", new float[2]);
                    } catch (IllegalStateException expected) {
                        return;
                    }
                }
            } catch (Throwable t) {
                unexpected.set(t);
            }
        });
        Thread closer = new Thread(() -> {
            try {
                start.await();
                raceStream.close();
            } catch (Throwable t) {
                unexpected.set(t);
            }
        });
        dispatcher.start();
        closer.start();
        start.countDown();
        dispatcher.join(2000);
        closer.join(2000);
        assertThat(unexpected.get()).isNull();
        verify(bridge, times(1)).closeStream(raceStreamHandle);
    }

    // ---- GpuExecutor & AutoCloseable GpuGraph capture/replay ---------------

    @Test
    void executor_graphCaptureReplayAndAutoCloseLifecycle() throws Exception {
        long execHandle = 1601L;
        long graphHandle = 1602L;
        when(bridge.graphBeginCapture(execHandle)).thenReturn(true);
        when(bridge.submitMethodHandle(eq(execHandle), eq("c/K"), eq("m"), eq("([F)V"), any()))
                .thenReturn(1603L);
        when(bridge.graphEndCapture(execHandle)).thenReturn(graphHandle);
        when(bridge.graphNodeCount(graphHandle)).thenReturn(3);
        when(bridge.graphReplay(execHandle, graphHandle)).thenReturn(1604L, 1606L);
        when(bridge.graphBeginReplay(execHandle, graphHandle)).thenReturn(true);
        when(bridge.graphEndReplay(execHandle)).thenReturn(1605L);
        when(bridge.futureStatus(1604L)).thenReturn(1);
        when(bridge.futureStatus(1606L)).thenReturn(1);

        try (GpuExecutorImpl exec = new GpuExecutorImpl(execHandle)) {
            exec.beginCapture();
            assertThat(exec.dispatchNamedHandle("c/K", "m", "([F)V", new Object[] {new float[4]}))
                    .isEqualTo(1603L);

            GpuGraph capturedRef;
            try (GpuGraph graph = exec.endCapture()) {
                capturedRef = graph;
                assertThat(graph.handle()).isEqualTo(graphHandle);
                assertThat(graph.executor()).isSameAs(exec);
                assertThat(graph.nodeCount()).isEqualTo(3);
                assertThat(graph.isClosed()).isFalse();

                long sub = graph.replay();
                exec.awaitSubmission(sub);
                exec.releaseSubmission(sub);
                verify(bridge).releaseFuture(sub);

                GpuFuture<Void> asyncReplay = graph.replayAsync();
                asyncReplay.get();

                graph.beginReplay();
                assertThat(graph.endReplay()).isEqualTo(1605L);
            }

            assertThat(capturedRef.isClosed()).isTrue();
            capturedRef.close(); // idempotent
            verify(bridge, times(1)).releaseGraph(graphHandle);
            assertThatThrownBy(capturedRef::handle).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(capturedRef::replay).isInstanceOf(IllegalStateException.class);
        }
    }

    // ---- GpuErrors structured & fallback classification --------------------

    @Test
    void gpuErrors_classifiesByErrorKindAndSpecificFallbackMarkersWithoutClassFalsePositives() {
        when(bridge.futureErrorKind(1L)).thenReturn(GpuBackendSpi.ERROR_KIND_COMPILE);
        when(bridge.futureErrorKind(2L)).thenReturn(GpuBackendSpi.ERROR_KIND_OUT_OF_MEMORY);
        when(bridge.futureErrorKind(3L)).thenReturn(GpuBackendSpi.ERROR_KIND_LAUNCH);
        when(bridge.futureErrorKind(4L)).thenReturn(GpuBackendSpi.ERROR_KIND_UNKNOWN);

        assertThat(GpuErrors.fromHandle(bridge, 1L, "oops"))
                .isInstanceOf(GpuCompileException.class);
        assertThat(GpuErrors.fromHandle(bridge, 2L, "oops"))
                .isInstanceOf(GpuOutOfMemoryException.class);
        assertThat(GpuErrors.fromHandle(bridge, 3L, "oops"))
                .isInstanceOf(GpuLaunchException.class);
        assertThat(GpuErrors.fromHandle(bridge, 4L, "CUDA_ERROR_OUT_OF_MEMORY"))
                .isInstanceOf(GpuOutOfMemoryException.class);
        assertThat(GpuErrors.fromHandle(bridge, 4L, "ptx lowering failed"))
                .isInstanceOf(GpuCompileException.class);
        assertThat(GpuErrors.fromHandle(bridge, 4L, "invalid configuration"))
                .isInstanceOf(GpuLaunchException.class);
        // Class name containing "Jit" or "Launcher" must NOT misclassify a generic failure!
        assertThat(GpuErrors.fromHandle(bridge, 4L, "com/acme/JitRunner: arithmetic failure"))
                .isExactlyInstanceOf(GpuException.class);
    }
}
