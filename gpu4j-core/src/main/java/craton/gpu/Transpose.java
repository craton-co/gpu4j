// SPDX-License-Identifier: Apache-2.0
// Copyright 2025-2026 Craton Software Company (https://craton.com.ar)

package craton.gpu;

/**
 * Matrix transpose modifier for BLAS operations such as {@link GpuBlas}.
 *
 * @since 0.1.0
 */
public enum Transpose {
    /** Use the matrix as stored in memory without transposition. */
    NONE,
    /** Use the transpose of the matrix as stored in memory. */
    TRANSPOSED;

    /**
     * Returns {@code true} if this constant represents a transposed operand.
     *
     * @return {@code true} for {@link #TRANSPOSED}, {@code false} for {@link #NONE}
     */
    public boolean isTransposed() {
        return this == TRANSPOSED;
    }
}
