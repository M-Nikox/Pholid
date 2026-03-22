/**
 * Copyright © 2026 Pholid
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pholid.controller;

import com.pholid.notification.NotificationPreference;
import com.pholid.notification.NotificationPreferenceService;
import com.pholid.service.UserContextService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Thymeleaf controller for the notification preferences page.
 *
 * <p>Available to all authenticated users (and to "anonymous" when auth is disabled).
 */
@Controller
@RequestMapping("/preferences")
public class PreferencesController {

    private final UserContextService userContextService;
    private final NotificationPreferenceService preferenceService;

    public PreferencesController(UserContextService userContextService,
                                  NotificationPreferenceService preferenceService) {
        this.userContextService = userContextService;
        this.preferenceService  = preferenceService;
    }

    @GetMapping
    public String showPreferences(Model model) {
        String username = userContextService.getCurrentUsername();
        NotificationPreference prefs = preferenceService.getOrCreate(username);
        model.addAttribute("prefs",       prefs);
        model.addAttribute("currentUser", username);
        model.addAttribute("fullName",    userContextService.getFullName());
        model.addAttribute("email",       userContextService.getEmail());
        model.addAttribute("isAdmin",     userContextService.isAdmin());
        return "preferences";
    }

    @PostMapping
    public String savePreferences(@ModelAttribute NotificationPreference prefs,
                                   RedirectAttributes redirectAttributes) {
        String username = userContextService.getCurrentUsername();
        preferenceService.save(username, prefs);
        redirectAttributes.addFlashAttribute("saved", true);
        return "redirect:/preferences";
    }
}
