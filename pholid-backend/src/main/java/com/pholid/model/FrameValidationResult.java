/**
 * Copyright © 2026 Pholid
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pholid.model;

/**
 * Sealed result type for frame range validation.
 * Pattern-matched at the call site, forces exhaustive handling of both cases.
 */
public sealed interface FrameValidationResult
        permits FrameValidationResult.Valid, FrameValidationResult.Invalid {

    record Valid(int start, int end, int totalFrames) implements FrameValidationResult {}
    record Invalid(String error) implements FrameValidationResult {}
}