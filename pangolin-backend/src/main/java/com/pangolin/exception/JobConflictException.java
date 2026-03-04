/**
 * Copyright © 2026 Pangolin
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pangolin.exception;

/** Thrown when a job cannot be deleted because it is not in a terminal state. Maps to HTTP 409. */
public class JobConflictException extends RuntimeException {
    public JobConflictException(String message) {
        super(message);
    }
}