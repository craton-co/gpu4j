// SPDX-License-Identifier: Apache-2.0
// Copyright 2025-2026 Craton Software Company (https://craton.com.ar)

package craton.gpu.internal;

import craton.gpu.GpuException;
import craton.gpu.testing.GpuBackendSpi;

import java.lang.ref.Cleaner;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Releases native handles owned by {@code GpuExecutor}, {@code GpuFuture}, {@code GpuStream},
 * {@code GpuArray}, and {@code GpuGraph} when their Java wrappers either call
 * {@link Registration#cleanThrowing()} explicitly or become phantom-reachable.
 */
public final class StreamCleaner {

    /** Kind of native resource being tracked. */
    public enum Kind { EXECUTOR, STREAM, FUTURE, ARRAY, GRAPH }

    /**
     * Handle returned by {@link #register(Object, long, Kind)}. Bundles the JDK
     * {@link Cleaner.Cleanable} safety net with an explicit-close entry point that surfaces native
     * release failures to the caller.
     */
    public static final class Registration {
        private final Cleaner.Cleanable cleanable;
        private final long handle;
        private final Kind kind;
        private final AtomicBoolean once;

        private Registration(
                Cleaner.Cleanable cleanable,
                long handle,
                Kind kind,
                AtomicBoolean once) {
            this.cleanable = cleanable;
            this.handle = handle;
            this.kind = kind;
            this.once = once;
        }

        /**
         * Deterministic close driven by an explicit caller (e.g. a {@code close()} method).
         * Releases the native handle at most once and propagates any native failure as a
         * {@link GpuException}.
         *
         * @throws GpuException if releasing the underlying native handle fails
         */
        public void cleanThrowing() {
            try {
                release(handle, kind, once, true);
            } finally {
                cleanable.clean();
            }
        }
    }

    private static void release(
            long handle,
            Kind kind,
            AtomicBoolean once,
            boolean propagate) {
        if (handle == 0L) {
            return;
        }
        if (!once.compareAndSet(false, true)) {
            return;
        }
        try {
            GpuBackendSpi b = Native.bridge();
            switch (kind) {
                case EXECUTOR -> b.releaseExecutor(handle);
                case STREAM -> b.closeStream(handle);
                case FUTURE -> b.releaseFuture(handle);
                case ARRAY -> b.releaseArray(handle);
                case GRAPH -> b.releaseGraph(handle);
            }
        } catch (Throwable t) {
            if (propagate) {
                if (t instanceof GpuException ge) {
                    throw ge;
                }
                throw new GpuException(
                        "gpu4j: release of " + kind + " handle " + handle + " failed", t);
            }
            try {
                logger.log(
                        System.Logger.Level.WARNING,
                        () -> "gpu4j: cleanup of " + kind + " handle " + handle + " failed",
                        t);
            } catch (Throwable ignored) {
                // Logger must never propagate out of Cleaner's worker.
            }
        }
    }

    private static final Cleaner CLEANER = Cleaner.create();

    private static final System.Logger logger = System.getLogger(StreamCleaner.class.getName());

    /**
     * Registers {@code owner} so that {@code handle} is released when either {@code owner} becomes
     * phantom-reachable or the returned {@link Registration#cleanThrowing()} is invoked.
     */
    public static Registration register(Object owner, long handle, Kind kind) {
        final long capturedHandle = handle;
        final Kind capturedKind = kind;
        final AtomicBoolean once = new AtomicBoolean(false);
        Runnable action = () -> release(capturedHandle, capturedKind, once, false);
        Cleaner.Cleanable cleanable = CLEANER.register(owner, action);
        return new Registration(cleanable, capturedHandle, capturedKind, once);
    }

    private StreamCleaner() {
    }
}
