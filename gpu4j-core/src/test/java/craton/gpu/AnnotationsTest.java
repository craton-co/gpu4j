// SPDX-License-Identifier: Apache-2.0
// Copyright 2025-2026 Craton Software Company (https://craton.com.ar)

package craton.gpu;

import org.junit.jupiter.api.Test;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reflection-based tests for the GPU annotations. The annotations use
 * {@link RetentionPolicy#CLASS}, so they are not visible to {@code
 * isAnnotationPresent} at runtime; we verify their meta-annotations
 * ({@link Retention}, {@link Target}, {@link Documented}) and default values
 * by reflecting on the annotation classes themselves.
 */
class AnnotationsTest {

    // -------------------- @GpuKernel --------------------

    @Test
    void gpuKernel_hasClassRetention() {
        Retention retention = GpuKernel.class.getAnnotation(Retention.class);

        assertThat(retention).isNotNull();
        assertThat(retention.value()).isEqualTo(RetentionPolicy.CLASS);
    }

    @Test
    void gpuKernel_targetsMethodsOnly() {
        Target target = GpuKernel.class.getAnnotation(Target.class);

        assertThat(target).isNotNull();
        assertThat(target.value()).containsExactly(ElementType.METHOD);
    }

    @Test
    void gpuKernel_isAnAnnotationType() {
        assertThat(GpuKernel.class.isAnnotation()).isTrue();
    }

    // -------------------- @EnableGpuAsync --------------------

    @Test
    void enableGpuAsync_hasClassRetention() {
        Retention retention = EnableGpuAsync.class.getAnnotation(Retention.class);

        assertThat(retention).isNotNull();
        assertThat(retention.value()).isEqualTo(RetentionPolicy.CLASS);
    }

    @Test
    void enableGpuAsync_targetsTypeOnly() {
        Target target = EnableGpuAsync.class.getAnnotation(Target.class);

        assertThat(target).isNotNull();
        assertThat(target.value()).containsExactly(ElementType.TYPE);
    }

    @Test
    void enableGpuAsync_isDocumented() {
        Documented documented = EnableGpuAsync.class.getAnnotation(Documented.class);
        assertThat(documented).isNotNull();
    }

    @Test
    void enableGpuAsync_warmup_defaultsToZero() throws NoSuchMethodException {
        Method warmup = EnableGpuAsync.class.getDeclaredMethod("warmup");

        assertThat(warmup.getReturnType()).isEqualTo(int.class);
        assertThat(warmup.getDefaultValue()).isEqualTo(0);
    }

    @Test
    void enableGpuAsync_isAnAnnotationType() {
        assertThat(EnableGpuAsync.class.isAnnotation()).isTrue();
    }

    // -------------------- @GpuExclude --------------------

    @Test
    void gpuExclude_hasClassRetention() {
        Retention retention = GpuExclude.class.getAnnotation(Retention.class);

        assertThat(retention).isNotNull();
        assertThat(retention.value()).isEqualTo(RetentionPolicy.CLASS);
    }

    @Test
    void gpuExclude_targetsMethodsOnly() {
        Target target = GpuExclude.class.getAnnotation(Target.class);

        assertThat(target).isNotNull();
        assertThat(target.value()).containsExactly(ElementType.METHOD);
    }

    @Test
    void gpuExclude_reason_defaultsToEmptyString() throws NoSuchMethodException {
        Method reason = GpuExclude.class.getDeclaredMethod("reason");

        assertThat(reason.getReturnType()).isEqualTo(String.class);
        assertThat(reason.getDefaultValue()).isEqualTo("");
    }

    @Test
    void gpuExclude_isAnAnnotationType() {
        assertThat(GpuExclude.class.isAnnotation()).isTrue();
    }
}
