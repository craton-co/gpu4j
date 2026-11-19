// SPDX-License-Identifier: Apache-2.0
// Copyright 2025-2026 Craton Software Company (https://craton.com.ar)

package craton.gpu;

import craton.gpu.internal.Native;
import craton.gpu.testing.GpuBackendSpi;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Snapshot describing an available GPU compute device reported by the runtime.
 *
 * @param ordinal                zero-based device ordinal
 * @param name                   device product name
 * @param totalMemoryBytes       total device memory in bytes
 * @param computeCapabilityMajor CUDA compute capability major version (or {@code 0} if unknown)
 * @param computeCapabilityMinor CUDA compute capability minor version (or {@code 0} if unknown)
 * @since 0.1.0
 */
public record GpuDevice(
        int ordinal,
        String name,
        long totalMemoryBytes,
        int computeCapabilityMajor,
        int computeCapabilityMinor) {

    public GpuDevice {
        Objects.requireNonNull(name, "name");
        if (ordinal < 0) {
            throw new IllegalArgumentException("ordinal must be non-negative: " + ordinal);
        }
        if (totalMemoryBytes < 0L) {
            throw new IllegalArgumentException("totalMemoryBytes must be non-negative: " + totalMemoryBytes);
        }
        if (computeCapabilityMajor < 0 || computeCapabilityMinor < 0) {
            throw new IllegalArgumentException(
                    "compute capability must be non-negative: "
                            + computeCapabilityMajor + "." + computeCapabilityMinor);
        }
    }

    /**
     * Returns {@code true} if device discovery natives are bound and queryable.
     *
     * @return {@code true} when {@link #count()} can query the runtime
     */
    public static boolean queryable() {
        try {
            return Native.bridge().deviceCount() >= 0;
        } catch (UnsatisfiedLinkError | UnsupportedOperationException e) {
            return false;
        }
    }

    /**
     * Returns the number of visible GPU devices, or {@code 0} if unavailable.
     *
     * @return visible device count ({@code >= 0})
     */
    public static int count() {
        try {
            return Math.max(0, Native.bridge().deviceCount());
        } catch (UnsatisfiedLinkError | UnsupportedOperationException e) {
            return 0;
        }
    }

    /**
     * Queries the device at {@code ordinal}.
     *
     * @param ordinal zero-based device ordinal in {@code [0, count())}
     * @return immutable device descriptor
     * @throws IllegalArgumentException if {@code ordinal} is out of range
     * @throws GpuException             if the runtime reports no device metadata for {@code ordinal}
     */
    public static GpuDevice get(int ordinal) {
        return getChecked(ordinal, count(), Native.bridge());
    }

    /**
     * Returns descriptors for all visible GPU devices in ordinal order.
     *
     * @return immutable list of visible devices
     */
    public static List<GpuDevice> all() {
        GpuBackendSpi bridge = Native.bridge();
        int available;
        try {
            available = Math.max(0, bridge.deviceCount());
        } catch (UnsatisfiedLinkError | UnsupportedOperationException e) {
            return List.of();
        }
        List<GpuDevice> devices = new ArrayList<>(available);
        for (int i = 0; i < available; i++) {
            devices.add(getChecked(i, available, bridge));
        }
        return List.copyOf(devices);
    }

    private static GpuDevice getChecked(int ordinal, int available, GpuBackendSpi bridge) {
        if (ordinal < 0 || ordinal >= available) {
            throw new IllegalArgumentException(
                    "device ordinal " + ordinal + " out of range [0, " + available + ")");
        }
        String deviceName = bridge.deviceName(ordinal);
        if (deviceName == null) {
            throw new GpuException("no GPU device metadata for ordinal " + ordinal);
        }
        long totalMemory = Math.max(0L, bridge.deviceTotalMemory(ordinal));
        int packedCc = bridge.deviceComputeCapability(ordinal);
        int major = packedCc > 0 ? packedCc / 10 : 0;
        int minor = packedCc > 0 ? packedCc % 10 : 0;
        return new GpuDevice(ordinal, deviceName, totalMemory, major, minor);
    }

    /**
     * Formats the compute capability as {@code sm_<major><minor>} or {@code sm_unknown}.
     *
     * @return formatted architecture label
     */
    public String computeCapability() {
        if (computeCapabilityMajor == 0 && computeCapabilityMinor == 0) {
            return "sm_unknown";
        }
        return "sm_" + computeCapabilityMajor + computeCapabilityMinor;
    }

    @Override
    public String toString() {
        long mib = totalMemoryBytes / 1_048_576L;
        return "[" + ordinal + "] " + name + " (" + mib + " MiB, " + computeCapability() + ")";
    }
}
