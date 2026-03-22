/**
 * Copyright © 2026 Pholid
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pholid.controller;

import com.pholid.service.UserContextService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the main UI.
 * Must be @Controller (not @RestController) so Thymeleaf resolves the view name.
 */
@Controller
public class IndexController {

    private final UserContextService userContextService;

    public IndexController(UserContextService userContextService) {
        this.userContextService = userContextService;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("isAdmin",     userContextService.isAdmin());
        model.addAttribute("currentUser", userContextService.getCurrentUsername());
        model.addAttribute("fullName",    userContextService.getFullName());
        model.addAttribute("email",       userContextService.getEmail());
        return "upload";
    }
}