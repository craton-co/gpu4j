// SPDX-License-Identifier: Apache-2.0
// Copyright 2025-2026 Craton Software Company (https://craton.com.ar)

package craton.gpu.internal;

import craton.gpu.GpuArray;
import craton.gpu.GpuException;
import craton.gpu.GpuFunction;
import craton.gpu.GpuFuture;
import craton.gpu.testing.GpuBackendSpi;

import java.lang.ref.Reference;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.locks.LockSupport;

/**
 * Native-handle-backed {@link GpuFuture} implementation with one-shot outcome memoization,
 * interruptible wait, non-blocking GPU continuation chaining, and zero-copy {@link GpuArray}
 * promotion.
 */
final class GpuFutureImpl<T> implements GpuFuture<T> {

    private static final System.Logger LOG = System.getLogger(GpuFutureImpl.class.getName());

    private static final int STATE_ACTIVE = 0;
    private static final int STATE_CANCELLED = 1;
    private static final int STATE_ARRAY_CLAIMED = 2;

    @SuppressWarnings("rawtypes")
    private static final AtomicIntegerFieldUpdater<GpuFutureImpl> STATE_UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(GpuFutureImpl.class, "state");

    private volatile long execHandle;
    private final long handle;
    private final StreamCleaner.Registration cleanable;
    private volatile int state = STATE_ACTIVE;
    private volatile Outcome<T> outcome;

    GpuFutureImpl(long handle) {
        this(0L, handle);
    }

    GpuFutureImpl(long execHandle, long handle) {
        this.execHandle = execHandle;
        this.handle = handle;
        this.cleanable = StreamCleaner.register(this, handle, StreamCleaner.Kind.FUTURE);
    }

    void bindExecutorIfUnset(long ownerExecHandle) {
        if (this.execHandle == 0L && ownerExecHandle != 0L) {
            this.execHandle = ownerExecHandle;
        }
    }

    long execHandle() {
        return execHandle;
    }

    private void checkValidHandle() {
        if (handle == 0L) {
            throw new GpuException("invalid future handle: 0");
        }
    }

    @Override
    public boolean isDone() {
        if (outcome != null || state == STATE_CANCELLED || state == STATE_ARRAY_CLAIMED) {
            return true;
        }
        if (handle == 0L) {
            return false;
        }
        try {
            GpuBackendSpi bridge = Native.bridge();
            if (bridge instanceof NativeImpl) {
                return bridge.futureIsDone(handle);
            }
            int status = bridge.futureStatus(handle);
            if (status == 1 || status == 2) {
                return true;
            }
            return bridge.futureIsDone(handle);
        } finally {
            Reference.reachabilityFence(this);
        }
    }

    @Override
    public T get() throws GpuException, InterruptedException {
        checkValidHandle();
        Outcome<T> cached = outcome;
        if (cached != null) {
            return cached.unwrap();
        }
        checkNotCancelledOrClaimed();
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        try {
            GpuBackendSpi bridge = Native.bridge();
            bridge.futureSynchronize(handle);
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            return resolveAfterCompletion(bridge).unwrap();
        } finally {
            Reference.reachabilityFence(this);
        }
    }

    @Override
    public T get(long timeout, TimeUnit unit)
            throws InterruptedException, TimeoutException, GpuException {
        Objects.requireNonNull(unit, "unit");
        checkValidHandle();
        Outcome<T> cached = outcome;
        if (cached != null) {
            return cached.unwrap();
        }
        checkNotCancelledOrClaimed();
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        try {
            GpuBackendSpi bridge = Native.bridge();
            if (isDone()) {
                return synchronizeAndResolve(bridge).unwrap();
            }
            if (timeout <= 0L) {
                throw new TimeoutException("timed out waiting for GPU future");
            }
            long timeoutNanos = Math.max(1L, unit.toNanos(timeout));
            int awaitResult = bridge.futureAwait(handle, timeoutNanos);
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            if (awaitResult == GpuBackendSpi.AWAIT_COMPLETED) {
                return synchronizeAndResolve(bridge).unwrap();
            }
            if (awaitResult == GpuBackendSpi.AWAIT_TIMED_OUT) {
                throw new TimeoutException(
                        "timed out waiting for GPU future after " + timeout + " " + unit);
            }
            long deadline = System.nanoTime() + timeoutNanos;
            long parkNanos = 50_000L;
            while (!isDone()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0L) {
                    throw new TimeoutException(
                            "timed out waiting for GPU future after " + timeout + " " + unit);
                }
                LockSupport.parkNanos(Math.min(parkNanos, remaining));
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
                parkNanos = Math.min(parkNanos << 1, 2_000_000L);
            }
            return synchronizeAndResolve(bridge).unwrap();
        } finally {
            Reference.reachabilityFence(this);
        }
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        checkValidHandle();
        if (outcome != null || state != STATE_ACTIVE) {
            return false;
        }
        try {
            GpuBackendSpi bridge = Native.bridge();
            synchronized (this) {
                if (outcome != null || state != STATE_ACTIVE) {
                    return false;
                }
                int rc = bridge.futureCancel(handle, mayInterruptIfRunning);
                if (rc != 1) {
                    return false;
                }
                if (!STATE_UPDATER.compareAndSet(this, STATE_ACTIVE, STATE_CANCELLED)) {
                    return false;
                }
                outcome = Outcome.error(new GpuException("future cancelled"));
            }
            cleanable.cleanThrowing();
            return true;
        } finally {
            Reference.reachabilityFence(this);
        }
    }

    @Override
    public boolean isCancelled() {
        return state == STATE_CANCELLED;
    }

    @Override
    public T getNow(T valueIfAbsent) {
        Outcome<T> cached = outcome;
        if (cached != null) {
            return cached.unwrap();
        }
        checkNotCancelledOrClaimed();
        if (!isDone()) {
            return valueIfAbsent;
        }
        try {
            return get();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return valueIfAbsent;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <E> GpuArray<E> getAsGpuArray() throws GpuException, InterruptedException {
        checkValidHandle();
        if (state == STATE_ARRAY_CLAIMED) {
            throw new GpuException("future GPU array result was already claimed");
        }
        if (state == STATE_CANCELLED) {
            throw new GpuException("future cancelled");
        }
        Outcome<T> cached = outcome;
        if (cached != null) {
            T val = cached.unwrap();
            if (!ImmediateFutures.isWrappableArray(val)) {
                throw new GpuException(
                        "future result cannot be wrapped as GpuArray: "
                                + (val == null ? "null" : val.getClass().getName()));
            }
            claimOnceOrThrow();
            return ImmediateFutures.wrapArrayValue(val);
        }
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        try {
            GpuBackendSpi bridge = Native.bridge();
            bridge.futureSynchronize(handle);
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            int status = bridge.futureStatus(handle);
            if (status == 2) {
                Outcome<T> failed = resolveFailedOutcome(bridge);
                throw failed.error;
            }
            claimOnceOrThrow();
            GpuArray<?> zeroCopy = null;
            boolean supported = true;
            try {
                zeroCopy = bridge.futureGetResultAsGpuArray(handle);
            } catch (UnsupportedOperationException uoe) {
                supported = false;
            }
            if (zeroCopy != null) {
                return (GpuArray<E>) zeroCopy;
            }
            T fallback = resolveAfterCompletion(bridge, true).unwrap();
            if (supported && fallback == null) {
                STATE_UPDATER.compareAndSet(this, STATE_ARRAY_CLAIMED, STATE_ACTIVE);
                throw new GpuException("future did not produce a GPU device array result");
            }
            if (!ImmediateFutures.isWrappableArray(fallback)) {
                STATE_UPDATER.compareAndSet(this, STATE_ARRAY_CLAIMED, STATE_ACTIVE);
                throw new GpuException(
                        "future result cannot be wrapped as GpuArray: "
                                + (fallback == null ? "null" : fallback.getClass().getName()));
            }
            return ImmediateFutures.wrapArrayValue(fallback);
        } finally {
            Reference.reachabilityFence(this);
        }
    }

    @Override
    public <U> GpuFuture<U> thenApplyGpu(GpuFunction<? super T, ? extends U> fn) {
        Objects.requireNonNull(fn, "fn");
        if (state == STATE_CANCELLED) {
            return ImmediateFutures.failed(new GpuException("future cancelled"));
        }
        Outcome<T> cached = outcome;
        if (cached != null && cached.error != null) {
            return ImmediateFutures.failed(cached.error);
        }

        try {
            GpuBackendSpi bridge = Native.bridge();
            GpuFuture<U> chained = null;
            try {
                chained = bridge.submitWithArg(execHandle, fn, this);
            } catch (UnsatisfiedLinkError | UnsupportedOperationException ignored) {
                // Backend does not support on-device lambda chaining; fall back to host below.
            }
            if (chained != null) {
                if (chained instanceof GpuFutureImpl<U> impl) {
                    impl.bindExecutorIfUnset(execHandle);
                    if (bridge.futureStatus(impl.handle()) != 2) {
                        return impl;
                    }
                    int errorKind = bridge.futureErrorKind(impl.handle());
                    if (errorKind == GpuBackendSpi.ERROR_KIND_OUT_OF_MEMORY
                            || errorKind == GpuBackendSpi.ERROR_KIND_LAUNCH) {
                        return impl;
                    }
                    impl.cleanable.cleanThrowing();
                } else {
                    return chained;
                }
            }
        } finally {
            Reference.reachabilityFence(this);
        }

        T value;
        try {
            value = get();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            GpuException ge = new GpuException("thenApplyGpu interrupted while resolving upstream", ie);
            LOG.log(System.Logger.Level.WARNING,
                    "thenApplyGpu: host-sync interrupted while resolving upstream", ie);
            return ImmediateFutures.failed(ge);
        } catch (GpuException ge) {
            LOG.log(System.Logger.Level.WARNING,
                    "thenApplyGpu: upstream future failed during host sync", ge);
            return ImmediateFutures.failed(ge);
        } catch (Exception e) {
            GpuException ge = new GpuException("thenApplyGpu host-sync failed", e);
            LOG.log(System.Logger.Level.WARNING,
                    "thenApplyGpu: unexpected error during host sync", e);
            return ImmediateFutures.failed(ge);
        }

        LOG.log(System.Logger.Level.WARNING,
                "thenApplyGpu: {0} could not run on GPU; executing on host",
                fn.getClass().getName());
        return ImmediateFutures.apply(fn, value);
    }

    @Override
    public <U> GpuFuture<U> thenApplyGpu(
            String className,
            String methodName,
            String descriptor,
            Object... extraArgs) {
        KernelArgs.checkTarget(className, methodName, descriptor);
        Object[] checked = KernelArgs.checkArgs(extraArgs);
        checkValidHandle();
        if (state == STATE_CANCELLED) {
            return ImmediateFutures.failed(new GpuException("future cancelled"));
        }
        Outcome<T> cached = outcome;
        if (cached != null && cached.error != null) {
            return ImmediateFutures.failed(cached.error);
        }
        try {
            long nextHandle = Native.bridge().futureThenApplyGpu(
                    handle, className, methodName, descriptor, checked);
            return Submissions.future(execHandle, nextHandle);
        } finally {
            Reference.reachabilityFence(this);
        }
    }

    @Override
    public CompletableFuture<T> toCompletableFuture() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return get();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new GpuException("interrupted", ie);
            }
        });
    }

    @Override
    public CompletableFuture<T> toCompletableFuture(Executor exec) {
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

    long handle() {
        return handle;
    }

    private void checkNotCancelledOrClaimed() {
        int s = state;
        if (s == STATE_CANCELLED) {
            throw new GpuException("future cancelled");
        }
        if (s == STATE_ARRAY_CLAIMED && outcome == null) {
            throw new GpuException("future GPU array result was already claimed");
        }
    }

    private void claimOnceOrThrow() {
        if (!STATE_UPDATER.compareAndSet(this, STATE_ACTIVE, STATE_ARRAY_CLAIMED)) {
            if (state == STATE_CANCELLED) {
                throw new GpuException("future cancelled");
            }
            throw new GpuException("future GPU array result was already claimed");
        }
    }

    private Outcome<T> synchronizeAndResolve(GpuBackendSpi bridge) throws InterruptedException {
        checkNotCancelledOrClaimed();
        bridge.futureSynchronize(handle);
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        return resolveAfterCompletion(bridge);
    }

    private Outcome<T> resolveAfterCompletion(GpuBackendSpi bridge) {
        return resolveAfterCompletion(bridge, false);
    }

    private Outcome<T> resolveAfterCompletion(GpuBackendSpi bridge, boolean allowClaimed) {
        Outcome<T> cached = outcome;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (outcome != null) {
                return outcome;
            }
            if (state == STATE_CANCELLED) {
                GpuException ge = new GpuException("future cancelled");
                outcome = Outcome.error(ge);
                return outcome;
            }
            if (!allowClaimed && state == STATE_ARRAY_CLAIMED) {
                throw new GpuException("future GPU array result was already claimed");
            }
            int status = bridge.futureStatus(handle);
            if (status == 0) {
                throw new GpuException(
                        "futureSynchronize returned while future still running (status=0)");
            }
            if (status == 2) {
                return resolveFailedOutcome(bridge);
            }
            @SuppressWarnings("unchecked")
            T value = (T) bridge.futureGetResult(handle);
            Outcome<T> resolved = Outcome.value(value);
            outcome = resolved;
            return resolved;
        }
    }

    private Outcome<T> resolveFailedOutcome(GpuBackendSpi bridge) {
        synchronized (this) {
            if (outcome != null) {
                return outcome;
            }
            GpuException ex = GpuErrors.fromHandle(bridge, handle);
            Outcome<T> failed = Outcome.error(ex);
            outcome = failed;
            return failed;
        }
    }

    private static final class Outcome<T> {
        final T value;
        final GpuException error;

        private Outcome(T value, GpuException error) {
            this.value = value;
            this.error = error;
        }

        static <T> Outcome<T> value(T value) {
            return new Outcome<>(value, null);
        }

        static <T> Outcome<T> error(GpuException error) {
            return new Outcome<>(null, error);
        }

        T unwrap() throws GpuException {
            if (error != null) {
                throw error;
            }
            return value;
        }
    }
}
