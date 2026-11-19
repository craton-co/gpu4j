// SPDX-License-Identifier: Apache-2.0
// Copyright 2025-2026 Craton Software Company (https://craton.com.ar)

package craton.gpu.internal;

import craton.gpu.GpuException;
import craton.gpu.GpuFuture;

/**
 * Module-internal factory wrapping native submission handles in {@link GpuFuture} instances.
 */
public final class Submissions {

    private Submissions() {
    }

    /**
     * Wraps a non-zero native submission handle in a {@link GpuFuture}.
     *
     * @param handle non-zero native submission handle
     * @param <T>    result type
     * @return future tracking the submission
     * @throws GpuException if {@code handle == 0}
     */
    public static <T> GpuFuture<T> future(long handle) {
        return future(0L, handle);
    }

    /**
     * Wraps a non-zero native submission handle bound to {@code execHandle} in a {@link GpuFuture}.
     *
     * @param execHandle originating native executor handle (or {@code 0L} when default)
     * @param handle     non-zero native submission handle
     * @param <T>        result type
     * @return future tracking the submission
     * @throws GpuException if {@code handle == 0}
     */
    public static <T> GpuFuture<T> future(long execHandle, long handle) {
        if (handle == 0L) {
            throw new GpuException("dispatch was not recorded (handle 0)");
        }
        return new GpuFutureImpl<>(execHandle, handle);
    }
}
