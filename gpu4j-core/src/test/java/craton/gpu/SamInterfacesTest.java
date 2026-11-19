// SPDX-License-Identifier: Apache-2.0
// Copyright 2025-2026 Craton Software Company (https://craton.com.ar)

package craton.gpu;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the GPU SAM (single-abstract-method) functional interfaces:
 * {@link GpuCallable}, {@link GpuFunction}, and {@link GpuRunnable}.
 *
 * <p>Each is required to (1) carry the {@link FunctionalInterface} marker, (2)
 * declare exactly one abstract method, and (3) be assignable from a lambda of
 * the appropriate shape.</p>
 */
class SamInterfacesTest {

    private static int countAbstractMethods(Class<?> type) {
        int count = 0;
        for (Method m : type.getDeclaredMethods()) {
            if (Modifier.isAbstract(m.getModifiers())) {
                count++;
            }
        }
        return count;
    }

    // -------------------- GpuCallable --------------------

    @Test
    void gpuCallable_hasFunctionalInterfaceAnnotation() {
        assertThat(GpuCallable.class.getAnnotation(FunctionalInterface.class)).isNotNull();
    }

    @Test
    void gpuCallable_isAnInterface() {
        assertThat(GpuCallable.class.isInterface()).isTrue();
    }

    @Test
    void gpuCallable_hasExactlyOneAbstractMethod() {
        assertThat(countAbstractMethods(GpuCallable.class)).isEqualTo(1);
    }

    @Test
    void gpuCallable_lambdaAssignmentCompilesAndRuns() throws Exception {
        GpuCallable<Integer> c = () -> 42;
        assertThat(c.call()).isEqualTo(42);
    }

    // -------------------- GpuFunction --------------------

    @Test
    void gpuFunction_hasFunctionalInterfaceAnnotation() {
        assertThat(GpuFunction.class.getAnnotation(FunctionalInterface.class)).isNotNull();
    }

    @Test
    void gpuFunction_isAnInterface() {
        assertThat(GpuFunction.class.isInterface()).isTrue();
    }

    @Test
    void gpuFunction_hasExactlyOneAbstractMethod() {
        assertThat(countAbstractMethods(GpuFunction.class)).isEqualTo(1);
    }

    @Test
    void gpuFunction_lambdaAssignmentCompilesAndRuns() throws Exception {
        GpuFunction<Integer, String> f = i -> "v=" + i;
        assertThat(f.apply(7)).isEqualTo("v=7");
    }

    // -------------------- GpuRunnable --------------------

    @Test
    void gpuRunnable_hasFunctionalInterfaceAnnotation() {
        assertThat(GpuRunnable.class.getAnnotation(FunctionalInterface.class)).isNotNull();
    }

    @Test
    void gpuRunnable_isAnInterface() {
        assertThat(GpuRunnable.class.isInterface()).isTrue();
    }

    @Test
    void gpuRunnable_hasExactlyOneAbstractMethod() {
        assertThat(countAbstractMethods(GpuRunnable.class)).isEqualTo(1);
    }

    @Test
    void gpuRunnable_lambdaAssignmentCompilesAndRuns() throws Exception {
        int[] flag = new int[1];
        GpuRunnable r = () -> flag[0] = 1;
        r.run();
        assertThat(flag[0]).isEqualTo(1);
    }

    // -------------------- Checked exception propagation --------------------

    @Test
    void runnable_propagatesCheckedException() {
        GpuRunnable r = () -> {
            throw new Exception("boom-runnable");
        };
        assertThatThrownBy(r::run)
                .isInstanceOf(Exception.class)
                .hasMessage("boom-runnable");
    }

    @Test
    void callable_propagatesCheckedException() {
        GpuCallable<Integer> c = () -> {
            throw new Exception("boom-callable");
        };
        assertThatThrownBy(c::call)
                .isInstanceOf(Exception.class)
                .hasMessage("boom-callable");
    }

    @Test
    void function_propagatesCheckedException() {
        GpuFunction<Integer, String> f = i -> {
            throw new Exception("boom-function");
        };
        assertThatThrownBy(() -> f.apply(1))
                .isInstanceOf(Exception.class)
                .hasMessage("boom-function");
    }

    // -------------------- Generics variance smoke test --------------------

    private static Number applyVariant(GpuFunction<? super String, ? extends Number> f, String input) throws Exception {
        return f.apply(input);
    }

    @Test
    void gpuFunction_acceptsVariantLambda() throws Exception {
        Number result = applyVariant((String s) -> Integer.valueOf(s), "123");
        assertThat(result).isEqualTo(123);
    }
}
