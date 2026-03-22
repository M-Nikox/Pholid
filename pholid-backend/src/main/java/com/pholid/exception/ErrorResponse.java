/**
 * Copyright © 2026 Pholid
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pholid.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Consistent JSON error body returned by GlobalExceptionHandler.
 * Replaces the ad-hoc Map.of("error", ...) pattern across all controllers.
 * Null fields are omitted from serialisation.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String error, String detail) {

    public ErrorResponse(String error) {
        this(error, null);
    }
}