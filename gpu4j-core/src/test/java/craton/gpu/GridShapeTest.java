// SPDX-License-Identifier: Apache-2.0
// Copyright 2025-2026 Craton Software Company (https://craton.com.ar)

package craton.gpu;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Wire-format stability tests for {@link GridShape}.
 *
 * <p>Locks the current declaration order so any reorder, rename or removal
 * breaks the build before it can break downstream binary compatibility.</p>
 */
class GridShapeTest {

    @Test
    void values_areExactlyThree() {
        assertThat(GridShape.values()).hasSize(3);
    }

    @Test
    void valueOf_ELEMENTWISE_succeeds() {
        assertThat(GridShape.valueOf("ELEMENTWISE")).isEqualTo(GridShape.ELEMENTWISE);
    }

    @Test
    void valueOf_ROW_PER_THREAD_succeeds() {
        assertThat(GridShape.valueOf("ROW_PER_THREAD")).isEqualTo(GridShape.ROW_PER_THREAD);
    }

    @Test
    void valueOf_BLOCK_REDUCTION_succeeds() {
        assertThat(GridShape.valueOf("BLOCK_REDUCTION")).isEqualTo(GridShape.BLOCK_REDUCTION);
    }

    @Test
    void valueOf_unknownName_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> GridShape.valueOf("NOT_A_SHAPE"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ordinal_ELEMENTWISE_isZero() {
        assertThat(GridShape.ELEMENTWISE.ordinal()).isEqualTo(0);
    }

    @Test
    void ordinal_ROW_PER_THREAD_isOne() {
        assertThat(GridShape.ROW_PER_THREAD.ordinal()).isEqualTo(1);
    }

    @Test
    void ordinal_BLOCK_REDUCTION_isTwo() {
        assertThat(GridShape.BLOCK_REDUCTION.ordinal()).isEqualTo(2);
    }

    @Test
    void names_areInStableDeclarationOrder() {
        String[] names = new String[GridShape.values().length];
        for (int i = 0; i < GridShape.values().length; i++) {
            names[i] = GridShape.values()[i].name();
        }
        assertThat(names).containsExactly(
                "ELEMENTWISE",
                "ROW_PER_THREAD",
                "BLOCK_REDUCTION");
    }
}
