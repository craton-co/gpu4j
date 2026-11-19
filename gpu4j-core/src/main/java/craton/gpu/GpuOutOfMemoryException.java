// SPDX-License-Identifier: Apache-2.0
// Copyright 2025-2026 Craton Software Company (https://craton.com.ar)

package craton.gpu;

/**
 * Thrown when the GPU device runs out of memory
 * ({@code cudaErrorMemoryAllocation} or equivalent).
 *
 * @since 0.1.0
 */
public class GpuOutOfMemoryException extends GpuException {

    private static final long serialVersionUID = 1L;

    /** Number of bytes requested by the failed allocation, or {@code -1L} if unknown. */
    private final long requestedBytes;

    /** Number of bytes reported available at allocation time, or {@code -1L} if unknown. */
    private final long availableBytes;

    /** Device ordinal where the allocation failed, or {@code -1} if unknown. */
    private final int deviceId;

    /**
     * Constructs a {@code GpuOutOfMemoryException} with the given message.
     *
     * @param message the detail message
     */
    public GpuOutOfMemoryException(String message) {
        super(message);
        this.requestedBytes = -1L;
        this.availableBytes = -1L;
        this.deviceId = -1;
    }

    /**
     * Constructs a {@code GpuOutOfMemoryException} with the given message and
     * cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public GpuOutOfMemoryException(String message, Throwable cause) {
        super(message, cause);
        this.requestedBytes = -1L;
        this.availableBytes = -1L;
        this.deviceId = -1;
    }

    /**
     * Constructs a {@code GpuOutOfMemoryException} with the given cause.
     *
     * @param cause the underlying cause
     */
    public GpuOutOfMemoryException(Throwable cause) {
        super(cause);
        this.requestedBytes = -1L;
        this.availableBytes = -1L;
        this.deviceId = -1;
    }

    /**
     * Constructs a {@code GpuOutOfMemoryException} with the given message and
     * native error code.
     *
     * @param message   the detail message
     * @param errorCode the underlying CUDA error code
     */
    public GpuOutOfMemoryException(String message, int errorCode) {
        super(message, errorCode);
        this.requestedBytes = -1L;
        this.availableBytes = -1L;
        this.deviceId = -1;
    }

    /**
     * Constructs a {@code GpuOutOfMemoryException} with the given message,
     * cause and native error code.
     *
     * @param message   the detail message
     * @param cause     the underlying cause
     * @param errorCode the underlying CUDA error code
     */
    public GpuOutOfMemoryException(String message, Throwable cause, int errorCode) {
        super(message, cause, errorCode);
        this.requestedBytes = -1L;
        this.availableBytes = -1L;
        this.deviceId = -1;
    }

    /**
     * Constructs a {@code GpuOutOfMemoryException} with the given message and
     * allocation diagnostics.
     *
     * @param message        the detail message
     * @param requestedBytes the number of bytes requested by the failed
     *                       allocation, or {@code -1L} if unknown
     * @param availableBytes the number of bytes reported available on the
     *                       device at allocation time, or {@code -1L} if
     *                       unknown
     * @param deviceId       the device ordinal where the allocation failed,
     *                       or {@code -1} if unknown
     */
    public GpuOutOfMemoryException(String message, long requestedBytes,
                                   long availableBytes, int deviceId) {
        super(message);
        this.requestedBytes = requestedBytes;
        this.availableBytes = availableBytes;
        this.deviceId = deviceId;
    }

    /**
     * Constructs a {@code GpuOutOfMemoryException} with the given message,
     * native error code, and allocation diagnostics.
     *
     * @param message        the detail message
     * @param errorCode      the underlying CUDA error code
     * @param requestedBytes the number of bytes requested by the failed
     *                       allocation, or {@code -1L} if unknown
     * @param availableBytes the number of bytes reported available on the
     *                       device at allocation time, or {@code -1L} if
     *                       unknown
     * @param deviceId       the device ordinal where the allocation failed,
     *                       or {@code -1} if unknown
     * @since 0.1.0
     */
    public GpuOutOfMemoryException(String message, int errorCode,
                                   long requestedBytes, long availableBytes,
                                   int deviceId) {
        super(message, errorCode);
        this.requestedBytes = requestedBytes;
        this.availableBytes = availableBytes;
        this.deviceId = deviceId;
    }

    /**
     * Constructs a {@code GpuOutOfMemoryException} with the given message,
     * cause, native error code, and allocation diagnostics.
     *
     * @param message        the detail message
     * @param cause          the underlying cause
     * @param errorCode      the underlying CUDA error code
     * @param requestedBytes the number of bytes requested by the failed
     *                       allocation, or {@code -1L} if unknown
     * @param availableBytes the number of bytes reported available on the
     *                       device at allocation time, or {@code -1L} if
     *                       unknown
     * @param deviceId       the device ordinal where the allocation failed,
     *                       or {@code -1} if unknown
     * @since 0.1.0
     */
    public GpuOutOfMemoryException(String message, Throwable cause, int errorCode,
                                   long requestedBytes, long availableBytes,
                                   int deviceId) {
        super(message, cause, errorCode);
        this.requestedBytes = requestedBytes;
        this.availableBytes = availableBytes;
        this.deviceId = deviceId;
    }

    /**
     * Returns the number of bytes requested by the failed allocation, or
     * {@code -1L} if the value is not known.
     *
     * @return the requested allocation size in bytes, or {@code -1L}
     */
    public long requestedBytes() {
        return requestedBytes;
    }

    /**
     * Returns the number of bytes reported available on the device at the
     * time of allocation, or {@code -1L} if the value is not known.
     *
     * @return the available bytes at allocation time, or {@code -1L}
     */
    public long availableBytes() {
        return availableBytes;
    }

    /**
     * Returns the device ordinal where the allocation failed, or {@code -1}
     * if the value is not known.
     *
     * @return the device id, or {@code -1}
     */
    public int deviceId() {
        return deviceId;
    }

    /**
     * Returns the standard {@link Throwable#toString()} representation,
     * optionally suffixed with allocation diagnostics when any of
     * {@link #requestedBytes()}, {@link #availableBytes()} or
     * {@link #deviceId()} is set to a non-default value.
     *
     * @return the diagnostic string representation
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(super.toString());
        if (requestedBytes != -1L || availableBytes != -1L || deviceId != -1) {
            sb.append(" requested=").append(requestedBytes)
              .append(" available=").append(availableBytes)
              .append(" device=").append(deviceId);
        }
        return sb.toString();
    }
}
