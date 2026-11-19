// SPDX-License-Identifier: Apache-2.0
// Copyright 2025-2026 Craton Software Company (https://craton.com.ar)

package craton.gpu;

/**
 * Thrown when PTX/JIT compilation of a GPU kernel fails (jit-cuda compiler
 * error).
 *
 * @since 0.1.0
 */
public class GpuCompileException extends GpuException {

    private static final long serialVersionUID = 1L;

    /** Name of the kernel whose compilation failed, or {@code null} if unknown. */
    private final String kernelName;

    /**
     * Constructs a {@code GpuCompileException} with the given message.
     *
     * @param message the detail message
     */
    public GpuCompileException(String message) {
        super(message);
        this.kernelName = null;
    }

    /**
     * Constructs a {@code GpuCompileException} with the given message and
     * cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public GpuCompileException(String message, Throwable cause) {
        super(message, cause);
        this.kernelName = null;
    }

    /**
     * Constructs a {@code GpuCompileException} with the given cause.
     *
     * @param cause the underlying cause
     */
    public GpuCompileException(Throwable cause) {
        super(cause);
        this.kernelName = null;
    }

    /**
     * Constructs a {@code GpuCompileException} with the given message and
     * native error code.
     *
     * @param message   the detail message
     * @param errorCode the underlying CUDA error code
     */
    public GpuCompileException(String message, int errorCode) {
        super(message, errorCode);
        this.kernelName = null;
    }

    /**
     * Constructs a {@code GpuCompileException} with the given message, cause
     * and native error code.
     *
     * @param message   the detail message
     * @param cause     the underlying cause
     * @param errorCode the underlying CUDA error code
     */
    public GpuCompileException(String message, Throwable cause, int errorCode) {
        super(message, cause, errorCode);
        this.kernelName = null;
    }

    /**
     * Constructs a {@code GpuCompileException} with the given message, native
     * error code, and the name of the kernel that failed to compile.
     *
     * @param message    the detail message
     * @param errorCode  the underlying CUDA error code
     * @param kernelName the name of the kernel that failed to compile, or
     *                   {@code null} if unknown
     */
    public GpuCompileException(String message, int errorCode, String kernelName) {
        super(message, errorCode);
        this.kernelName = kernelName;
    }

    /**
     * Constructs a {@code GpuCompileException} with the given message, cause,
     * native error code, and the name of the kernel that failed to compile.
     *
     * @param message    the detail message
     * @param cause      the underlying cause
     * @param errorCode  the underlying CUDA error code
     * @param kernelName the name of the kernel that failed to compile, or
     *                   {@code null} if unknown
     * @since 0.1.0
     */
    public GpuCompileException(String message, Throwable cause, int errorCode,
                               String kernelName) {
        super(message, cause, errorCode);
        this.kernelName = kernelName;
    }

    /**
     * Returns the name of the kernel whose compilation failed, or
     * {@code null} if the name is not known.
     *
     * @return the kernel name, or {@code null}
     */
    public String kernelName() {
        return kernelName;
    }

    /**
     * Returns the standard {@link Throwable#toString()} representation,
     * optionally suffixed with kernel-name diagnostic information when
     * available.
     *
     * @return the diagnostic string representation
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(super.toString());
        if (kernelName != null) {
            sb.append(" kernel=").append(kernelName);
        }
        return sb.toString();
    }
}
