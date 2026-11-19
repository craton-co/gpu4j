// SPDX-License-Identifier: Apache-2.0
// Copyright 2025-2026 Craton Software Company (https://craton.com.ar)

package craton.gpu;

/**
 * A unit of work submitted to a {@link GpuExecutor} that produces a result.
 * Analogous to {@link java.util.concurrent.Callable} but specifically intended
 * for GPU-offloadable work; the implementation may execute either as a kernel
 * launch on the device or as a host-side coordinating call, depending on what
 * the underlying executor supports.
 *
 * @param <R> the type of the produced result
 * @since 0.1.0
 */
@FunctionalInterface
public interface GpuCallable<R> {

    /**
     * Performs the computation and returns its result.
     *
     * <p>Any checked exception thrown from this method surfaces to the host
     * through {@link GpuFuture#get()} wrapped in a {@link GpuException} (set
     * as the future's failure cause).
     *
     * @return the computed result
     * @throws Exception if the computation failed for any reason
     */
    R call() throws Exception;
}
