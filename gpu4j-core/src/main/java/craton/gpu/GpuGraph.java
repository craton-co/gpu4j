// SPDX-License-Identifier: Apache-2.0
// Copyright 2025-2026 Craton Software Company (https://craton.com.ar)

package craton.gpu;

import craton.gpu.internal.Native;
import craton.gpu.internal.StreamCleaner;
import craton.gpu.internal.Submissions;

import java.lang.ref.Reference;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Captured executable GPU computation graph bound to a {@link GpuExecutor} and managed by a
 * {@link java.lang.ref.Cleaner} safety net.
 *
 * <p>Instances are thread-safe with respect to {@link #close()}: replay and inspection methods
 * acquire a shared read lock while {@link #close()} acquires the exclusive write lock so the
 * underlying native graph handle cannot be freed mid-replay.</p>
 *
 * @since 0.1.0
 */
public final class GpuGraph implements AutoCloseable {

    private final GpuExecutor executor;
    private final long handle;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final StreamCleaner.Registration cleanable;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private GpuGraph(GpuExecutor executor, long handle) {
        this.executor = Objects.requireNonNull(executor, "executor");
        if (handle == 0L) {
            throw new GpuException("invalid graph handle: 0");
        }
        this.handle = handle;
        this.cleanable = StreamCleaner.register(this, handle, StreamCleaner.Kind.GRAPH);
    }

    /**
     * Wraps a captured native graph handle owned by {@code executor}.
     *
     * @param executor owning executor
     * @param handle   non-zero native graph handle
     * @return managed {@code GpuGraph} instance
     * @throws NullPointerException if {@code executor} is {@code null}
     * @throws GpuException         if {@code handle == 0L}
     */
    public static GpuGraph fromNativeHandle(GpuExecutor executor, long handle) {
        return new GpuGraph(executor, handle);
    }

    /**
     * Returns the native handle identifying this captured graph.
     *
     * @return non-zero native graph handle
     * @throws IllegalStateException if this graph has been closed
     */
    public long handle() {
        lock.readLock().lock();
        try {
            ensureOpen();
            return handle;
        } finally {
            lock.readLock().unlock();
            Reference.reachabilityFence(this);
        }
    }

    /**
     * Returns the {@link GpuExecutor} that captured this graph.
     *
     * @return owning executor
     */
    public GpuExecutor executor() {
        return executor;
    }

    /**
     * Returns the number of nodes recorded in this graph, or {@code 0} if the backend does not
     * report node counts.
     *
     * @return non-negative node count
     * @throws IllegalStateException if this graph has been closed
     */
    public int nodeCount() {
        lock.readLock().lock();
        try {
            ensureOpen();
            return Math.max(0, Native.bridge().graphNodeCount(handle));
        } finally {
            lock.readLock().unlock();
            Reference.reachabilityFence(this);
        }
    }

    /**
     * Replays this graph on its owning {@link GpuExecutor} and returns the raw submission handle.
     *
     * @return non-zero submission handle
     * @throws IllegalStateException if this graph has been closed
     * @throws GpuException          if the executor is closed or replay fails
     */
    public long replay() {
        return executor.replay(this);
    }

    /**
     * Replays this graph asynchronously on its owning {@link GpuExecutor} and wraps the launch in
     * a {@link GpuFuture}.
     *
     * @return future tracking completion of the graph replay
     * @throws IllegalStateException if this graph has been closed
     * @throws GpuException          if the executor is closed or replay fails
     */
    public GpuFuture<Void> replayAsync() {
        long execHandle = executor.handleForDispatch();
        long subHandle = replay();
        return Submissions.future(execHandle, subHandle);
    }

    /**
     * Begins a parameter-update replay session for this graph on its owning {@link GpuExecutor}.
     *
     * @throws IllegalStateException if this graph has been closed
     * @throws GpuException          if the executor is closed or starting replay fails
     */
    public void beginReplay() {
        executor.beginReplay(this);
    }

    /**
     * Finalizes and launches an active replay session started with {@link #beginReplay()}.
     *
     * @return non-zero submission handle
     * @throws IllegalStateException if this graph has been closed
     * @throws GpuException          if the executor is closed or ending replay fails
     */
    public long endReplay() {
        lock.readLock().lock();
        try {
            ensureOpen();
            return executor.endReplay();
        } finally {
            lock.readLock().unlock();
            Reference.reachabilityFence(this);
        }
    }

    /**
     * Returns {@code true} if {@link #close()} has been invoked on this graph.
     *
     * @return {@code true} when closed
     */
    public boolean isClosed() {
        return closed.get();
    }

    /**
     * Releases the native graph handle. Idempotent across repeated calls.
     *
     * @throws GpuException if releasing the native handle fails
     */
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

    ReentrantReadWriteLock.ReadLock readLock() {
        return lock.readLock();
    }

    void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("graph closed");
        }
    }
}
