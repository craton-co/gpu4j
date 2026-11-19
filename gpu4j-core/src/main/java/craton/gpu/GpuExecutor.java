// SPDX-License-Identifier: Apache-2.0
// Copyright 2025-2026 Craton Software Company (https://craton.com.ar)

package craton.gpu;

import craton.gpu.internal.Native;

import java.util.Objects;

/**
 * Entry point for compiling, capturing, and dispatching GPU work on a specific device.
 *
 * <p>Operations invoked after {@link #close()} throw {@link GpuException}.</p>
 *
 * @since 0.1.0
 */
public interface GpuExecutor extends AutoCloseable {

    /**
     * Opens an executor bound to the default GPU device ({@code 0}).
     *
     * @return opened {@code GpuExecutor}
     * @throws GpuException if no executor could be opened on device {@code 0}
     */
    static GpuExecutor open() {
        return open(0);
    }

    /**
     * Opens an executor bound to {@code deviceOrdinal}.
     *
     * @param deviceOrdinal zero-based non-negative GPU ordinal
     * @return opened {@code GpuExecutor}
     * @throws IllegalArgumentException if {@code deviceOrdinal < 0}
     * @throws GpuException             if the runtime could not open an executor on {@code deviceOrdinal}
     */
    static GpuExecutor open(int deviceOrdinal) {
        if (deviceOrdinal < 0) {
            throw new IllegalArgumentException("deviceOrdinal must be non-negative: " + deviceOrdinal);
        }
        GpuExecutor exec = Native.bridge().openExecutor(deviceOrdinal);
        if (exec == null) {
            throw new GpuException("failed to open GPU executor on device " + deviceOrdinal);
        }
        return exec;
    }

    /**
     * Submits a value-producing {@link GpuCallable} task for execution.
     *
     * @param task non-null task
     * @param <R>  result type
     * @return future tracking completion
     */
    <R> GpuFuture<R> submit(GpuCallable<R> task);

    /**
     * Launches a void {@link GpuRunnable} task for execution.
     *
     * @param task non-null runnable task
     * @return future tracking completion
     */
    GpuFuture<Void> launch(GpuRunnable task);

    /**
     * Submits a named static kernel method identified by class name, method name, and JVM
     * descriptor.
     *
     * @param className  slash-delimited or binary class name
     * @param methodName static kernel method name
     * @param descriptor JVM method descriptor (e.g. {@code "([I[I[I)V"})
     * @param args       kernel arguments
     * @param <R>        result type
     * @return future tracking completion
     */
    default <R> GpuFuture<R> submit(
            String className,
            String methodName,
            String descriptor,
            Object... args) {
        return dispatchNamed(className, methodName, descriptor, args);
    }

    /**
     * Submits a binary {@code int[]} vector kernel {@code (a, b, out) -> void}.
     *
     * @param className  target class name
     * @param methodName target method name
     * @param descriptor JVM method descriptor
     * @param a          first input array
     * @param b          second input array
     * @param out        output array
     * @return future tracking completion
     */
    default GpuFuture<Void> submitV(
            String className,
            String methodName,
            String descriptor,
            int[] a,
            int[] b,
            int[] out) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        Objects.requireNonNull(out, "out");
        return dispatchNamed(className, methodName, descriptor, new Object[] {a, b, out});
    }

    /**
     * Submits a binary {@code float[]} vector kernel {@code (a, b, out) -> void}.
     *
     * @param className  target class name
     * @param methodName target method name
     * @param descriptor JVM method descriptor
     * @param a          first input array
     * @param b          second input array
     * @param out        output array
     * @return future tracking completion
     */
    default GpuFuture<Void> submitV(
            String className,
            String methodName,
            String descriptor,
            float[] a,
            float[] b,
            float[] out) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        Objects.requireNonNull(out, "out");
        return dispatchNamed(className, methodName, descriptor, new Object[] {a, b, out});
    }

    /**
     * Submits a binary {@code double[]} vector kernel {@code (a, b, out) -> void}.
     *
     * @param className  target class name
     * @param methodName target method name
     * @param descriptor JVM method descriptor
     * @param a          first input array
     * @param b          second input array
     * @param out        output array
     * @return future tracking completion
     */
    default GpuFuture<Void> submitV(
            String className,
            String methodName,
            String descriptor,
            double[] a,
            double[] b,
            double[] out) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        Objects.requireNonNull(out, "out");
        return dispatchNamed(className, methodName, descriptor, new Object[] {a, b, out});
    }

    /**
     * Submits a binary {@code long[]} vector kernel {@code (a, b, out) -> void}.
     *
     * @param className  target class name
     * @param methodName target method name
     * @param descriptor JVM method descriptor
     * @param a          first input array
     * @param b          second input array
     * @param out        output array
     * @return future tracking completion
     */
    default GpuFuture<Void> submitV(
            String className,
            String methodName,
            String descriptor,
            long[] a,
            long[] b,
            long[] out) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        Objects.requireNonNull(out, "out");
        return dispatchNamed(className, methodName, descriptor, new Object[] {a, b, out});
    }

    /**
     * Submits an {@code int} scalar-by-array kernel {@code (scalar, in, out) -> void}.
     *
     * @param className  target class name
     * @param methodName target method name
     * @param descriptor JVM method descriptor
     * @param scalar     scalar multiplier/operand
     * @param in         input array
     * @param out        output array
     * @return future tracking completion
     */
    default GpuFuture<Void> submitScale(
            String className,
            String methodName,
            String descriptor,
            int scalar,
            int[] in,
            int[] out) {
        Objects.requireNonNull(in, "in");
        Objects.requireNonNull(out, "out");
        return dispatchNamed(className, methodName, descriptor, new Object[] {scalar, in, out});
    }

    /**
     * Submits a {@code float} scalar-by-array kernel {@code (scalar, in, out) -> void}.
     *
     * @param className  target class name
     * @param methodName target method name
     * @param descriptor JVM method descriptor
     * @param scalar     scalar multiplier/operand
     * @param in         input array
     * @param out        output array
     * @return future tracking completion
     */
    default GpuFuture<Void> submitScale(
            String className,
            String methodName,
            String descriptor,
            float scalar,
            float[] in,
            float[] out) {
        Objects.requireNonNull(in, "in");
        Objects.requireNonNull(out, "out");
        return dispatchNamed(className, methodName, descriptor, new Object[] {scalar, in, out});
    }

    /**
     * Returns the underlying native executor handle for low-level dispatch.
     *
     * @return non-zero native executor handle
     */
    long handleForDispatch();

    /**
     * Dispatches a named static kernel method with an explicit argument array.
     *
     * @param className  target class name
     * @param methodName target method name
     * @param descriptor JVM method descriptor
     * @param args       kernel arguments
     * @param <R>        result type
     * @return future tracking completion
     * @since 0.1.0
     */
    <R> GpuFuture<R> dispatchNamed(
            String className,
            String methodName,
            String descriptor,
            Object[] args);

    /**
     * Dispatches a named static kernel method and returns its raw submission handle for explicit
     * {@link #awaitSubmission(long)} / {@link #releaseSubmission(long)} management or graph capture.
     *
     * @param className  target class name
     * @param methodName target method name
     * @param descriptor JVM method descriptor
     * @param args       kernel arguments
     * @return non-zero native submission handle
     * @since 0.1.0
     */
    long dispatchNamedHandle(
            String className,
            String methodName,
            String descriptor,
            Object[] args);

    /**
     * Begins recording subsequent kernel launches into a CUDA graph.
     *
     * @since 0.1.0
     */
    void beginCapture();

    /**
     * Ends graph capture and returns the captured {@link GpuGraph}.
     *
     * @return captured auto-closeable graph
     * @since 0.1.0
     */
    GpuGraph endCapture();

    /**
     * Replays a captured {@link GpuGraph} and returns the raw submission handle.
     *
     * @param graph non-null open {@link GpuGraph}
     * @return non-zero native submission handle
     * @since 0.1.0
     */
    long replay(GpuGraph graph);

    /**
     * Begins a parameter-update replay session for {@code graph}.
     *
     * @param graph non-null open {@link GpuGraph}
     * @since 0.1.0
     */
    void beginReplay(GpuGraph graph);

    /**
     * Ends an active replay session started with {@link #beginReplay(GpuGraph)} and launches it.
     *
     * @return non-zero native submission handle
     * @since 0.1.0
     */
    long endReplay();

    /**
     * Blocks until a raw submission handle completes on the device, throwing {@link GpuException}
     * if the submission failed.
     *
     * @param submissionHandle native submission handle (no-op if {@code 0L})
     * @throws InterruptedException if interrupted while waiting
     * @since 0.1.0
     */
    void awaitSubmission(long submissionHandle) throws InterruptedException;

    /**
     * Releases a raw submission handle returned by {@link #dispatchNamedHandle},
     * {@link #replay(GpuGraph)}, or {@link #endReplay()}.
     *
     * @param submissionHandle native submission handle (no-op if {@code 0L})
     * @since 0.1.0
     */
    default void releaseSubmission(long submissionHandle) {
        if (submissionHandle != 0L) {
            Native.bridge().releaseFuture(submissionHandle);
        }
    }

    /**
     * Pre-validates a static kernel method signature into a reusable {@link PreparedKernel}.
     *
     * @param className  target class name
     * @param methodName target method name
     * @param descriptor JVM method descriptor
     * @return reusable prepared kernel handle
     * @since 0.1.0
     */
    default PreparedKernel prepare(String className, String methodName, String descriptor) {
        return new PreparedKernel(this, className, methodName, descriptor);
    }

    /**
     * Creates a new asynchronous {@link GpuStream} bound to this executor.
     *
     * @return new stream
     */
    GpuStream newStream();

    /**
     * Releases the native executor handle. Idempotent across repeated calls.
     */
    @Override
    void close();
}
