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
 * Constructor and hierarchy tests for {@link GpuLaunchException}.
 */
class GpuLaunchExceptionTest {

    @Test
    void extendsGpuException() {
        assertThat(GpuException.class).isAssignableFrom(GpuLaunchException.class);
    }

    @Test
    void extendsRuntimeExceptionTransitively() {
        assertThat(RuntimeException.class).isAssignableFrom(GpuLaunchException.class);
    }

    @Test
    void messageConstructor_storesMessageAndDefaultsErrorCodeToMinusOne() {
        GpuLaunchException ex = new GpuLaunchException("launch failed");

        assertThat(ex.getMessage()).isEqualTo("launch failed");
        assertThat(ex.getCause()).isNull();
        assertThat(ex.errorCode()).isEqualTo(-1);
    }

    @Test
    void messageCauseConstructor_storesBoth() {
        Throwable cause = new IllegalStateException("root");
        GpuLaunchException ex = new GpuLaunchException("launch failed", cause);

        assertThat(ex.getMessage()).isEqualTo("launch failed");
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.errorCode()).isEqualTo(-1);
    }

    @Test
    void causeOnlyConstructor_storesCause() {
        Throwable cause = new IllegalStateException("root");
        GpuLaunchException ex = new GpuLaunchException(cause);

        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.errorCode()).isEqualTo(-1);
    }

    @Test
    void messageErrorCodeConstructor_propagatesCodeToBaseClass() {
        GpuLaunchException ex = new GpuLaunchException("bad launch", 9);

        assertThat(ex.getMessage()).isEqualTo("bad launch");
        assertThat(ex.errorCode()).isEqualTo(9);
    }

    @Test
    void canBeCaughtAsGpuException() {
        GpuException base = new GpuLaunchException("x");
        assertThat(base).isInstanceOf(GpuLaunchException.class);
    }

    @Test
    void messageCauseErrorCodeConstructor_storesAllThree() {
        Throwable cause = new IllegalStateException("root");
        GpuLaunchException ex = new GpuLaunchException("bad launch", cause, 9);

        assertThat(ex.getMessage()).isEqualTo("bad launch");
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.errorCode()).isEqualTo(9);
    }

    @Test
    void kernelAndGridShapeConstructor_storesAllFields() {
        GpuLaunchException ex = new GpuLaunchException("bad launch", 9, "vec_add", "<<<32,256>>>");

        assertThat(ex.getMessage()).isEqualTo("bad launch");
        assertThat(ex.errorCode()).isEqualTo(9);
        assertThat(ex.kernelName()).isEqualTo("vec_add");
        assertThat(ex.gridShape()).isEqualTo("<<<32,256>>>");
    }

    @Test
    void kernelName_and_gridShape_defaultToNullForOtherConstructors() {
        GpuLaunchException a = new GpuLaunchException("x");
        assertThat(a.kernelName()).isNull();
        assertThat(a.gridShape()).isNull();

        GpuLaunchException b = new GpuLaunchException("x", new IllegalStateException("r"));
        assertThat(b.kernelName()).isNull();
        assertThat(b.gridShape()).isNull();

        GpuLaunchException c = new GpuLaunchException(new IllegalStateException("r"));
        assertThat(c.kernelName()).isNull();
        assertThat(c.gridShape()).isNull();

        GpuLaunchException d = new GpuLaunchException("x", 7);
        assertThat(d.kernelName()).isNull();
        assertThat(d.gridShape()).isNull();

        GpuLaunchException e = new GpuLaunchException("x", new IllegalStateException("r"), 7);
        assertThat(e.kernelName()).isNull();
        assertThat(e.gridShape()).isNull();
    }

    @Test
    void toString_includesKernelAndGridShapeWhenSet() {
        GpuLaunchException ex = new GpuLaunchException("bad launch", 9, "vec_add", "<<<32,256>>>");

        assertThat(ex.toString())
            .contains("kernel=vec_add")
            .contains("gridShape=<<<32,256>>>");
    }

    @Test
    void toString_omitsFieldsWhenNull() {
        GpuLaunchException ex = new GpuLaunchException("bad launch", 9);

        assertThat(ex.toString())
            .doesNotContain("kernel=")
            .doesNotContain("gridShape=");
    }

    @Test
    void messageCauseErrorCodeKernelGridShapeConstructor_storesAllFields() {
        Throwable cause = new IllegalStateException("root");
        GpuLaunchException ex =
                new GpuLaunchException("bad launch", cause, 9, "vec_add", "<<<32,256>>>");

        assertThat(ex.getMessage()).isEqualTo("bad launch");
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.errorCode()).isEqualTo(9);
        assertThat(ex.kernelName()).isEqualTo("vec_add");
        assertThat(ex.gridShape()).isEqualTo("<<<32,256>>>");
    }

    @Test
    void toString_includesKernelButOmitsGridShapeWhenGridShapeNull() {
        GpuLaunchException ex = new GpuLaunchException("bad launch", 9, "vec_add", null);

        assertThat(ex.toString())
            .contains("kernel=vec_add")
            .doesNotContain("gridShape=");
    }

    @Test
    void toString_includesGridShapeButOmitsKernelWhenKernelNull() {
        GpuLaunchException ex = new GpuLaunchException("bad launch", 9, null, "<<<32,256>>>");

        assertThat(ex.toString())
            .contains("gridShape=<<<32,256>>>")
            .doesNotContain("kernel=");
    }

    @Test
    void serialization_roundTripPreservesMessageErrorCodeKernelAndGridShape()
            throws IOException, ClassNotFoundException {
        GpuLaunchException original =
                new GpuLaunchException("bad launch", 9, "vec_add", "<<<32,256>>>");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(original);
        }
        GpuLaunchException restored;
        try (ObjectInputStream ois =
                     new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            restored = (GpuLaunchException) ois.readObject();
        }

        assertThat(restored.getMessage()).isEqualTo("bad launch");
        assertThat(restored.errorCode()).isEqualTo(9);
        assertThat(restored.kernelName()).isEqualTo("vec_add");
        assertThat(restored.gridShape()).isEqualTo("<<<32,256>>>");
    }
}
