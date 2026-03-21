/**
 * Copyright © 2026 Pangolin
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pangolin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class RenderApplication {

    public static void main(String[] args) {
        SpringApplication.run(RenderApplication.class, args);
    }
}
