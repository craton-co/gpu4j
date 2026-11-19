// SPDX-License-Identifier: Apache-2.0
// Copyright 2025-2026 Craton Software Company (https://craton.com.ar)
//
// =====================================================================
//  gpu4j Demo (v0.1.0)
// ---------------------------------------------------------------------
//  A single-file, runnable tour of the gpu4j public API surface:
//    - GpuExecutor (open, submit, submitV, submitScale, launch, prepare,
//                   beginCapture/endCapture, newStream)
//    - GpuGraph    (handle, nodeCount, replay, replayAsync, close)
//    - GpuArray<T> (wrap, wrapHalf, allocate*, copyFromHost, toHost)
//    - GpuFuture<T>(isDone, get, get(timeout), getNow, getAsGpuArray,
//                   cancel, thenApplyGpu, toCompletableFuture)
//    - GpuStream   (handle, dispatch, synchronize, close)
//    - GpuBlas, Transpose, Half, GpuDevice, PreparedKernel
//    - GpuTestBridge & GpuBackendSpi (public simulation seam)
//    - Annotations (@EnableGpuAsync, @GpuKernel, @GpuExclude) & enums
//    - GpuException hierarchy + diagnostic formatting
//
//  Build & run on any JDK 17+:
//    mvn -DskipTests package
//    java -cp gpu4j-core/target/gpu4j-core-0.1.0.jar examples/Demo.java
// =====================================================================

import craton.gpu.AdmissionHint;
import craton.gpu.EnableGpuAsync;
import craton.gpu.GpuArray;
import craton.gpu.GpuBlas;
import craton.gpu.GpuCallable;
import craton.gpu.GpuCompileException;
import craton.gpu.GpuDevice;
import craton.gpu.GpuException;
import craton.gpu.GpuExclude;
import craton.gpu.GpuExecutor;
import craton.gpu.GpuFunction;
import craton.gpu.GpuFuture;
import craton.gpu.GpuGraph;
import craton.gpu.GpuKernel;
import craton.gpu.GpuLaunchException;
import craton.gpu.GpuOutOfMemoryException;
import craton.gpu.GpuRunnable;
import craton.gpu.GpuStream;
import craton.gpu.GridShape;
import craton.gpu.Half;
import craton.gpu.PreparedKernel;
import craton.gpu.Transpose;
import craton.gpu.testing.GpuBackendSpi;
import craton.gpu.testing.GpuTestBridge;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

@EnableGpuAsync(warmup = 2)
public class Demo {

    private static int methodsExercised = 0;

    private static void section(String title) {
        System.out.println();
        System.out.println("================================================================");
        System.out.println("  " + title);
        System.out.println("================================================================");
    }

    private static void call(String label) {
        methodsExercised++;
        System.out.println("  [" + methodsExercised + "] " + label);
    }

    private static void note(String msg) {
        System.out.println("      " + msg);
    }

    public static void main(String[] args) throws Exception {
        section("1. Banner & simulation backend setup");
        System.out.println("  gpu4j Demo v0.1.0");
        note("native CratonVM queryable before mock install: " + GpuDevice.queryable());

        try (GpuTestBridge.Installed ignored = GpuTestBridge.install(new SimBackend())) {
            note("simulation GpuBackendSpi installed via GpuTestBridge.install(...)");
            call("GpuDevice.all()");
            note("devices = " + GpuDevice.all());

            section("2. GpuExecutor.open(0)");
            call("GpuExecutor.open(0)");
            try (GpuExecutor exec = GpuExecutor.open(0)) {
                call("exec.handleForDispatch()");
                note("executor handle = " + exec.handleForDispatch());

                section("3. GpuArray lifecycle & Half (fp16) conversion");
                int[] hi = {1, 2, 3, 4};
                call("GpuArray.wrap(int[])");
                try (GpuArray<int[]> ai = GpuArray.wrap(hi)) {
                    call("ai.toHost()");
                    note("toHost() = " + Arrays.toString(ai.toHost().get()));
                    int[] dest = new int[hi.length];
                    call("ai.toHost(int[])");
                    ai.toHost(dest);
                    note("toHost(dest) = " + Arrays.toString(dest));
                }

                short[] halfBits = Half.fromFloats(new float[] {0.5f, 1.25f, -3.5f, 8.0f});
                call("GpuArray.wrapHalf(short[])");
                try (GpuArray<short[]> ah = GpuArray.wrapHalf(halfBits)) {
                    short[] outHalf = new short[halfBits.length];
                    ah.toHost(outHalf);
                    note("Half round-trip = " + Arrays.toString(Half.toFloats(outHalf)));
                }

                section("4. PreparedKernel, submitV, and submitScale");
                PreparedKernel pk = exec.prepare("Demo$Kernels", "elemwise", "([I[I[I)V");
                call("exec.prepare(...).dispatch(int[], int[], int[])");
                pk.dispatch(new int[] {1, 2}, new int[] {3, 4}, new int[2]).get();

                call("exec.submitV & exec.submitScale");
                exec.submitV("Demo$Kernels", "elemwise", "([I[I[I)V",
                        new int[] {1, 2}, new int[] {10, 20}, new int[2]).get();
                exec.submitScale("Demo$Kernels", "scale", "(F[F[F)V",
                        2.0f, new float[] {1f, 2f}, new float[2]).get();

                section("5. GpuBlas.gemmReference & GpuBlas.gemm");
                float[] ma = {1, 2, 3, 4};
                float[] mb = {5, 6, 7, 8};
                float[] mc = new float[4];
                call("GpuBlas.gemmReference");
                GpuBlas.gemmReference(ma, Transpose.NONE, mb, Transpose.NONE, mc, 2, 2, 2);
                note("gemmReference 2x2 = " + Arrays.toString(mc));

                try (GpuArray<float[]> ga = GpuArray.wrap(ma);
                     GpuArray<float[]> gb = GpuArray.wrap(mb);
                     GpuArray<float[]> gc = GpuArray.allocateFloat(4)) {
                    call("GpuBlas.gemm");
                    GpuBlas.gemm(ga, gb, gc, 2, 2, 2).get();
                }

                section("6. GpuGraph capture & replay (AutoCloseable)");
                call("exec.beginCapture() / endCapture()");
                exec.beginCapture();
                long capturedSub = exec.dispatchNamedHandle(
                        "Demo$Kernels", "elemwise", "([I[I[I)V",
                        new Object[] {new int[2], new int[2], new int[2]});
                note("captured submission handle = " + capturedSub);
                try (GpuGraph graph = exec.endCapture()) {
                    call("graph.nodeCount() & graph.replay()");
                    note("graph handle=" + graph.handle() + " nodes=" + graph.nodeCount());
                    long replaySub = graph.replay();
                    exec.awaitSubmission(replaySub);
                    exec.releaseSubmission(replaySub);
                }

                section("7. GpuStream lifecycle");
                call("exec.newStream()");
                try (GpuStream stream = exec.newStream()) {
                    call("stream.dispatch(...) + stream.synchronize()");
                    stream.dispatch("Demo$Kernels", "elemwise", "([I[I[I)V",
                            new int[2], new int[2], new int[2]);
                    stream.synchronize();
                }

                section("8. GpuFuture operations");
                GpuFuture<String> f = exec.submit(() -> "hello");
                call("f.get(100ms) / thenApplyGpu / toCompletableFuture");
                try {
                    note("get(100ms) = " + f.get(100, TimeUnit.MILLISECONDS));
                } catch (TimeoutException te) {
                    note("timed out: " + te.getMessage());
                }
                note("thenApplyGpu = " + f.thenApplyGpu(v -> v + "!").get());

                ExecutorService es = Executors.newSingleThreadExecutor();
                try {
                    CompletableFuture<String> cf = f.toCompletableFuture((Executor) es);
                    note("toCompletableFuture = " + cf.get());
                } finally {
                    es.shutdown();
                }

                section("9. Annotation & enum inspection");
                Method elemwise = Kernels.class.getDeclaredMethod(
                        "elemwise", int[].class, int[].class, int[].class);
                call("elemwise.getAnnotation(GpuKernel.class)");
                note("CLASS retention => runtime reflection returns "
                        + elemwise.getAnnotation(GpuKernel.class));
                note("GridShape values = " + Arrays.toString(GridShape.values()));
                note("AdmissionHint values = " + Arrays.toString(AdmissionHint.values()));

                section("10. Exception family & nested failing backend swap");
                note(new GpuLaunchException("sim launch failure", 7, "K.m", "<<<1,128>>>").toString());
                note(new GpuOutOfMemoryException("sim OOM", 1_000_000L, 64_000L, 0).toString());
                note(new GpuCompileException("sim PTX error", 42, "K.m").toString());

                call("nested GpuTestBridge.install(FailingBackend)");
                try (GpuTestBridge.Installed nested = GpuTestBridge.install(new FailingBackend())) {
                    try {
                        exec.submit("X", "m", "()V").get();
                    } catch (GpuLaunchException caught) {
                        note("caught expected " + caught);
                    }
                }
            }
        }

        section("11. Summary");
        System.out.println("  Demo finished cleanly. " + methodsExercised + " steps exercised.");
    }

    @EnableGpuAsync(warmup = 2)
    public static final class Kernels {
        private Kernels() {
        }

        @GpuKernel(grid = GridShape.ELEMENTWISE, blockX = 128, admit = AdmissionHint.STRICT)
        public static void elemwise(int[] a, int[] b, int[] out) {
            for (int i = 0; i < a.length; i++) {
                out[i] = a[i] + b[i];
            }
        }

        @GpuExclude(reason = "host-only utility")
        public static long hostOnly() {
            return 42L;
        }
    }

    static class SimBackend implements GpuBackendSpi {
        private final AtomicLong handles = new AtomicLong(1_000);
        private final Map<Long, Object> arrayStore = new ConcurrentHashMap<>();

        long nextHandle() {
            return handles.incrementAndGet();
        }

        @Override
        public GpuExecutor openExecutor(int deviceOrdinal) {
            return new SimExecutor(nextHandle());
        }

        @Override
        public <R> GpuFuture<R> submit(long execHandle, GpuCallable<R> task) {
            try {
                return new SimFuture<>(task.call());
            } catch (Exception e) {
                throw new GpuException("sim submit failed", e);
            }
        }

        @Override
        public GpuFuture<Void> launch(long execHandle, GpuRunnable task) {
            try {
                task.run();
                return new SimFuture<>(null);
            } catch (Exception e) {
                throw new GpuException("sim launch failed", e);
            }
        }

        @Override
        public <R> GpuFuture<R> submitMethod(
                long execHandle, String className, String methodName, String descriptor, Object[] args) {
            return new SimFuture<>(null);
        }

        @Override
        public long submitMethodHandle(
                long execHandle, String className, String methodName, String descriptor, Object[] args) {
            return nextHandle();
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public <R> GpuFuture<R> submitWithArg(long execHandle, Object fn, Object arg) {
            try {
                if (fn instanceof GpuFunction gpuFn && !(arg instanceof GpuFuture<?>)) {
                    return new SimFuture<>((R) gpuFn.apply(arg));
                }
                return null;
            } catch (Exception e) {
                throw new GpuException("sim submitWithArg failed", e);
            }
        }

        @Override public boolean graphBeginCapture(long execHandle) { return true; }
        @Override public long graphEndCapture(long execHandle) { return nextHandle(); }
        @Override public long graphReplay(long execHandle, long graphHandle) { return nextHandle(); }
        @Override public boolean graphBeginReplay(long execHandle, long graphHandle) { return true; }
        @Override public long graphEndReplay(long execHandle) { return nextHandle(); }
        @Override public int graphNodeCount(long graphHandle) { return 1; }
        @Override public void releaseGraph(long graphHandle) { }

        @Override public GpuStream newStream(long execHandle) { return new SimStream(nextHandle()); }
        @Override public void closeStream(long streamHandle) { }
        @Override public long streamSubmitMethod(long s, String c, String m, String d, Object[] a) { return nextHandle(); }
        @Override public void streamSynchronize(long streamHandle) { }

        @Override public int futureStatus(long h) { return 1; }
        @Override public void futureSynchronize(long h) { }
        @Override public Object futureGetResult(long h) { return null; }
        @Override public String futureGetErrorMessage(long h) { return ""; }
        @Override public int futureCancel(long h, boolean m) { return 1; }
        @Override public String futureGetError(long h) { return null; }

        @Override public long arrayWrapInt(int[] host) { long h = nextHandle(); arrayStore.put(h, host.clone()); return h; }
        @Override public long arrayWrapLong(long[] host) { long h = nextHandle(); arrayStore.put(h, host.clone()); return h; }
        @Override public long arrayWrapFloat(float[] host) { long h = nextHandle(); arrayStore.put(h, host.clone()); return h; }
        @Override public long arrayWrapShort(short[] host) { long h = nextHandle(); arrayStore.put(h, host.clone()); return h; }
        @Override public long arrayWrapDouble(double[] host) { long h = nextHandle(); arrayStore.put(h, host.clone()); return h; }
        @Override public long arrayAllocateFloat(int len) { long h = nextHandle(); arrayStore.put(h, new float[len]); return h; }
        @Override public Object arrayToHost(long arrayHandle) { return arrayStore.get(arrayHandle); }
        @Override public boolean arrayIsResident(long arrayHandle) { return arrayStore.containsKey(arrayHandle); }

        @Override
        public long gemm(long a, long b, long c, int m, int n, int k,
                         boolean half, boolean transA, boolean transB, long stream) {
            return nextHandle();
        }

        @Override public int deviceCount() { return 1; }
        @Override public String deviceName(int ordinal) { return "Simulated Craton GPU"; }
        @Override public long deviceTotalMemory(int ordinal) { return 16L * 1024 * 1024 * 1024; }
        @Override public int deviceComputeCapability(int ordinal) { return 90; }

        @Override public void releaseFuture(long h) { }
        @Override public void releaseArray(long h) { arrayStore.remove(h); }
        @Override public void releaseExecutor(long h) { }
    }

    static final class FailingBackend extends SimBackend {
        @Override
        public <R> GpuFuture<R> submitMethod(
                long execHandle, String className, String methodName, String descriptor, Object[] args) {
            throw new GpuLaunchException(
                    "simulated dispatch failure", 99, className + "." + methodName, "n/a");
        }
    }

    static final class SimExecutor implements GpuExecutor {
        private final long handle;
        private volatile boolean closed;

        SimExecutor(long handle) {
            this.handle = handle;
        }

        @Override
        public <R> GpuFuture<R> submit(GpuCallable<R> task) {
            check();
            return GpuTestBridge.current().submit(handle, task);
        }

        @Override
        public GpuFuture<Void> launch(GpuRunnable task) {
            check();
            return GpuTestBridge.current().launch(handle, task);
        }

        @Override
        public long handleForDispatch() {
            check();
            return handle;
        }

        @Override
        public <R> GpuFuture<R> dispatchNamed(
                String className, String methodName, String descriptor, Object[] args) {
            check();
            return GpuTestBridge.current().submitMethod(handle, className, methodName, descriptor, args);
        }

        @Override
        public long dispatchNamedHandle(
                String className, String methodName, String descriptor, Object[] args) {
            check();
            return GpuTestBridge.current().submitMethodHandle(handle, className, methodName, descriptor, args);
        }

        @Override
        public void beginCapture() {
            check();
            GpuTestBridge.current().graphBeginCapture(handle);
        }

        @Override
        public GpuGraph endCapture() {
            check();
            return GpuGraph.fromNativeHandle(this, GpuTestBridge.current().graphEndCapture(handle));
        }

        @Override
        public long replay(GpuGraph graph) {
            check();
            return GpuTestBridge.current().graphReplay(handle, graph.handle());
        }

        @Override
        public void beginReplay(GpuGraph graph) {
            check();
            GpuTestBridge.current().graphBeginReplay(handle, graph.handle());
        }

        @Override
        public long endReplay() {
            check();
            return GpuTestBridge.current().graphEndReplay(handle);
        }

        @Override
        public void awaitSubmission(long submissionHandle) {
            check();
            GpuTestBridge.current().futureSynchronize(submissionHandle);
        }

        @Override
        public GpuStream newStream() {
            check();
            return GpuTestBridge.current().newStream(handle);
        }

        @Override
        public void close() {
            closed = true;
        }

        private void check() {
            if (closed) {
                throw new GpuException("executor closed");
            }
        }
    }

    static final class SimStream implements GpuStream {
        private final long h;
        private volatile boolean closed;

        SimStream(long h) {
            this.h = h;
        }

        @Override
        public long handle() {
            if (closed) {
                throw new IllegalStateException("stream closed");
            }
            return h;
        }

        @Override
        public void dispatch(String className, String methodName, String descriptor, Object... args) {
            GpuTestBridge.current().streamSubmitMethod(handle(), className, methodName, descriptor, args);
        }

        @Override
        public void synchronize() {
            GpuTestBridge.current().streamSynchronize(handle());
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    static final class SimFuture<T> implements GpuFuture<T> {
        private final T value;
        private volatile boolean cancelled;

        SimFuture(T value) {
            this.value = value;
        }

        @Override public boolean isDone() { return true; }
        @Override public T get() { return value; }
        @Override public T get(long timeout, TimeUnit unit) { return value; }
        @Override public T getNow(T fallback) { return value; }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            if (cancelled) {
                return false;
            }
            cancelled = true;
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public <U> GpuFuture<U> thenApplyGpu(GpuFunction<? super T, ? extends U> fn) {
            try {
                return new SimFuture<>(fn.apply(value));
            } catch (Exception e) {
                throw new GpuException("thenApplyGpu failed", e);
            }
        }

        @Override
        public CompletableFuture<T> toCompletableFuture() {
            return CompletableFuture.completedFuture(value);
        }
    }
}
