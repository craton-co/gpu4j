// SPDX-License-Identifier: Apache-2.0
// Copyright 2025-2026 Craton Software Company (https://craton.com.ar)

package craton.gpu;

import craton.gpu.internal.ImmediateFutures;
import craton.gpu.internal.Native;
import craton.gpu.internal.StreamCleaner;
import craton.gpu.testing.GpuBackendSpi;

import java.lang.ref.Reference;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Typed handle to a primitive array residing on (or mirrored to) the GPU device.
 *
 * <p>Instances are thread-safe with respect to {@link #close()}: operations acquire a read lock
 * and {@link #close()} acquires the exclusive write lock so native resources cannot be freed while
 * a host/device transfer or launch is in flight. If the caller forgets to close a {@code GpuArray},
 * {@link StreamCleaner} releases the native handle when the Java wrapper becomes
 * phantom-reachable.</p>
 *
 * @param <T> primitive array type (for example {@code int[]} or {@code float[]})
 * @since 0.1.0
 */
public final class GpuArray<T> implements AutoCloseable {

    /** Element kind discriminant for {@code int[]}. */
    public static final int ELEM_INT = 0;

    /** Element kind discriminant for {@code long[]}. */
    public static final int ELEM_LONG = 1;

    /** Element kind discriminant for {@code float[]}. */
    public static final int ELEM_FLOAT = 2;

    /** Element kind discriminant for {@code double[]}. */
    public static final int ELEM_DOUBLE = 3;

    /** Element kind discriminant for {@code short[]} (including IEEE-754 binary16). */
    public static final int ELEM_SHORT = 4;

    private final long handle;
    private final Class<?> elementType;
    private final int length;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final StreamCleaner.Registration cleanable;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private GpuArray(long handle, Class<?> elementType, int length) {
        if (handle == 0L) {
            throw new GpuException("array allocation failed (native handle 0)");
        }
        this.handle = handle;
        this.elementType = Objects.requireNonNull(elementType, "elementType");
        this.length = length;
        this.cleanable = StreamCleaner.register(this, handle, StreamCleaner.Kind.ARRAY);
    }

    /**
     * Wraps a newly promoted device array handle from native code.
     */
    @SuppressWarnings("unused")
    GpuArray(long handle, int elemKind, int length) {
        this(handle, elementTypeForKind(elemKind), validateNonNegativeLength(length));
    }

    /**
     * Wraps a pre-allocated native device array handle owned by CratonVM's device cache.
     *
     * @param handle   non-zero native array handle
     * @param elemKind one of the {@code ELEM_*} constants
     * @param length   non-negative element count
     * @param <T>      expected primitive array type matching {@code elemKind}
     * @return wrapped {@code GpuArray}
     * @since 0.1.0
     */
    public static <T> GpuArray<T> fromNativeHandle(long handle, int elemKind, int length) {
        return new GpuArray<>(handle, elementTypeForKind(elemKind), validateNonNegativeLength(length));
    }

    /**
     * Uploads a host {@code int[]} to device memory.
     *
     * @param array non-null host array
     * @return device-resident {@code GpuArray}
     */
    public static GpuArray<int[]> wrap(int[] array) {
        Objects.requireNonNull(array, "host array must not be null");
        long h = Native.bridge().arrayWrapInt(array);
        return new GpuArray<>(h, Integer.TYPE, array.length);
    }

    /**
     * Uploads a host {@code long[]} to device memory.
     *
     * @param array non-null host array
     * @return device-resident {@code GpuArray}
     */
    public static GpuArray<long[]> wrap(long[] array) {
        Objects.requireNonNull(array, "host array must not be null");
        long h = Native.bridge().arrayWrapLong(array);
        return new GpuArray<>(h, Long.TYPE, array.length);
    }

    /**
     * Uploads a host {@code float[]} to device memory.
     *
     * @param array non-null host array
     * @return device-resident {@code GpuArray}
     */
    public static GpuArray<float[]> wrap(float[] array) {
        Objects.requireNonNull(array, "host array must not be null");
        long h = Native.bridge().arrayWrapFloat(array);
        return new GpuArray<>(h, Float.TYPE, array.length);
    }

    /**
     * Uploads a host {@code short[]} of IEEE-754 binary16 values to device memory.
     *
     * @param array non-null host array
     * @return device-resident {@code GpuArray}
     * @since 0.1.0
     */
    public static GpuArray<short[]> wrapHalf(short[] array) {
        Objects.requireNonNull(array, "host array must not be null");
        long h = Native.bridge().arrayWrapShort(array);
        return new GpuArray<>(h, Short.TYPE, array.length);
    }

    /**
     * Uploads a host {@code double[]} to device memory.
     *
     * @param array non-null host array
     * @return device-resident {@code GpuArray}
     */
    public static GpuArray<double[]> wrap(double[] array) {
        Objects.requireNonNull(array, "host array must not be null");
        long h = Native.bridge().arrayWrapDouble(array);
        return new GpuArray<>(h, Double.TYPE, array.length);
    }

    /**
     * Allocates a device-resident {@code int} buffer of {@code length} elements.
     *
     * @param length positive element count whose byte size fits within {@code Integer.MAX_VALUE}
     * @return allocated {@code GpuArray}
     * @since 0.1.0
     */
    public static GpuArray<int[]> allocateInt(int length) {
        checkByteSize(length, Integer.BYTES, "int");
        return device(Native.bridge().arrayAllocateInt(length), Integer.TYPE, length);
    }

    /**
     * Allocates a device-resident {@code long} buffer of {@code length} elements.
     *
     * @param length positive element count whose byte size fits within {@code Integer.MAX_VALUE}
     * @return allocated {@code GpuArray}
     * @since 0.1.0
     */
    public static GpuArray<long[]> allocateLong(int length) {
        checkByteSize(length, Long.BYTES, "long");
        return device(Native.bridge().arrayAllocateLong(length), Long.TYPE, length);
    }

    /**
     * Allocates a device-resident {@code float} buffer of {@code length} elements.
     *
     * @param length positive element count whose byte size fits within {@code Integer.MAX_VALUE}
     * @return allocated {@code GpuArray}
     * @since 0.1.0
     */
    public static GpuArray<float[]> allocateFloat(int length) {
        checkByteSize(length, Float.BYTES, "float");
        return device(Native.bridge().arrayAllocateFloat(length), Float.TYPE, length);
    }

    /**
     * Allocates a device-resident {@code double} buffer of {@code length} elements.
     *
     * @param length positive element count whose byte size fits within {@code Integer.MAX_VALUE}
     * @return allocated {@code GpuArray}
     * @since 0.1.0
     */
    public static GpuArray<double[]> allocateDouble(int length) {
        checkByteSize(length, Double.BYTES, "double");
        return device(Native.bridge().arrayAllocateDouble(length), Double.TYPE, length);
    }

    /**
     * Allocates a device-resident IEEE-754 binary16 ({@code short}) buffer of {@code length}
     * elements.
     *
     * @param length positive element count whose byte size fits within {@code Integer.MAX_VALUE}
     * @return allocated {@code GpuArray}
     * @since 0.1.0
     */
    public static GpuArray<short[]> allocateHalf(int length) {
        checkByteSize(length, Short.BYTES, "short");
        return device(Native.bridge().arrayAllocateShort(length), Short.TYPE, length);
    }

    private static <T> GpuArray<T> device(long handle, Class<?> elementType, int length) {
        return new GpuArray<>(handle, elementType, length);
    }

    /**
     * Copies the device array contents into a fresh host array wrapped in an immediate future.
     *
     * @return completed future containing a newly allocated host array
     * @throws IllegalStateException if this array has been closed
     * @throws GpuException          if the native transfer fails
     */
    @SuppressWarnings("unchecked")
    public GpuFuture<T> toHost() {
        lock.readLock().lock();
        try {
            ensureOpen();
            Object host = Native.bridge().arrayToHost(handle);
            if (host == null) {
                throw new GpuException("native arrayToHost returned null for handle " + handle);
            }
            int actualLen = primitiveArrayLength(host, elementType);
            if (actualLen != length) {
                throw new GpuException(
                        "native arrayToHost returned length " + actualLen + ", expected " + length);
            }
            return ImmediateFutures.completed((T) host);
        } finally {
            lock.readLock().unlock();
            Reference.reachabilityFence(this);
        }
    }

    /**
     * Copies this array's elements into {@code dest}.
     *
     * @param dest destination array with {@code dest.length >= length()}
     * @return number of elements written ({@link #length()})
     */
    public int toHost(int[] dest) {
        return toHostInto(dest, Integer.TYPE, "int[]");
    }

    /**
     * Copies this array's elements into {@code dest}.
     *
     * @param dest destination array with {@code dest.length >= length()}
     * @return number of elements written ({@link #length()})
     */
    public int toHost(long[] dest) {
        return toHostInto(dest, Long.TYPE, "long[]");
    }

    /**
     * Copies this array's elements into {@code dest}.
     *
     * @param dest destination array with {@code dest.length >= length()}
     * @return number of elements written ({@link #length()})
     * @since 0.1.0
     */
    public int toHost(short[] dest) {
        return toHostInto(dest, Short.TYPE, "short[]");
    }

    /**
     * Copies this array's elements into {@code dest}.
     *
     * @param dest destination array with {@code dest.length >= length()}
     * @return number of elements written ({@link #length()})
     */
    public int toHost(float[] dest) {
        return toHostInto(dest, Float.TYPE, "float[]");
    }

    /**
     * Copies this array's elements into {@code dest}.
     *
     * @param dest destination array with {@code dest.length >= length()}
     * @return number of elements written ({@link #length()})
     */
    public int toHost(double[] dest) {
        return toHostInto(dest, Double.TYPE, "double[]");
    }

    /**
     * Copies the first {@link #length()} elements from {@code src} into this device array.
     *
     * @param src source host array with {@code src.length >= length()}
     * @return number of elements copied ({@link #length()})
     * @since 0.1.0
     */
    public int copyFromHost(int[] src) {
        return copyFromHostFrom(src, Integer.TYPE, "int[]");
    }

    /**
     * Copies the first {@link #length()} elements from {@code src} into this device array.
     *
     * @param src source host array with {@code src.length >= length()}
     * @return number of elements copied ({@link #length()})
     * @since 0.1.0
     */
    public int copyFromHost(long[] src) {
        return copyFromHostFrom(src, Long.TYPE, "long[]");
    }

    /**
     * Copies the first {@link #length()} elements from {@code src} into this device array.
     *
     * @param src source host array with {@code src.length >= length()}
     * @return number of elements copied ({@link #length()})
     * @since 0.1.0
     */
    public int copyFromHost(short[] src) {
        return copyFromHostFrom(src, Short.TYPE, "short[]");
    }

    /**
     * Copies the first {@link #length()} elements from {@code src} into this device array.
     *
     * @param src source host array with {@code src.length >= length()}
     * @return number of elements copied ({@link #length()})
     * @since 0.1.0
     */
    public int copyFromHost(float[] src) {
        return copyFromHostFrom(src, Float.TYPE, "float[]");
    }

    /**
     * Copies the first {@link #length()} elements from {@code src} into this device array.
     *
     * @param src source host array with {@code src.length >= length()}
     * @return number of elements copied ({@link #length()})
     * @since 0.1.0
     */
    public int copyFromHost(double[] src) {
        return copyFromHostFrom(src, Double.TYPE, "double[]");
    }

    private int copyFromHostFrom(Object src, Class<?> expectedType, String label) {
        Objects.requireNonNull(src, "src");
        lock.readLock().lock();
        try {
            ensureOpen();
            if (elementType != expectedType) {
                throw new IllegalStateException(
                        "elementType mismatch: array holds " + elementType.getName()
                                + ", expected " + expectedType.getName() + " (" + label + ")");
            }
            int srcLen = primitiveArrayLength(src, expectedType);
            if (srcLen < length) {
                throw new IllegalArgumentException(
                        "src.length (" + srcLen + ") < array length (" + length + ")");
            }
            Object exactSrc = (srcLen == length) ? src : slicePrimitiveArray(src, expectedType, length);
            if (!Native.bridge().arrayCopyFromHost(handle, exactSrc)) {
                throw new GpuException("arrayCopyFromHost failed for handle " + handle);
            }
            return length;
        } finally {
            lock.readLock().unlock();
            Reference.reachabilityFence(this);
        }
    }

    private int toHostInto(Object dest, Class<?> expectedType, String label) {
        Objects.requireNonNull(dest, "dest");
        lock.readLock().lock();
        try {
            ensureOpen();
            if (elementType != expectedType) {
                throw new IllegalStateException(
                        "elementType mismatch: array holds " + elementType.getName()
                                + ", expected " + expectedType.getName() + " (" + label + ")");
            }
            int destLen = primitiveArrayLength(dest, expectedType);
            if (destLen < length) {
                throw new IllegalArgumentException(
                        "dest.length (" + destLen + ") < array length (" + length + ")");
            }
            GpuBackendSpi bridge = Native.bridge();
            if (bridge.arrayToHostInto(handle, dest)) {
                return length;
            }
            Object raw = bridge.arrayToHost(handle);
            if (raw == null) {
                throw new GpuException("native arrayToHost returned null for handle " + handle);
            }
            int rawLen = primitiveArrayLength(raw, expectedType);
            if (rawLen != length) {
                throw new GpuException(
                        "native arrayToHost returned length " + rawLen + ", expected " + length);
            }
            System.arraycopy(raw, 0, dest, 0, length);
            return length;
        } finally {
            lock.readLock().unlock();
            Reference.reachabilityFence(this);
        }
    }

    private static Object slicePrimitiveArray(Object array, Class<?> expectedType, int len) {
        if (expectedType == Integer.TYPE) {
            return Arrays.copyOf((int[]) array, len);
        }
        if (expectedType == Long.TYPE) {
            return Arrays.copyOf((long[]) array, len);
        }
        if (expectedType == Short.TYPE) {
            return Arrays.copyOf((short[]) array, len);
        }
        if (expectedType == Float.TYPE) {
            return Arrays.copyOf((float[]) array, len);
        }
        return Arrays.copyOf((double[]) array, len);
    }

    private static int primitiveArrayLength(Object array, Class<?> expectedType) {
        if (expectedType == Integer.TYPE && array instanceof int[] arr) {
            return arr.length;
        }
        if (expectedType == Long.TYPE && array instanceof long[] arr) {
            return arr.length;
        }
        if (expectedType == Short.TYPE && array instanceof short[] arr) {
            return arr.length;
        }
        if (expectedType == Float.TYPE && array instanceof float[] arr) {
            return arr.length;
        }
        if (expectedType == Double.TYPE && array instanceof double[] arr) {
            return arr.length;
        }
        throw new GpuException(
                "unexpected array type " + array.getClass().getName()
                        + ", expected " + expectedType.getName() + "[]");
    }

    /**
     * Returns {@code true} if this array currently resides on the GPU device.
     *
     * @return residency status
     * @throws IllegalStateException if this array has been closed
     */
    public boolean isResident() {
        lock.readLock().lock();
        try {
            ensureOpen();
            return Native.bridge().arrayIsResident(handle);
        } finally {
            lock.readLock().unlock();
            Reference.reachabilityFence(this);
        }
    }

    /**
     * Returns the underlying native handle for this device array.
     *
     * @return non-zero native array handle
     * @throws IllegalStateException if this array has been closed
     */
    public long handle() {
        lock.readLock().lock();
        try {
            ensureOpen();
            return handle;
        } finally {
            lock.readLock().unlock();
            Reference.reachabilityFence(this);
        }
    }

    /**
     * Returns the primitive component type of this array (e.g. {@code int.class} or
     * {@code float.class}).
     *
     * @return primitive element class
     */
    public Class<?> elementType() {
        return elementType;
    }

    /**
     * Returns the element count of this array. Remains valid after {@link #close()}.
     *
     * @return element count
     */
    public int length() {
        return length;
    }

    /**
     * Releases the native device buffer. Idempotent across repeated calls.
     *
     * @throws GpuException if releasing the native handle fails
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        lock.writeLock().lock();
        try {
            cleanable.cleanThrowing();
        } finally {
            lock.writeLock().unlock();
            Reference.reachabilityFence(this);
        }
    }

    long rawHandleForLockOrder() {
        return handle;
    }

    ReentrantReadWriteLock.ReadLock readLock() {
        return lock.readLock();
    }

    void ensureOpenUnderLock() {
        ensureOpen();
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("array closed");
        }
    }

    private static Class<?> elementTypeForKind(int elemKind) {
        return switch (elemKind) {
            case ELEM_INT -> Integer.TYPE;
            case ELEM_LONG -> Long.TYPE;
            case ELEM_FLOAT -> Float.TYPE;
            case ELEM_DOUBLE -> Double.TYPE;
            case ELEM_SHORT -> Short.TYPE;
            default -> throw new IllegalArgumentException("unknown element kind: " + elemKind);
        };
    }

    private static int validateNonNegativeLength(int length) {
        if (length < 0) {
            throw new IllegalArgumentException("length must be non-negative: " + length);
        }
        return length;
    }

    private static void checkByteSize(int length, int elementBytes, String label) {
        if (length <= 0) {
            throw new IllegalArgumentException(label + " allocation length must be positive: " + length);
        }
        long bytes = (long) length * elementBytes;
        if (bytes > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(label + " allocation byte size overflows 2 GiB limit: " + bytes);
        }
    }
}
