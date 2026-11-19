// SPDX-License-Identifier: Apache-2.0
// Copyright 2025-2026 Craton Software Company (https://craton.com.ar)

package craton.gpu;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.LockSupport;

/**
 * Represents the pending result of asynchronous GPU work submitted through a {@link GpuExecutor}.
 *
 * <p>A {@code GpuFuture} composes with subsequent GPU work via {@link #thenApplyGpu(GpuFunction)}
 * and {@link #thenApplyGpu(String, String, String, Object...)} without round-tripping intermediate
 * device buffers through host code, and can be bridged to {@link java.util.concurrent} via
 * {@link #toCompletableFuture()}.</p>
 *
 * @param <T> the type produced by the underlying computation
 * @since 0.1.0
 */
public interface GpuFuture<T> {

    /**
     * Returns {@code true} if the underlying GPU work has completed (either successfully or with
     * an error).
     *
     * <p><strong>Memory ordering note:</strong> {@code isDone()} establishes no happens-before
     * relationship between device-side writes and host observers. Callers that need to read a
     * value produced by the kernel must call {@link #get()} (or {@link #get(long, TimeUnit)})
     * &mdash; only those methods publish device-to-host memory ordering.</p>
     *
     * @return {@code true} if complete
     */
    boolean isDone();

    /**
     * Blocks the calling host thread until the underlying GPU work completes and returns its
     * result. If the work failed, throws a {@link GpuException}.
     *
     * @return computed result
     * @throws GpuException         if the GPU computation failed or was cancelled
     * @throws InterruptedException if interrupted while waiting
     */
    T get() throws GpuException, InterruptedException;

    /**
     * Blocks for at most {@code timeout} of {@code unit}, returning the result or throwing
     * {@link TimeoutException} if still incomplete when the deadline expires.
     *
     * @param timeout maximum wait duration
     * @param unit    time unit of {@code timeout}
     * @return computed result
     * @throws InterruptedException if interrupted while waiting
     * @throws TimeoutException     if the timeout elapsed before completion
     * @throws GpuException         if the GPU computation failed or was cancelled
     */
    default T get(long timeout, TimeUnit unit)
            throws InterruptedException, TimeoutException, GpuException {
        Objects.requireNonNull(unit, "unit");
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        if (isDone()) {
            return get();
        }
        if (timeout <= 0L) {
            throw new TimeoutException("future not done");
        }
        long deadline = System.nanoTime() + Math.max(1L, unit.toNanos(timeout));
        long parkNanos = 50_000L;
        while (!isDone()) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0L) {
                throw new TimeoutException("future not done after " + timeout + " " + unit);
            }
            LockSupport.parkNanos(Math.min(parkNanos, remaining));
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            parkNanos = Math.min(parkNanos << 1, 2_000_000L);
        }
        return get();
    }

    /**
     * Attempts to cancel execution of this future.
     *
     * @param mayInterruptIfRunning {@code true} if in-flight execution may be interrupted
     * @return {@code true} if cancelled before completion
     */
    default boolean cancel(boolean mayInterruptIfRunning) {
        return false;
    }

    /**
     * Returns {@code true} if this future was cancelled before completion.
     *
     * @return {@code true} if cancelled
     */
    default boolean isCancelled() {
        return false;
    }

    /**
     * Returns the result immediately if complete, otherwise {@code valueIfAbsent}.
     *
     * @param valueIfAbsent fallback value returned while still running
     * @return completed result or {@code valueIfAbsent}
     */
    T getNow(T valueIfAbsent);

    /**
     * Claims the array result of a completed future as a user-owned {@link GpuArray}. Native
     * implementations promote the device-resident buffer without copying back to the host and
     * reject subsequent claims with {@link GpuException}; custom host-backed implementations fall
     * back to wrapping the host array returned by {@link #get()}.
     *
     * @param <E> primitive array type (for example {@code float[]} or {@code int[]})
     * @return resident {@link GpuArray} holding the kernel output
     * @throws GpuException         if the future failed, was already claimed, or did not produce a
     *                              supported array type
     * @throws InterruptedException if interrupted while awaiting completion
     * @since 0.1.0
     */
    @SuppressWarnings("unchecked")
    default <E> GpuArray<E> getAsGpuArray() throws GpuException, InterruptedException {
        T value = get();
        if (value instanceof GpuArray<?> array) {
            return (GpuArray<E>) array;
        }
        if (value instanceof int[] ints) {
            return (GpuArray<E>) GpuArray.wrap(ints);
        }
        if (value instanceof long[] longs) {
            return (GpuArray<E>) GpuArray.wrap(longs);
        }
        if (value instanceof short[] shorts) {
            return (GpuArray<E>) GpuArray.wrapHalf(shorts);
        }
        if (value instanceof float[] floats) {
            return (GpuArray<E>) GpuArray.wrap(floats);
        }
        if (value instanceof double[] doubles) {
            return (GpuArray<E>) GpuArray.wrap(doubles);
        }
        throw new GpuException(
                "future result cannot be wrapped as GpuArray: "
                        + (value == null ? "null" : value.getClass().getName()));
    }

    /**
     * Chains a GPU continuation onto this future without blocking on host sync when CratonVM can
     * compile and route {@code fn} on the device. Falls back to host evaluation if {@code fn}
     * cannot be compiled for device execution.
     *
     * @param fn  continuation function
     * @param <U> output type
     * @return chained future
     */
    <U> GpuFuture<U> thenApplyGpu(GpuFunction<? super T, ? extends U> fn);

    /**
     * Chains a named GPU kernel stage directly onto this future's device result without
     * transferring intermediate results to the host.
     *
     * @param className  target class name
     * @param methodName target method name
     * @param descriptor JVM method descriptor
     * @param extraArgs  additional arguments passed after the upstream output
     * @param <U>        output type
     * @return chained future
     * @since 0.1.0
     */
    default <U> GpuFuture<U> thenApplyGpu(
            String className,
            String methodName,
            String descriptor,
            Object... extraArgs) {
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(methodName, "methodName");
        Objects.requireNonNull(descriptor, "descriptor");
        throw new UnsupportedOperationException(
                "this GpuFuture does not support named GPU continuation chaining");
    }

    /**
     * Adapts this {@code GpuFuture} to a standard {@link CompletableFuture}.
     *
     * @return bridged {@link CompletableFuture}
     */
    CompletableFuture<T> toCompletableFuture();

    /**
     * Adapts this {@code GpuFuture} to a standard {@link CompletableFuture} executing on
     * {@code exec}.
     *
     * @param exec executor running the wait task
     * @return bridged {@link CompletableFuture}
     */
    default CompletableFuture<T> toCompletableFuture(Executor exec) {
        Objects.requireNonNull(exec, "exec");
        return CompletableFuture.supplyAsync(() -> {
            try {
                return get();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new GpuException("interrupted", ie);
            }
        }, exec);
    }
}
