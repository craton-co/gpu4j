// SPDX-License-Identifier: Apache-2.0
// Copyright 2025-2026 Craton Software Company (https://craton.com.ar)

package craton.gpu.internal;

import craton.gpu.GpuArray;
import craton.gpu.GpuDevice;
import craton.gpu.testing.GpuBackendSpi;
import craton.gpu.testing.GpuTestBridge;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * Exercises {@link NativeImpl} fallback and symbol-state caching directly on a stock JVM (where
 * {@link Native} methods throw {@link UnsatisfiedLinkError}) as well as {@link GpuBackendSpi}
 * default methods and {@link ImmediateFutures} edge cases.
 */
class NativeImplAndDefaultSpiTest {

    @AfterEach
    void restoreDefault() {
        GpuTestBridge.restoreDefault();
    }

    @Test
    void nativeImpl_cachesOptionalSymbolAbsenceAndDegradesGracefullyOnStockJvm() {
        NativeImpl impl = new NativeImpl();

        // Device discovery caches ABSENT on first UnsatisfiedLinkError and repeats cleanly.
        assertThat(impl.deviceCount()).isEqualTo(-1);
        assertThat(impl.deviceCount()).isEqualTo(-1);
        assertThat(impl.deviceName(0)).isNull();
        assertThat(impl.deviceName(0)).isNull();
        assertThat(impl.deviceTotalMemory(0)).isZero();
        assertThat(impl.deviceTotalMemory(0)).isZero();
        assertThat(impl.deviceComputeCapability(0)).isZero();
        assertThat(impl.deviceComputeCapability(0)).isZero();

        assertThat(GpuDevice.queryable()).isFalse();
        assertThat(GpuDevice.count()).isZero();
        assertThat(GpuDevice.all()).isEmpty();

        // Optional future & array probes cache ABSENT on stock JVM.
        assertThat(impl.futureErrorKind(1L)).isEqualTo(GpuBackendSpi.ERROR_KIND_UNKNOWN);
        assertThat(impl.futureErrorKind(1L)).isEqualTo(GpuBackendSpi.ERROR_KIND_UNKNOWN);
        assertThat(impl.futureAwait(1L, 1_000L)).isEqualTo(GpuBackendSpi.AWAIT_UNSUPPORTED);
        assertThat(impl.futureAwait(1L, 1_000L)).isEqualTo(GpuBackendSpi.AWAIT_UNSUPPORTED);
        assertThat(impl.arrayToHostInto(1L, new int[1])).isFalse();
        assertThat(impl.arrayToHostInto(1L, new int[1])).isFalse();
        assertThat(impl.arrayCopyFromHost(1L, new int[1])).isFalse();
        assertThat(impl.arrayCopyFromHost(1L, new int[1])).isFalse();

        // Graph methods track state independently: probing graphBeginReplay must not pre-mark
        // other graph symbols before they are probed themselves.
        assertThat(impl.graphBeginReplay(1L, 2L)).isFalse();
        assertThat(impl.graphBeginReplay(1L, 2L)).isFalse();
        assertThat(impl.graphBeginCapture(1L)).isFalse();
        assertThat(impl.graphBeginCapture(1L)).isFalse();
        assertThat(impl.graphEndCapture(1L)).isZero();
        assertThat(impl.graphEndCapture(1L)).isZero();
        assertThat(impl.graphReplay(1L, 2L)).isZero();
        assertThat(impl.graphReplay(1L, 2L)).isZero();
        assertThat(impl.graphEndReplay(1L)).isZero();
        assertThat(impl.graphEndReplay(1L)).isZero();
        assertThat(impl.graphNodeCount(2L)).isEqualTo(-1);
        assertThat(impl.graphNodeCount(2L)).isEqualTo(-1);
        assertThatCode(() -> impl.releaseGraph(2L)).doesNotThrowAnyException();
        assertThatCode(() -> impl.releaseGraph(2L)).doesNotThrowAnyException();

        // 0.4/0.5 mandatory extensions translate UnsatisfiedLinkError into UnsupportedOperationException.
        assertThatThrownBy(() -> impl.submitMethodHandle(1L, "c/K", "m", "()V", new Object[0]))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> impl.futureGetResultAsGpuArray(1L))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> impl.futureThenApplyGpu(1L, "c/K", "m", "()V", new Object[0]))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> impl.arrayWrapShort(new short[] {1}))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> impl.arrayAllocateInt(4))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> impl.arrayAllocateLong(4))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> impl.arrayAllocateShort(4))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> impl.arrayAllocateFloat(4))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> impl.arrayAllocateDouble(4))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> impl.streamSubmitMethod(1L, "c/K", "m", "()V", new Object[0]))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> impl.streamSynchronize(1L))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> impl.gemm(1L, 2L, 3L, 2, 2, 2, false, false, false, 0L))
                .isInstanceOf(UnsupportedOperationException.class);

        // Core 0.2 JNI methods throw raw UnsatisfiedLinkError on stock JVM.
        assertThatThrownBy(() -> impl.openExecutor(0)).isInstanceOf(UnsatisfiedLinkError.class);
        assertThatThrownBy(() -> impl.futureIsDone(1L)).isInstanceOf(UnsatisfiedLinkError.class);
    }

    @Test
    void gpuBackendSpi_defaultMethodsBehaveAsDocumented() {
        GpuBackendSpi spi = mock(GpuBackendSpi.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
        when(spi.futureStatus(10L)).thenReturn(0);
        when(spi.futureStatus(11L)).thenReturn(1);
        when(spi.futureStatus(12L)).thenReturn(2);

        assertThat(spi.futureIsDone(10L)).isFalse();
        assertThat(spi.futureIsDone(11L)).isTrue();
        assertThat(spi.futureIsDone(12L)).isTrue();
        assertThat(spi.submitWithArgs(1L, "fn", new Object[0])).isNull();
        assertThat(spi.deviceCount()).isEqualTo(-1);
        assertThat(spi.deviceName(0)).isNull();
        assertThat(spi.deviceTotalMemory(0)).isZero();
        assertThat(spi.deviceComputeCapability(0)).isZero();
        assertThat(spi.futureErrorKind(1L)).isEqualTo(GpuBackendSpi.ERROR_KIND_UNKNOWN);
        assertThat(spi.futureAwait(1L, 100L)).isEqualTo(GpuBackendSpi.AWAIT_UNSUPPORTED);
        assertThat(spi.arrayToHostInto(1L, new int[1])).isFalse();
        assertThat(spi.arrayCopyFromHost(1L, new int[1])).isFalse();

        assertThatThrownBy(() -> spi.submitMethodHandle(1L, "c/K", "m", "()V", new Object[0]))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> spi.graphBeginCapture(1L))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> spi.graphEndCapture(1L))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> spi.graphReplay(1L, 2L))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> spi.graphBeginReplay(1L, 2L))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> spi.graphEndReplay(1L))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> spi.graphNodeCount(2L))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> spi.releaseGraph(2L))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> spi.futureGetResultAsGpuArray(1L))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> spi.futureThenApplyGpu(1L, "c/K", "m", "()V", new Object[0]))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> spi.arrayWrapShort(new short[1]))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> spi.arrayAllocateInt(1))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> spi.arrayAllocateLong(1))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> spi.arrayAllocateFloat(1))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> spi.arrayAllocateDouble(1))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> spi.arrayAllocateShort(1))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> spi.gemm(1L, 2L, 3L, 1, 1, 1, false, false, false, 0L))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> spi.streamSubmitMethod(1L, "c/K", "m", "()V", new Object[0]))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> spi.streamSynchronize(1L))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void kernelArgs_validatesBlankNamesMalformedDescriptorsScalarShortsAndClosedArrays() {
        assertThatThrownBy(() -> KernelArgs.checkTarget("   ", "m", "()V"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> KernelArgs.checkTarget("c/K", "  ", "()V"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> KernelArgs.checkTarget("c/K", "m", "(II"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> KernelArgs.checkTarget("c/K", "m", "()"))
                .isInstanceOf(IllegalArgumentException.class);

        // Scalar Byte, Short, and byte[] are supported.
        Object[] args = KernelArgs.checkArgs(new Object[] {(byte) 1, (short) 2, new byte[] {3}});
        assertThat(args).hasSize(3);

        // Closed GpuArray in kernel args is rejected with IllegalStateException.
        GpuBackendSpi mockSpi = mock(GpuBackendSpi.class);
        when(mockSpi.arrayAllocateInt(2)).thenReturn(55L);
        try (GpuTestBridge.Installed ignored = GpuTestBridge.install(mockSpi)) {
            GpuArray<int[]> closedArr = GpuArray.allocateInt(2);
            closedArr.close();
            assertThatThrownBy(() -> KernelArgs.checkArgs(new Object[] {closedArr}))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("array closed");
        }
    }
}
