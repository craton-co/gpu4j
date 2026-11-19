// SPDX-License-Identifier: Apache-2.0
// Copyright 2025-2026 Craton Software Company (https://craton.com.ar)

package craton.gpu;

import craton.gpu.internal.KernelArgs;

import java.util.Objects;

/**
 * Reusable pre-validated handle for a specific Java static kernel method executed via a
 * {@link GpuExecutor}.
 *
 * @since 0.1.0
 */
public final class PreparedKernel {

    private final GpuExecutor executor;
    private final String className;
    private final String methodName;
    private final String descriptor;

    PreparedKernel(
            GpuExecutor executor,
            String className,
            String methodName,
            String descriptor) {
        this.executor = Objects.requireNonNull(executor, "executor");
        KernelArgs.checkTarget(className, methodName, descriptor);
        this.className = className;
        this.methodName = methodName;
        this.descriptor = descriptor;
    }

    /**
     * Returns the target class name.
     *
     * @return target class name
     */
    public String className() {
        return className;
    }

    /**
     * Returns the target static method name.
     *
     * @return target method name
     */
    public String methodName() {
        return methodName;
    }

    /**
     * Returns the target JVM method descriptor.
     *
     * @return JVM method descriptor
     */
    public String descriptor() {
        return descriptor;
    }

    /**
     * Dispatches this prepared kernel with arbitrary supported arguments.
     *
     * @param args kernel arguments
     * @param <R>  result type
     * @return future tracking completion
     */
    public <R> GpuFuture<R> dispatch(Object... args) {
        return executor.dispatchNamed(className, methodName, descriptor, KernelArgs.checkArgs(args));
    }

    /**
     * Dispatches this prepared kernel with three {@code int[]} arguments.
     *
     * @param a   first input array
     * @param b   second input array
     * @param out output array
     * @return future tracking completion
     */
    public GpuFuture<Void> dispatch(int[] a, int[] b, int[] out) {
        return executor.dispatchNamed(className, methodName, descriptor, triple(a, b, out));
    }

    /**
     * Dispatches this prepared kernel with three {@code long[]} arguments.
     *
     * @param a   first input array
     * @param b   second input array
     * @param out output array
     * @return future tracking completion
     */
    public GpuFuture<Void> dispatch(long[] a, long[] b, long[] out) {
        return executor.dispatchNamed(className, methodName, descriptor, triple(a, b, out));
    }

    /**
     * Dispatches this prepared kernel with three {@code float[]} arguments.
     *
     * @param a   first input array
     * @param b   second input array
     * @param out output array
     * @return future tracking completion
     */
    public GpuFuture<Void> dispatch(float[] a, float[] b, float[] out) {
        return executor.dispatchNamed(className, methodName, descriptor, triple(a, b, out));
    }

    /**
     * Dispatches this prepared kernel with three {@code double[]} arguments.
     *
     * @param a   first input array
     * @param b   second input array
     * @param out output array
     * @return future tracking completion
     */
    public GpuFuture<Void> dispatch(double[] a, double[] b, double[] out) {
        return executor.dispatchNamed(className, methodName, descriptor, triple(a, b, out));
    }

    /**
     * Dispatches this prepared kernel with an {@code int} scalar and two {@code int[]} arrays.
     *
     * @param scalar scalar operand
     * @param in     input array
     * @param out    output array
     * @return future tracking completion
     */
    public GpuFuture<Void> dispatch(int scalar, int[] in, int[] out) {
        Objects.requireNonNull(in, "in");
        Objects.requireNonNull(out, "out");
        return executor.dispatchNamed(
                className, methodName, descriptor, new Object[] {scalar, in, out});
    }

    /**
     * Dispatches this prepared kernel with a {@code float} scalar and two {@code float[]} arrays.
     *
     * @param scalar scalar operand
     * @param in     input array
     * @param out    output array
     * @return future tracking completion
     */
    public GpuFuture<Void> dispatch(float scalar, float[] in, float[] out) {
        Objects.requireNonNull(in, "in");
        Objects.requireNonNull(out, "out");
        return executor.dispatchNamed(
                className, methodName, descriptor, new Object[] {scalar, in, out});
    }

    /**
     * Dispatches this prepared kernel and returns its raw native submission handle.
     *
     * @param args kernel arguments
     * @return non-zero native submission handle
     */
    public long dispatchHandle(Object... args) {
        return executor.dispatchNamedHandle(
                className, methodName, descriptor, KernelArgs.checkArgs(args));
    }

    private static Object[] triple(Object a, Object b, Object out) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        Objects.requireNonNull(out, "out");
        return new Object[] {a, b, out};
    }

    @Override
    public String toString() {
        return className + "." + methodName + descriptor;
    }
}
