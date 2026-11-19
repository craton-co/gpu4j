// SPDX-License-Identifier: Apache-2.0
// Copyright 2025-2026 Craton Software Company (https://craton.com.ar)

package craton.gpu.internal;

import craton.gpu.GpuException;
import craton.gpu.GpuStream;
import craton.gpu.testing.GpuBackendSpi;

import java.lang.ref.Reference;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Native-backed {@link GpuStream} implementation managed by {@link StreamCleaner}.
 */
final class GpuStreamImpl implements GpuStream {

    private final long handle;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final StreamCleaner.Registration cleanable;
    private final ConcurrentLinkedQueue<Long> pending = new ConcurrentLinkedQueue<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    GpuStreamImpl(long handle) {
        if (handle == 0L) {
            throw new IllegalArgumentException("invalid stream handle: 0");
        }
        this.handle = handle;
        this.cleanable = StreamCleaner.register(this, handle, StreamCleaner.Kind.STREAM);
    }

    @Override
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

    @Override
    public void dispatch(
            String className,
            String methodName,
            String descriptor,
            Object... args) {
        KernelArgs.checkTarget(className, methodName, descriptor);
        Object[] checked = KernelArgs.checkArgs(args);
        lock.readLock().lock();
        try {
            ensureOpen();
            long subHandle =
                    Native.bridge().streamSubmitMethod(handle, className, methodName, descriptor, checked);
            if (subHandle == 0L) {
                throw new GpuException("stream dispatch was not recorded (handle 0)");
            }
            pending.add(subHandle);
        } finally {
            lock.readLock().unlock();
            Reference.reachabilityFence(this);
        }
    }

    @Override
    public void synchronize() throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        lock.readLock().lock();
        try {
            ensureOpen();
            GpuBackendSpi bridge = Native.bridge();
            GpuException firstError = null;
            Long subHandle;
            while ((subHandle = pending.poll()) != null) {
                long h = subHandle;
                try {
                    bridge.futureSynchronize(h);
                    if (firstError == null && bridge.futureStatus(h) == 2) {
                        firstError = GpuErrors.fromHandle(bridge, h);
                    }
                } finally {
                    bridge.releaseFuture(h);
                }
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
            }
            // Always synchronize the underlying stream so stream work launched outside dispatch()
            // (such as GpuBlas.gemm(stream, ...)) is visible on return.
            bridge.streamSynchronize(handle);
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            if (firstError != null) {
                throw firstError;
            }
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
            GpuBackendSpi bridge = Native.bridge();
            Long remaining;
            while ((remaining = pending.poll()) != null) {
                bridge.releaseFuture(remaining);
            }
            cleanable.cleanThrowing();
        } finally {
            lock.writeLock().unlock();
            Reference.reachabilityFence(this);
        }
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("stream closed");
        }
    }
}
