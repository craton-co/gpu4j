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
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dedicated tests for {@link GpuKernel}.
 *
 * <p>The annotation uses {@link RetentionPolicy#CLASS}, so it is not visible to
 * {@code isAnnotationPresent} on a {@link Method} at runtime. To verify that
 * reflection can still round-trip the declared values when they <em>are</em>
 * visible (annotation methods always retain their {@code default} values via
 * the {@link Method#getDefaultValue()} mirror, and custom values can be checked
 * the same way), we reflect directly on the annotation type and on helper
 * methods inside nested helper classes declared below.</p>
 */
class GpuKernelTest {

    // -------------------- Meta-annotation contract --------------------

    @Test
    void retention_isClass() {
        Retention retention = GpuKernel.class.getAnnotation(Retention.class);

        assertThat(retention).isNotNull();
        assertThat(retention.value()).isEqualTo(RetentionPolicy.CLASS);
    }

    @Test
    void target_isMethod() {
        Target target = GpuKernel.class.getAnnotation(Target.class);

        assertThat(target).isNotNull();
        assertThat(target.value()).containsExactly(ElementType.METHOD);
    }

    @Test
    void isDocumented() {
        Documented documented = GpuKernel.class.getAnnotation(Documented.class);

        assertThat(documented).isNotNull();
    }

    // -------------------- Default values --------------------

    @Test
    void defaults_areExpected() throws NoSuchMethodException {
        Method blockX = GpuKernel.class.getDeclaredMethod("blockX");
        Method blockY = GpuKernel.class.getDeclaredMethod("blockY");
        Method blockZ = GpuKernel.class.getDeclaredMethod("blockZ");
        Method sharedBytes = GpuKernel.class.getDeclaredMethod("sharedBytes");
        Method admit = GpuKernel.class.getDeclaredMethod("admit");
        Method grid = GpuKernel.class.getDeclaredMethod("grid");

        assertThat(blockX.getDefaultValue()).isEqualTo(0);
        assertThat(blockY.getDefaultValue()).isEqualTo(0);
        assertThat(blockZ.getDefaultValue()).isEqualTo(0);
        assertThat(sharedBytes.getDefaultValue()).isEqualTo(0);
        assertThat(admit.getDefaultValue()).isEqualTo(AdmissionHint.STRICT);
        assertThat(grid.getDefaultValue()).isEqualTo(GridShape.ELEMENTWISE);
    }

    // -------------------- Round-trip on a real annotated method --------------------

    @Test
    void customAnnotationNotVisibleAtRuntime_dueToClassRetention() throws Exception {
        // @GpuKernel has RetentionPolicy.CLASS: the annotation is encoded in the
        // .class file (so AOT/compile-time consumers like jit-cuda can read it),
        // but it is discarded by the classloader and therefore not visible to
        // reflection at runtime. Lock that documented behavior in.
        Method m = CustomHelper.class.getDeclaredMethod("customKernel");
        assertThat(m.getAnnotation(GpuKernel.class)).isNull();
    }

    // -------------------- Member surface --------------------

    @Test
    void membersExposed() {
        Method[] declared = GpuKernel.class.getDeclaredMethods();
        List<String> names = Arrays.stream(declared)
                .map(Method::getName)
                .sorted()
                .collect(Collectors.toList());

        assertThat(names).containsExactlyInAnyOrder(
                "grid",
                "blockX",
                "blockY",
                "blockZ",
                "sharedBytes",
                "admit");

        // Return-type contract.
        assertThat(member("grid").getReturnType()).isEqualTo(GridShape.class);
        assertThat(member("blockX").getReturnType()).isEqualTo(int.class);
        assertThat(member("blockY").getReturnType()).isEqualTo(int.class);
        assertThat(member("blockZ").getReturnType()).isEqualTo(int.class);
        assertThat(member("sharedBytes").getReturnType()).isEqualTo(int.class);
        assertThat(member("admit").getReturnType()).isEqualTo(AdmissionHint.class);

        // Every element must declare a default so call sites can omit it.
        for (Method m : declared) {
            assertThat(m.getDefaultValue())
                    .as("annotation element '%s' must declare a default value", m.getName())
                    .isNotNull();
        }
    }

    private static Method member(String name) {
        try {
            return GpuKernel.class.getDeclaredMethod(name);
        } catch (NoSuchMethodException e) {
            throw new AssertionError("missing annotation member: " + name, e);
        }
    }

    // -------------------- Nested helpers carrying real @GpuKernel sites --------------------

    /** Helper holding a method annotated with {@code @GpuKernel} at its defaults. */
    private static final class DefaultsHelper {
        @GpuKernel
        private static void defaultKernel() {
            // body intentionally empty; method exists only as an annotation target.
        }

        // Reference to silence "unused" warnings on some toolchains while keeping
        // the method discoverable via reflection if a future test wants it.
        @SuppressWarnings("unused")
        static Runnable ref() {
            return DefaultsHelper::defaultKernel;
        }
    }

    /** Helper holding a method annotated with {@code @GpuKernel} at non-default values. */
    private static final class CustomHelper {
        @GpuKernel(
                grid = GridShape.BLOCK_REDUCTION,
                blockX = 128,
                blockY = 2,
                blockZ = 1,
                sharedBytes = 4096,
                admit = AdmissionHint.ALLOW_INTRINSIC_CALLS)
        private static void customKernel() {
            // body intentionally empty; method exists only as an annotation target.
        }

        @SuppressWarnings("unused")
        static Runnable ref() {
            return CustomHelper::customKernel;
        }
    }
}
