// SPDX-License-Identifier: Apache-2.0
// Copyright 2025-2026 Craton Software Company (https://craton.com.ar)

package craton.gpu.internal;

import craton.gpu.GpuArray;
import craton.gpu.GpuCallable;
import craton.gpu.GpuExecutor;
import craton.gpu.GpuFuture;
import craton.gpu.GpuRunnable;
import craton.gpu.GpuStream;

/**
 * Production {@link NativeBridge} delegating to {@link Native} JNI methods, caching absent
 * optional VM symbols on first {@link UnsatisfiedLinkError} so older VMs degrade gracefully.
 */
final class NativeImpl implements NativeBridge {

    private static final byte UNPROBED = 0;
    private static final byte PRESENT = 1;
    private static final byte ABSENT = 2;

    private volatile byte deviceCountState = UNPROBED;
    private volatile byte deviceNameState = UNPROBED;
    private volatile byte deviceTotalMemoryState = UNPROBED;
    private volatile byte deviceComputeCapabilityState = UNPROBED;
    private volatile byte futureErrorKindState = UNPROBED;
    private volatile byte futureAwaitState = UNPROBED;
    private volatile byte futureIsDoneState = UNPROBED;
    private volatile byte arrayToHostIntoState = UNPROBED;
    private volatile byte arrayCopyFromHostState = UNPROBED;
    private volatile byte graphBeginCaptureState = UNPROBED;
    private volatile byte graphEndCaptureState = UNPROBED;
    private volatile byte graphReplayState = UNPROBED;
    private volatile byte graphBeginReplayState = UNPROBED;
    private volatile byte graphEndReplayState = UNPROBED;
    private volatile byte graphNodeCountState = UNPROBED;
    private volatile byte releaseGraphState = UNPROBED;

    @Override
    public GpuExecutor openExecutor(int deviceOrdinal) {
        return Native.openExecutor(deviceOrdinal);
    }

    @Override
    public <R> GpuFuture<R> submit(long execHandle, GpuCallable<R> fn) {
        return Native.submit(execHandle, fn);
    }

    @Override
    public GpuFuture<Void> launch(long execHandle, GpuRunnable fn) {
        return Native.launch(execHandle, fn);
    }

    @Override
    public <R> GpuFuture<R> submitMethod(
            long execHandle,
            String className,
            String methodName,
            String descriptor,
            Object[] args) {
        return Native.submitMethod(execHandle, className, methodName, descriptor, args);
    }

    @Override
    public <R> GpuFuture<R> submitWithArg(long execHandle, Object fn, Object arg) {
        return Native.submitWithArg(execHandle, fn, arg);
    }

    @Override
    public <R> GpuFuture<R> submitWithArgs(long execHandle, Object fn, Object[] args) {
        return Native.submitWithArgs(execHandle, fn, args);
    }

    @Override
    public long submitMethodHandle(
            long execHandle,
            String className,
            String methodName,
            String descriptor,
            Object[] args) {
        try {
            return Native.submitMethodHandle(execHandle, className, methodName, descriptor, args);
        } catch (UnsatisfiedLinkError e) {
            throw unsupported("submitMethodHandle", e);
        }
    }

    @Override
    public boolean graphBeginCapture(long execHandle) {
        if (graphBeginCaptureState == ABSENT) {
            return false;
        }
        try {
            boolean ok = Native.graphBeginCapture(execHandle);
            graphBeginCaptureState = PRESENT;
            return ok;
        } catch (UnsatisfiedLinkError e) {
            graphBeginCaptureState = ABSENT;
            return false;
        }
    }

    @Override
    public long graphEndCapture(long execHandle) {
        if (graphEndCaptureState == ABSENT) {
            return 0L;
        }
        try {
            long handle = Native.graphEndCapture(execHandle);
            graphEndCaptureState = PRESENT;
            return handle;
        } catch (UnsatisfiedLinkError e) {
            graphEndCaptureState = ABSENT;
            return 0L;
        }
    }

    @Override
    public long graphReplay(long execHandle, long graphHandle) {
        if (graphReplayState == ABSENT) {
            return 0L;
        }
        try {
            long sub = Native.graphReplay(execHandle, graphHandle);
            graphReplayState = PRESENT;
            return sub;
        } catch (UnsatisfiedLinkError e) {
            graphReplayState = ABSENT;
            return 0L;
        }
    }

    @Override
    public boolean graphBeginReplay(long execHandle, long graphHandle) {
        if (graphBeginReplayState == ABSENT) {
            return false;
        }
        try {
            boolean ok = Native.graphBeginReplay(execHandle, graphHandle);
            graphBeginReplayState = PRESENT;
            return ok;
        } catch (UnsatisfiedLinkError e) {
            graphBeginReplayState = ABSENT;
            return false;
        }
    }

    @Override
    public long graphEndReplay(long execHandle) {
        if (graphEndReplayState == ABSENT) {
            return 0L;
        }
        try {
            long sub = Native.graphEndReplay(execHandle);
            graphEndReplayState = PRESENT;
            return sub;
        } catch (UnsatisfiedLinkError e) {
            graphEndReplayState = ABSENT;
            return 0L;
        }
    }

    @Override
    public int graphNodeCount(long graphHandle) {
        if (graphNodeCountState == ABSENT) {
            return -1;
        }
        try {
            int count = Native.graphNodeCount(graphHandle);
            graphNodeCountState = PRESENT;
            return count;
        } catch (UnsatisfiedLinkError e) {
            graphNodeCountState = ABSENT;
            return -1;
        }
    }

    @Override
    public void releaseGraph(long graphHandle) {
        if (releaseGraphState == ABSENT) {
            return;
        }
        try {
            Native.releaseGraph(graphHandle);
            releaseGraphState = PRESENT;
        } catch (UnsatisfiedLinkError e) {
            releaseGraphState = ABSENT;
        }
    }

    @Override
    public GpuStream newStream(long execHandle) {
        return Native.newStream(execHandle);
    }

    @Override
    public void closeStream(long streamHandle) {
        Native.closeStream(streamHandle);
    }

    @Override
    public int futureStatus(long futureHandle) {
        return Native.futureStatus(futureHandle);
    }

    @Override
    public boolean futureIsDone(long futureHandle) {
        if (futureIsDoneState != ABSENT) {
            try {
                boolean done = Native.futureIsDone(futureHandle);
                futureIsDoneState = PRESENT;
                return done;
            } catch (UnsatisfiedLinkError e) {
                futureIsDoneState = ABSENT;
            }
        }
        int status = Native.futureStatus(futureHandle);
        return status == 1 || status == 2;
    }

    @Override
    public void futureSynchronize(long futureHandle) {
        Native.futureSynchronize(futureHandle);
    }

    @Override
    public Object futureGetResult(long futureHandle) {
        return Native.futureGetResult(futureHandle);
    }

    @Override
    public GpuArray<?> futureGetResultAsGpuArray(long futureHandle) {
        try {
            return Native.futureGetResultAsGpuArray(futureHandle);
        } catch (UnsatisfiedLinkError e) {
            throw unsupported("futureGetResultAsGpuArray", e);
        }
    }

    @Override
    public String futureGetErrorMessage(long futureHandle) {
        return Native.futureGetErrorMessage(futureHandle);
    }

    @Override
    public int futureCancel(long futureHandle, boolean mayInterruptIfRunning) {
        return Native.futureCancel(futureHandle, mayInterruptIfRunning);
    }

    @Override
    public String futureGetError(long futureHandle) {
        return Native.futureGetError(futureHandle);
    }

    @Override
    public long futureThenApplyGpu(
            long futureHandle,
            String className,
            String methodName,
            String descriptor,
            Object[] args) {
        try {
            return Native.futureThenApplyGpu(futureHandle, className, methodName, descriptor, args);
        } catch (UnsatisfiedLinkError e) {
            throw unsupported("futureThenApplyGpu", e);
        }
    }

    @Override
    public long arrayWrapInt(int[] arr) {
        return Native.arrayWrapInt(arr);
    }

    @Override
    public long arrayWrapLong(long[] arr) {
        return Native.arrayWrapLong(arr);
    }

    @Override
    public long arrayWrapShort(short[] arr) {
        try {
            return Native.arrayWrapShort(arr);
        } catch (UnsatisfiedLinkError e) {
            throw unsupported("arrayWrapShort", e);
        }
    }

    @Override
    public long arrayWrapFloat(float[] arr) {
        return Native.arrayWrapFloat(arr);
    }

    @Override
    public long arrayWrapDouble(double[] arr) {
        return Native.arrayWrapDouble(arr);
    }

    @Override
    public Object arrayToHost(long arrayHandle) {
        return Native.arrayToHost(arrayHandle);
    }

    @Override
    public boolean arrayIsResident(long arrayHandle) {
        return Native.arrayIsResident(arrayHandle);
    }

    @Override
    public void releaseFuture(long futureHandle) {
        Native.releaseFuture(futureHandle);
    }

    @Override
    public void releaseArray(long arrayHandle) {
        Native.releaseArray(arrayHandle);
    }

    @Override
    public void releaseExecutor(long execHandle) {
        Native.releaseExecutor(execHandle);
    }

    @Override
    public int deviceCount() {
        if (deviceCountState == ABSENT) {
            return -1;
        }
        try {
            int count = Native.deviceCount();
            deviceCountState = PRESENT;
            return count;
        } catch (UnsatisfiedLinkError e) {
            deviceCountState = ABSENT;
            return -1;
        }
    }

    @Override
    public String deviceName(int ordinal) {
        if (deviceNameState == ABSENT) {
            return null;
        }
        try {
            String name = Native.deviceName(ordinal);
            deviceNameState = PRESENT;
            return name;
        } catch (UnsatisfiedLinkError e) {
            deviceNameState = ABSENT;
            return null;
        }
    }

    @Override
    public long deviceTotalMemory(int ordinal) {
        if (deviceTotalMemoryState == ABSENT) {
            return 0L;
        }
        try {
            long bytes = Native.deviceTotalMemory(ordinal);
            deviceTotalMemoryState = PRESENT;
            return bytes;
        } catch (UnsatisfiedLinkError e) {
            deviceTotalMemoryState = ABSENT;
            return 0L;
        }
    }

    @Override
    public int deviceComputeCapability(int ordinal) {
        if (deviceComputeCapabilityState == ABSENT) {
            return 0;
        }
        try {
            int cc = Native.deviceComputeCapability(ordinal);
            deviceComputeCapabilityState = PRESENT;
            return cc;
        } catch (UnsatisfiedLinkError e) {
            deviceComputeCapabilityState = ABSENT;
            return 0;
        }
    }

    @Override
    public int futureErrorKind(long futureHandle) {
        if (futureErrorKindState == ABSENT) {
            return ERROR_KIND_UNKNOWN;
        }
        try {
            int kind = Native.futureErrorKind(futureHandle);
            futureErrorKindState = PRESENT;
            return kind;
        } catch (UnsatisfiedLinkError e) {
            futureErrorKindState = ABSENT;
            return ERROR_KIND_UNKNOWN;
        }
    }

    @Override
    public int futureAwait(long futureHandle, long timeoutNanos) {
        if (futureAwaitState == ABSENT) {
            return AWAIT_UNSUPPORTED;
        }
        try {
            int status = Native.futureAwait(futureHandle, timeoutNanos);
            futureAwaitState = PRESENT;
            return status;
        } catch (UnsatisfiedLinkError e) {
            futureAwaitState = ABSENT;
            return AWAIT_UNSUPPORTED;
        }
    }

    @Override
    public boolean arrayToHostInto(long arrayHandle, Object dest) {
        if (arrayToHostIntoState == ABSENT) {
            return false;
        }
        try {
            boolean ok = Native.arrayToHostInto(arrayHandle, dest);
            arrayToHostIntoState = PRESENT;
            return ok;
        } catch (UnsatisfiedLinkError e) {
            arrayToHostIntoState = ABSENT;
            return false;
        }
    }

    @Override
    public boolean arrayCopyFromHost(long arrayHandle, Object src) {
        if (arrayCopyFromHostState == ABSENT) {
            return false;
        }
        try {
            boolean ok = Native.arrayCopyFromHost(arrayHandle, src);
            arrayCopyFromHostState = PRESENT;
            return ok;
        } catch (UnsatisfiedLinkError e) {
            arrayCopyFromHostState = ABSENT;
            return false;
        }
    }

    @Override
    public long arrayAllocateInt(int length) {
        try {
            return Native.arrayAllocateInt(length);
        } catch (UnsatisfiedLinkError e) {
            throw unsupported("arrayAllocateInt", e);
        }
    }

    @Override
    public long arrayAllocateLong(int length) {
        try {
            return Native.arrayAllocateLong(length);
        } catch (UnsatisfiedLinkError e) {
            throw unsupported("arrayAllocateLong", e);
        }
    }

    @Override
    public long arrayAllocateShort(int length) {
        try {
            return Native.arrayAllocateShort(length);
        } catch (UnsatisfiedLinkError e) {
            throw unsupported("arrayAllocateShort", e);
        }
    }

    @Override
    public long arrayAllocateFloat(int length) {
        try {
            return Native.arrayAllocateFloat(length);
        } catch (UnsatisfiedLinkError e) {
            throw unsupported("arrayAllocateFloat", e);
        }
    }

    @Override
    public long arrayAllocateDouble(int length) {
        try {
            return Native.arrayAllocateDouble(length);
        } catch (UnsatisfiedLinkError e) {
            throw unsupported("arrayAllocateDouble", e);
        }
    }

    @Override
    public long streamSubmitMethod(
            long streamHandle,
            String className,
            String methodName,
            String descriptor,
            Object[] args) {
        try {
            return Native.streamSubmitMethod(streamHandle, className, methodName, descriptor, args);
        } catch (UnsatisfiedLinkError e) {
            throw unsupported("streamSubmitMethod", e);
        }
    }

    @Override
    public void streamSynchronize(long streamHandle) {
        try {
            Native.streamSynchronize(streamHandle);
        } catch (UnsatisfiedLinkError e) {
            throw unsupported("streamSynchronize", e);
        }
    }

    @Override
    public long gemm(
            long aHandle,
            long bHandle,
            long cHandle,
            int m,
            int n,
            int k,
            boolean half,
            boolean transA,
            boolean transB,
            long streamHandle) {
        try {
            return Native.gemm(aHandle, bHandle, cHandle, m, n, k, half, transA, transB, streamHandle);
        } catch (UnsatisfiedLinkError e) {
            throw unsupported("gemm", e);
        }
    }

    private static UnsupportedOperationException unsupported(String name, Throwable cause) {
        return new UnsupportedOperationException(
                "this CratonVM native library does not implement " + name, cause);
    }
}
