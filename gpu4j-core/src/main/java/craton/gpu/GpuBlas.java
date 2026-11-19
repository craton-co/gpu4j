// SPDX-License-Identifier: Apache-2.0
// Copyright 2025-2026 Craton Software Company (https://craton.com.ar)

package craton.gpu;

import craton.gpu.internal.Native;
import craton.gpu.internal.Submissions;

import java.lang.ref.Reference;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;

/**
 * Hardware-accelerated matrix multiplication ({@code C = op(A) * op(B)}) backed by the runtime's
 * BLAS engine (cuBLAS on CUDA devices), plus a double-accumulating CPU reference implementation
 * for verification and driverless fallback.
 *
 * @since 0.1.0
 */
public final class GpuBlas {

    private GpuBlas() {
    }

    /**
     * Computes single-precision {@code C = A * B} on the default stream.
     *
     * @param a left matrix ({@code m * k})
     * @param b right matrix ({@code k * n})
     * @param c output matrix ({@code m * n})
     * @param m rows of A and C
     * @param n columns of B and C
     * @param k columns of A / rows of B
     * @return future tracking completion
     */
    public static GpuFuture<Void> gemm(
            GpuArray<float[]> a,
            GpuArray<float[]> b,
            GpuArray<float[]> c,
            int m,
            int n,
            int k) {
        return gemm(null, a, Transpose.NONE, b, Transpose.NONE, c, m, n, k);
    }

    /**
     * Computes single-precision {@code C = op(A) * op(B)} on the default stream.
     *
     * @param a      left matrix
     * @param transA transpose modifier for A
     * @param b      right matrix
     * @param transB transpose modifier for B
     * @param c      output matrix ({@code m * n})
     * @param m      rows of op(A) and C
     * @param n      columns of op(B) and C
     * @param k      columns of op(A) / rows of op(B)
     * @return future tracking completion
     */
    public static GpuFuture<Void> gemm(
            GpuArray<float[]> a,
            Transpose transA,
            GpuArray<float[]> b,
            Transpose transB,
            GpuArray<float[]> c,
            int m,
            int n,
            int k) {
        return gemm(null, a, transA, b, transB, c, m, n, k);
    }

    /**
     * Computes single-precision {@code C = op(A) * op(B)} on {@code stream} (or default stream if
     * {@code stream == null}).
     *
     * @param stream target stream, or {@code null} for default stream
     * @param a      left matrix
     * @param transA transpose modifier for A
     * @param b      right matrix
     * @param transB transpose modifier for B
     * @param c      output matrix ({@code m * n})
     * @param m      rows of op(A) and C
     * @param n      columns of op(B) and C
     * @param k      columns of op(A) / rows of op(B)
     * @return future tracking completion
     */
    public static GpuFuture<Void> gemm(
            GpuStream stream,
            GpuArray<float[]> a,
            Transpose transA,
            GpuArray<float[]> b,
            Transpose transB,
            GpuArray<float[]> c,
            int m,
            int n,
            int k) {
        Objects.requireNonNull(transA, "transA");
        Objects.requireNonNull(transB, "transB");
        check(a, "A", Float.TYPE, m, k);
        check(b, "B", Float.TYPE, k, n);
        check(c, "C", Float.TYPE, m, n);
        return dispatch(stream, a, b, c, m, n, k, false, transA, transB);
    }

    /**
     * Computes mixed-precision {@code C = A * B} (IEEE-754 fp16 inputs in {@code short[]}, fp32
     * output in {@code float[]}) on the default stream.
     *
     * @param a left fp16 matrix ({@code m * k})
     * @param b right fp16 matrix ({@code k * n})
     * @param c output fp32 matrix ({@code m * n})
     * @param m rows of A and C
     * @param n columns of B and C
     * @param k columns of A / rows of B
     * @return future tracking completion
     */
    public static GpuFuture<Void> gemmHalf(
            GpuArray<short[]> a,
            GpuArray<short[]> b,
            GpuArray<float[]> c,
            int m,
            int n,
            int k) {
        return gemmHalf(null, a, Transpose.NONE, b, Transpose.NONE, c, m, n, k);
    }

    /**
     * Computes mixed-precision {@code C = op(A) * op(B)} on the default stream.
     *
     * @param a      left fp16 matrix
     * @param transA transpose modifier for A
     * @param b      right fp16 matrix
     * @param transB transpose modifier for B
     * @param c      output fp32 matrix ({@code m * n})
     * @param m      rows of op(A) and C
     * @param n      columns of op(B) and C
     * @param k      columns of op(A) / rows of op(B)
     * @return future tracking completion
     */
    public static GpuFuture<Void> gemmHalf(
            GpuArray<short[]> a,
            Transpose transA,
            GpuArray<short[]> b,
            Transpose transB,
            GpuArray<float[]> c,
            int m,
            int n,
            int k) {
        return gemmHalf(null, a, transA, b, transB, c, m, n, k);
    }

    /**
     * Computes mixed-precision {@code C = op(A) * op(B)} on {@code stream} (or default stream if
     * {@code stream == null}).
     *
     * @param stream target stream, or {@code null} for default stream
     * @param a      left fp16 matrix
     * @param transA transpose modifier for A
     * @param b      right fp16 matrix
     * @param transB transpose modifier for B
     * @param c      output fp32 matrix ({@code m * n})
     * @param m      rows of op(A) and C
     * @param n      columns of op(B) and C
     * @param k      columns of op(A) / rows of op(B)
     * @return future tracking completion
     */
    public static GpuFuture<Void> gemmHalf(
            GpuStream stream,
            GpuArray<short[]> a,
            Transpose transA,
            GpuArray<short[]> b,
            Transpose transB,
            GpuArray<float[]> c,
            int m,
            int n,
            int k) {
        Objects.requireNonNull(transA, "transA");
        Objects.requireNonNull(transB, "transB");
        check(a, "A", Short.TYPE, m, k);
        check(b, "B", Short.TYPE, k, n);
        check(c, "C", Float.TYPE, m, n);
        return dispatch(stream, a, b, c, m, n, k, true, transA, transB);
    }

    /**
     * Synchronous row-major CPU reference {@code C = A * B} using {@code double} accumulation.
     *
     * @param a left host matrix ({@code m * k})
     * @param b right host matrix ({@code k * n})
     * @param c output host matrix ({@code m * n})
     * @param m rows of A and C
     * @param n columns of B and C
     * @param k columns of A / rows of B
     */
    public static void gemmReference(float[] a, float[] b, float[] c, int m, int n, int k) {
        gemmReference(a, Transpose.NONE, b, Transpose.NONE, c, m, n, k);
    }

    /**
     * Synchronous row-major CPU reference {@code C = op(A) * op(B)} using {@code double}
     * accumulation.
     *
     * @param a      left host matrix
     * @param transA transpose modifier for A
     * @param b      right host matrix
     * @param transB transpose modifier for B
     * @param c      output host matrix ({@code m * n})
     * @param m      rows of op(A) and C
     * @param n      columns of op(B) and C
     * @param k      columns of op(A) / rows of op(B)
     */
    public static void gemmReference(
            float[] a,
            Transpose transA,
            float[] b,
            Transpose transB,
            float[] c,
            int m,
            int n,
            int k) {
        Objects.requireNonNull(a, "A");
        Objects.requireNonNull(b, "B");
        Objects.requireNonNull(c, "C");
        Objects.requireNonNull(transA, "transA");
        Objects.requireNonNull(transB, "transB");
        requirePositive(m, n, k);
        requireLength(a.length, m, k, "A");
        requireLength(b.length, k, n, "B");
        requireLength(c.length, m, n, "C");

        int aRowStride = transA.isTransposed() ? 1 : k;
        int aColStride = transA.isTransposed() ? m : 1;
        int bRowStride = transB.isTransposed() ? 1 : n;
        int bColStride = transB.isTransposed() ? k : 1;

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                double sum = 0.0;
                for (int p = 0; p < k; p++) {
                    sum += (double) a[i * aRowStride + p * aColStride]
                            * (double) b[p * bRowStride + j * bColStride];
                }
                c[i * n + j] = (float) sum;
            }
        }
    }

    private static GpuFuture<Void> dispatch(
            GpuStream stream,
            GpuArray<?> a,
            GpuArray<?> b,
            GpuArray<?> c,
            int m,
            int n,
            int k,
            boolean half,
            Transpose transA,
            Transpose transB) {
        GpuArray<?>[] ordered = new GpuArray<?>[] {a, b, c};
        Arrays.sort(ordered, Comparator.comparingLong(GpuArray::rawHandleForLockOrder));
        ordered[0].readLock().lock();
        try {
            ordered[1].readLock().lock();
            try {
                ordered[2].readLock().lock();
                try {
                    a.ensureOpenUnderLock();
                    b.ensureOpenUnderLock();
                    c.ensureOpenUnderLock();
                    long streamHandle = stream == null ? 0L : stream.handle();
                    long handle = Native.bridge().gemm(
                            a.rawHandleForLockOrder(),
                            b.rawHandleForLockOrder(),
                            c.rawHandleForLockOrder(),
                            m,
                            n,
                            k,
                            half,
                            transA.isTransposed(),
                            transB.isTransposed(),
                            streamHandle);
                    if (handle == 0L) {
                        throw new GpuException(
                                "gemm dispatch was not recorded (handle 0) вЂ” this CratonVM has no GPU offload, or no device is available");
                    }
                    return Submissions.future(handle);
                } finally {
                    ordered[2].readLock().unlock();
                }
            } finally {
                ordered[1].readLock().unlock();
            }
        } finally {
            ordered[0].readLock().unlock();
            Reference.reachabilityFence(a);
            Reference.reachabilityFence(b);
            Reference.reachabilityFence(c);
            Reference.reachabilityFence(stream);
        }
    }

    private static void check(
            GpuArray<?> array,
            String name,
            Class<?> expectedElementType,
            int rows,
            int cols) {
        Objects.requireNonNull(array, name);
        requirePositive(rows, cols, 1);
        if (array.elementType() != expectedElementType) {
            throw new IllegalArgumentException(
                    name + " must have elementType " + expectedElementType.getName()
                            + ", got " + array.elementType().getName());
        }
        requireLength(array.length(), rows, cols, name);
    }

    private static void requirePositive(int m, int n, int k) {
        if (m <= 0 || n <= 0 || k <= 0) {
            throw new IllegalArgumentException(
                    "GEMM dimensions must be positive: m=" + m + ", n=" + n + ", k=" + k);
        }
    }

    private static void requireLength(int actualLength, int rows, int cols, String name) {
        long needed = (long) rows * (long) cols;
        if (needed > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    name + " required element count overflows int: " + needed);
        }
        if (actualLength < needed) {
            throw new IllegalArgumentException(
                    name + " length " + actualLength + " is smaller than "
                            + rows + " * " + cols + " (" + needed + ")");
        }
    }
}
