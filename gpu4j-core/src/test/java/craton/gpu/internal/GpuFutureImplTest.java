// SPDX-License-Identifier: Apache-2.0
// Copyright 2025-2026 Craton Software Company (https://craton.com.ar)

package craton.gpu.internal;

import craton.gpu.GpuException;
import craton.gpu.GpuFuture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Behavioural tests for {@link GpuFutureImpl}, focused on completion, error
 * propagation, cancellation, interrupt handling, and the timed-{@code get}
 * polling loop. All native traffic is routed through a mocked
 * {@link NativeBridge}.
 */
class GpuFutureImplTest {

    private static final long HANDLE = 0x1234L;

    private NativeBridge bridge;

    @BeforeEach
    void installMockBridge() {
        // Clear any stray interrupt flag from a prior test before we start.
        Thread.interrupted();
        bridge = mock(NativeBridge.class);
        Native.setBridge(bridge);
    }

    @AfterEach
    void restoreDefaultBridge() {
        // Clear any interrupt flag set by tests so the next test starts clean.
        Thread.interrupted();
        Native.setBridge(null);
    }

    @Test
    void getOnZeroHandleThrowsGpuException() {
        GpuFutureImpl<Object> fut = new GpuFutureImpl<>(0L);

        assertThatThrownBy(fut::get)
            .isInstanceOf(GpuException.class)
            .hasMessageContaining("invalid future handle");
    }

    @Test
    void isDoneIsFalseWhileStatusReportsRunning() {
        when(bridge.futureStatus(HANDLE)).thenReturn(0);

        GpuFutureImpl<Object> fut = new GpuFutureImpl<>(HANDLE);

        assertThat(fut.isDone()).isFalse();
    }

    @Test
    void isDoneFlipsTrueWhenStatusBecomesNonZero() {
        AtomicInteger status = new AtomicInteger(0);
        when(bridge.futureStatus(HANDLE)).thenAnswer(inv -> status.get());

        GpuFutureImpl<Object> fut = new GpuFutureImpl<>(HANDLE);

        assertThat(fut.isDone()).isFalse();
        status.set(1);
        assertThat(fut.isDone()).isTrue();
    }

    @Test
    void isDoneShortCircuitsAfterSuccessfulGet() throws Exception {
        when(bridge.futureStatus(HANDLE)).thenReturn(1);
        when(bridge.futureGetResult(HANDLE)).thenReturn("ok");

        GpuFutureImpl<String> fut = new GpuFutureImpl<>(HANDLE);
        String value = fut.get();
        assertThat(value).isEqualTo("ok");

        clearInvocations(bridge);

        // Subsequent isDone() must not consult the bridge - done flag short-circuits.
        for (int i = 0; i < 5; i++) {
            assertThat(fut.isDone()).isTrue();
        }
        verify(bridge, never()).futureStatus(anyLong());
    }

    @Test
    void getReturnsResultWhenStatusIsCompleted() throws Exception {
        when(bridge.futureStatus(HANDLE)).thenReturn(1);
        when(bridge.futureGetResult(HANDLE)).thenReturn("ok");

        GpuFutureImpl<String> fut = new GpuFutureImpl<>(HANDLE);

        assertThat(fut.get()).isEqualTo("ok");
        verify(bridge).futureSynchronize(HANDLE);
        verify(bridge).futureGetResult(HANDLE);
    }

    @Test
    void getThrowsGpuExceptionWhenStatusReportsFailure() {
        when(bridge.futureStatus(HANDLE)).thenReturn(2);
        when(bridge.futureGetError(HANDLE)).thenReturn("boom");

        GpuFutureImpl<String> fut = new GpuFutureImpl<>(HANDLE);

        assertThatThrownBy(fut::get)
            .isInstanceOf(GpuException.class)
            .hasMessageContaining("boom");
    }

    @Test
    void getThrowsInterruptedExceptionWhenInterruptFlagSet() {
        GpuFutureImpl<Object> fut = new GpuFutureImpl<>(HANDLE);

        Thread.currentThread().interrupt();

        assertThatThrownBy(fut::get).isInstanceOf(InterruptedException.class);
        // The interrupted() call inside get() clears the flag, but the test
        // @AfterEach also clears defensively.
    }

    @Test
    void timedGetThrowsTimeoutWhenStatusStaysZero() {
        when(bridge.futureStatus(HANDLE)).thenReturn(0);

        GpuFutureImpl<Object> fut = new GpuFutureImpl<>(HANDLE);

        assertThatThrownBy(() -> fut.get(50, TimeUnit.MILLISECONDS))
            .isInstanceOf(TimeoutException.class);
    }

    @Test
    void timedGetReturnsWhenStatusFlipsToCompletedDuringWait() throws Exception {
        AtomicInteger status = new AtomicInteger(0);
        when(bridge.futureStatus(HANDLE)).thenAnswer(inv -> status.get());
        when(bridge.futureGetResult(HANDLE)).thenReturn("done");

        GpuFutureImpl<String> fut = new GpuFutureImpl<>(HANDLE);

        Thread flipper = new Thread(() -> {
            try {
                Thread.sleep(20);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            status.set(1);
        }, "future-status-flipper");
        flipper.start();

        String value = fut.get(200, TimeUnit.MILLISECONDS);
        assertThat(value).isEqualTo("done");

        flipper.join(1000);
    }

    @Test
    void cancelReturnsTrueOnFirstSuccessAndFalseThereafter() {
        when(bridge.futureCancel(eq(HANDLE), anyBoolean())).thenReturn(1);

        GpuFutureImpl<Object> fut = new GpuFutureImpl<>(HANDLE);

        assertThat(fut.cancel(false)).isTrue();
        assertThat(fut.isCancelled()).isTrue();
        assertThat(fut.cancel(false)).isFalse();

        // Subsequent get() must report cancellation.
        assertThatThrownBy(fut::get)
            .isInstanceOf(GpuException.class)
            .hasMessageContaining("future cancelled");

        // releaseFuture was triggered exactly once via Cleanable.clean()
        // when cancel succeeded.
        verify(bridge, times(1)).releaseFuture(HANDLE);
    }

    @Test
    void cancelReturnsFalseWhenNativeRejectsRequest() {
        when(bridge.futureCancel(eq(HANDLE), anyBoolean())).thenReturn(0);

        GpuFutureImpl<Object> fut = new GpuFutureImpl<>(HANDLE);

        assertThat(fut.cancel(false)).isFalse();
        assertThat(fut.isCancelled()).isFalse();
        verify(bridge, never()).releaseFuture(anyLong());
    }

    @Test
    void toCompletableFutureBridgesValueOnCompletion() throws Exception {
        when(bridge.futureStatus(HANDLE)).thenReturn(1);
        when(bridge.futureGetResult(HANDLE)).thenReturn("bridge-value");

        GpuFutureImpl<String> fut = new GpuFutureImpl<>(HANDLE);
        CompletableFuture<String> cf = fut.toCompletableFuture();

        assertThat(cf.get(2, TimeUnit.SECONDS)).isEqualTo("bridge-value");
    }

    @Test
    void getNow_returnsValueWhenDone() throws Exception {
        when(bridge.futureStatus(HANDLE)).thenReturn(1);
        when(bridge.futureGetResult(HANDLE)).thenReturn("real-value");

        GpuFutureImpl<String> fut = new GpuFutureImpl<>(HANDLE);
        // Drive the future into the done state via a normal get().
        assertThat(fut.get()).isEqualTo("real-value");

        assertThat(fut.getNow("fallback")).isEqualTo("real-value");
    }

    @Test
    void getNow_returnsFallbackWhenNotDone() {
        when(bridge.futureStatus(HANDLE)).thenReturn(0);

        GpuFutureImpl<String> fut = new GpuFutureImpl<>(HANDLE);

        assertThat(fut.getNow("fallback")).isEqualTo("fallback");
    }

    @Test
    void getNow_propagatesGpuExceptionFromGet() {
        when(bridge.futureStatus(HANDLE)).thenReturn(2);
        when(bridge.futureGetErrorMessage(HANDLE)).thenReturn("boom");

        GpuFutureImpl<String> fut = new GpuFutureImpl<>(HANDLE);

        assertThatThrownBy(() -> fut.getNow("fallback"))
            .isInstanceOf(GpuException.class)
            .hasMessageContaining("boom");
    }

    @Test
    void thenApplyGpu_appliesFunctionToCompletedValue() throws Exception {
        when(bridge.futureStatus(HANDLE)).thenReturn(1);
        when(bridge.futureGetResult(HANDLE)).thenReturn(42);

        GpuFutureImpl<Integer> upstream = new GpuFutureImpl<>(HANDLE);

        GpuFuture<Integer> chained = upstream.thenApplyGpu(v -> v + 1);

        assertThat(chained.get()).isEqualTo(43);
    }

    @Test
    void thenApplyGpu_upstreamFailureProducesFailedFuture() {
        when(bridge.futureStatus(HANDLE)).thenReturn(2);
        when(bridge.futureGetErrorMessage(HANDLE)).thenReturn("upstream-failed");

        GpuFutureImpl<Integer> upstream = new GpuFutureImpl<>(HANDLE);

        GpuFuture<Integer> chained = upstream.thenApplyGpu(v -> v + 1);

        assertThatThrownBy(chained::get).isInstanceOf(GpuException.class);
    }

    @Test
    void thenApplyGpu_functionThrows_producesFailedFutureWithCause() {
        when(bridge.futureStatus(HANDLE)).thenReturn(1);
        when(bridge.futureGetResult(HANDLE)).thenReturn(7);

        GpuFutureImpl<Integer> upstream = new GpuFutureImpl<>(HANDLE);

        GpuFuture<Integer> failed = upstream.thenApplyGpu(v -> {
            throw new RuntimeException("fn-boom");
        });

        assertThatThrownBy(failed::get)
                .isInstanceOf(GpuException.class)
                .hasMessageContaining("thenApplyGpu failed")
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void toCompletableFutureWithExecutor_completesNormally() throws Exception {
        when(bridge.futureStatus(HANDLE)).thenReturn(1);
        when(bridge.futureGetResult(HANDLE)).thenReturn("V");

        GpuFutureImpl<String> fut = new GpuFutureImpl<>(HANDLE);
        ExecutorService exec = Executors.newSingleThreadExecutor();
        try {
            String value = fut.toCompletableFuture(exec).get(2, TimeUnit.SECONDS);
            assertThat(value).isEqualTo("V");
        } finally {
            exec.shutdown();
        }
    }

    @Test
    void toCompletableFutureWithExecutor_propagatesFailureAsExecutionException() {
        when(bridge.futureStatus(HANDLE)).thenReturn(2);
        when(bridge.futureGetError(HANDLE)).thenReturn("kernel-boom");

        GpuFutureImpl<String> fut = new GpuFutureImpl<>(HANDLE);
        ExecutorService exec = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<String> cf = fut.toCompletableFuture(exec);
            assertThatThrownBy(() -> cf.get(2, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(GpuException.class);
        } finally {
            exec.shutdown();
        }
    }

    @Test
    void cancelThenGet_throwsAppropriateException() {
        when(bridge.futureCancel(eq(HANDLE), anyBoolean())).thenReturn(1);

        GpuFutureImpl<Object> fut = new GpuFutureImpl<>(HANDLE);

        assertThat(fut.cancel(true)).isTrue();

        // Per GpuFutureImpl.get(): after cancellation, get() throws GpuException("future cancelled").
        assertThatThrownBy(fut::get)
            .isInstanceOf(GpuException.class)
            .hasMessageContaining("future cancelled");
    }

    // ---------------------------------------------------------------
    // CompletedFuture inner-class coverage
    //
    // CompletedFuture is the value-bearing future returned from the CPU
    // fallback branch of thenApplyGpu(). The mocked NativeBridge returns
    // null from submitWithArg by default, which forces that fallback,
    // so each test below drives a successful upstream sync, calls
    // thenApplyGpu, and then probes the returned CompletedFuture<T>.
    // ---------------------------------------------------------------

    private GpuFuture<Integer> completedFutureFromChain() {
        when(bridge.futureStatus(HANDLE)).thenReturn(1);
        when(bridge.futureGetResult(HANDLE)).thenReturn(10);
        GpuFutureImpl<Integer> upstream = new GpuFutureImpl<>(HANDLE);
        return upstream.thenApplyGpu(v -> v + 1);
    }

    @Test
    void completedFuture_isDone_returnsTrue() {
        GpuFuture<Integer> cf = completedFutureFromChain();
        assertThat(cf.isDone()).isTrue();
    }

    @Test
    void completedFuture_isCancelled_returnsFalse() {
        GpuFuture<Integer> cf = completedFutureFromChain();
        assertThat(cf.isCancelled()).isFalse();
    }

    @Test
    void completedFuture_cancel_returnsFalse() {
        GpuFuture<Integer> cf = completedFutureFromChain();
        assertThat(cf.cancel(true)).isFalse();
        assertThat(cf.isCancelled()).isFalse();
    }

    @Test
    void completedFuture_getNow_returnsValue() {
        GpuFuture<Integer> cf = completedFutureFromChain();
        assertThat(cf.getNow(-999)).isEqualTo(11);
    }

    @Test
    void completedFuture_getWithTimeout_returnsValue() throws Exception {
        GpuFuture<Integer> cf = completedFutureFromChain();
        assertThat(cf.get(1, TimeUnit.SECONDS)).isEqualTo(11);
    }

    @Test
    void completedFuture_thenApplyGpu_chains() throws Exception {
        when(bridge.futureStatus(HANDLE)).thenReturn(1);
        when(bridge.futureGetResult(HANDLE)).thenReturn("hello");
        GpuFutureImpl<String> upstream = new GpuFutureImpl<>(HANDLE);
        GpuFuture<String> cf = upstream.thenApplyGpu(v -> v + "-1");
        // Chain a second thenApplyGpu off the CompletedFuture. The
        // CompletedFuture.thenApplyGpu path simply runs fn on the
        // resident value and wraps the result in another CompletedFuture.
        GpuFuture<String> chained = cf.thenApplyGpu(v -> v + "!");
        assertThat(chained.get()).isEqualTo("hello-1!");
        assertThat(chained.isDone()).isTrue();
    }

    @Test
    void completedFuture_toCompletableFuture_completesNormally() throws Exception {
        GpuFuture<Integer> cf = completedFutureFromChain();
        assertThat(cf.toCompletableFuture().get(1, TimeUnit.SECONDS)).isEqualTo(11);
    }

    // ---------------------------------------------------------------
    // FailedFuture inner-class coverage
    //
    // FailedFuture is returned from thenApplyGpu() when upstream host
    // sync throws GpuException (status == 2 path). Each test below
    // arranges for that and then probes the returned FailedFuture.
    // ---------------------------------------------------------------

    private GpuFuture<Integer> failedFutureFromChain() {
        when(bridge.futureStatus(HANDLE)).thenReturn(2);
        when(bridge.futureGetError(HANDLE)).thenReturn("upstream-boom");
        GpuFutureImpl<Integer> upstream = new GpuFutureImpl<>(HANDLE);
        return upstream.thenApplyGpu(v -> v + 1);
    }

    @Test
    void failedFuture_isDone_returnsTrue() {
        GpuFuture<Integer> ff = failedFutureFromChain();
        assertThat(ff.isDone()).isTrue();
    }

    @Test
    void failedFuture_isCancelled_returnsFalse() {
        GpuFuture<Integer> ff = failedFutureFromChain();
        assertThat(ff.isCancelled()).isFalse();
    }

    @Test
    void failedFuture_cancel_returnsFalse() {
        GpuFuture<Integer> ff = failedFutureFromChain();
        assertThat(ff.cancel(true)).isFalse();
        assertThat(ff.isCancelled()).isFalse();
    }

    @Test
    void failedFuture_get_throwsGpuException() {
        GpuFuture<Integer> ff = failedFutureFromChain();
        assertThatThrownBy(ff::get).isInstanceOf(GpuException.class);
    }

    @Test
    void failedFuture_getWithTimeout_throwsGpuException() {
        GpuFuture<Integer> ff = failedFutureFromChain();
        // FailedFuture.get(timeout, unit) throws the stored GpuException
        // immediately - it does not wait or surface TimeoutException.
        assertThatThrownBy(() -> ff.get(1, TimeUnit.SECONDS))
            .isInstanceOf(GpuException.class);
    }

    @Test
    void failedFuture_getNow_throwsGpuException() {
        GpuFuture<Integer> ff = failedFutureFromChain();
        // FailedFuture.getNow rethrows the stored failure rather than
        // returning the fallback (mirrors GpuFutureImpl.getNow C4 fix).
        assertThatThrownBy(() -> ff.getNow(-1))
            .isInstanceOf(GpuException.class);
    }

    @Test
    void failedFuture_thenApplyGpu_propagatesFailure() {
        GpuFuture<Integer> ff = failedFutureFromChain();
        GpuFuture<String> chained = ff.thenApplyGpu(v -> "should-not-run-" + v);
        assertThat(chained.isDone()).isTrue();
        assertThatThrownBy(chained::get).isInstanceOf(GpuException.class);
    }

    @Test
    void failedFuture_toCompletableFuture_completesExceptionally() {
        GpuFuture<Integer> ff = failedFutureFromChain();
        CompletableFuture<Integer> cf = ff.toCompletableFuture();
        assertThat(cf.isCompletedExceptionally()).isTrue();
        assertThatThrownBy(() -> cf.get(1, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(GpuException.class);
    }

    // ---------------------------------------------------------------
    // Memoization coverage (wave-1).
    //
    // After get() observes completion, the result/failure is cached and
    // subsequent get() calls must not re-enter the native layer. We use
    // a hand-written counting NativeBridge so we can assert exact call
    // counts on the synchronize / status / result entry points.
    // ---------------------------------------------------------------

    /**
     * Minimal {@link NativeBridge} that records how many times each
     * future-related native entry point was invoked. Only the methods
     * exercised by {@link GpuFutureImpl} carry behaviour; everything else
     * throws {@link UnsupportedOperationException}.
     */
    private static final class CountingBridge implements NativeBridge {
        final AtomicInteger synchronizeCalls = new AtomicInteger();
        final AtomicInteger statusCalls = new AtomicInteger();
        final AtomicInteger getResultCalls = new AtomicInteger();
        final AtomicInteger getErrorCalls = new AtomicInteger();
        final AtomicInteger getErrorMessageCalls = new AtomicInteger();

        private volatile int status;
        private volatile Object result;
        private volatile String error;
        private volatile String errorMessage;

        CountingBridge(int status, Object result, String error, String errorMessage) {
            this.status = status;
            this.result = result;
            this.error = error;
            this.errorMessage = errorMessage;
        }

        @Override public int futureStatus(long h) { statusCalls.incrementAndGet(); return status; }
        @Override public void futureSynchronize(long h) { synchronizeCalls.incrementAndGet(); }
        @Override public Object futureGetResult(long h) { getResultCalls.incrementAndGet(); return result; }
        @Override public String futureGetError(long h) { getErrorCalls.incrementAndGet(); return error; }
        @Override public String futureGetErrorMessage(long h) {
            getErrorMessageCalls.incrementAndGet();
            return errorMessage;
        }
        @Override public int futureCancel(long h, boolean i) { return 0; }
        @Override public void releaseFuture(long h) { }

        // --- unused entry points ---
        @Override public craton.gpu.GpuExecutor openExecutor(int d) { throw new UnsupportedOperationException(); }
        @Override public <R> GpuFuture<R> submit(long e, craton.gpu.GpuCallable<R> t) { throw new UnsupportedOperationException(); }
        @Override public GpuFuture<Void> launch(long e, craton.gpu.GpuRunnable t) { throw new UnsupportedOperationException(); }
        @Override public <R> GpuFuture<R> submitMethod(long e, String c, String m, String d, Object[] a) { throw new UnsupportedOperationException(); }
        @Override public <R> GpuFuture<R> submitWithArg(long e, Object l, Object s) { return null; }
        @Override public <R> GpuFuture<R> submitWithArgs(long e, Object l, Object[] s) { return null; }
        @Override public craton.gpu.GpuStream newStream(long e) { throw new UnsupportedOperationException(); }
        @Override public void closeStream(long s) { throw new UnsupportedOperationException(); }
        @Override public long arrayWrapInt(int[] h) { throw new UnsupportedOperationException(); }
        @Override public long arrayWrapLong(long[] h) { throw new UnsupportedOperationException(); }
        @Override public long arrayWrapFloat(float[] h) { throw new UnsupportedOperationException(); }
        @Override public long arrayWrapDouble(double[] h) { throw new UnsupportedOperationException(); }
        @Override public Object arrayToHost(long h) { throw new UnsupportedOperationException(); }
        @Override public boolean arrayIsResident(long h) { throw new UnsupportedOperationException(); }
        @Override public void releaseArray(long h) { throw new UnsupportedOperationException(); }
        @Override public void releaseExecutor(long h) { throw new UnsupportedOperationException(); }
    }

    @Test
    void get_memoizesSuccessfulResult_secondCallSkipsNativeRoundTrip() throws Exception {
        CountingBridge counting = new CountingBridge(1, "memo-value", null, null);
        Native.setBridge(counting);

        GpuFutureImpl<String> fut = new GpuFutureImpl<>(HANDLE);

        assertThat(fut.get()).isEqualTo("memo-value");
        assertThat(counting.synchronizeCalls.get()).isEqualTo(1);
        assertThat(counting.getResultCalls.get()).isEqualTo(1);

        // Second get(): cached result, no further native traffic.
        assertThat(fut.get()).isEqualTo("memo-value");
        assertThat(fut.get()).isEqualTo("memo-value");
        assertThat(counting.synchronizeCalls.get()).isEqualTo(1);
        assertThat(counting.getResultCalls.get()).isEqualTo(1);
    }

    @Test
    void get_memoizesFailure_secondCallReThrowsCachedFailureWithoutNativeRoundTrip() {
        CountingBridge counting = new CountingBridge(2, null, "failed-once", null);
        Native.setBridge(counting);

        GpuFutureImpl<String> fut = new GpuFutureImpl<>(HANDLE);

        AtomicReference<GpuException> first = new AtomicReference<>();
        assertThatThrownBy(fut::get)
            .isInstanceOf(GpuException.class)
            .hasMessageContaining("failed-once")
            .satisfies(t -> first.set((GpuException) t));
        assertThat(counting.synchronizeCalls.get()).isEqualTo(1);

        // Second get(): identical cached GpuException instance, no native traffic.
        assertThatThrownBy(fut::get)
            .isInstanceOf(GpuException.class)
            .isSameAs(first.get());
        assertThat(counting.synchronizeCalls.get()).isEqualTo(1);
        assertThat(counting.getErrorCalls.get()).isEqualTo(1);
    }

    @Test
    void get_statusZeroAfterSynchronize_throwsGpuExceptionNotInterrupted() {
        // futureSynchronize returns, but status is still 0 (running). Per the
        // wave-1 correction this is surfaced as a GpuException — NOT a bogus
        // InterruptedException — and the interrupt flag is left clear.
        when(bridge.futureStatus(HANDLE)).thenReturn(0);

        GpuFutureImpl<Object> fut = new GpuFutureImpl<>(HANDLE);

        assertThatThrownBy(fut::get)
            .isInstanceOf(GpuException.class)
            .hasMessageContaining("status=0");
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }

    @Test
    void get_genuineInterruptAfterSynchronize_throwsInterruptedException() {
        // Interrupt flag genuinely set: the post-synchronize interrupt check
        // inside get() must throw InterruptedException.
        when(bridge.futureStatus(HANDLE)).thenReturn(1);
        when(bridge.futureGetResult(HANDLE)).thenReturn("never-returned");
        doAnswer(inv -> {
            Thread.currentThread().interrupt();
            return null;
        }).when(bridge).futureSynchronize(HANDLE);

        GpuFutureImpl<String> fut = new GpuFutureImpl<>(HANDLE);

        assertThatThrownBy(fut::get).isInstanceOf(InterruptedException.class);
    }

    @Test
    void get_failureFallsBackToErrorMessageWhenGetErrorReturnsNull() {
        // futureGetError -> null forces the fallback to futureGetErrorMessage.
        when(bridge.futureStatus(HANDLE)).thenReturn(2);
        when(bridge.futureGetError(HANDLE)).thenReturn(null);
        when(bridge.futureGetErrorMessage(HANDLE)).thenReturn("fallback-message");

        GpuFutureImpl<String> fut = new GpuFutureImpl<>(HANDLE);

        assertThatThrownBy(fut::get)
            .isInstanceOf(GpuException.class)
            .hasMessageContaining("fallback-message");
    }

    @Test
    void get_failureUsesDefaultMessageWhenBothErrorSourcesReturnNull() {
        // Both futureGetError and futureGetErrorMessage return null: the
        // implementation falls back to the literal "kernel failed".
        when(bridge.futureStatus(HANDLE)).thenReturn(2);
        when(bridge.futureGetError(HANDLE)).thenReturn(null);
        when(bridge.futureGetErrorMessage(HANDLE)).thenReturn(null);

        GpuFutureImpl<String> fut = new GpuFutureImpl<>(HANDLE);

        assertThatThrownBy(fut::get)
            .isInstanceOf(GpuException.class)
            .hasMessageContaining("kernel failed");
    }

    @Test
    void timedGet_nonPositiveTimeoutOnRunningFutureThrowsTimeout() {
        // A zero / negative timeout on a not-done future expires immediately.
        when(bridge.futureStatus(HANDLE)).thenReturn(0);

        GpuFutureImpl<Object> fut = new GpuFutureImpl<>(HANDLE);

        assertThatThrownBy(() -> fut.get(0, TimeUnit.MILLISECONDS))
            .isInstanceOf(TimeoutException.class);
        assertThatThrownBy(() -> fut.get(-5, TimeUnit.MILLISECONDS))
            .isInstanceOf(TimeoutException.class);
    }

    @Test
    void timedGet_interruptDuringWaitThrowsInterruptedException() {
        when(bridge.futureStatus(HANDLE)).thenReturn(0);

        GpuFutureImpl<Object> fut = new GpuFutureImpl<>(HANDLE);

        Thread.currentThread().interrupt();
        assertThatThrownBy(() -> fut.get(200, TimeUnit.MILLISECONDS))
            .isInstanceOf(InterruptedException.class);
    }

    @Test
    void timedGet_onCancelledFutureThrowsGpuException() {
        when(bridge.futureCancel(eq(HANDLE), anyBoolean())).thenReturn(1);

        GpuFutureImpl<Object> fut = new GpuFutureImpl<>(HANDLE);
        assertThat(fut.cancel(false)).isTrue();

        assertThatThrownBy(() -> fut.get(10, TimeUnit.MILLISECONDS))
            .isInstanceOf(GpuException.class)
            .hasMessageContaining("future cancelled");
    }

    @Test
    void timedGet_exponentialBackoffPollLoopEventuallyResolves() throws Exception {
        // Status flips to completed only after several poll iterations,
        // exercising the exponential-backoff park loop (100us -> 1ms cap).
        AtomicInteger polls = new AtomicInteger(0);
        when(bridge.futureStatus(HANDLE)).thenAnswer(inv -> polls.incrementAndGet() >= 6 ? 1 : 0);
        when(bridge.futureGetResult(HANDLE)).thenReturn("backoff-done");

        GpuFutureImpl<String> fut = new GpuFutureImpl<>(HANDLE);

        assertThat(fut.get(500, TimeUnit.MILLISECONDS)).isEqualTo("backoff-done");
        assertThat(polls.get()).isGreaterThanOrEqualTo(6);
    }

    @Test
    void timedGet_onZeroHandleThrowsGpuException() {
        GpuFutureImpl<Object> fut = new GpuFutureImpl<>(0L);

        assertThatThrownBy(() -> fut.get(10, TimeUnit.MILLISECONDS))
            .isInstanceOf(GpuException.class)
            .hasMessageContaining("invalid future handle");
    }

    @Test
    void getNow_restoresInterruptFlagAndReturnsFallbackOnInterrupt() {
        // isDone() reports true so getNow() proceeds to get(); get() then
        // observes a set interrupt flag and throws InterruptedException,
        // which getNow() swallows after restoring the flag.
        when(bridge.futureStatus(HANDLE)).thenReturn(1);

        GpuFutureImpl<String> fut = new GpuFutureImpl<>(HANDLE);

        Thread.currentThread().interrupt();
        assertThat(fut.getNow("fallback")).isEqualTo("fallback");
        assertThat(Thread.interrupted()).isTrue(); // flag was restored (and now cleared)
    }

    @Test
    void toCompletableFuture_propagatesFailureAsExecutionException() {
        when(bridge.futureStatus(HANDLE)).thenReturn(2);
        when(bridge.futureGetError(HANDLE)).thenReturn("cf-boom");

        GpuFutureImpl<String> fut = new GpuFutureImpl<>(HANDLE);
        CompletableFuture<String> cf = fut.toCompletableFuture();

        assertThatThrownBy(() -> cf.get(2, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(GpuException.class);
    }

    @Test
    void toCompletableFuture_wrapsInterruptAsGpuException() {
        // futureSynchronize sets the interrupt flag on the bridging worker;
        // get() throws InterruptedException, which toCompletableFuture wraps
        // in a GpuException to complete the CF exceptionally.
        when(bridge.futureStatus(HANDLE)).thenReturn(1);
        doAnswer(inv -> {
            Thread.currentThread().interrupt();
            return null;
        }).when(bridge).futureSynchronize(HANDLE);

        GpuFutureImpl<String> fut = new GpuFutureImpl<>(HANDLE);
        CompletableFuture<String> cf = fut.toCompletableFuture();

        assertThatThrownBy(() -> cf.get(2, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(GpuException.class)
            .hasRootCauseInstanceOf(InterruptedException.class);
    }

    @Test
    void toCompletableFutureWithExecutor_rejectsNullExecutor() {
        when(bridge.futureStatus(HANDLE)).thenReturn(1);
        GpuFutureImpl<String> fut = new GpuFutureImpl<>(HANDLE);

        assertThatThrownBy(() -> fut.toCompletableFuture(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void concurrentGet_multipleReadersResolveConsistentlyUnderReadLock() throws Exception {
        // Many concurrent get() callers share the read lock; all must
        // observe the same memoized value with no escaping exception.
        when(bridge.futureStatus(HANDLE)).thenReturn(1);
        when(bridge.futureGetResult(HANDLE)).thenReturn("shared");

        GpuFutureImpl<String> fut = new GpuFutureImpl<>(HANDLE);

        int n = 8;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(n);
        AtomicReference<Throwable> err = new AtomicReference<>();
        try {
            java.util.List<java.util.concurrent.Future<String>> results = new java.util.ArrayList<>();
            for (int i = 0; i < n; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return fut.get();
                }));
            }
            start.countDown();
            for (java.util.concurrent.Future<String> r : results) {
                assertThat(r.get(2, TimeUnit.SECONDS)).isEqualTo("shared");
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(err.get()).isNull();
    }

    @Test
    void concurrentGetAndCancel_completesWithoutDeadlockOrCorrupt() throws Exception {
        // Bridge answers in a way that lets either thread "win":
        //  - futureStatus returns 1 (completed) so get() can resolve a value if it runs first
        //  - futureCancel returns 1 so cancel() succeeds if it runs first
        // Both paths are legal; we only assert no deadlock and no test-time exception.
        when(bridge.futureStatus(HANDLE)).thenReturn(1);
        when(bridge.futureGetResult(HANDLE)).thenReturn("raced-value");
        when(bridge.futureCancel(eq(HANDLE), anyBoolean())).thenReturn(1);

        GpuFutureImpl<String> fut = new GpuFutureImpl<>(HANDLE);

        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> getError = new AtomicReference<>();
        AtomicReference<String> getValue = new AtomicReference<>();
        AtomicBoolean cancelResult = new AtomicBoolean(false);
        AtomicReference<Throwable> cancelError = new AtomicReference<>();

        Thread a = new Thread(() -> {
            try {
                start.await();
                getValue.set(fut.get());
            } catch (Throwable t) {
                getError.set(t);
            }
        }, "race-get");

        Thread b = new Thread(() -> {
            try {
                start.await();
                cancelResult.set(fut.cancel(true));
            } catch (Throwable t) {
                cancelError.set(t);
            }
        }, "race-cancel");

        a.start();
        b.start();
        start.countDown();

        a.join(1000);
        b.join(1000);

        assertThat(a.isAlive()).isFalse();
        assertThat(b.isAlive()).isFalse();
        // No surprise test-time exception types beyond the documented contract.
        if (getError.get() != null) {
            assertThat(getError.get())
                .isInstanceOfAny(GpuException.class, InterruptedException.class);
        }
        assertThat(cancelError.get()).isNull();
        // Acceptable: either cancel won (cancelResult=true and getError set)
        // or get won (cancelResult=false and getValue=="raced-value"). Both legal.
    }
}
