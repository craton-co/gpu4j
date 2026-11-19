// SPDX-License-Identifier: Apache-2.0
// Copyright 2025-2026 Craton Software Company (https://craton.com.ar)

package craton.gpu.internal;

import craton.gpu.GpuArray;
import craton.gpu.GpuException;
import craton.gpu.GpuFunction;
import craton.gpu.GpuFuture;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Factory for already-completed and already-failed {@link GpuFuture} instances used on immediate
 * and host-fallback continuation paths.
 */
public final class ImmediateFutures {

    private ImmediateFutures() {
    }

    /**
     * Returns an already-completed future holding {@code value}.
     */
    public static <T> GpuFuture<T> completed(T value) {
        return new Completed<>(value);
    }

    /**
     * Returns an already-failed future holding {@code failure}.
     */
    public static <T> GpuFuture<T> failed(GpuException failure) {
        Objects.requireNonNull(failure, "failure");
        return new Failed<>(failure);
    }

    /**
     * Applies {@code fn} to {@code value} on the calling thread and wraps the outcome in an
     * immediate {@link GpuFuture}.
     */
    public static <T, U> GpuFuture<U> apply(
            GpuFunction<? super T, ? extends U> fn,
            T value) {
        Objects.requireNonNull(fn, "fn");
        try {
            return completed(fn.apply(value));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return failed(new GpuException("thenApplyGpu interrupted", ie));
        } catch (GpuException ge) {
            return failed(ge);
        } catch (Exception e) {
            return failed(new GpuException("thenApplyGpu failed", e));
        }
    }

    @SuppressWarnings("unchecked")
    static <E> GpuArray<E> wrapArrayValue(Object value) {
        if (value instanceof GpuArray<?> arr) {
            return (GpuArray<E>) arr;
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

    static boolean isWrappableArray(Object value) {
        return value instanceof GpuArray<?>
                || value instanceof int[]
                || value instanceof long[]
                || value instanceof short[]
                || value instanceof float[]
                || value instanceof double[];
    }

    private static final class Completed<T> implements GpuFuture<T> {
        private final T value;
        private final AtomicBoolean claimed = new AtomicBoolean();

        Completed(T value) {
            this.value = value;
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public T get() {
            return value;
        }

        @Override
        public T get(long timeout, TimeUnit unit) {
            Objects.requireNonNull(unit, "unit");
            return value;
        }

        @Override
        public T getNow(T valueIfAbsent) {
            return value;
        }

        @Override
        public <E> GpuArray<E> getAsGpuArray() {
            if (!isWrappableArray(value)) {
                throw new GpuException(
                        "future result cannot be wrapped as GpuArray: "
                                + (value == null ? "null" : value.getClass().getName()));
            }
            if (!claimed.compareAndSet(false, true)) {
                throw new GpuException("future GPU array result was already claimed");
            }
            return wrapArrayValue(value);
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public <U> GpuFuture<U> thenApplyGpu(GpuFunction<? super T, ? extends U> fn) {
            Objects.requireNonNull(fn, "fn");
            return ImmediateFutures.apply(fn, value);
        }

        @Override
        public CompletableFuture<T> toCompletableFuture() {
            return CompletableFuture.completedFuture(value);
        }

        @Override
        public CompletableFuture<T> toCompletableFuture(Executor exec) {
            Objects.requireNonNull(exec, "exec");
            return CompletableFuture.completedFuture(value);
        }
    }

    private static final class Failed<T> implements GpuFuture<T> {
        private final GpuException failure;

        Failed(GpuException failure) {
            this.failure = failure;
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public T get() throws GpuException {
            throw failure;
        }

        @Override
        public T get(long timeout, TimeUnit unit) throws GpuException {
            Objects.requireNonNull(unit, "unit");
            throw failure;
        }

        @Override
        public T getNow(T valueIfAbsent) {
            throw failure;
        }

        @Override
        public <E> GpuArray<E> getAsGpuArray() {
            throw failure;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public <U> GpuFuture<U> thenApplyGpu(GpuFunction<? super T, ? extends U> fn) {
            Objects.requireNonNull(fn, "fn");
            return ImmediateFutures.failed(failure);
        }

        @Override
        public CompletableFuture<T> toCompletableFuture() {
            CompletableFuture<T> cf = new CompletableFuture<>();
            cf.completeExceptionally(failure);
            return cf;
        }

        @Override
        public CompletableFuture<T> toCompletableFuture(Executor exec) {
            Objects.requireNonNull(exec, "exec");
            return toCompletableFuture();
        }
    }
}
