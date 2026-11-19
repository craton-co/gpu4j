// SPDX-License-Identifier: Apache-2.0
// Copyright 2025-2026 Craton Software Company (https://craton.com.ar)

package craton.gpu;

/**
 * Thrown when a kernel launch fails post-compile &mdash; invalid grid/block
 * configuration, launch timeout, or device-side runtime error.
 *
 * @since 0.1.0
 */
public class GpuLaunchException extends GpuException {

    private static final long serialVersionUID = 1L;

    /** Name of the kernel that failed to launch, or {@code null} if unknown. */
    private final String kernelName;

    /**
     * Free-form representation of the grid/block shape used at launch, or
     * {@code null} if unknown. Intentionally untyped so this class does not
     * couple to any {@code GridShape} enum.
     */
    private final String gridShape;

    /**
     * Constructs a {@code GpuLaunchException} with the given message.
     *
     * @param message the detail message
     */
    public GpuLaunchException(String message) {
        super(message);
        this.kernelName = null;
        this.gridShape = null;
    }

    /**
     * Constructs a {@code GpuLaunchException} with the given message and
     * cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public GpuLaunchException(String message, Throwable cause) {
        super(message, cause);
        this.kernelName = null;
        this.gridShape = null;
    }

    /**
     * Constructs a {@code GpuLaunchException} with the given cause.
     *
     * @param cause the underlying cause
     */
    public GpuLaunchException(Throwable cause) {
        super(cause);
        this.kernelName = null;
        this.gridShape = null;
    }

    /**
     * Constructs a {@code GpuLaunchException} with the given message and
     * native error code.
     *
     * @param message   the detail message
     * @param errorCode the underlying CUDA error code
     */
    public GpuLaunchException(String message, int errorCode) {
        super(message, errorCode);
        this.kernelName = null;
        this.gridShape = null;
    }

    /**
     * Constructs a {@code GpuLaunchException} with the given message, cause
     * and native error code.
     *
     * @param message   the detail message
     * @param cause     the underlying cause
     * @param errorCode the underlying CUDA error code
     */
    public GpuLaunchException(String message, Throwable cause, int errorCode) {
        super(message, cause, errorCode);
        this.kernelName = null;
        this.gridShape = null;
    }

    /**
     * Constructs a {@code GpuLaunchException} with the given message, native
     * error code, kernel name, and a free-form grid-shape description.
     *
     * @param message    the detail message
     * @param errorCode  the underlying CUDA error code
     * @param kernelName the name of the kernel that failed to launch, or
     *                   {@code null} if unknown
     * @param gridShape  free-form representation of the grid/block shape used
     *                   at launch, or {@code null} if unknown
     */
    public GpuLaunchException(String message, int errorCode, String kernelName, String gridShape) {
        super(message, errorCode);
        this.kernelName = kernelName;
        this.gridShape = gridShape;
    }

    /**
     * Constructs a {@code GpuLaunchException} with the given message, cause,
     * native error code, kernel name, and a free-form grid-shape description.
     *
     * @param message    the detail message
     * @param cause      the underlying cause
     * @param errorCode  the underlying CUDA error code
     * @param kernelName the name of the kernel that failed to launch, or
     *                   {@code null} if unknown
     * @param gridShape  free-form representation of the grid/block shape used
     *                   at launch, or {@code null} if unknown
     * @since 0.1.0
     */
    public GpuLaunchException(String message, Throwable cause, int errorCode,
                              String kernelName, String gridShape) {
        super(message, cause, errorCode);
        this.kernelName = kernelName;
        this.gridShape = gridShape;
    }

    /**
     * Returns the name of the kernel that failed to launch, or {@code null}
     * if the name is not known.
     *
     * @return the kernel name, or {@code null}
     */
    public String kernelName() {
        return kernelName;
    }

    /**
     * Returns the free-form grid-shape description associated with the failed
     * launch, or {@code null} if not known.
     *
     * @return the grid-shape description, or {@code null}
     */
    public String gridShape() {
        return gridShape;
    }

    /**
     * Returns the standard {@link Throwable#toString()} representation,
     * optionally suffixed with kernel and grid-shape diagnostic information
     * when available.
     *
     * @return the diagnostic string representation
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(super.toString());
        if (kernelName != null) {
            sb.append(" kernel=").append(kernelName);
        }
        if (gridShape != null) {
            sb.append(" gridShape=").append(gridShape);
        }
        return sb.toString();
    }
}
