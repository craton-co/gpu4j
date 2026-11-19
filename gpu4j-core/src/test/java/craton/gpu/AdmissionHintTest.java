// SPDX-License-Identifier: Apache-2.0
// Copyright 2025-2026 Craton Software Company (https://craton.com.ar)

package craton.gpu;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Wire-format stability tests for {@link AdmissionHint}.
 *
 * <p>The enum constant names and ordinals are part of the stable wire format
 * shared with downstream CratonVM versions. These tests lock the current order
 * and names so that any reorder, rename, or removal fails fast.</p>
 */
class AdmissionHintTest {

    @Test
    void values_areExactlyFour() {
        assertThat(AdmissionHint.values()).hasSize(4);
    }

    @Test
    void valueOf_STRICT_succeeds() {
        assertThat(AdmissionHint.valueOf("STRICT")).isEqualTo(AdmissionHint.STRICT);
    }

    @Test
    void valueOf_ALLOW_ALLOCATION_succeeds() {
        assertThat(AdmissionHint.valueOf("ALLOW_ALLOCATION"))
                .isEqualTo(AdmissionHint.ALLOW_ALLOCATION);
    }

    @Test
    void valueOf_ALLOW_DIV_BY_ZERO_succeeds() {
        assertThat(AdmissionHint.valueOf("ALLOW_DIV_BY_ZERO"))
                .isEqualTo(AdmissionHint.ALLOW_DIV_BY_ZERO);
    }

    @Test
    void valueOf_ALLOW_INTRINSIC_CALLS_succeeds() {
        assertThat(AdmissionHint.valueOf("ALLOW_INTRINSIC_CALLS"))
                .isEqualTo(AdmissionHint.ALLOW_INTRINSIC_CALLS);
    }

    @Test
    void valueOf_unknownName_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> AdmissionHint.valueOf("NOT_A_HINT"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ordinal_STRICT_isZero() {
        assertThat(AdmissionHint.STRICT.ordinal()).isEqualTo(0);
    }

    @Test
    void ordinal_ALLOW_ALLOCATION_isOne() {
        assertThat(AdmissionHint.ALLOW_ALLOCATION.ordinal()).isEqualTo(1);
    }

    @Test
    void ordinal_ALLOW_DIV_BY_ZERO_isTwo() {
        assertThat(AdmissionHint.ALLOW_DIV_BY_ZERO.ordinal()).isEqualTo(2);
    }

    @Test
    void ordinal_ALLOW_INTRINSIC_CALLS_isThree() {
        assertThat(AdmissionHint.ALLOW_INTRINSIC_CALLS.ordinal()).isEqualTo(3);
    }

    @Test
    void names_areInStableDeclarationOrder() {
        String[] names = new String[AdmissionHint.values().length];
        for (int i = 0; i < AdmissionHint.values().length; i++) {
            names[i] = AdmissionHint.values()[i].name();
        }
        assertThat(names).containsExactly(
                "STRICT",
                "ALLOW_ALLOCATION",
                "ALLOW_DIV_BY_ZERO",
                "ALLOW_INTRINSIC_CALLS");
    }
}
