// SPDX-License-Identifier: Apache-2.0
// Copyright 2025-2026 Craton Software Company (https://craton.com.ar)

package craton.gpu.testing;

import craton.gpu.GpuArray;
import craton.gpu.GpuCallable;
import craton.gpu.GpuExecutor;
import craton.gpu.GpuFuture;
import craton.gpu.GpuRunnable;
import craton.gpu.GpuStream;

/**
 * Service-provider test seam representing the low-level GPU runtime operations delegated by
 * {@code craton.gpu}.
 *
 * <p>Unit tests and standalone simulations on stock JVMs can implement or mock this interface and
 * install it via {@link GpuTestBridge#install(GpuBackendSpi)} without exposing internal runtime
 * classes.</p>
 *
 * @since 0.1.0
 */
public interface GpuBackendSpi {

    /** Unclassified native error kind. */
    int ERROR_KIND_UNKNOWN = 0;

    /** Kernel compilation / PTX / lowering failure kind. */
    int ERROR_KIND_COMPILE = 1;

    /** Device out-of-memory failure kind. */
    int ERROR_KIND_OUT_OF_MEMORY = 2;

    /** Kernel launch or device runtime failure kind. */
    int ERROR_KIND_LAUNCH = 3;

    /** Timed native wait is not supported by this backend; caller should poll. */
    int AWAIT_UNSUPPORTED = 0;

    /** Timed native wait observed completion before timeout. */
    int AWAIT_COMPLETED = 1;

    /** Timed native wait expired before completion. */
    int AWAIT_TIMED_OUT = 2;

    /**
     * Opens an executor bound to {@code deviceOrdinal}.
     *
     * @param deviceOrdinal non-negative zero-based GPU ordinal
     * @return opened executor, or {@code null} on failure
     */
    GpuExecutor openExecutor(int deviceOrdinal);

    /**
     * Submits a value-returning callable task on {@code execHandle}.
     *
     * @param execHandle native executor handle
     * @param fn         task to execute
     * @param <R>        result type
     * @return future tracking completion
     */
    <R> GpuFuture<R> submit(long execHandle, GpuCallable<R> fn);

    /**
     * Launches a void runnable task on {@code execHandle}.
     *
     * @param execHandle native executor handle
     * @param fn         task to execute
     * @return future tracking completion
     */
    GpuFuture<Void> launch(long execHandle, GpuRunnable fn);

    /**
     * Dispatches a static kernel method identified by JVM internal class name, method name, and
     * descriptor.
     *
     * @param execHandle native executor handle
     * @param className  slash-delimited or binary class name
     * @param methodName target static method name
     * @param descriptor JVM method descriptor
     * @param args       validated kernel arguments
     * @param <R>        result type
     * @return future tracking completion
     */
    <R> GpuFuture<R> submitMethod(
            long execHandle,
            String className,
            String methodName,
            String descriptor,
            Object[] args);

    /**
     * Submits a single-argument continuation (such as {@link craton.gpu.GpuFunction}) chained to
     * {@code arg}.
     *
     * @param execHandle native executor handle (or {@code 0L} when unspecified)
     * @param fn         continuation function or lambda
     * @param arg        upstream argument or future
     * @param <R>        result type
     * @return chained future, or {@code null} when on-device chaining is unavailable
     */
    <R> GpuFuture<R> submitWithArg(long execHandle, Object fn, Object arg);

    /**
     * Submits a multi-argument lambda or continuation on {@code execHandle}.
     *
     * @param execHandle native executor handle
     * @param fn         target lambda
     * @param args       arguments
     * @param <R>        result type
     * @return future tracking completion, or {@code null} when unsupported
     */
    default <R> GpuFuture<R> submitWithArgs(long execHandle, Object fn, Object[] args) {
        return null;
    }

    /**
     * Dispatches a named static kernel method and returns its raw native submission handle.
     *
     * @param execHandle native executor handle
     * @param className  target class name
     * @param methodName target method name
     * @param descriptor JVM method descriptor
     * @param args       validated kernel arguments
     * @return non-zero submission handle, or {@code 0L} if not recorded
     */
    default long submitMethodHandle(
            long execHandle,
            String className,
            String methodName,
            String descriptor,
            Object[] args) {
        throw new UnsupportedOperationException("this backend does not implement submitMethodHandle");
    }

    /**
     * Begins CUDA graph capture on {@code execHandle}.
     *
     * @param execHandle native executor handle
     * @return {@code true} if capture started
     */
    default boolean graphBeginCapture(long execHandle) {
        throw new UnsupportedOperationException("this backend does not implement graphBeginCapture");
    }

    /**
     * Ends CUDA graph capture on {@code execHandle} and returns the captured graph handle.
     *
     * @param execHandle native executor handle
     * @return non-zero native graph handle, or {@code 0L} on failure
     */
    default long graphEndCapture(long execHandle) {
        throw new UnsupportedOperationException("this backend does not implement graphEndCapture");
    }

    /**
     * Launches {@code graphHandle} on {@code execHandle} and returns a submission handle.
     *
     * @param execHandle  native executor handle
     * @param graphHandle native graph handle
     * @return non-zero submission handle, or {@code 0L} on failure
     */
    default long graphReplay(long execHandle, long graphHandle) {
        throw new UnsupportedOperationException("this backend does not implement graphReplay");
    }

    /**
     * Begins a multi-step replay update session for {@code graphHandle} on {@code execHandle}.
     *
     * @param execHandle  native executor handle
     * @param graphHandle native graph handle
     * @return {@code true} if replay session started
     */
    default boolean graphBeginReplay(long execHandle, long graphHandle) {
        throw new UnsupportedOperationException("this backend does not implement graphBeginReplay");
    }

    /**
     * Finalizes and launches an active replay session on {@code execHandle}.
     *
     * @param execHandle native executor handle
     * @return non-zero submission handle, or {@code 0L} on failure
     */
    default long graphEndReplay(long execHandle) {
        throw new UnsupportedOperationException("this backend does not implement graphEndReplay");
    }

    /**
     * Returns the number of nodes recorded in {@code graphHandle}.
     *
     * @param graphHandle native graph handle
     * @return node count, or negative if unavailable
     */
    default int graphNodeCount(long graphHandle) {
        throw new UnsupportedOperationException("this backend does not implement graphNodeCount");
    }

    /**
     * Releases native resources associated with {@code graphHandle}.
     *
     * @param graphHandle native graph handle
     */
    default void releaseGraph(long graphHandle) {
        throw new UnsupportedOperationException("this backend does not implement releaseGraph");
    }

    /**
     * Creates a new asynchronous stream bound to {@code execHandle}.
     *
     * @param execHandle native executor handle
     * @return new stream instance
     */
    GpuStream newStream(long execHandle);

    /**
     * Destroys the native stream identified by {@code streamHandle}.
     *
     * @param streamHandle native stream handle
     */
    void closeStream(long streamHandle);

    /**
     * Queries completion status of {@code futureHandle} ({@code 0} = running, {@code 1} = completed,
     * {@code 2} = failed).
     *
     * @param futureHandle native submission handle
     * @return status code
     */
    int futureStatus(long futureHandle);

    /**
     * Returns {@code true} when {@code futureHandle} has completed (successfully or with failure).
     *
     * @param futureHandle native submission handle
     * @return {@code true} if complete
     */
    default boolean futureIsDone(long futureHandle) {
        int status = futureStatus(futureHandle);
        return status == 1 || status == 2;
    }

    /**
     * Blocks until {@code futureHandle} finishes on the device and establishes host memory
     * visibility.
     *
     * @param futureHandle native submission handle
     */
    void futureSynchronize(long futureHandle);

    /**
     * Materializes the host-side result of a completed {@code futureHandle}.
     *
     * @param futureHandle native submission handle
     * @return result object, or {@code null} for void kernels
     */
    Object futureGetResult(long futureHandle);

    /**
     * Claims the device-resident output buffer of {@code futureHandle} as a {@link GpuArray}
     * without copying back to host memory.
     *
     * @param futureHandle native submission handle
     * @return promoted {@link GpuArray}, or {@code null} if no device array output exists
     */
    default GpuArray<?> futureGetResultAsGpuArray(long futureHandle) {
        throw new UnsupportedOperationException(
                "this backend does not implement futureGetResultAsGpuArray");
    }

    /**
     * Returns the fallback diagnostic error message for a failed {@code futureHandle}.
     *
     * @param futureHandle native submission handle
     * @return error message, or {@code null}
     */
    String futureGetErrorMessage(long futureHandle);

    /**
     * Attempts to cancel {@code futureHandle}.
     *
     * @param futureHandle          native submission handle
     * @param mayInterruptIfRunning interrupt hint
     * @return {@code 1} if cancelled, {@code 0} otherwise
     */
    int futureCancel(long futureHandle, boolean mayInterruptIfRunning);

    /**
     * Returns the primary error string recorded for a failed {@code futureHandle}.
     *
     * @param futureHandle native submission handle
     * @return error string, or {@code null}
     */
    String futureGetError(long futureHandle);

    /**
     * Chains a named static GPU kernel onto {@code futureHandle}'s device output.
     *
     * @param futureHandle upstream native submission handle
     * @param className    target class name
     * @param methodName   target method name
     * @param descriptor   JVM method descriptor
     * @param args         additional kernel arguments
     * @return downstream native submission handle
     */
    default long futureThenApplyGpu(
            long futureHandle,
            String className,
            String methodName,
            String descriptor,
            Object[] args) {
        throw new UnsupportedOperationException("this backend does not implement futureThenApplyGpu");
    }

    /**
     * Uploads an {@code int[]} to device memory and returns its native array handle.
     *
     * @param arr host source array
     * @return non-zero native array handle
     */
    long arrayWrapInt(int[] arr);

    /**
     * Uploads a {@code long[]} to device memory and returns its native array handle.
     *
     * @param arr host source array
     * @return non-zero native array handle
     */
    long arrayWrapLong(long[] arr);

    /**
     * Uploads a {@code float[]} to device memory and returns its native array handle.
     *
     * @param arr host source array
     * @return non-zero native array handle
     */
    long arrayWrapFloat(float[] arr);

    /**
     * Uploads a {@code short[]} (such as IEEE-754 binary16 elements) to device memory.
     *
     * @param arr host source array
     * @return non-zero native array handle
     */
    default long arrayWrapShort(short[] arr) {
        throw new UnsupportedOperationException("this backend does not implement arrayWrapShort");
    }

    /**
     * Uploads a {@code double[]} to device memory and returns its native array handle.
     *
     * @param arr host source array
     * @return non-zero native array handle
     */
    long arrayWrapDouble(double[] arr);

    /**
     * Copies device array {@code arrayHandle} into a newly allocated host primitive array.
     *
     * @param arrayHandle native array handle
     * @return host primitive array
     */
    Object arrayToHost(long arrayHandle);

    /**
     * Returns {@code true} if {@code arrayHandle} currently resides in device memory.
     *
     * @param arrayHandle native array handle
     * @return residency flag
     */
    boolean arrayIsResident(long arrayHandle);

    /**
     * Releases native resources for {@code futureHandle}.
     *
     * @param futureHandle native submission handle
     */
    void releaseFuture(long futureHandle);

    /**
     * Releases native resources for {@code arrayHandle}.
     *
     * @param arrayHandle native array handle
     */
    void releaseArray(long arrayHandle);

    /**
     * Releases native resources for {@code execHandle}.
     *
     * @param execHandle native executor handle
     */
    void releaseExecutor(long execHandle);

    /**
     * Returns the number of visible GPU devices, or {@code -1} if unsupported.
     *
     * @return visible device count, or negative if unsupported
     */
    default int deviceCount() {
        return -1;
    }

    /**
     * Returns the product name of the GPU at {@code ordinal}.
     *
     * @param ordinal zero-based device ordinal
     * @return device name, or {@code null}
     */
    default String deviceName(int ordinal) {
        return null;
    }

    /**
     * Returns total memory in bytes for the GPU at {@code ordinal}.
     *
     * @param ordinal zero-based device ordinal
     * @return total memory in bytes
     */
    default long deviceTotalMemory(int ordinal) {
        return 0L;
    }

    /**
     * Returns packed compute capability ({@code major * 10 + minor}) for {@code ordinal}.
     *
     * @param ordinal zero-based device ordinal
     * @return packed compute capability, or {@code 0} if unknown
     */
    default int deviceComputeCapability(int ordinal) {
        return 0;
    }

    /**
     * Allocates an uninitialized or zeroed {@code int} buffer of {@code length} elements on the
     * device.
     *
     * @param length element count
     * @return non-zero native array handle
     */
    default long arrayAllocateInt(int length) {
        throw new UnsupportedOperationException("this backend does not implement arrayAllocateInt");
    }

    /**
     * Allocates a {@code long} buffer of {@code length} elements on the device.
     *
     * @param length element count
     * @return non-zero native array handle
     */
    default long arrayAllocateLong(int length) {
        throw new UnsupportedOperationException("this backend does not implement arrayAllocateLong");
    }

    /**
     * Allocates a {@code float} buffer of {@code length} elements on the device.
     *
     * @param length element count
     * @return non-zero native array handle
     */
    default long arrayAllocateFloat(int length) {
        throw new UnsupportedOperationException("this backend does not implement arrayAllocateFloat");
    }

    /**
     * Allocates a {@code double} buffer of {@code length} elements on the device.
     *
     * @param length element count
     * @return non-zero native array handle
     */
    default long arrayAllocateDouble(int length) {
        throw new UnsupportedOperationException("this backend does not implement arrayAllocateDouble");
    }

    /**
     * Allocates a 16-bit {@code short} / IEEE-754 binary16 buffer of {@code length} elements on
     * the device.
     *
     * @param length element count
     * @return non-zero native array handle
     */
    default long arrayAllocateShort(int length) {
        throw new UnsupportedOperationException("this backend does not implement arrayAllocateShort");
    }

    /**
     * Enqueues a row-major GEMM operation ({@code C = op(A) * op(B)}) on {@code streamHandle} (or
     * default stream when {@code 0L}).
     *
     * @param aHandle      native handle for matrix A
     * @param bHandle      native handle for matrix B
     * @param cHandle      native handle for output matrix C
     * @param m            rows of op(A) and C
     * @param n            columns of op(B) and C
     * @param k            columns of op(A) / rows of op(B)
     * @param half         {@code true} for fp16 inputs accumulating into fp32 C
     * @param transA       {@code true} if A is transposed
     * @param transB       {@code true} if B is transposed
     * @param streamHandle stream handle, or {@code 0L} for default stream
     * @return non-zero native submission handle
     */
    default long gemm(
            long aHandle,
            long bHandle,
            long cHandle,
            int m,
            int n,
            int k,
            boolean half,
            boolean transA,
            boolean transB,
            long streamHandle) {
        throw new UnsupportedOperationException("this backend does not implement gemm");
    }

    /**
     * Returns the structured {@code ERROR_KIND_*} code for a failed {@code futureHandle}.
     *
     * @param futureHandle native submission handle
     * @return structured error kind constant
     */
    default int futureErrorKind(long futureHandle) {
        return ERROR_KIND_UNKNOWN;
    }

    /**
     * Waits up to {@code timeoutNanos} for {@code futureHandle} to complete.
     *
     * @param futureHandle native submission handle
     * @param timeoutNanos timeout in nanoseconds
     * @return one of {@link #AWAIT_UNSUPPORTED}, {@link #AWAIT_COMPLETED}, or {@link #AWAIT_TIMED_OUT}
     */
    default int futureAwait(long futureHandle, long timeoutNanos) {
        return AWAIT_UNSUPPORTED;
    }

    /**
     * Copies device array {@code arrayHandle} directly into pre-allocated host array {@code dest}.
     *
     * @param arrayHandle native array handle
     * @param dest        destination host primitive array
     * @return {@code true} if direct copy succeeded; {@code false} to fall back to {@link #arrayToHost}
     */
    default boolean arrayToHostInto(long arrayHandle, Object dest) {
        return false;
    }

    /**
     * Copies elements from host primitive array {@code src} into device array {@code arrayHandle}.
     *
     * @param arrayHandle native array handle
     * @param src         source host primitive array
     * @return {@code true} if copy succeeded
     */
    default boolean arrayCopyFromHost(long arrayHandle, Object src) {
        return false;
    }

    /**
     * Enqueues a named static kernel method onto {@code streamHandle}.
     *
     * @param streamHandle native stream handle
     * @param className    target class name
     * @param methodName   target method name
     * @param descriptor   JVM method descriptor
     * @param args         validated kernel arguments
     * @return non-zero native submission handle
     */
    default long streamSubmitMethod(
            long streamHandle,
            String className,
            String methodName,
            String descriptor,
            Object[] args) {
        throw new UnsupportedOperationException("this backend does not implement streamSubmitMethod");
    }

    /**
     * Blocks until all work previously enqueued on {@code streamHandle} completes on the device.
     *
     * @param streamHandle native stream handle
     */
    default void streamSynchronize(long streamHandle) {
        throw new UnsupportedOperationException("this backend does not implement streamSynchronize");
    }
}
