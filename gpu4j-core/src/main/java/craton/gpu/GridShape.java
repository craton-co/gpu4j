// SPDX-License-Identifier: Apache-2.0
// Copyright 2025-2026 Craton Software Company (https://craton.com.ar)

package craton.gpu;

/**
 * Selects the grid/launch shape the GPU lowering should target for a kernel.
 *
 * <p><b>Note:</b> despite some older documentation referring to this type as an
 * annotation, it is an {@code enum} that is consumed as the value of the
 * {@link GpuKernel#grid()} element.</p>
 *
 * @since 0.1.0
 */
public enum GridShape {
    /**
     * 1D mapping: one thread is launched per element of the largest input array.
     * This is the default pattern for simple per-element kernels.
     */
    ELEMENTWISE,

    /**
     * Per-row dispatch for 2D inputs: one thread (or one block, depending on
     * lowering) is launched per row of the input.
     */
    ROW_PER_THREAD,

    /**
     * Cooperative thread-block reduction pattern. The lowering uses a tree-style
     * reduction across threads in a block and consumes shared memory for the
     * intermediate partial results.
     */
    BLOCK_REDUCTION
}
