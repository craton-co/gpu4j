// SPDX-License-Identifier: Apache-2.0
// Copyright 2025-2026 Craton Software Company (https://craton.com.ar)

package craton.gpu;

import java.lang.annotation.*;

/**
 * Class-level marker that requests eager warmup compilation of the first
 * {@code N} {@link GpuKernel}-annotated methods when a {@link GpuExecutor} opens.
 *
 * <p><b>Retention rationale:</b> this annotation uses {@link RetentionPolicy#CLASS}
 * rather than {@link RetentionPolicy#RUNTIME} because the jit-cuda toolchain reads
 * it directly from the bytecode at AOT compile time, not via reflection at run time.</p>
 *
 * @since 0.1.0
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface EnableGpuAsync {
    /**
     * Number of {@link GpuKernel}-annotated methods to pre-compile when the executor opens.
     *
     * <p>A value of {@code 0} disables warmup; kernels then compile lazily on first dispatch.
     * The "first N" ordering follows the bytecode declaration order of the methods on the
     * enclosing type, which jit-cuda reads deterministically.</p>
     *
     * <p>If {@code N} exceeds the total number of {@link GpuKernel}-annotated methods on
     * the type, the runtime warms up all of them with no error. Negative values are not
     * permitted and are rejected at compile time by the analyzer.</p>
     *
     * @return the number of kernels to eagerly compile at executor open
     */
    int warmup() default 0;
}
