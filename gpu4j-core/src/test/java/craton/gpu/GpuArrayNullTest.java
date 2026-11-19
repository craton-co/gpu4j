// SPDX-License-Identifier: Apache-2.0
// Copyright 2025-2026 Craton Software Company (https://craton.com.ar)

package craton.gpu;

import craton.gpu.testing.GpuBackendSpi;
import craton.gpu.testing.GpuTestBridge;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Null-input validation for the typed {@link GpuArray#wrap} factories and {@code toHost(dest)} /
 * {@code copyFromHost(src)} overloads via {@link GpuTestBridge}.
 */
class GpuArrayNullTest {

    private GpuTestBridge.Installed installed;

    @BeforeEach
    void installMockBackend() {
        GpuBackendSpi mockBackend = mock(GpuBackendSpi.class);
        when(mockBackend.arrayWrapInt(any(int[].class))).thenReturn(101L);
        when(mockBackend.arrayWrapLong(any(long[].class))).thenReturn(102L);
        when(mockBackend.arrayWrapFloat(any(float[].class))).thenReturn(103L);
        when(mockBackend.arrayWrapDouble(any(double[].class))).thenReturn(104L);
        when(mockBackend.arrayWrapShort(any(short[].class))).thenReturn(105L);
        installed = GpuTestBridge.install(mockBackend);
    }

    @AfterEach
    void restoreBackend() {
        if (installed != null) {
            installed.close();
        }
    }

    @Test
    void wrapNullIntArrayThrowsNpeMentioningHostArray() {
        assertThatThrownBy(() -> GpuArray.wrap((int[]) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("host array");
    }

    @Test
    void wrapNullLongArrayThrowsNpeMentioningHostArray() {
        assertThatThrownBy(() -> GpuArray.wrap((long[]) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("host array");
    }

    @Test
    void wrapNullFloatArrayThrowsNpeMentioningHostArray() {
        assertThatThrownBy(() -> GpuArray.wrap((float[]) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("host array");
    }

    @Test
    void wrapNullDoubleArrayThrowsNpeMentioningHostArray() {
        assertThatThrownBy(() -> GpuArray.wrap((double[]) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("host array");
    }

    @Test
    void wrapNullHalfArrayThrowsNpeMentioningHostArray() {
        assertThatThrownBy(() -> GpuArray.wrapHalf(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("host array");
    }

    @Test
    void toHostNullIntDestThrowsNpeMentioningDest() {
        try (GpuArray<int[]> a = GpuArray.wrap(new int[] {1})) {
            assertThatThrownBy(() -> a.toHost((int[]) null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("dest");
        }
    }

    @Test
    void toHostNullLongDestThrowsNpeMentioningDest() {
        try (GpuArray<long[]> a = GpuArray.wrap(new long[] {1L})) {
            assertThatThrownBy(() -> a.toHost((long[]) null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("dest");
        }
    }

    @Test
    void toHostNullFloatDestThrowsNpeMentioningDest() {
        try (GpuArray<float[]> a = GpuArray.wrap(new float[] {1.0f})) {
            assertThatThrownBy(() -> a.toHost((float[]) null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("dest");
        }
    }

    @Test
    void toHostNullDoubleDestThrowsNpeMentioningDest() {
        try (GpuArray<double[]> a = GpuArray.wrap(new double[] {1.0})) {
            assertThatThrownBy(() -> a.toHost((double[]) null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("dest");
        }
    }

    @Test
    void toHostNullShortDestThrowsNpeMentioningDest() {
        try (GpuArray<short[]> a = GpuArray.wrapHalf(new short[] {1})) {
            assertThatThrownBy(() -> a.toHost((short[]) null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("dest");
        }
    }
}
