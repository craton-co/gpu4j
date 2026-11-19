// SPDX-License-Identifier: Apache-2.0
// Copyright 2025-2026 Craton Software Company (https://craton.com.ar)

package craton.gpu;

import java.lang.annotation.*;

/**
 * Marks a static method as a candidate for GPU offload and configures its launch
 * shape and admission policy.
 *
 * <p><b>Kernel lifecycle:</b> compilation happens ahead-of-time during the cargo
 * build via the jit-cuda toolchain. The resulting PTX is cached per-class. At run
 * time, a {@code @GpuKernel}-annotated method becomes dispatchable via
 * {@link GpuExecutor#submit(String, String, String, Object...)},
 * {@link GpuExecutor#prepare(String, String, String)}, or {@link GpuStream#dispatch}.</p>
 *
 * <p>At run time, the runtime resolves the compiled kernel per-executor by the tuple
 * {@code (className, methodName, descriptor)}.</p>
 *
 * @since 0.1.0
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface GpuKernel {
    /**
     * Selects the lowering pattern the GPU code generator should target for this kernel.
     *
     * @return the grid/launch shape; see {@link GridShape} for the supported patterns
     */
    GridShape grid()        default GridShape.ELEMENTWISE;

    /**
     * CUDA block dimension along the X axis.
     *
     * <p>A value of {@code 0} means "auto-pick by jit-cuda" (not invalid): the
     * toolchain will choose a block size based on the kernel and target device.</p>
     *
     * @return the X block dimension, or {@code 0} to defer to jit-cuda
     */
    int       blockX()      default 0;

    /**
     * CUDA block dimension along the Y axis.
     *
     * <p>A value of {@code 0} means "auto-pick by jit-cuda" (not invalid): the
     * toolchain will choose a block size based on the kernel and target device.</p>
     *
     * @return the Y block dimension, or {@code 0} to defer to jit-cuda
     */
    int       blockY()      default 0;

    /**
     * CUDA block dimension along the Z axis.
     *
     * <p>A value of {@code 0} means "auto-pick by jit-cuda" (not invalid): the
     * toolchain will choose a block size based on the kernel and target device.</p>
     *
     * @return the Z block dimension, or {@code 0} to defer to jit-cuda
     */
    int       blockZ()      default 0;

    /**
     * Bytes of dynamic shared memory to allocate per block.
     *
     * <p>Must be non-negative. The upper bound is device-dependent; typical limits
     * are 48&nbsp;KB or 96&nbsp;KB on modern devices. Exceeding the device limit
     * causes a launch-time failure.</p>
     *
     * @return the per-block dynamic shared-memory budget in bytes
     */
    int       sharedBytes() default 0;

    /**
     * Admission hint controlling which analyzer rejections are relaxed for this kernel.
     *
     * @return the admission policy; see {@link AdmissionHint} for the supported relaxations
     */
    AdmissionHint admit()   default AdmissionHint.STRICT;
}
