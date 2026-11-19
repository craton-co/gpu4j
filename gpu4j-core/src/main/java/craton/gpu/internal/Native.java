// SPDX-License-Identifier: Apache-2.0
// Copyright 2025-2026 Craton Software Company (https://craton.com.ar)

package craton.gpu.internal;

import craton.gpu.GpuArray;
import craton.gpu.GpuCallable;
import craton.gpu.GpuExecutor;
import craton.gpu.GpuFuture;
import craton.gpu.GpuRunnable;
import craton.gpu.GpuStream;
import craton.gpu.testing.GpuBackendSpi;

/**
 * Low-level native bridge declarations bound by CratonVM's {@code craton_gpu} builtin registry.
 * Callers inside {@code craton.gpu} invoke {@link #bridge()} rather than calling these static
 * natives directly so unit tests can substitute a mock {@link GpuBackendSpi}.
 */
public final class Native {

    private static final System.Logger LOG = System.getLogger(Native.class.getName());
    private static final NativeImpl DEFAULT = new NativeImpl();
    private static volatile GpuBackendSpi BRIDGE = DEFAULT;
    private static final Object SWAP_LOCK = new Object();

    private Native() {
    }

    /** Returns the active {@link GpuBackendSpi} (defaults to {@link NativeImpl}). */
    public static GpuBackendSpi bridge() {
        return BRIDGE;
    }

    /** Installs a replacement bridge for internal tests; pass {@code null} to restore default. */
    static void setBridge(GpuBackendSpi replacement) {
        installForTesting(replacement);
    }

    /**
     * Module-internal hook invoked by {@link craton.gpu.testing.GpuTestBridge}.
     *
     * @param replacement replacement backend, or {@code null} for default
     * @return the previously installed non-default override, or {@code null} if default was active
     */
    public static GpuBackendSpi installForTesting(GpuBackendSpi replacement) {
        synchronized (SWAP_LOCK) {
            GpuBackendSpi current = BRIDGE;
            GpuBackendSpi previousOverride = (current == DEFAULT) ? null : current;
            BRIDGE = replacement != null ? replacement : DEFAULT;
            if (replacement != null && LOG.isLoggable(System.Logger.Level.DEBUG)) {
                LOG.log(System.Logger.Level.DEBUG, "Installed test GpuBackendSpi: {0}",
                        replacement.getClass().getName());
            }
            return previousOverride;
        }
    }

    /**
     * Restores {@code previous} if {@code expected} is still the active backend.
     */
    public static void restoreForTesting(GpuBackendSpi expected, GpuBackendSpi previous) {
        synchronized (SWAP_LOCK) {
            if (BRIDGE == expected) {
                BRIDGE = previous != null ? previous : DEFAULT;
            }
        }
    }

    // ---- executors & streams -----------------------------------------------

    public static native GpuExecutor openExecutor(int deviceOrdinal);

    public static native <R> GpuFuture<R> submit(long execHandle, GpuCallable<R> fn);

    public static native GpuFuture<Void> launch(long execHandle, GpuRunnable fn);

    public static native <R> GpuFuture<R> submitMethod(
            long execHandle,
            String className,
            String methodName,
            String descriptor,
            Object[] args);

    public static native <R> GpuFuture<R> submitWithArg(
            long execHandle,
            Object fn,
            Object arg);

    public static native <R> GpuFuture<R> submitWithArgs(
            long execHandle,
            Object fn,
            Object[] args);

    public static native long submitMethodHandle(
            long execHandle,
            String className,
            String methodName,
            String descriptor,
            Object[] args);

    public static native GpuStream newStream(long execHandle);

    public static native void closeStream(long streamHandle);

    public static native long streamSubmitMethod(
            long streamHandle,
            String className,
            String methodName,
            String descriptor,
            Object[] args);

    public static native void streamSynchronize(long streamHandle);

    // ---- futures -----------------------------------------------------------

    public static native long futureThenApplyGpu(
            long futureHandle,
            String className,
            String methodName,
            String descriptor,
            Object[] args);

    public static native int futureStatus(long futureHandle);

    public static native boolean futureIsDone(long futureHandle);

    public static native void futureSynchronize(long futureHandle);

    public static native Object futureGetResult(long futureHandle);

    public static native GpuArray<?> futureGetResultAsGpuArray(long futureHandle);

    public static native String futureGetErrorMessage(long futureHandle);

    public static native int futureCancel(long futureHandle, boolean mayInterruptIfRunning);

    public static native String futureGetError(long futureHandle);

    public static native int futureErrorKind(long futureHandle);

    public static native int futureAwait(long futureHandle, long timeoutNanos);

    // ---- arrays ------------------------------------------------------------

    public static native long arrayWrapInt(int[] arr);

    public static native long arrayWrapLong(long[] arr);

    public static native long arrayWrapShort(short[] arr);

    public static native long arrayWrapFloat(float[] arr);

    public static native long arrayWrapDouble(double[] arr);

    public static native long arrayAllocateInt(int length);

    public static native long arrayAllocateLong(int length);

    public static native long arrayAllocateShort(int length);

    public static native long arrayAllocateFloat(int length);

    public static native long arrayAllocateDouble(int length);

    public static native Object arrayToHost(long arrayHandle);

    public static native boolean arrayToHostInto(long arrayHandle, Object dest);

    public static native boolean arrayIsResident(long arrayHandle);

    public static native boolean arrayCopyFromHost(long arrayHandle, Object src);

    // ---- BLAS --------------------------------------------------------------

    public static native long gemm(
            long aHandle,
            long bHandle,
            long cHandle,
            int m,
            int n,
            int k,
            boolean half,
            boolean transA,
            boolean transB,
            long streamHandle);

    // ---- cleanup -----------------------------------------------------------

    public static native void releaseFuture(long futureHandle);

    public static native void releaseArray(long arrayHandle);

    public static native void releaseExecutor(long execHandle);

    // ---- device discovery --------------------------------------------------

    public static native int deviceCount();

    public static native String deviceName(int ordinal);

    public static native long deviceTotalMemory(int ordinal);

    public static native int deviceComputeCapability(int ordinal);

    // ---- CUDA graph capture & replay ---------------------------------------

    public static native boolean graphBeginCapture(long execHandle);

    public static native long graphEndCapture(long execHandle);

    public static native long graphReplay(long execHandle, long graphHandle);

    public static native boolean graphBeginReplay(long execHandle, long graphHandle);

    public static native long graphEndReplay(long execHandle);

    public static native int graphNodeCount(long graphHandle);

    public static native void releaseGraph(long graphHandle);
}
