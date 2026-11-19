// SPDX-License-Identifier: Apache-2.0
// Copyright 2025-2026 Craton Software Company (https://craton.com.ar)

package craton.gpu.internal;

import craton.gpu.GpuCallable;
import craton.gpu.GpuException;
import craton.gpu.GpuExecutor;
import craton.gpu.GpuFuture;
import craton.gpu.GpuGraph;
import craton.gpu.GpuRunnable;
import craton.gpu.GpuStream;
import craton.gpu.testing.GpuBackendSpi;

import java.lang.ref.Reference;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Native-backed {@link GpuExecutor} implementation managed by {@link StreamCleaner}.
 */
final class GpuExecutorImpl implements GpuExecutor {

    private final long handle;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final StreamCleaner.Registration cleanable;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    GpuExecutorImpl(long handle) {
        if (handle == 0L) {
            throw new GpuException("invalid executor handle: 0");
        }
        this.handle = handle;
        this.cleanable = StreamCleaner.register(this, handle, StreamCleaner.Kind.EXECUTOR);
    }

    long handle() {
        return handle;
    }

    @Override
    public <R> GpuFuture<R> submit(GpuCallable<R> task) {
        Objects.requireNonNull(task, "task");
        lock.readLock().lock();
        try {
            ensureOpen();
            GpuFuture<R> future = Native.bridge().submit(handle, task);
            if (future == null) {
                throw new GpuException("submit returned null GpuFuture");
            }
            return bindFuture(future);
        } finally {
            lock.readLock().unlock();
            Reference.reachabilityFence(this);
        }
    }

    @Override
    public GpuFuture<Void> launch(GpuRunnable task) {
        Objects.requireNonNull(task, "task");
        lock.readLock().lock();
        try {
            ensureOpen();
            GpuFuture<Void> future = Native.bridge().launch(handle, task);
            if (future == null) {
                throw new GpuException("launch returned null GpuFuture");
            }
            return bindFuture(future);
        } finally {
            lock.readLock().unlock();
            Reference.reachabilityFence(this);
        }
    }

    @Override
    public long handleForDispatch() {
        lock.readLock().lock();
        try {
            ensureOpen();
            return handle;
        } finally {
            lock.readLock().unlock();
            Reference.reachabilityFence(this);
        }
    }

    @Override
    public <R> GpuFuture<R> dispatchNamed(
            String className,
            String methodName,
            String descriptor,
            Object[] args) {
        KernelArgs.checkTarget(className, methodName, descriptor);
        Object[] checked = KernelArgs.checkArgs(args);
        lock.readLock().lock();
        try {
            ensureOpen();
            GpuFuture<R> future =
                    Native.bridge().submitMethod(handle, className, methodName, descriptor, checked);
            if (future == null) {
                throw new GpuException("submitMethod returned null GpuFuture");
            }
            return bindFuture(future);
        } finally {
            lock.readLock().unlock();
            Reference.reachabilityFence(this);
        }
    }

    @Override
    public long dispatchNamedHandle(
            String className,
            String methodName,
            String descriptor,
            Object[] args) {
        KernelArgs.checkTarget(className, methodName, descriptor);
        Object[] checked = KernelArgs.checkArgs(args);
        lock.readLock().lock();
        try {
            ensureOpen();
            long subHandle =
                    Native.bridge().submitMethodHandle(handle, className, methodName, descriptor, checked);
            if (subHandle == 0L) {
                throw new GpuException("submitMethodHandle was not recorded (handle 0)");
            }
            return subHandle;
        } finally {
            lock.readLock().unlock();
            Reference.reachabilityFence(this);
        }
    }

    @Override
    public void beginCapture() {
        lock.readLock().lock();
        try {
            ensureOpen();
            if (!Native.bridge().graphBeginCapture(handle)) {
                throw new GpuException("graph capture failed to start");
            }
        } finally {
            lock.readLock().unlock();
            Reference.reachabilityFence(this);
        }
    }

    @Override
    public GpuGraph endCapture() {
        lock.readLock().lock();
        try {
            ensureOpen();
            long graphHandle = Native.bridge().graphEndCapture(handle);
            if (graphHandle == 0L) {
                throw new GpuException("graph capture ended without a valid graph (handle 0)");
            }
            return GpuGraph.fromNativeHandle(this, graphHandle);
        } finally {
            lock.readLock().unlock();
            Reference.reachabilityFence(this);
        }
    }

    @Override
    public long replay(GpuGraph graph) {
        Objects.requireNonNull(graph, "graph");
        lock.readLock().lock();
        try {
            ensureOpen();
            long graphHandle = graph.handle();
            long subHandle = Native.bridge().graphReplay(handle, graphHandle);
            if (subHandle == 0L) {
                throw new GpuException("graph replay failed (handle 0)");
            }
            return subHandle;
        } finally {
            lock.readLock().unlock();
            Reference.reachabilityFence(graph);
            Reference.reachabilityFence(this);
        }
    }

    @Override
    public void beginReplay(GpuGraph graph) {
        Objects.requireNonNull(graph, "graph");
        lock.readLock().lock();
        try {
            ensureOpen();
            long graphHandle = graph.handle();
            if (!Native.bridge().graphBeginReplay(handle, graphHandle)) {
                throw new GpuException("graph beginReplay failed");
            }
        } finally {
            lock.readLock().unlock();
            Reference.reachabilityFence(graph);
            Reference.reachabilityFence(this);
        }
    }

    @Override
    public long endReplay() {
        lock.readLock().lock();
        try {
            ensureOpen();
            long subHandle = Native.bridge().graphEndReplay(handle);
            if (subHandle == 0L) {
                throw new GpuException("graph endReplay failed (handle 0)");
            }
            return subHandle;
        } finally {
            lock.readLock().unlock();
            Reference.reachabilityFence(this);
        }
    }

    @Override
    public void awaitSubmission(long submissionHandle) throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        lock.readLock().lock();
        try {
            ensureOpen();
            if (submissionHandle == 0L) {
                return;
            }
            GpuBackendSpi bridge = Native.bridge();
            bridge.futureSynchronize(submissionHandle);
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            int status = bridge.futureStatus(submissionHandle);
            if (status == 2) {
                throw GpuErrors.fromHandle(bridge, submissionHandle);
            }
        } finally {
            lock.readLock().unlock();
            Reference.reachabilityFence(this);
        }
    }

    @Override
    public void releaseSubmission(long submissionHandle) {
        lock.readLock().lock();
        try {
            ensureOpen();
            if (submissionHandle != 0L) {
                Native.bridge().releaseFuture(submissionHandle);
            }
        } finally {
            lock.readLock().unlock();
            Reference.reachabilityFence(this);
        }
    }

    @Override
    public GpuStream newStream() {
        lock.readLock().lock();
        try {
            ensureOpen();
            GpuStream stream = Native.bridge().newStream(handle);
            if (stream == null) {
                throw new GpuException("newStream returned null GpuStream");
            }
            return stream;
        } finally {
            lock.readLock().unlock();
            Reference.reachabilityFence(this);
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        lock.writeLock().lock();
        try {
            cleanable.cleanThrowing();
        } finally {
            lock.writeLock().unlock();
            Reference.reachabilityFence(this);
        }
    }

    private <R> GpuFuture<R> bindFuture(GpuFuture<R> future) {
        if (future instanceof GpuFutureImpl<R> impl) {
            impl.bindExecutorIfUnset(handle);
        }
        return future;
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new GpuException("executor closed");
        }
    }
}
