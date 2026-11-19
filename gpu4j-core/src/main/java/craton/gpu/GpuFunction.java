// SPDX-License-Identifier: Apache-2.0
// Copyright 2025-2026 Craton Software Company (https://craton.com.ar)

package craton.gpu;

/**
 * A function applied to a value produced by a {@link GpuFuture}. Used by
 * {@link GpuFuture#thenApplyGpu(GpuFunction)} to chain GPU-resident
 * continuations: the function is expected to operate on, and produce, values
 * compatible with device-side execution (primitives, {@link GpuArray} handles,
 * etc.).
 *
 * @param <T> the input type
 * @param <R> the output type
 * @since 0.1.0
 */
@FunctionalInterface
public interface GpuFunction<T, R> {

    /**
     * Applies this function to the given input.
     *
     * <p>Any checked exception thrown from this method surfaces to the host
     * through {@link GpuFuture#get()} wrapped in a {@link GpuException} (set
     * as the future's failure cause).
     *
     * @param input the input value
     * @return the computed result
     * @throws Exception if the application failed for any reason
     */
    R apply(T input) throws Exception;
}
