/**
 * Copyright © 2026 Pangolin
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pangolin.exception;

/** Thrown for invalid user input, mapped to HTTP 400 by GlobalExceptionHandler. */
public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}