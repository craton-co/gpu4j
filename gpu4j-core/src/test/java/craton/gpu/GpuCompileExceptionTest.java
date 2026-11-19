// SPDX-License-Identifier: Apache-2.0
// Copyright 2025-2026 Craton Software Company (https://craton.com.ar)

package craton.gpu;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Constructor and hierarchy tests for {@link GpuCompileException}.
 */
class GpuCompileExceptionTest {

    @Test
    void extendsGpuException() {
        assertThat(GpuException.class).isAssignableFrom(GpuCompileException.class);
    }

    @Test
    void extendsRuntimeExceptionTransitively() {
        assertThat(RuntimeException.class).isAssignableFrom(GpuCompileException.class);
    }

    @Test
    void messageConstructor_storesMessageAndDefaultsErrorCodeToMinusOne() {
        GpuCompileException ex = new GpuCompileException("ptx compile failed");

        assertThat(ex.getMessage()).isEqualTo("ptx compile failed");
        assertThat(ex.getCause()).isNull();
        assertThat(ex.errorCode()).isEqualTo(-1);
    }

    @Test
    void messageCauseConstructor_storesBoth() {
        Throwable cause = new IllegalStateException("root");
        GpuCompileException ex = new GpuCompileException("boom", cause);

        assertThat(ex.getMessage()).isEqualTo("boom");
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.errorCode()).isEqualTo(-1);
    }

    @Test
    void causeOnlyConstructor_storesCause() {
        Throwable cause = new IllegalStateException("root");
        GpuCompileException ex = new GpuCompileException(cause);

        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.errorCode()).isEqualTo(-1);
    }

    @Test
    void messageErrorCodeConstructor_propagatesCodeToBaseClass() {
        GpuCompileException ex = new GpuCompileException("compile bad", 218);

        assertThat(ex.getMessage()).isEqualTo("compile bad");
        assertThat(ex.errorCode()).isEqualTo(218);
    }

    @Test
    void canBeCaughtAsGpuException() {
        GpuException base = new GpuCompileException("x");
        assertThat(base).isInstanceOf(GpuCompileException.class);
    }

    @Test
    void messageCauseErrorCodeConstructor_storesAllThree() {
        Throwable cause = new IllegalStateException("root");
        GpuCompileException ex = new GpuCompileException("compile bad", cause, 218);

        assertThat(ex.getMessage()).isEqualTo("compile bad");
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.errorCode()).isEqualTo(218);
    }

    @Test
    void kernelNameConstructor_storesAllFields() {
        GpuCompileException ex = new GpuCompileException("compile bad", 218, "vec_add");

        assertThat(ex.getMessage()).isEqualTo("compile bad");
        assertThat(ex.errorCode()).isEqualTo(218);
        assertThat(ex.kernelName()).isEqualTo("vec_add");
    }

    @Test
    void kernelName_defaultsToNullForOtherConstructors() {
        assertThat(new GpuCompileException("x").kernelName()).isNull();
        assertThat(new GpuCompileException("x", new IllegalStateException("r")).kernelName()).isNull();
        assertThat(new GpuCompileException(new IllegalStateException("r")).kernelName()).isNull();
        assertThat(new GpuCompileException("x", 7).kernelName()).isNull();
        assertThat(new GpuCompileException("x", new IllegalStateException("r"), 7).kernelName()).isNull();
    }

    @Test
    void toString_includesKernelNameWhenSet() {
        GpuCompileException ex = new GpuCompileException("compile bad", 218, "vec_add");

        assertThat(ex.toString()).contains("kernel=vec_add");
    }

    @Test
    void toString_doesNotIncludeKernelNameSuffixWhenNull() {
        GpuCompileException ex = new GpuCompileException("compile bad", 218);

        assertThat(ex.toString()).doesNotContain("kernel=");
    }

    @Test
    void messageCauseErrorCodeKernelNameConstructor_storesAllFields() {
        Throwable cause = new IllegalStateException("root");
        GpuCompileException ex =
                new GpuCompileException("compile bad", cause, 218, "vec_add");

        assertThat(ex.getMessage()).isEqualTo("compile bad");
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.errorCode()).isEqualTo(218);
        assertThat(ex.kernelName()).isEqualTo("vec_add");
    }

    @Test
    void serialization_roundTripPreservesMessageErrorCodeAndKernelName()
            throws IOException, ClassNotFoundException {
        GpuCompileException original = new GpuCompileException("compile bad", 218, "vec_add");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(original);
        }
        GpuCompileException restored;
        try (ObjectInputStream ois =
                     new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            restored = (GpuCompileException) ois.readObject();
        }

        assertThat(restored.getMessage()).isEqualTo("compile bad");
        assertThat(restored.errorCode()).isEqualTo(218);
        assertThat(restored.kernelName()).isEqualTo("vec_add");
    }
}
