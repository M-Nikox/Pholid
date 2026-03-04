/**
 * Copyright © 2026 Pangolin
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pangolin.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the main UI.
 * Must be @Controller (not @RestController) so Thymeleaf resolves the view name.
 */
@Controller
public class IndexController {

    @GetMapping("/")
    public String index() {
        return "upload";
    }
}