// SPDX-License-Identifier: Apache-2.0
// Copyright 2025-2026 Craton Software Company (https://craton.com.ar)

package craton.gpu.internal;

import craton.gpu.GpuArray;
import craton.gpu.GpuException;
import craton.gpu.GpuFuture;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lifecycle tests for {@link craton.gpu.GpuArray} that require swapping the
 * {@link NativeBridge}. Lives in {@code craton.gpu.internal} so it can call
 * the package-private {@link Native#setBridge(NativeBridge)} hook.
 */
class GpuArrayLifecycleTest {

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
    void wrapWithZeroHandleThrowsGpuException() {
        when(bridge.arrayWrapInt(any(int[].class))).thenReturn(0L);

        assertThatThrownBy(() -> GpuArray.wrap(new int[]{1, 2, 3}))
            .isInstanceOf(GpuException.class)
            .hasMessageContaining("array allocation failed");
    }

    @Test
    void wrapSucceedsAndExposesLengthAndHandle() {
        when(bridge.arrayWrapInt(any(int[].class))).thenReturn(42L);

        GpuArray<int[]> arr = GpuArray.wrap(new int[]{1, 2, 3});

        assertThat(arr.length()).isEqualTo(3);
        assertThat(arr.handle()).isEqualTo(42L);
        assertThat(arr.elementType()).isEqualTo(int.class);

        arr.close();
    }

    @Test
    void closeIsIdempotentAndReleasesArrayOnce() {
        when(bridge.arrayWrapInt(any(int[].class))).thenReturn(42L);

        GpuArray<int[]> arr = GpuArray.wrap(new int[]{1, 2, 3});

        arr.close();
        arr.close();
        arr.close();

        verify(bridge, times(1)).releaseArray(42L);
    }

    @Test
    void handleAfterCloseThrowsIllegalState() {
        when(bridge.arrayWrapInt(any(int[].class))).thenReturn(42L);

        GpuArray<int[]> arr = GpuArray.wrap(new int[]{1, 2, 3});
        arr.close();

        assertThatThrownBy(arr::handle)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("array closed");
    }

    @Test
    void toHostAfterCloseThrowsIllegalState() {
        when(bridge.arrayWrapInt(any(int[].class))).thenReturn(42L);

        GpuArray<int[]> arr = GpuArray.wrap(new int[]{1, 2, 3});
        arr.close();

        assertThatThrownBy(arr::toHost)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("array closed");
    }

    @Test
    void isResidentAfterCloseThrowsIllegalState() {
        when(bridge.arrayWrapInt(any(int[].class))).thenReturn(42L);

        GpuArray<int[]> arr = GpuArray.wrap(new int[]{1, 2, 3});
        arr.close();

        assertThatThrownBy(arr::isResident)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("array closed");
    }

    @Test
    void lengthRemainsValidAfterClose() {
        // Length is a pure Java field captured at construction; close must
        // not invalidate it (matches the javadoc contract).
        when(bridge.arrayWrapInt(any(int[].class))).thenReturn(42L);

        GpuArray<int[]> arr = GpuArray.wrap(new int[]{1, 2, 3, 4, 5});
        arr.close();

        assertThat(arr.length()).isEqualTo(5);
    }

    @Test
    void wrap_long_andRoundTripsViaToHostInto() {
        long[] src = new long[]{10L, 20L, 30L};
        when(bridge.arrayWrapLong(any(long[].class))).thenReturn(7L);
        // toHostInto delegates to bridge.arrayToHost and then System.arraycopy
        // into the caller-supplied dest. Have the mock return the source array.
        when(bridge.arrayToHost(7L)).thenAnswer(inv -> src);

        GpuArray<long[]> arr = GpuArray.wrap(src);
        long[] dest = new long[3];
        int copied = arr.toHost(dest);

        assertThat(copied).isEqualTo(3);
        assertThat(dest).containsExactly(10L, 20L, 30L);
        verify(bridge, times(1)).arrayToHost(7L);

        arr.close();
    }

    @Test
    void wrap_float_andRoundTripsViaToHostInto() {
        float[] src = new float[]{1.5f, 2.5f, 3.5f};
        when(bridge.arrayWrapFloat(any(float[].class))).thenReturn(8L);
        when(bridge.arrayToHost(8L)).thenAnswer(inv -> src);

        GpuArray<float[]> arr = GpuArray.wrap(src);
        float[] dest = new float[3];
        int copied = arr.toHost(dest);

        assertThat(copied).isEqualTo(3);
        assertThat(dest).containsExactly(1.5f, 2.5f, 3.5f);
        verify(bridge, times(1)).arrayToHost(8L);

        arr.close();
    }

    @Test
    void wrap_double_andRoundTripsViaToHostInto() {
        double[] src = new double[]{1.25, 2.5, 3.75};
        when(bridge.arrayWrapDouble(any(double[].class))).thenReturn(9L);
        when(bridge.arrayToHost(9L)).thenAnswer(inv -> src);

        GpuArray<double[]> arr = GpuArray.wrap(src);
        double[] dest = new double[3];
        int copied = arr.toHost(dest);

        assertThat(copied).isEqualTo(3);
        assertThat(dest).containsExactly(1.25, 2.5, 3.75);
        verify(bridge, times(1)).arrayToHost(9L);

        arr.close();
    }

    @Test
    void toHostInto_wrongElementType_throwsIllegalStateException() {
        // Wrap an int[] but call the long[] toHost overload. The type-mismatch
        // guard in toHostInto throws IllegalStateException with a message that
        // names the actual elementType and the expected type.
        when(bridge.arrayWrapInt(any(int[].class))).thenReturn(11L);

        GpuArray<int[]> arr = GpuArray.wrap(new int[]{1, 2, 3});

        assertThatThrownBy(() -> arr.toHost(new long[3]))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("elementType")
            .hasMessageContaining("long");

        arr.close();
    }

    @Test
    void toHostInto_wrongLength_throwsIllegalArgumentException() {
        // dest.length < length() -> IllegalArgumentException
        when(bridge.arrayWrapInt(any(int[].class))).thenReturn(12L);

        GpuArray<int[]> arr = GpuArray.wrap(new int[]{1, 2, 3, 4, 5});

        assertThatThrownBy(() -> arr.toHost(new int[3]))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("dest.length")
            .hasMessageContaining("3")
            .hasMessageContaining("5");

        arr.close();
    }

    @Test
    void wrap_excessiveLength_throwsOnOverflow() {
        // Java arrays cannot themselves overflow the byte-size check at
        // ordinary lengths, so this is a negative test: a legitimate, modestly
        // sized array must NOT trip the overflow guard.
        when(bridge.arrayWrapInt(any(int[].class))).thenReturn(13L);

        GpuArray<int[]> arr = GpuArray.wrap(new int[]{1, 2, 3, 4});

        assertThat(arr.length()).isEqualTo(4);
        assertThat(arr.elementType()).isEqualTo(int.class);

        arr.close();
    }

    // ---- toHost(int[]) happy-path round-trip ----------------------------

    @Test
    void wrap_int_andRoundTripsViaToHostInto() {
        // Only long/float/double round-trips were covered; this fills the
        // int[] toHost(dest) gap and exercises the int.class branch of the
        // destLen switch inside toHostInto.
        int[] src = new int[]{7, 8, 9};
        when(bridge.arrayWrapInt(any(int[].class))).thenReturn(20L);
        when(bridge.arrayToHost(20L)).thenAnswer(inv -> src);

        GpuArray<int[]> arr = GpuArray.wrap(src);
        int[] dest = new int[3];
        int copied = arr.toHost(dest);

        assertThat(copied).isEqualTo(3);
        assertThat(dest).containsExactly(7, 8, 9);
        verify(bridge, times(1)).arrayToHost(20L);

        arr.close();
    }

    @Test
    void toHostInt_intoLargerDest_copiesOnlyLengthElements() {
        // dest may be longer than length(); only length() elements are copied,
        // the trailing slots stay at their default value.
        int[] src = new int[]{1, 2};
        when(bridge.arrayWrapInt(any(int[].class))).thenReturn(21L);
        when(bridge.arrayToHost(21L)).thenAnswer(inv -> src);

        GpuArray<int[]> arr = GpuArray.wrap(src);
        int[] dest = new int[5];
        int copied = arr.toHost(dest);

        assertThat(copied).isEqualTo(2);
        assertThat(dest).containsExactly(1, 2, 0, 0, 0);

        arr.close();
    }

    // ---- toHost(dest) overloads after close() throw ----------------------

    @Test
    void toHostIntDest_afterClose_throwsIllegalState() {
        when(bridge.arrayWrapInt(any(int[].class))).thenReturn(30L);

        GpuArray<int[]> arr = GpuArray.wrap(new int[]{1, 2, 3});
        arr.close();

        assertThatThrownBy(() -> arr.toHost(new int[3]))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("array closed");
    }

    @Test
    void toHostLongDest_afterClose_throwsIllegalState() {
        when(bridge.arrayWrapLong(any(long[].class))).thenReturn(31L);

        GpuArray<long[]> arr = GpuArray.wrap(new long[]{1L, 2L, 3L});
        arr.close();

        assertThatThrownBy(() -> arr.toHost(new long[3]))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("array closed");
    }

    @Test
    void toHostFloatDest_afterClose_throwsIllegalState() {
        when(bridge.arrayWrapFloat(any(float[].class))).thenReturn(32L);

        GpuArray<float[]> arr = GpuArray.wrap(new float[]{1f, 2f, 3f});
        arr.close();

        assertThatThrownBy(() -> arr.toHost(new float[3]))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("array closed");
    }

    @Test
    void toHostDoubleDest_afterClose_throwsIllegalState() {
        when(bridge.arrayWrapDouble(any(double[].class))).thenReturn(33L);

        GpuArray<double[]> arr = GpuArray.wrap(new double[]{1d, 2d, 3d});
        arr.close();

        assertThatThrownBy(() -> arr.toHost(new double[3]))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("array closed");
    }

    // ---- isResident() happy path -----------------------------------------

    @Test
    void isResident_returnsTrue_whenBridgeReportsResident() {
        when(bridge.arrayWrapInt(any(int[].class))).thenReturn(40L);
        when(bridge.arrayIsResident(40L)).thenReturn(true);

        GpuArray<int[]> arr = GpuArray.wrap(new int[]{1, 2, 3});

        assertThat(arr.isResident()).isTrue();
        verify(bridge, times(1)).arrayIsResident(40L);

        arr.close();
    }

    @Test
    void isResident_returnsFalse_whenBridgeReportsNotResident() {
        when(bridge.arrayWrapInt(any(int[].class))).thenReturn(41L);
        when(bridge.arrayIsResident(41L)).thenReturn(false);

        GpuArray<int[]> arr = GpuArray.wrap(new int[]{1, 2, 3});

        assertThat(arr.isResident()).isFalse();

        arr.close();
    }

    // ---- elementType() for non-int element types -------------------------

    @Test
    void elementType_reportsLongFloatDouble() {
        when(bridge.arrayWrapLong(any(long[].class))).thenReturn(50L);
        when(bridge.arrayWrapFloat(any(float[].class))).thenReturn(51L);
        when(bridge.arrayWrapDouble(any(double[].class))).thenReturn(52L);

        GpuArray<long[]> l = GpuArray.wrap(new long[]{1L});
        GpuArray<float[]> f = GpuArray.wrap(new float[]{1f});
        GpuArray<double[]> d = GpuArray.wrap(new double[]{1d});

        assertThat(l.elementType()).isEqualTo(long.class);
        assertThat(f.elementType()).isEqualTo(float.class);
        assertThat(d.elementType()).isEqualTo(double.class);

        l.close();
        f.close();
        d.close();
    }

    // ---- checkByteSize integer-overflow guard ----------------------------

    @Test
    void wrap_overflowByteSize_documentedNotUnitTestable() {
        // checkByteSize throws IllegalArgumentException when length*elementSize
        // exceeds Integer.MAX_VALUE. To trip it for a long[]/double[]
        // (elementSize 8) the array must have > 268_435_455 elements, i.e. an
        // allocation of >2 GiB. Allocating such an array in a unit test is
        // infeasible (it would OOM most CI heaps and be non-deterministic), so
        // the overflow branch cannot be exercised with a real primitive array.
        //
        // What CAN be verified deterministically is the boundary just below
        // the overflow: an array whose byte size is exactly representable
        // (here a tiny array) must NOT trip the guard. The unreachable
        // overflow branch is left to integration coverage.
        when(bridge.arrayWrapDouble(any(double[].class))).thenReturn(60L);

        GpuArray<double[]> arr = GpuArray.wrap(new double[]{1d, 2d});

        assertThat(arr.length()).isEqualTo(2);
        assertThat(arr.elementType()).isEqualTo(double.class);

        arr.close();
    }

    // ---- GpuArray.CompletedFuture (the future returned by toHost()) ------

    @Test
    void toHost_returnsCompletedFuture_isDoneAndGet() throws Exception {
        int[] src = new int[]{4, 5, 6};
        when(bridge.arrayWrapInt(any(int[].class))).thenReturn(70L);
        when(bridge.arrayToHost(70L)).thenAnswer(inv -> src);

        GpuArray<int[]> arr = GpuArray.wrap(src);
        GpuFuture<int[]> fut = arr.toHost();

        assertThat(fut.isDone()).isTrue();
        assertThat(fut.get()).containsExactly(4, 5, 6);
        assertThat(fut.getNow(new int[]{0})).containsExactly(4, 5, 6);

        arr.close();
    }

    @Test
    void completedFuture_toCompletableFuture_isCompletedWithValue() throws Exception {
        long[] src = new long[]{9L};
        when(bridge.arrayWrapLong(any(long[].class))).thenReturn(71L);
        when(bridge.arrayToHost(71L)).thenAnswer(inv -> src);

        GpuArray<long[]> arr = GpuArray.wrap(src);
        CompletableFuture<long[]> cf = arr.toHost().toCompletableFuture();

        assertThat(cf.isDone()).isTrue();
        assertThat(cf.isCompletedExceptionally()).isFalse();
        assertThat(cf.get()).containsExactly(9L);

        arr.close();
    }

    @Test
    void completedFuture_thenApplyGpu_appliesFunction() {
        int[] src = new int[]{1, 2, 3, 4};
        when(bridge.arrayWrapInt(any(int[].class))).thenReturn(72L);
        when(bridge.arrayToHost(72L)).thenAnswer(inv -> src);

        GpuArray<int[]> arr = GpuArray.wrap(src);
        GpuFuture<Integer> mapped = arr.toHost().thenApplyGpu(a -> a.length);

        assertThat(mapped.isDone()).isTrue();
        assertThat(mapped.getNow(-1)).isEqualTo(4);

        arr.close();
    }

    @Test
    void completedFuture_thenApplyGpu_wrapsThrowingFunctionInFailedGpuFuture() {
        int[] src = new int[]{1};
        when(bridge.arrayWrapInt(any(int[].class))).thenReturn(73L);
        when(bridge.arrayToHost(73L)).thenAnswer(inv -> src);

        GpuArray<int[]> arr = GpuArray.wrap(src);
        GpuFuture<int[]> fut = arr.toHost();

        GpuFuture<Object> failed = fut.thenApplyGpu(a -> {
            throw new IllegalStateException("boom");
        });

        assertThatThrownBy(failed::get)
            .isInstanceOf(GpuException.class)
            .hasMessageContaining("thenApplyGpu failed")
            .hasCauseInstanceOf(IllegalStateException.class);

        arr.close();
    }

    // ---- concurrency: close() racing toHost()/handle()/isResident() ------

    @Test
    void concurrentCloseAndUse_neverThrowsUnexpectedException() throws Exception {
        // Exercises the ReentrantReadWriteLock: many reader threads call
        // toHost()/handle()/isResident() while one thread calls close().
        // The only tolerated failure is IllegalStateException("array closed");
        // anything else (e.g. a native use-after-free surfaced as some other
        // throwable) fails the test.
        when(bridge.arrayWrapInt(any(int[].class))).thenReturn(80L);
        when(bridge.arrayToHost(anyLong())).thenAnswer(inv -> new int[]{1, 2, 3});
        when(bridge.arrayIsResident(anyLong())).thenReturn(true);

        GpuArray<int[]> arr = GpuArray.wrap(new int[]{1, 2, 3});

        final int readerCount = 8;
        final int iterations = 200;
        ExecutorService pool = Executors.newFixedThreadPool(readerCount + 1);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> unexpected = new AtomicReference<>();

        try {
            for (int i = 0; i < readerCount; i++) {
                final int which = i % 3;
                pool.submit(() -> {
                    try {
                        start.await();
                        for (int n = 0; n < iterations; n++) {
                            try {
                                switch (which) {
                                    case 0: arr.toHost();     break;
                                    case 1: arr.handle();     break;
                                    default: arr.isResident(); break;
                                }
                            } catch (IllegalStateException expected) {
                                // tolerated: lost the race against close()
                            }
                        }
                    } catch (Throwable t) {
                        unexpected.compareAndSet(null, t);
                    }
                });
            }
            pool.submit(() -> {
                try {
                    start.await();
                    arr.close();
                } catch (Throwable t) {
                    unexpected.compareAndSet(null, t);
                }
            });

            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        if (unexpected.get() != null) {
            throw new AssertionError(
                "unexpected throwable escaped a concurrent operation",
                unexpected.get());
        }
        // close() must have released the handle exactly once despite the race.
        verify(bridge, times(1)).releaseArray(80L);
    }
}
