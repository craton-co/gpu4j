// SPDX-License-Identifier: Apache-2.0
// Copyright 2025-2026 Craton Software Company (https://craton.com.ar)

package craton.gpu.internal;

import craton.gpu.GpuArray;
import craton.gpu.GpuFuture;

import java.util.Objects;

/**
 * Shared argument validation for kernel dispatch helpers ({@link GpuExecutorImpl},
 * {@link GpuStreamImpl}, {@link craton.gpu.PreparedKernel}).
 */
public final class KernelArgs {

    private static final Object[] EMPTY = new Object[0];

    private KernelArgs() {
    }

    /**
     * Validates a target Java method triple {@code (className, methodName, descriptor)}.
     *
     * @throws NullPointerException     if any component is {@code null}
     * @throws IllegalArgumentException if any component is blank/empty or {@code descriptor} is not
     *                                  a well-formed method descriptor skeleton {@code (...)R}
     */
    public static void checkTarget(String className, String methodName, String descriptor) {
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(methodName, "methodName");
        Objects.requireNonNull(descriptor, "descriptor");
        if (className.isBlank()) {
            throw new IllegalArgumentException("className is blank");
        }
        if (methodName.isBlank()) {
            throw new IllegalArgumentException("methodName is blank");
        }
        if (descriptor.isEmpty() || descriptor.charAt(0) != '(') {
            throw new IllegalArgumentException("descriptor must start with '('");
        }
        int closeParen = descriptor.indexOf(')');
        if (closeParen < 0 || closeParen == descriptor.length() - 1) {
            throw new IllegalArgumentException(
                    "descriptor must contain ')' followed by a return type: " + descriptor);
        }
    }

    /**
     * Validates that every element of {@code args} is non-null, open (if a {@link GpuArray}), and
     * of a GPU-marshalable type.
     *
     * @param args caller argument array (may be {@code null})
     * @return non-null validated array
     * @throws NullPointerException     if any element is {@code null}
     * @throws IllegalArgumentException if any element has an unsupported runtime type
     * @throws IllegalStateException    if any {@link GpuArray} element has already been closed
     */
    public static Object[] checkArgs(Object[] args) {
        if (args == null) {
            return EMPTY;
        }
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (arg == null) {
                throw new NullPointerException("args[" + i + "] must not be null");
            }
            if (!isSupported(arg)) {
                throw new IllegalArgumentException(
                        "args[" + i + "] has unsupported type: " + arg.getClass().getName());
            }
            if (arg instanceof GpuArray<?> array) {
                // Validates that the GpuArray is still open before its handle crosses JNI.
                array.handle();
            }
        }
        return args;
    }

    private static boolean isSupported(Object arg) {
        return arg instanceof Boolean
                || arg instanceof Byte
                || arg instanceof Short
                || arg instanceof Integer
                || arg instanceof Long
                || arg instanceof Float
                || arg instanceof Double
                || arg instanceof String
                || arg instanceof byte[]
                || arg instanceof short[]
                || arg instanceof int[]
                || arg instanceof long[]
                || arg instanceof float[]
                || arg instanceof double[]
                || arg instanceof GpuArray<?>
                || arg instanceof GpuFuture<?>;
    }
}
