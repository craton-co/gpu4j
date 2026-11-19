// SPDX-License-Identifier: Apache-2.0
// Copyright 2025-2026 Craton Software Company (https://craton.com.ar)

package craton.gpu;

import craton.gpu.testing.GpuBackendSpi;
import craton.gpu.testing.GpuTestBridge;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for 0.4.0 and 0.5.0 public API types: {@link Half}, {@link Transpose},
 * {@link GpuDevice}, {@link GpuBlas}, {@link PreparedKernel}, {@link GpuGraph},
 * {@link GpuExecutor#open}, {@link GpuArray} allocate/wrapHalf/copyFromHost/toHost(short[]), and
 * {@link GpuTestBridge}.
 */
class GpuV050TypesAndBlasTest {

    private GpuBackendSpi bridge;
    private GpuTestBridge.Installed installed;

    @BeforeEach
    void setUpBridge() {
        bridge = mock(GpuBackendSpi.class);
        installed = GpuTestBridge.install(bridge);
    }

    @AfterEach
    void tearDownBridge() {
        if (installed != null) {
            installed.close();
        }
        GpuTestBridge.restoreDefault();
    }

    // ---- Half --------------------------------------------------------------

    @Test
    void half_roundTripsNormalsZerosInfinitiesNanAndSubnormals() {
        assertThat(Half.fromFloat(0.0f)).isEqualTo((short) 0x0000);
        assertThat(Half.fromFloat(-0.0f)).isEqualTo((short) 0x8000);
        assertThat(Half.toFloat((short) 0x0000)).isZero();
        assertThat(Float.floatToRawIntBits(Half.toFloat((short) 0x8000)))
                .isEqualTo(Integer.MIN_VALUE);

        assertThat(Half.toFloat(Half.fromFloat(1.0f))).isEqualTo(1.0f);
        assertThat(Half.toFloat(Half.fromFloat(-2.5f))).isEqualTo(-2.5f);
        assertThat(Half.toFloat(Half.fromFloat(Half.MAX_VALUE))).isEqualTo(Half.MAX_VALUE);

        short posInf = Half.fromFloat(Float.POSITIVE_INFINITY);
        short negInf = Half.fromFloat(Float.NEGATIVE_INFINITY);
        assertThat(posInf).isEqualTo((short) 0x7C00);
        assertThat(negInf).isEqualTo((short) 0xFC00);
        assertThat(Half.toFloat(posInf)).isInfinite().isPositive();
        assertThat(Half.toFloat(negInf)).isInfinite().isNegative();
        assertThat(Half.fromFloat(100_000.0f)).isEqualTo(posInf);

        short nanBits = Half.fromFloat(Float.NaN);
        assertThat(Float.isNaN(Half.toFloat(nanBits))).isTrue();

        short subBits = Half.fromFloat(Half.MIN_VALUE);
        assertThat(Half.toFloat(subBits)).isCloseTo(Half.MIN_VALUE, within(1e-9f));

        float halfUlp = 0x1.0p-11f;
        assertThat(Half.toFloat(Half.fromFloat(1.0f + halfUlp))).isEqualTo(1.0f);
        float oddNext = 1.0f + 0x1.0p-10f;
        assertThat(Half.toFloat(Half.fromFloat(oddNext + halfUlp)))
                .isEqualTo(1.0f + 0x1.0p-9f);

        short[] encoded = Half.fromFloats(new float[] {0.5f, -3.25f, 16.0f});
        assertThat(Half.toFloats(encoded)).containsExactly(0.5f, -3.25f, 16.0f);
    }

    // ---- GpuTestBridge nested scoping & GpuExecutor.open -------------------

    @Test
    void testBridge_nestedInstallRestoresPreviousBackendAndExecutorOpenValidates() {
        assertThat(GpuTestBridge.current()).isSameAs(bridge);

        GpuBackendSpi nested = mock(GpuBackendSpi.class);
        try (GpuTestBridge.Installed inner = GpuTestBridge.install(nested)) {
            assertThat(GpuTestBridge.current()).isSameAs(nested);
        }
        assertThat(GpuTestBridge.current()).isSameAs(bridge);

        assertThatThrownBy(() -> GpuExecutor.open(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deviceOrdinal");

        when(bridge.openExecutor(0)).thenReturn(null);
        assertThatThrownBy(GpuExecutor::open)
                .isInstanceOf(GpuException.class)
                .hasMessageContaining("failed to open GPU executor");

        GpuExecutor mockExec = mock(GpuExecutor.class);
        when(bridge.openExecutor(0)).thenReturn(mockExec);
        assertThat(GpuExecutor.open()).isSameAs(mockExec);
    }

    // ---- Transpose & GpuDevice ---------------------------------------------

    @Test
    void transposeAndGpuDeviceMetadata() {
        assertThat(Transpose.NONE.isTransposed()).isFalse();
        assertThat(Transpose.TRANSPOSED.isTransposed()).isTrue();

        when(bridge.deviceCount()).thenReturn(2);
        when(bridge.deviceName(0)).thenReturn("NVIDIA RTX 4090");
        when(bridge.deviceTotalMemory(0)).thenReturn(24L * 1024L * 1024L * 1024L);
        when(bridge.deviceComputeCapability(0)).thenReturn(89);
        when(bridge.deviceName(1)).thenReturn("Mock Compute");
        when(bridge.deviceTotalMemory(1)).thenReturn(1024L * 1024L * 512L);
        when(bridge.deviceComputeCapability(1)).thenReturn(0);

        assertThat(GpuDevice.queryable()).isTrue();
        assertThat(GpuDevice.count()).isEqualTo(2);
        assertThat(GpuDevice.all()).hasSize(2);
        // GpuDevice.all() queries deviceCount() only once (plus queryable + count above = 3).
        verify(bridge, times(3)).deviceCount();

        GpuDevice d0 = GpuDevice.get(0);
        assertThat(d0.computeCapability()).isEqualTo("sm_89");
        assertThat(d0.toString()).contains("NVIDIA RTX 4090").contains("sm_89");

        GpuDevice d1 = GpuDevice.get(1);
        assertThat(d1.computeCapability()).isEqualTo("sm_unknown");

        assertThatThrownBy(() -> GpuDevice.get(2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GpuDevice(-1, "bad", 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GpuDevice(0, "bad", -1, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GpuDevice(0, "bad", 0, -1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- GpuArray 0.4.0/0.5.0 surface -------------------------------------

    @Test
    void gpuArray_allocations_half_copyFromHost_oversizedSlice_and_overflowGuard() {
        when(bridge.arrayAllocateInt(4)).thenReturn(101L);
        when(bridge.arrayAllocateLong(4)).thenReturn(102L);
        when(bridge.arrayAllocateFloat(4)).thenReturn(103L);
        when(bridge.arrayAllocateDouble(4)).thenReturn(104L);
        when(bridge.arrayAllocateShort(4)).thenReturn(105L);
        when(bridge.arrayWrapShort(any(short[].class))).thenReturn(106L);
        when(bridge.arrayToHostInto(eq(106L), any(short[].class))).thenReturn(true);
        when(bridge.arrayCopyFromHost(anyLong(), any())).thenReturn(true);

        try (GpuArray<int[]> ints = GpuArray.allocateInt(4);
             GpuArray<long[]> longs = GpuArray.allocateLong(4);
             GpuArray<float[]> floats = GpuArray.allocateFloat(4);
             GpuArray<double[]> doubles = GpuArray.allocateDouble(4);
             GpuArray<short[]> halves = GpuArray.allocateHalf(4);
             GpuArray<short[]> wrappedHalf = GpuArray.wrapHalf(new short[] {1, 2, 3, 4});
             GpuArray<float[]> promoted = GpuArray.fromNativeHandle(107L, GpuArray.ELEM_FLOAT, 8)) {

            assertThat(ints.elementType()).isEqualTo(int.class);
            assertThat(longs.elementType()).isEqualTo(long.class);
            assertThat(floats.elementType()).isEqualTo(float.class);
            assertThat(doubles.elementType()).isEqualTo(double.class);
            assertThat(halves.elementType()).isEqualTo(short.class);
            assertThat(promoted.elementType()).isEqualTo(float.class);
            assertThat(promoted.length()).isEqualTo(8);

            assertThat(ints.copyFromHost(new int[] {1, 2, 3, 4})).isEqualTo(4);
            // Oversized host source array is safely sliced to exact device length (4).
            assertThat(ints.copyFromHost(new int[] {10, 20, 30, 40, 50, 60})).isEqualTo(4);
            verify(bridge).arrayCopyFromHost(eq(101L), eq(new int[] {10, 20, 30, 40}));

            assertThat(longs.copyFromHost(new long[] {1, 2, 3, 4})).isEqualTo(4);
            assertThat(floats.copyFromHost(new float[] {1, 2, 3, 4})).isEqualTo(4);
            assertThat(doubles.copyFromHost(new double[] {1, 2, 3, 4})).isEqualTo(4);
            assertThat(halves.copyFromHost(new short[] {1, 2, 3, 4})).isEqualTo(4);
            assertThat(wrappedHalf.toHost(new short[4])).isEqualTo(4);

            assertThatThrownBy(() -> ints.copyFromHost(new float[4]))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("elementType");
            assertThatThrownBy(() -> ints.copyFromHost(new int[2]))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("src.length");
        }

        assertThatThrownBy(() -> GpuArray.allocateFloat(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GpuArray.allocateDouble((Integer.MAX_VALUE / Double.BYTES) + 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2 GiB");
        assertThatThrownBy(() -> GpuArray.fromNativeHandle(108L, 99, 4))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- GpuBlas & Rectangular Reference Verification ----------------------

    @Test
    void gpuBlas_gemm_gemmHalf_and_nonSquareGemmReference() throws Exception {
        when(bridge.arrayAllocateFloat(4)).thenReturn(201L, 202L, 203L);
        when(bridge.arrayAllocateShort(4)).thenReturn(205L, 206L);
        when(bridge.gemm(
                anyLong(), anyLong(), anyLong(),
                anyInt(), anyInt(), anyInt(),
                anyBoolean(), anyBoolean(), anyBoolean(),
                anyLong())).thenReturn(501L, 502L);
        when(bridge.futureStatus(501L)).thenReturn(1);
        when(bridge.futureStatus(502L)).thenReturn(1);

        try (GpuArray<float[]> a = GpuArray.allocateFloat(4);
             GpuArray<float[]> b = GpuArray.allocateFloat(4);
             GpuArray<float[]> c = GpuArray.allocateFloat(4);
             GpuArray<short[]> ha = GpuArray.allocateHalf(4);
             GpuArray<short[]> hb = GpuArray.allocateHalf(4)) {

            GpuFuture<Void> f1 = GpuBlas.gemm(a, Transpose.NONE, b, Transpose.TRANSPOSED, c, 2, 2, 2);
            f1.get();
            verify(bridge).gemm(201L, 202L, 203L, 2, 2, 2, false, false, true, 0L);

            GpuStream stream = new GpuStream() {
                @Override public long handle() { return 77L; }
                @Override public void dispatch(String cl, String m, String d, Object... args) { }
                @Override public void synchronize() { }
                @Override public void close() { }
            };
            GpuFuture<Void> f2 = GpuBlas.gemmHalf(
                    stream, ha, Transpose.TRANSPOSED, hb, Transpose.NONE, c, 2, 2, 2);
            f2.get();
            verify(bridge).gemm(205L, 206L, 203L, 2, 2, 2, true, true, false, 77L);

            assertThatThrownBy(() -> GpuBlas.gemm(a, b, c, 0, 2, 2))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> GpuBlas.gemm(a, b, c, 4, 4, 4))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        // Non-square m=2, k=3, n=4 verification across all 4 transpose combinations.
        // op(A) = [[1, 2, 3], [4, 5, 6]] (2x3)
        // op(B) = [[1, 2, 3, 4], [5, 6, 7, 8], [9, 10, 11, 12]] (3x4)
        // Expected C (2x4) = [[38, 44, 50, 56], [83, 98, 113, 128]]
        float[] aNN = {1, 2, 3, 4, 5, 6};
        float[] aT = {1, 4, 2, 5, 3, 6}; // 3x2 stored row-major
        float[] bNN = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};
        float[] bT = {1, 5, 9, 2, 6, 10, 3, 7, 11, 4, 8, 12}; // 4x3 stored row-major
        float[] expected = {38, 44, 50, 56, 83, 98, 113, 128};

        float[] outNN = new float[8];
        GpuBlas.gemmReference(aNN, bNN, outNN, 2, 4, 3);
        assertThat(outNN).containsExactly(expected);

        float[] outTN = new float[8];
        GpuBlas.gemmReference(aT, Transpose.TRANSPOSED, bNN, Transpose.NONE, outTN, 2, 4, 3);
        assertThat(outTN).containsExactly(expected);

        float[] outNT = new float[8];
        GpuBlas.gemmReference(aNN, Transpose.NONE, bT, Transpose.TRANSPOSED, outNT, 2, 4, 3);
        assertThat(outNT).containsExactly(expected);

        float[] outTT = new float[8];
        GpuBlas.gemmReference(aT, Transpose.TRANSPOSED, bT, Transpose.TRANSPOSED, outTT, 2, 4, 3);
        assertThat(outTT).containsExactly(expected);
    }

    @Test
    void preparedKernel_dispatchesAllOverloads() {
        @SuppressWarnings("unchecked")
        GpuFuture<Void> stubFuture = mock(GpuFuture.class);
        GpuExecutor exec = new GpuExecutor() {
            @Override public <R> GpuFuture<R> submit(GpuCallable<R> task) { throw new UnsupportedOperationException(); }
            @Override public GpuFuture<Void> launch(GpuRunnable task) { throw new UnsupportedOperationException(); }
            @Override public long handleForDispatch() { return 1L; }
            @Override
            @SuppressWarnings("unchecked")
            public <R> GpuFuture<R> dispatchNamed(String c, String m, String d, Object[] args) {
                return (GpuFuture<R>) stubFuture;
            }
            @Override
            public long dispatchNamedHandle(String c, String m, String d, Object[] args) {
                return 999L;
            }
            @Override public void beginCapture() { }
            @Override public GpuGraph endCapture() { return GpuGraph.fromNativeHandle(this, 77L); }
            @Override public long replay(GpuGraph graph) { return 88L; }
            @Override public void beginReplay(GpuGraph graph) { }
            @Override public long endReplay() { return 89L; }
            @Override public void awaitSubmission(long submissionHandle) { }
            @Override public GpuStream newStream() { throw new UnsupportedOperationException(); }
            @Override public void close() { }
        };

        PreparedKernel pk = exec.prepare("c/K", "m", "([F[F[F)V");
        assertThat(pk.className()).isEqualTo("c/K");
        assertThat(pk.methodName()).isEqualTo("m");
        assertThat(pk.descriptor()).isEqualTo("([F[F[F)V");
        assertThat(pk.toString()).isEqualTo("c/K.m([F[F[F)V");
        assertThat(pk.dispatch(new float[2], new float[2], new float[2])).isSameAs(stubFuture);
        assertThat(pk.dispatch(new int[2], new int[2], new int[2])).isSameAs(stubFuture);
        assertThat(pk.dispatch(new long[2], new long[2], new long[2])).isSameAs(stubFuture);
        assertThat(pk.dispatch(new double[2], new double[2], new double[2])).isSameAs(stubFuture);
        assertThat(pk.dispatch(3, new int[2], new int[2])).isSameAs(stubFuture);
        assertThat(pk.dispatch(3.0f, new float[2], new float[2])).isSameAs(stubFuture);
        assertThat(pk.dispatch((Object) "ok")).isSameAs(stubFuture);
        assertThat(pk.dispatchHandle("ok")).isEqualTo(999L);

        assertThat(exec.submit("c/K", "m", "()V")).isSameAs(stubFuture);
        assertThat(exec.submitV("c/K", "m", "([I[I[I)V", new int[1], new int[1], new int[1])).isSameAs(stubFuture);
        assertThat(exec.submitV("c/K", "m", "([F[F[F)V", new float[1], new float[1], new float[1])).isSameAs(stubFuture);
        assertThat(exec.submitV("c/K", "m", "([D[D[D)V", new double[1], new double[1], new double[1])).isSameAs(stubFuture);
        assertThat(exec.submitV("c/K", "m", "([J[J[J)V", new long[1], new long[1], new long[1])).isSameAs(stubFuture);
        assertThat(exec.submitScale("c/K", "m", "(I[I[I)V", 2, new int[1], new int[1])).isSameAs(stubFuture);
        assertThat(exec.submitScale("c/K", "m", "(F[F[F)V", 2.0f, new float[1], new float[1])).isSameAs(stubFuture);
    }
}
