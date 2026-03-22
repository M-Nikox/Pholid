/**
 * Copyright © 2026 Pholid
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pholid;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RenderApplication {

    public static void main(String[] args) {
        SpringApplication.run(RenderApplication.class, args);
    }
}