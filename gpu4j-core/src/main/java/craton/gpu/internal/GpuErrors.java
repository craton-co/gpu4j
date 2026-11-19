// SPDX-License-Identifier: Apache-2.0
// Copyright 2025-2026 Craton Software Company (https://craton.com.ar)

package craton.gpu.internal;

import craton.gpu.GpuCompileException;
import craton.gpu.GpuException;
import craton.gpu.GpuLaunchException;
import craton.gpu.GpuOutOfMemoryException;
import craton.gpu.testing.GpuBackendSpi;

import java.util.Locale;

/**
 * Classifies native error messages and structured {@code GpuBackendSpi.ERROR_KIND_*} tags into the
 * typed {@link GpuException} hierarchy.
 */
public final class GpuErrors {

    private static final String[] OOM_MARKERS = {
        "out of memory",
        "outofmemory",
        "cuda_error_out_of_memory",
        "cudaerrormemoryallocation",
        "insufficient device memory",
    };

    private static final String[] COMPILE_MARKERS = {
        "ptx ",
        "invalid ptx",
        "nvrtc",
        "compile",
        "compilation",
        "cuda_error_invalid_ptx",
        "cuda_error_invalid_source",
        "jit-cuda",
        "jit_cuda",
        "lowering failed",
    };

    private static final String[] LAUNCH_MARKERS = {
        "kernel launch",
        "launch failed",
        "launch timeout",
        "invalid configuration",
        "illegal address",
        "misaligned address",
        "cuda_error_launch_failed",
        "cuda_error_launch_timeout",
        "too many resources requested",
    };

    private GpuErrors() {
    }

    /**
     * Resolves the error string for {@code handle}, preferring {@link GpuBackendSpi#futureGetError}
     * and falling back to {@link GpuBackendSpi#futureGetErrorMessage}.
     */
    public static String resolveErrorMessage(GpuBackendSpi bridge, long handle) {
        String err = bridge.futureGetError(handle);
        if (err == null || err.isBlank()) {
            err = bridge.futureGetErrorMessage(handle);
        }
        return err;
    }

    /**
     * Classifies a native failure message by phrase inspection.
     */
    public static GpuException fromNative(String msg, String fallback, int code) {
        if (msg == null || msg.isBlank()) {
            return new GpuException(fallback, code);
        }
        String lower = msg.toLowerCase(Locale.ROOT);
        if (containsAny(lower, OOM_MARKERS)) {
            return new GpuOutOfMemoryException(msg, code);
        }
        if (containsAny(lower, COMPILE_MARKERS)) {
            return new GpuCompileException(msg, code, null);
        }
        if (containsAny(lower, LAUNCH_MARKERS)) {
            return new GpuLaunchException(msg, code, null, null);
        }
        return new GpuException(msg, code);
    }

    /**
     * Classifies a native failure using an explicit {@link GpuBackendSpi#ERROR_KIND_UNKNOWN} kind
     * code, falling back to {@link #fromNative(String, String, int)} when unknown.
     */
    public static GpuException fromNative(String msg, String fallback, int code, int kind) {
        String resolved = (msg == null || msg.isBlank()) ? fallback : msg;
        return switch (kind) {
            case GpuBackendSpi.ERROR_KIND_COMPILE -> new GpuCompileException(resolved, code, null);
            case GpuBackendSpi.ERROR_KIND_OUT_OF_MEMORY -> new GpuOutOfMemoryException(resolved, code);
            case GpuBackendSpi.ERROR_KIND_LAUNCH -> new GpuLaunchException(resolved, code, null, null);
            default -> fromNative(msg, fallback, code);
        };
    }

    /**
     * Classifies a failed submission handle by querying {@link GpuBackendSpi#futureErrorKind(long)}.
     */
    public static GpuException fromHandle(GpuBackendSpi bridge, long handle, String msg) {
        return fromNative(msg, "kernel failed", -1, bridge.futureErrorKind(handle));
    }

    /**
     * Resolves and classifies a failed submission handle using both primary and fallback error
     * channels.
     */
    public static GpuException fromHandle(GpuBackendSpi bridge, long handle) {
        return fromHandle(bridge, handle, resolveErrorMessage(bridge, handle));
    }

    /**
     * Classifies a native failure message with unknown native error code ({@code -1}).
     */
    public static GpuException fromNative(String msg, String fallback) {
        return fromNative(msg, fallback, -1);
    }

    private static boolean containsAny(String haystack, String[] markers) {
        for (String marker : markers) {
            if (haystack.contains(marker)) {
                return true;
            }
        }
        return false;
    }
}
