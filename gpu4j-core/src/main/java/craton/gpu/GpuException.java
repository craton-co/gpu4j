// SPDX-License-Identifier: Apache-2.0
// Copyright 2025-2026 Craton Software Company (https://craton.com.ar)

package craton.gpu;

/**
 * Unchecked exception raised when a GPU operation fails. Wraps lower-level
 * driver errors (CUDA error codes, PTX/JIT compile failures, out-of-memory on
 * the device, kernel launch failures, etc.) into a single host-side type that
 * callers can catch without depending on the native bridge.
 *
 * <p>Concrete failure modes are represented by subclasses to enable targeted
 * {@code catch} clauses:
 * <ul>
 *   <li>{@link GpuCompileException} &mdash; PTX/JIT compilation failure</li>
 *   <li>{@link GpuOutOfMemoryException} &mdash; device-side allocation failure</li>
 *   <li>{@link GpuLaunchException} &mdash; kernel launch / runtime failure</li>
 * </ul>
 *
 * <p>{@link #errorCode()} returns the underlying CUDA error code when the
 * native bridge reports one, or {@code -1} if the code is not known.
 *
 * @since 0.1.0
 */
public class GpuException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Underlying CUDA error code, or {@code -1} if unknown. */
    private final int errorCode;

    /**
     * Constructs a {@code GpuException} with the given message.
     *
     * @param message the detail message
     */
    public GpuException(String message) {
        super(message);
        this.errorCode = -1;
    }

    /**
     * Constructs a {@code GpuException} with the given message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public GpuException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = -1;
    }

    /**
     * Constructs a {@code GpuException} with the given cause.
     *
     * @param cause the underlying cause
     */
    public GpuException(Throwable cause) {
        super(cause);
        this.errorCode = -1;
    }

    /**
     * Constructs a {@code GpuException} with the given message and native
     * error code.
     *
     * @param message   the detail message
     * @param errorCode the underlying CUDA error code
     */
    public GpuException(String message, int errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * Constructs a {@code GpuException} with the given message, cause and
     * native error code.
     *
     * @param message   the detail message
     * @param cause     the underlying cause
     * @param errorCode the underlying CUDA error code
     */
    public GpuException(String message, Throwable cause, int errorCode) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * Constructs a {@code GpuException} with the given cause and native error
     * code.
     *
     * @param cause     the underlying cause
     * @param errorCode the underlying CUDA error code
     * @since 0.1.0
     */
    public GpuException(Throwable cause, int errorCode) {
        super(cause);
        this.errorCode = errorCode;
    }

    /**
     * Constructs a {@code GpuException} with full control over suppression
     * and stack-trace writability. Provided for subclasses that need the
     * extended {@link RuntimeException} contract.
     *
     * @param message            the detail message
     * @param cause              the underlying cause
     * @param enableSuppression  whether suppression is enabled
     * @param writableStackTrace whether the stack trace is writable
     */
    protected GpuException(String message, Throwable cause,
                           boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
        this.errorCode = -1;
    }

    /**
     * Returns the underlying CUDA error code associated with this failure,
     * or {@code -1} if no code is available.
     *
     * @return the native error code, or {@code -1} if unknown
     */
    public int errorCode() {
        return errorCode;
    }
}
