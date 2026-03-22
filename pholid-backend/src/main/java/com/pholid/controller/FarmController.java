/**
 * Copyright © 2026 Pholid
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pholid.controller;

import com.pholid.client.FlamencoClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Farm status endpoint. Previously also held RestTemplate + managerUrl setup
 * now just delegates to FlamencoClient. Connectivity errors handled by GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/api/farm")
public class FarmController {

    private static final Logger log = LoggerFactory.getLogger(FarmController.class);
    private final FlamencoClient flamencoClient;

    public FarmController(FlamencoClient flamencoClient) {
        this.flamencoClient = flamencoClient;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getFarmStatus() {
        Map<String, Object> status = flamencoClient.getFarmStatus();
        log.debug("Farm status: {}", status);
        return ResponseEntity.ok(status);
    }
}