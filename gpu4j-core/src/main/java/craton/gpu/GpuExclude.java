// SPDX-License-Identifier: Apache-2.0
// Copyright 2025-2026 Craton Software Company (https://craton.com.ar)

package craton.gpu;

import java.lang.annotation.*;

/**
 * Forces the analyzer to blacklist a method from GPU offload, overriding any
 * {@link GpuKernel} annotation.
 *
 * <p><b>Precedence:</b> when both {@code @GpuExclude} and {@link GpuKernel} are applied
 * to the same method, {@code @GpuExclude} wins and the method is never offloaded.</p>
 *
 * <p>On a method that is not also annotated with {@link GpuKernel}, this annotation is
 * a no-op (the method was not a GPU offload candidate to begin with).</p>
 *
 * <p>The annotation is <b>not transitive to overrides</b>: a subclass overriding an
 * excluded method does not inherit the exclusion and must repeat the annotation if
 * the same behavior is desired.</p>
 *
 * @since 0.1.0
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface GpuExclude {
    /**
     * Informational explanation for the exclusion.
     *
     * <p>The value is purely informational and does not affect analyzer behavior. It
     * may be surfaced in compile-time diagnostics or in the analyzer's emitted JSON
     * log. An empty string (the default) means no reason was given.</p>
     *
     * @return human-readable reason for the exclusion, or empty string if unspecified
     */
    String reason() default "";
}
