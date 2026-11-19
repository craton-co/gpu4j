// SPDX-License-Identifier: Apache-2.0
// Copyright 2025-2026 Craton Software Company (https://craton.com.ar)

package craton.gpu;

/**
 * Loosens specific analyzer rejections so otherwise-ineligible methods can be offloaded.
 *
 * <p><b>Wire format stability:</b> the enum constant names and ordinals are part of the
 * stable wire format shared with downstream CratonVM versions. Reordering, renaming, or
 * removing constants will break compatibility with already-compiled artifacts.</p>
 *
 * @since 0.1.0
 */
public enum AdmissionHint {
    /**
     * No relaxations applied. The analyzer rejects any kernel that allocates objects,
     * performs integer division that could divide by zero, or calls non-intrinsic methods.
     */
    STRICT,

    /**
     * Permit object allocation inside the kernel body. The jit-cuda lowering may map
     * such allocations to scratchpad (shared / local) memory rather than rejecting them.
     */
    ALLOW_ALLOCATION,

    /**
     * Permit divides without raising the host's {@link ArithmeticException}. The GPU
     * follows IEEE 754 semantics and returns positive infinity, negative infinity, or
     * NaN as appropriate instead of throwing.
     */
    ALLOW_DIV_BY_ZERO,

    /**
     * Permit calls to a curated set of math intrinsics (for example {@code Math.sin},
     * {@code Math.cos}, {@code Math.sqrt}, and similar) that the analyzer would
     * otherwise treat as disallowed non-intrinsic method calls.
     */
    ALLOW_INTRINSIC_CALLS
}
