// SPDX-License-Identifier: Apache-2.0
// Copyright 2025-2026 Craton Software Company (https://craton.com.ar)

package craton.gpu;

/**
 * Asynchronous FIFO execution queue within a {@link GpuExecutor} context.
 *
 * <p>Operations invoked after {@link #close()} throw {@link IllegalStateException}.</p>
 *
 * @since 0.1.0
 */
public interface GpuStream extends AutoCloseable {

    /**
     * Returns the native handle identifying this stream.
     *
     * @return non-zero native stream handle
     * @throws IllegalStateException if this stream has been closed
     */
    long handle();

    /**
     * Enqueues a static kernel method onto this stream. The underlying submission lifetime is
     * owned by this stream and drained automatically on {@link #synchronize()} or {@link #close()}.
     *
     * @param className  slash-delimited or binary class name
     * @param methodName static kernel method name
     * @param descriptor JVM method descriptor
     * @param args       kernel arguments
     * @throws IllegalStateException if this stream has been closed
     * @throws GpuException          if the kernel dispatch was not recorded
     * @since 0.1.0
     */
    void dispatch(String className, String methodName, String descriptor, Object... args);

    /**
     * Blocks until all work previously enqueued on this stream has completed on the device and
     * surfaces the first failed submission as a {@link GpuException}.
     *
     * @throws InterruptedException  if interrupted while waiting
     * @throws IllegalStateException if this stream has been closed
     * @throws GpuException          if any enqueued operation failed on the device
     * @since 0.1.0
     */
    void synchronize() throws InterruptedException;

    /**
     * Releases all pending stream submissions and destroys the native stream handle. Idempotent
     * across repeated calls.
     *
     * @throws GpuException if destroying the native stream fails
     */
    @Override
    void close();
}
