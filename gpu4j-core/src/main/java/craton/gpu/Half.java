// SPDX-License-Identifier: Apache-2.0
// Copyright 2025-2026 Craton Software Company (https://craton.com.ar)

package craton.gpu;

import java.util.Objects;

/**
 * IEEE 754 binary16 (half-precision float, {@code short}-packed) conversion helpers using
 * round-to-nearest-even (RNE) rounding.
 *
 * @since 0.1.0
 */
public final class Half {

    /** Largest finite IEEE 754 binary16 value ({@code 65504.0f}). */
    public static final float MAX_VALUE = 65504.0f;

    /** Smallest positive normal IEEE 754 binary16 value ({@code 2^-14}). */
    public static final float MIN_NORMAL = 0x1.0p-14f;

    /** Smallest positive subnormal IEEE 754 binary16 value ({@code 2^-24}). */
    public static final float MIN_VALUE = 0x1.0p-24f;

    private Half() {
    }

    /**
     * Converts a 32-bit IEEE 754 {@code float} to its IEEE 754 binary16 {@code short} encoding
     * using round-to-nearest-even.
     *
     * @param value single-precision float value
     * @return IEEE 754 binary16 bit pattern packed in a {@code short}
     */
    public static short fromFloat(float value) {
        int bits = Float.floatToRawIntBits(value);
        int sign = (bits >>> 16) & 0x8000;
        int exp = (bits >>> 23) & 0xFF;
        int mant = bits & 0x007F_FFFF;

        if (exp == 0xFF) {
            if (mant == 0) {
                return (short) (sign | 0x7C00);
            }
            int nanMant = mant >>> 13;
            if (nanMant == 0) {
                nanMant = 1;
            }
            return (short) (sign | 0x7E00 | nanMant);
        }

        if (exp == 0) {
            return (short) sign;
        }

        int hExp = exp - 112;
        if (hExp >= 31) {
            return (short) (sign | 0x7C00);
        }

        if (hExp > 0) {
            int low13 = mant & 0x1FFF;
            int kept = mant >>> 13;
            int roundBit = (low13 > 0x1000 || (low13 == 0x1000 && (kept & 1) != 0)) ? 1 : 0;
            return (short) (sign | ((hExp << 10) + kept + roundBit));
        }

        if (hExp < -10) {
            return (short) sign;
        }

        int sig = 0x0080_0000 | mant;
        int shift = 14 - hExp;
        int sub = sig >>> shift;
        int remMask = (1 << shift) - 1;
        int halfway = 1 << (shift - 1);
        int rem = sig & remMask;
        int roundBit = (rem > halfway || (rem == halfway && (sub & 1) != 0)) ? 1 : 0;
        return (short) (sign | (sub + roundBit));
    }

    /**
     * Converts an IEEE 754 binary16 {@code short} encoding to a 32-bit IEEE 754 {@code float}.
     *
     * @param bits IEEE 754 binary16 bit pattern
     * @return converted single-precision float value
     */
    public static float toFloat(short bits) {
        int h = bits & 0xFFFF;
        int sign = (h & 0x8000) << 16;
        int exp = (h >>> 10) & 0x1F;
        int mant = h & 0x03FF;

        if (exp == 0) {
            if (mant == 0) {
                return Float.intBitsToFloat(sign);
            }
            int m = mant;
            int fExp = 113;
            while ((m & 0x0400) == 0) {
                m <<= 1;
                fExp--;
            }
            m &= 0x03FF;
            return Float.intBitsToFloat(sign | (fExp << 23) | (m << 13));
        }

        if (exp == 31) {
            if (mant == 0) {
                return Float.intBitsToFloat(sign | 0x7F80_0000);
            }
            return Float.intBitsToFloat(sign | 0x7FC0_0000 | (mant << 13));
        }

        return Float.intBitsToFloat(sign | ((exp + 112) << 23) | (mant << 13));
    }

    /**
     * Converts every element of {@code src} into a newly allocated IEEE 754 binary16 array.
     *
     * @param src non-null single-precision source array
     * @return newly allocated binary16 {@code short[]}
     */
    public static short[] fromFloats(float[] src) {
        Objects.requireNonNull(src, "src");
        short[] dst = new short[src.length];
        for (int i = 0; i < src.length; i++) {
            dst[i] = fromFloat(src[i]);
        }
        return dst;
    }

    /**
     * Converts every IEEE 754 binary16 element of {@code src} into a newly allocated {@code float[]}.
     *
     * @param src non-null binary16 source array
     * @return newly allocated single-precision {@code float[]}
     */
    public static float[] toFloats(short[] src) {
        Objects.requireNonNull(src, "src");
        float[] dst = new float[src.length];
        for (int i = 0; i < src.length; i++) {
            dst[i] = toFloat(src[i]);
        }
        return dst;
    }
}
