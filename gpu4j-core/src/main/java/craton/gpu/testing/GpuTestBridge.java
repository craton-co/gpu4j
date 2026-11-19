// SPDX-License-Identifier: Apache-2.0
// Copyright 2025-2026 Craton Software Company (https://craton.com.ar)

package craton.gpu.testing;

import craton.gpu.internal.Native;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Public test hook for substituting the active {@link GpuBackendSpi} in unit tests and simulations
 * without exposing {@code craton.gpu.internal} packages.
 *
 * @since 0.1.0
 */
public final class GpuTestBridge {

    private GpuTestBridge() {
    }

    /**
     * Installs {@code backend} as the active {@link GpuBackendSpi} until the returned handle is
     * closed (or {@link #restoreDefault()} is called). Nested installations restore the previously
     * active backend on close.
     *
     * @param backend non-null replacement backend
     * @return auto-closeable scope handle that restores the prior backend when closed
     */
    public static Installed install(GpuBackendSpi backend) {
        Objects.requireNonNull(backend, "backend");
        GpuBackendSpi previous = Native.installForTesting(backend);
        return new Installed(backend, previous);
    }

    /**
     * Unconditionally restores the default CratonVM JNI-backed {@link GpuBackendSpi}.
     */
    public static void restoreDefault() {
        Native.installForTesting(null);
    }

    /**
     * Returns the currently active {@link GpuBackendSpi}.
     *
     * @return active backend instance (never {@code null})
     */
    public static GpuBackendSpi current() {
        return Native.bridge();
    }

    /**
     * Try-with-resources scope handle that restores the prior backend when closed.
     */
    public static final class Installed implements AutoCloseable {

        private final GpuBackendSpi installed;
        private final GpuBackendSpi previous;
        private final AtomicBoolean closed = new AtomicBoolean();

        Installed(GpuBackendSpi installed, GpuBackendSpi previous) {
            this.installed = installed;
            this.previous = previous;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                Native.restoreForTesting(installed, previous);
            }
        }
    }
}
