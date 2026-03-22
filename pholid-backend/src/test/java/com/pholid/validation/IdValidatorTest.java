/**
 * Copyright © 2026 Pholid
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pholid.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class IdValidatorTest {

    // ── Pholid IDs (16 lowercase hex chars) ──────────────────────────────

    @Test
    void validPholidId_accepted() {
        assertThat(IdValidator.isValidPholidId("a1b2c3d4e5f60718")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",                       // empty
        "a1b2c3d4e5f6071",        // 15 chars (too short)
        "a1b2c3d4e5f607189",      // 17 chars (too long)
        "A1B2C3D4E5F60718",       // uppercase not allowed
        "a1b2c3d4e5f6071g",       // 'g' not hex
        "a1b2c3d4-e5f6-0718",     // hyphens not allowed
    })
    void invalidPholidId_rejected(String id) {
        assertThat(IdValidator.isValidPholidId(id)).isFalse();
    }

    @Test
    void nullPholidId_rejected() {
        assertThat(IdValidator.isValidPholidId(null)).isFalse();
    }

    // ── Flamenco IDs (standard UUID format) ────────────────────────────────

    @Test
    void validFlamencoId_accepted() {
        assertThat(IdValidator.isValidFlamencoId("550e8400-e29b-41d4-a716-446655440000")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "550e8400-e29b-41d4-a716-44665544000",   // too short
        "550e8400-e29b-41d4-a716-4466554400000",  // too long
        "550e8400-e29b-41d4-a716-44665544000G",   // non-hex char
        "550e8400e29b41d4a716446655440000",        // no hyphens
        "550E8400-E29B-41D4-A716-446655440000",   // uppercase
    })
    void invalidFlamencoId_rejected(String id) {
        assertThat(IdValidator.isValidFlamencoId(id)).isFalse();
    }

    @Test
    void nullFlamencoId_rejected() {
        assertThat(IdValidator.isValidFlamencoId(null)).isFalse();
    }
}
