// SPDX-License-Identifier: Apache-2.0
// Copyright 2025-2026 Craton Software Company (https://craton.com.ar)

package craton.gpu.internal;

import craton.gpu.GpuCallable;
import craton.gpu.GpuException;
import craton.gpu.GpuFuture;
import craton.gpu.GpuRunnable;
import craton.gpu.GpuStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lifecycle and use-after-free behaviour for {@link GpuExecutorImpl}, using
 * a mocked {@link NativeBridge} so the test never touches the real native
 * layer.
 */
class GpuExecutorImplTest {

    private static final long HANDLE = 0xDEADBEEFL;

    private NativeBridge bridge;

    @BeforeEach
    void installMockBridge() {
        bridge = mock(NativeBridge.class);
        Native.setBridge(bridge);
    }

    @AfterEach
    void restoreDefaultBridge() {
        Native.setBridge(null);
    }

    @Test
    void constructionWithZeroHandleThrows() {
        assertThatThrownBy(() -> new GpuExecutorImpl(0L))
            .isInstanceOf(GpuException.class)
            .hasMessageContaining("invalid executor handle");
    }

    @Test
    void constructionWithNonZeroHandleSucceeds() {
        GpuExecutorImpl exec = new GpuExecutorImpl(HANDLE);

        assertThat(exec.handle()).isEqualTo(HANDLE);
        assertThat(exec.handleForDispatch()).isEqualTo(HANDLE);

        exec.close();
    }

    @Test
    void closeIsIdempotentAndCallsReleaseOnce() {
        GpuExecutorImpl exec = new GpuExecutorImpl(HANDLE);

        exec.close();
        exec.close();
        exec.close();

        verify(bridge, times(1)).releaseExecutor(HANDLE);
    }

    @Test
    void submitAfterCloseThrowsGpuException() {
        GpuExecutorImpl exec = new GpuExecutorImpl(HANDLE);
        exec.close();

        GpuCallable<Integer> task = () -> 42;
        assertThatThrownBy(() -> exec.submit(task))
            .isInstanceOf(GpuException.class)
            .hasMessageContaining("executor closed");
    }

    @Test
    void launchAfterCloseThrowsGpuException() {
        GpuExecutorImpl exec = new GpuExecutorImpl(HANDLE);
        exec.close();

        GpuRunnable task = () -> { };
        assertThatThrownBy(() -> exec.launch(task))
            .isInstanceOf(GpuException.class)
            .hasMessageContaining("executor closed");
    }

    @Test
    void newStreamAfterCloseThrowsGpuException() {
        GpuExecutorImpl exec = new GpuExecutorImpl(HANDLE);
        exec.close();

        assertThatThrownBy(exec::newStream)
            .isInstanceOf(GpuException.class)
            .hasMessageContaining("executor closed");
    }

    @Test
    void handleForDispatchAfterCloseThrowsGpuException() {
        GpuExecutorImpl exec = new GpuExecutorImpl(HANDLE);
        exec.close();

        assertThatThrownBy(exec::handleForDispatch)
            .isInstanceOf(GpuException.class)
            .hasMessageContaining("executor closed");
    }

    @Test
    void submitBeforeCloseInvokesBridgeAndReturnsFuture() {
        @SuppressWarnings("unchecked")
        GpuFuture<Integer> stubFuture = mock(GpuFuture.class);
        when(bridge.submit(anyLong(), ArgumentMatchers.<GpuCallable<Integer>>any()))
            .thenReturn(stubFuture);

        GpuExecutorImpl exec = new GpuExecutorImpl(HANDLE);
        GpuCallable<Integer> task = () -> 7;
        GpuFuture<Integer> result = exec.submit(task);

        assertThat(result).isSameAs(stubFuture);
        verify(bridge).submit(HANDLE, task);

        exec.close();
    }

    @Test
    void concurrentSubmitAndCloseDoNotLeakExceptionsAndReleaseRunsOnce()
            throws InterruptedException {
        @SuppressWarnings("unchecked")
        GpuFuture<Integer> stubFuture = mock(GpuFuture.class);
        when(bridge.submit(anyLong(), ArgumentMatchers.<GpuCallable<Integer>>any()))
            .thenReturn(stubFuture);

        GpuExecutorImpl exec = new GpuExecutorImpl(HANDLE);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> submitterError = new AtomicReference<>();
        AtomicReference<Throwable> closerError = new AtomicReference<>();

        Thread submitter = new Thread(() -> {
            try {
                start.await();
                for (int i = 0; i < 200; i++) {
                    try {
                        exec.submit(() -> 1);
                    } catch (GpuException expected) {
                        // Expected once close lands and races ahead of us.
                    }
                }
            } catch (Throwable t) {
                submitterError.set(t);
            }
        }, "exec-submitter");

        Thread closer = new Thread(() -> {
            try {
                start.await();
                exec.close();
            } catch (Throwable t) {
                closerError.set(t);
            }
        }, "exec-closer");

        submitter.start();
        closer.start();
        start.countDown();

        submitter.join(2000);
        closer.join(2000);

        assertThat(submitter.isAlive()).isFalse();
        assertThat(closer.isAlive()).isFalse();
        assertThat(submitterError.get())
            .as("submitter should only see GpuException (caught) or nothing")
            .isNull();
        assertThat(closerError.get()).isNull();

        // Executor is now closed - subsequent submit should always fail.
        assertThatThrownBy(() -> exec.submit(() -> 0))
            .isInstanceOf(GpuException.class)
            .hasMessageContaining("executor closed");

        // releaseExecutor was invoked exactly once despite the race.
        verify(bridge, times(1)).releaseExecutor(HANDLE);
    }

    // ------------------------------------------------------------------
    // Primitive-specialized submitV / submitScale overloads
    // ------------------------------------------------------------------

    @Test
    void submitV_intArrays_dispatchesViaBridge() {
        @SuppressWarnings("unchecked")
        GpuFuture<Void> stubFuture = mock(GpuFuture.class);
        doReturn(stubFuture).when(bridge)
            .submitMethod(anyLong(), any(), any(), any(), any(Object[].class));

        GpuExecutorImpl exec = new GpuExecutorImpl(HANDLE);
        int[] a = {1, 2, 3};
        int[] b = {4, 5, 6};
        int[] out = new int[3];

        GpuFuture<Void> result =
            exec.submitV("com/example/K", "add", "([I[I[I)V", a, b, out);

        assertThat(result).isSameAs(stubFuture);

        ArgumentCaptor<Object[]> argsCap = ArgumentCaptor.forClass(Object[].class);
        verify(bridge).submitMethod(
            eq(HANDLE), eq("com/example/K"), eq("add"), eq("([I[I[I)V"), argsCap.capture());
        assertThat(argsCap.getValue()).containsExactly(a, b, out);

        exec.close();
    }

    @Test
    void submitV_longArrays_dispatchesViaBridge() {
        @SuppressWarnings("unchecked")
        GpuFuture<Void> stubFuture = mock(GpuFuture.class);
        doReturn(stubFuture).when(bridge)
            .submitMethod(anyLong(), any(), any(), any(), any(Object[].class));

        GpuExecutorImpl exec = new GpuExecutorImpl(HANDLE);
        long[] a = {10L, 20L};
        long[] b = {30L, 40L};
        long[] out = new long[2];

        GpuFuture<Void> result =
            exec.submitV("com/example/K", "add", "([J[J[J)V", a, b, out);

        assertThat(result).isSameAs(stubFuture);

        ArgumentCaptor<Object[]> argsCap = ArgumentCaptor.forClass(Object[].class);
        verify(bridge).submitMethod(
            eq(HANDLE), eq("com/example/K"), eq("add"), eq("([J[J[J)V"), argsCap.capture());
        assertThat(argsCap.getValue()).containsExactly(a, b, out);

        exec.close();
    }

    @Test
    void submitV_floatArrays_dispatchesViaBridge() {
        @SuppressWarnings("unchecked")
        GpuFuture<Void> stubFuture = mock(GpuFuture.class);
        doReturn(stubFuture).when(bridge)
            .submitMethod(anyLong(), any(), any(), any(), any(Object[].class));

        GpuExecutorImpl exec = new GpuExecutorImpl(HANDLE);
        float[] a = {1.0f, 2.0f};
        float[] b = {3.0f, 4.0f};
        float[] out = new float[2];

        GpuFuture<Void> result =
            exec.submitV("com/example/K", "add", "([F[F[F)V", a, b, out);

        assertThat(result).isSameAs(stubFuture);

        ArgumentCaptor<Object[]> argsCap = ArgumentCaptor.forClass(Object[].class);
        verify(bridge).submitMethod(
            eq(HANDLE), eq("com/example/K"), eq("add"), eq("([F[F[F)V"), argsCap.capture());
        assertThat(argsCap.getValue()).containsExactly(a, b, out);

        exec.close();
    }

    @Test
    void submitV_doubleArrays_dispatchesViaBridge() {
        @SuppressWarnings("unchecked")
        GpuFuture<Void> stubFuture = mock(GpuFuture.class);
        doReturn(stubFuture).when(bridge)
            .submitMethod(anyLong(), any(), any(), any(), any(Object[].class));

        GpuExecutorImpl exec = new GpuExecutorImpl(HANDLE);
        double[] a = {1.5, 2.5};
        double[] b = {3.5, 4.5};
        double[] out = new double[2];

        GpuFuture<Void> result =
            exec.submitV("com/example/K", "add", "([D[D[D)V", a, b, out);

        assertThat(result).isSameAs(stubFuture);

        ArgumentCaptor<Object[]> argsCap = ArgumentCaptor.forClass(Object[].class);
        verify(bridge).submitMethod(
            eq(HANDLE), eq("com/example/K"), eq("add"), eq("([D[D[D)V"), argsCap.capture());
        assertThat(argsCap.getValue()).containsExactly(a, b, out);

        exec.close();
    }

    @Test
    void submitScale_intScalar_dispatchesViaBridge() {
        @SuppressWarnings("unchecked")
        GpuFuture<Void> stubFuture = mock(GpuFuture.class);
        doReturn(stubFuture).when(bridge)
            .submitMethod(anyLong(), any(), any(), any(), any(Object[].class));

        GpuExecutorImpl exec = new GpuExecutorImpl(HANDLE);
        int[] in = {1, 2, 3};
        int[] out = new int[3];

        GpuFuture<Void> result =
            exec.submitScale("com/example/K", "scale", "(I[I[I)V", 7, in, out);

        assertThat(result).isSameAs(stubFuture);

        ArgumentCaptor<Object[]> argsCap = ArgumentCaptor.forClass(Object[].class);
        verify(bridge).submitMethod(
            eq(HANDLE), eq("com/example/K"), eq("scale"), eq("(I[I[I)V"), argsCap.capture());
        assertThat(argsCap.getValue()).containsExactly(7, in, out);

        exec.close();
    }

    @Test
    void submitScale_floatScalar_dispatchesViaBridge() {
        @SuppressWarnings("unchecked")
        GpuFuture<Void> stubFuture = mock(GpuFuture.class);
        doReturn(stubFuture).when(bridge)
            .submitMethod(anyLong(), any(), any(), any(), any(Object[].class));

        GpuExecutorImpl exec = new GpuExecutorImpl(HANDLE);
        float[] in = {1.0f, 2.0f, 3.0f};
        float[] out = new float[3];

        GpuFuture<Void> result =
            exec.submitScale("com/example/K", "scale", "(F[F[F)V", 2.5f, in, out);

        assertThat(result).isSameAs(stubFuture);

        ArgumentCaptor<Object[]> argsCap = ArgumentCaptor.forClass(Object[].class);
        verify(bridge).submitMethod(
            eq(HANDLE), eq("com/example/K"), eq("scale"), eq("(F[F[F)V"), argsCap.capture());
        assertThat(argsCap.getValue()).containsExactly(2.5f, in, out);

        exec.close();
    }

    // ------------------------------------------------------------------
    // Happy-path coverage for launch() and newStream()
    // ------------------------------------------------------------------

    @Test
    void launch_happyPath_dispatchesViaBridge() {
        @SuppressWarnings("unchecked")
        GpuFuture<Void> stubFuture = mock(GpuFuture.class);
        when(bridge.launch(anyLong(), any(GpuRunnable.class)))
            .thenReturn(stubFuture);

        GpuExecutorImpl exec = new GpuExecutorImpl(HANDLE);
        GpuRunnable task = () -> { };

        GpuFuture<Void> result = exec.launch(task);

        assertThat(result).isSameAs(stubFuture);
        verify(bridge).launch(HANDLE, task);

        exec.close();
    }

    @Test
    void newStream_happyPath_returnsCleanableStream() {
        long streamHandle = 0xFEEDFACEL;
        GpuStream stubStream = new GpuStreamImpl(streamHandle);
        when(bridge.newStream(HANDLE)).thenReturn(stubStream);

        GpuExecutorImpl exec = new GpuExecutorImpl(HANDLE);

        GpuStream stream = exec.newStream();

        assertThat(stream).isNotNull().isSameAs(stubStream);
        verify(bridge).newStream(HANDLE);

        stream.close();
        verify(bridge, times(1)).closeStream(streamHandle);

        exec.close();
    }

    // ------------------------------------------------------------------
    // Default submit(String, String, String, Object...) input validation
    // ------------------------------------------------------------------

    @Test
    void submit_nullClassName_throwsNPE() {
        GpuExecutorImpl exec = new GpuExecutorImpl(HANDLE);
        try {
            assertThatThrownBy(() -> exec.submit(null, "m", "()V"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("className");
        } finally {
            exec.close();
        }
    }

    @Test
    void submit_emptyClassName_throwsIAE() {
        GpuExecutorImpl exec = new GpuExecutorImpl(HANDLE);
        try {
            assertThatThrownBy(() -> exec.submit("", "m", "()V"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("className");
        } finally {
            exec.close();
        }
    }

    @Test
    void submit_nullDescriptor_throwsNPE() {
        GpuExecutorImpl exec = new GpuExecutorImpl(HANDLE);
        try {
            assertThatThrownBy(() -> exec.submit("com/example/K", "m", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("descriptor");
        } finally {
            exec.close();
        }
    }

    @Test
    void submit_descriptorMissingOpenParen_throwsIAE() {
        GpuExecutorImpl exec = new GpuExecutorImpl(HANDLE);
        try {
            assertThatThrownBy(() -> exec.submit("com/example/K", "m", "V"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("(");
        } finally {
            exec.close();
        }
    }

    @Test
    void submit_nullArgElement_throwsNPE() {
        GpuExecutorImpl exec = new GpuExecutorImpl(HANDLE);
        try {
            // A null element inside the varargs array must be rejected with
            // an NPE that names the offending index.
            assertThatThrownBy(() ->
                    exec.submit("com/example/K", "m", "(I)V", 1, null, 3))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("args[1]");
        } finally {
            exec.close();
        }
    }

    @Test
    void submit_unsupportedArgType_throwsIAE() {
        GpuExecutorImpl exec = new GpuExecutorImpl(HANDLE);
        try {
            // An Object that is not a boxed primitive / supported array /
            // GpuArray must be rejected with IllegalArgumentException.
            assertThatThrownBy(() ->
                    exec.submit("com/example/K", "m", "(I)V", new Object()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("args[0]")
                .hasMessageContaining("unsupported type");
        } finally {
            exec.close();
        }
    }

    @Test
    void submit_supportedArgTypes_dispatchViaBridge() {
        @SuppressWarnings("unchecked")
        GpuFuture<Void> stubFuture = mock(GpuFuture.class);
        doReturn(stubFuture).when(bridge)
            .submitMethod(anyLong(), any(), any(), any(), any(Object[].class));

        GpuExecutorImpl exec = new GpuExecutorImpl(HANDLE);
        try {
            // All supported scalar/array types pass validation and dispatch.
            GpuFuture<?> result = exec.submit(
                "com/example/K", "m", "(ZIJFDLjava/lang/String;)V",
                Boolean.TRUE, 1, 2L, 3.0f, 4.0d, "five");

            assertThat(result).isSameAs(stubFuture);
            verify(bridge).submitMethod(
                eq(HANDLE), eq("com/example/K"), eq("m"),
                eq("(ZIJFDLjava/lang/String;)V"), any(Object[].class));
        } finally {
            exec.close();
        }
    }

    // ------------------------------------------------------------------
    // ReentrantReadWriteLock: read-lock ops vs write-lock close()
    // ------------------------------------------------------------------

    @Test
    void readWriteLock_concurrentOpsAndClose_releaseRunsOnceNoLeakedExceptions()
            throws InterruptedException {
        @SuppressWarnings("unchecked")
        GpuFuture<Integer> stubFuture = mock(GpuFuture.class);
        when(bridge.submit(anyLong(), ArgumentMatchers.<GpuCallable<Integer>>any()))
            .thenReturn(stubFuture);
        @SuppressWarnings("unchecked")
        GpuFuture<Void> stubLaunch = mock(GpuFuture.class);
        when(bridge.launch(anyLong(), any(GpuRunnable.class)))
            .thenReturn(stubLaunch);

        GpuExecutorImpl exec = new GpuExecutorImpl(HANDLE);

        final int opThreads = 4;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(opThreads + 1);
        java.util.List<AtomicReference<Throwable>> errs = new java.util.ArrayList<>(opThreads + 1);

        // Op threads alternate read-lock operations (submit / launch /
        // handleForDispatch) while one writer thread closes the executor.
        for (int i = 0; i < opThreads; i++) {
            final AtomicReference<Throwable> err = new AtomicReference<>();
            errs.add(err);
            new Thread(() -> {
                try {
                    start.await();
                    for (int j = 0; j < 500; j++) {
                        try {
                            switch (j % 3) {
                                case 0 -> exec.submit(() -> 1);
                                case 1 -> exec.launch(() -> { });
                                default -> exec.handleForDispatch();
                            }
                        } catch (GpuException expected) {
                            // Documented once close() wins the write lock.
                        }
                    }
                } catch (Throwable t) {
                    err.set(t);
                } finally {
                    done.countDown();
                }
            }, "exec-rwlock-op-" + i).start();
        }

        final AtomicReference<Throwable> closerErr = new AtomicReference<>();
        errs.add(closerErr);
        new Thread(() -> {
            try {
                start.await();
                exec.close();
            } catch (Throwable t) {
                closerErr.set(t);
            } finally {
                done.countDown();
            }
        }, "exec-rwlock-closer").start();

        start.countDown();
        assertThat(done.await(10, java.util.concurrent.TimeUnit.SECONDS))
            .as("all read/write lock threads must finish without deadlock")
            .isTrue();

        for (AtomicReference<Throwable> err : errs) {
            assertThat(err.get())
                .as("only caught GpuException is permitted")
                .isNull();
        }

        // The write-locked close() freed the handle exactly once despite
        // concurrent read-locked operations.
        verify(bridge, times(1)).releaseExecutor(HANDLE);
        assertThatThrownBy(() -> exec.submit(() -> 0))
            .isInstanceOf(GpuException.class)
            .hasMessageContaining("executor closed");
    }
}
