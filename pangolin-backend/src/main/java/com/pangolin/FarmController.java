/*
 * Copyright © 2026 Pangolin
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pangolin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Controller for Flamenco farm status.
 * Proxies /api/v3/status from Flamenco Manager for the farm status badge.
 */
@RestController
@RequestMapping("/api/farm")
public class FarmController {

    private static final Logger log = LoggerFactory.getLogger(FarmController.class);

    private final RestTemplate restTemplate;
    private final String managerUrl;

    public FarmController(RestTemplate restTemplate,
                          @Value("${flamenco.manager.url:http://flamenco-manager:8080}") String managerUrl) {
        this.restTemplate = restTemplate;
        this.managerUrl = managerUrl;
    }

    /**
     * Get the current farm status for the status badge in the header.
     * Maps directly from Flamenco's /api/v3/status response.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getFarmStatus() {
        String url = managerUrl + "/api/v3/status";

        log.debug("Fetching farm status from: {}", url);

        try {
            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response =
                (ResponseEntity<Map<String, Object>>) (ResponseEntity<?>) restTemplate.getForEntity(url, Map.class);

            log.debug("Farm status: {}", response.getBody());
            return ResponseEntity.ok(response.getBody());

        } catch (Exception e) {
            log.error("Error fetching farm status from Flamenco Manager", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                        "status", "unknown",
                        "error", e.getMessage()
                    ));
        }
    }
}
