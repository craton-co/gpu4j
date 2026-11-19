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
 * Constructor and hierarchy tests for {@link GpuOutOfMemoryException}.
 */
class GpuOutOfMemoryExceptionTest {

    @Test
    void extendsGpuException() {
        assertThat(GpuException.class).isAssignableFrom(GpuOutOfMemoryException.class);
    }

    @Test
    void extendsRuntimeExceptionTransitively() {
        assertThat(RuntimeException.class).isAssignableFrom(GpuOutOfMemoryException.class);
    }

    @Test
    void messageConstructor_storesMessageAndDefaultsErrorCodeToMinusOne() {
        GpuOutOfMemoryException ex = new GpuOutOfMemoryException("device oom");

        assertThat(ex.getMessage()).isEqualTo("device oom");
        assertThat(ex.getCause()).isNull();
        assertThat(ex.errorCode()).isEqualTo(-1);
    }

    @Test
    void messageCauseConstructor_storesBoth() {
        Throwable cause = new IllegalStateException("root");
        GpuOutOfMemoryException ex = new GpuOutOfMemoryException("device oom", cause);

        assertThat(ex.getMessage()).isEqualTo("device oom");
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.errorCode()).isEqualTo(-1);
    }

    @Test
    void causeOnlyConstructor_storesCause() {
        Throwable cause = new IllegalStateException("root");
        GpuOutOfMemoryException ex = new GpuOutOfMemoryException(cause);

        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.errorCode()).isEqualTo(-1);
    }

    @Test
    void messageErrorCodeConstructor_propagatesCodeToBaseClass() {
        GpuOutOfMemoryException ex = new GpuOutOfMemoryException("oom", 2);

        assertThat(ex.getMessage()).isEqualTo("oom");
        assertThat(ex.errorCode()).isEqualTo(2);
    }

    @Test
    void canBeCaughtAsGpuException() {
        GpuException base = new GpuOutOfMemoryException("x");
        assertThat(base).isInstanceOf(GpuOutOfMemoryException.class);
    }

    @Test
    void messageCauseErrorCodeConstructor_storesAllThree() {
        Throwable cause = new IllegalStateException("root");
        GpuOutOfMemoryException ex = new GpuOutOfMemoryException("oom", cause, 2);

        assertThat(ex.getMessage()).isEqualTo("oom");
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.errorCode()).isEqualTo(2);
    }

    @Test
    void diagnosticConstructor_storesAllFields() {
        GpuOutOfMemoryException ex = new GpuOutOfMemoryException("oom", 1024L, 512L, 3);

        assertThat(ex.getMessage()).isEqualTo("oom");
        assertThat(ex.requestedBytes()).isEqualTo(1024L);
        assertThat(ex.availableBytes()).isEqualTo(512L);
        assertThat(ex.deviceId()).isEqualTo(3);
    }

    @Test
    void diagnosticFields_defaultToMinusOneForOtherConstructors() {
        GpuOutOfMemoryException a = new GpuOutOfMemoryException("x");
        assertThat(a.requestedBytes()).isEqualTo(-1L);
        assertThat(a.availableBytes()).isEqualTo(-1L);
        assertThat(a.deviceId()).isEqualTo(-1);

        GpuOutOfMemoryException b = new GpuOutOfMemoryException("x", new IllegalStateException("r"));
        assertThat(b.requestedBytes()).isEqualTo(-1L);
        assertThat(b.availableBytes()).isEqualTo(-1L);
        assertThat(b.deviceId()).isEqualTo(-1);

        GpuOutOfMemoryException c = new GpuOutOfMemoryException(new IllegalStateException("r"));
        assertThat(c.requestedBytes()).isEqualTo(-1L);
        assertThat(c.availableBytes()).isEqualTo(-1L);
        assertThat(c.deviceId()).isEqualTo(-1);

        GpuOutOfMemoryException d = new GpuOutOfMemoryException("x", 7);
        assertThat(d.requestedBytes()).isEqualTo(-1L);
        assertThat(d.availableBytes()).isEqualTo(-1L);
        assertThat(d.deviceId()).isEqualTo(-1);

        GpuOutOfMemoryException e = new GpuOutOfMemoryException("x", new IllegalStateException("r"), 7);
        assertThat(e.requestedBytes()).isEqualTo(-1L);
        assertThat(e.availableBytes()).isEqualTo(-1L);
        assertThat(e.deviceId()).isEqualTo(-1);
    }

    @Test
    void toString_includesDiagnosticsWhenSet() {
        GpuOutOfMemoryException ex = new GpuOutOfMemoryException("oom", 1024L, 512L, 3);

        assertThat(ex.toString()).contains("requested=1024 available=512 device=3");
    }

    @Test
    void toString_omitsDiagnosticsWhenAllDefault() {
        GpuOutOfMemoryException ex = new GpuOutOfMemoryException("oom");

        assertThat(ex.toString())
            .doesNotContain("requested=")
            .doesNotContain("available=")
            .doesNotContain("device=");
    }

    @Test
    void errorCodeDiagnosticConstructor_storesErrorCodeAndAllFields() {
        GpuOutOfMemoryException ex =
                new GpuOutOfMemoryException("oom", 2, 1024L, 512L, 3);

        assertThat(ex.getMessage()).isEqualTo("oom");
        assertThat(ex.getCause()).isNull();
        assertThat(ex.errorCode()).isEqualTo(2);
        assertThat(ex.requestedBytes()).isEqualTo(1024L);
        assertThat(ex.availableBytes()).isEqualTo(512L);
        assertThat(ex.deviceId()).isEqualTo(3);
    }

    @Test
    void messageCauseErrorCodeDiagnosticConstructor_storesAllFields() {
        Throwable cause = new IllegalStateException("root");
        GpuOutOfMemoryException ex =
                new GpuOutOfMemoryException("oom", cause, 2, 1024L, 512L, 3);

        assertThat(ex.getMessage()).isEqualTo("oom");
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.errorCode()).isEqualTo(2);
        assertThat(ex.requestedBytes()).isEqualTo(1024L);
        assertThat(ex.availableBytes()).isEqualTo(512L);
        assertThat(ex.deviceId()).isEqualTo(3);
    }

    @Test
    void toString_includesDiagnosticsWhenOnlyDeviceIdSet() {
        GpuOutOfMemoryException ex =
                new GpuOutOfMemoryException("oom", -1L, -1L, 3);

        assertThat(ex.toString()).contains("requested=-1 available=-1 device=3");
    }

    @Test
    void toString_includesDiagnosticsWhenOnlyRequestedBytesSet() {
        GpuOutOfMemoryException ex =
                new GpuOutOfMemoryException("oom", 1024L, -1L, -1);

        assertThat(ex.toString()).contains("requested=1024 available=-1 device=-1");
    }

    @Test
    void serialization_roundTripPreservesMessageErrorCodeAndDiagnostics()
            throws IOException, ClassNotFoundException {
        GpuOutOfMemoryException original =
                new GpuOutOfMemoryException("oom", 2, 1024L, 512L, 3);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(original);
        }
        GpuOutOfMemoryException restored;
        try (ObjectInputStream ois =
                     new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            restored = (GpuOutOfMemoryException) ois.readObject();
        }

        assertThat(restored.getMessage()).isEqualTo("oom");
        assertThat(restored.errorCode()).isEqualTo(2);
        assertThat(restored.requestedBytes()).isEqualTo(1024L);
        assertThat(restored.availableBytes()).isEqualTo(512L);
        assertThat(restored.deviceId()).isEqualTo(3);
    }
}
