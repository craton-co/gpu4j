// SPDX-License-Identifier: Apache-2.0
// Copyright 2025-2026 Craton Software Company (https://craton.com.ar)

package craton.gpu;

/**
 * A unit of GPU-offloadable work that produces no result. Analogous to
 * {@link Runnable} but permitted to throw checked exceptions so that
 * device-side failures propagate naturally back to the host caller through
 * {@link GpuFuture#get()}.
 *
 * @since 0.1.0
 */
@FunctionalInterface
public interface GpuRunnable {

    /**
     * Performs the work.
     *
     * <p>Any checked exception thrown from this method surfaces to the host
     * through {@link GpuFuture#get()} wrapped in a {@link GpuException} (set
     * as the future's failure cause).
     *
     * @throws Exception if the work failed for any reason
     */
    void run() throws Exception;
}
