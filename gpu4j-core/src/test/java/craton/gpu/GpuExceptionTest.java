// SPDX-License-Identifier: Apache-2.0
// Copyright 2025-2026 Craton Software Company (https://craton.com.ar)

package craton.gpu;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link GpuException} covering all public constructors, the
 * {@link GpuException#errorCode()} accessor, and required serialization metadata.
 */
class GpuExceptionTest {

    @Test
    void isSubclassOfRuntimeException() {
        assertThat(RuntimeException.class).isAssignableFrom(GpuException.class);
    }

    @Test
    void messageConstructor_storesMessageAndDefaultsErrorCodeToMinusOne() {
        GpuException ex = new GpuException("boom");

        assertThat(ex.getMessage()).isEqualTo("boom");
        assertThat(ex.getCause()).isNull();
        assertThat(ex.errorCode()).isEqualTo(-1);
    }

    @Test
    void messageCauseConstructor_storesBothAndDefaultsErrorCodeToMinusOne() {
        Throwable cause = new IllegalStateException("root");
        GpuException ex = new GpuException("boom", cause);

        assertThat(ex.getMessage()).isEqualTo("boom");
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.errorCode()).isEqualTo(-1);
    }

    @Test
    void causeOnlyConstructor_storesCauseAndDefaultsErrorCodeToMinusOne() {
        Throwable cause = new IllegalStateException("root");
        GpuException ex = new GpuException(cause);

        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.errorCode()).isEqualTo(-1);
    }

    @Test
    void messageErrorCodeConstructor_storesMessageAndCode() {
        GpuException ex = new GpuException("oom", 2);

        assertThat(ex.getMessage()).isEqualTo("oom");
        assertThat(ex.getCause()).isNull();
        assertThat(ex.errorCode()).isEqualTo(2);
    }

    @Test
    void messageCauseErrorCodeConstructor_storesAllThree() {
        Throwable cause = new IllegalStateException("root");
        GpuException ex = new GpuException("oom", cause, 700);

        assertThat(ex.getMessage()).isEqualTo("oom");
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.errorCode()).isEqualTo(700);
    }

    @Test
    void errorCode_canBeNegativeOrZero() {
        assertThat(new GpuException("a", -42).errorCode()).isEqualTo(-42);
        assertThat(new GpuException("a", 0).errorCode()).isEqualTo(0);
    }

    @Test
    void serialVersionUID_isDeclared() throws NoSuchFieldException {
        Field f = GpuException.class.getDeclaredField("serialVersionUID");

        int mods = f.getModifiers();
        assertThat(Modifier.isStatic(mods)).isTrue();
        assertThat(Modifier.isFinal(mods)).isTrue();
        assertThat(f.getType()).isEqualTo(long.class);
    }

    @Test
    void causeErrorCodeConstructor_storesCauseAndCode() {
        Throwable cause = new IllegalStateException("root");
        GpuException ex = new GpuException(cause, 700);

        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.errorCode()).isEqualTo(700);
    }

    @Test
    void serialization_roundTripPreservesMessageAndErrorCode() throws IOException, ClassNotFoundException {
        GpuException original = new GpuException("boom", 218);

        GpuException restored = roundTrip(original);

        assertThat(restored.getMessage()).isEqualTo("boom");
        assertThat(restored.errorCode()).isEqualTo(218);
    }

    @Test
    void protectedConstructor_isUsableBySubclass() {
        Throwable cause = new IllegalStateException("root");
        GpuException ex = new ExtendedGpuException("boom", cause, false, false);

        assertThat(ex.getMessage()).isEqualTo("boom");
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.errorCode()).isEqualTo(-1);
        assertThat(ex.getStackTrace()).isEmpty();
        ex.addSuppressed(new IllegalStateException("ignored"));
        assertThat(ex.getSuppressed()).isEmpty();
    }

    /** Test-only subclass exercising the protected 4-arg constructor. */
    private static final class ExtendedGpuException extends GpuException {
        private static final long serialVersionUID = 1L;

        ExtendedGpuException(String message, Throwable cause,
                             boolean enableSuppression, boolean writableStackTrace) {
            super(message, cause, enableSuppression, writableStackTrace);
        }
    }

    private static GpuException roundTrip(GpuException ex)
            throws IOException, ClassNotFoundException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(ex);
        }
        try (ObjectInputStream ois =
                     new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            return (GpuException) ois.readObject();
        }
    }
}
