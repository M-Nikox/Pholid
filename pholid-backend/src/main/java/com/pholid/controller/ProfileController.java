/**
 * Copyright © 2026 Pholid
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pholid.controller;

import com.pholid.service.AvatarStorageService;
import com.pholid.service.UserContextService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * Handles user avatar upload and retrieval.
 *
 * GET  /api/profile/avatar/{username} — serves the avatar image (404 if none uploaded)
 * POST /api/profile/avatar            — uploads/replaces the calling user's avatar
 */
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final AvatarStorageService avatarStorage;
    private final UserContextService userContextService;

    public ProfileController(AvatarStorageService avatarStorage,
                             UserContextService userContextService) {
        this.avatarStorage      = avatarStorage;
        this.userContextService = userContextService;
    }

    @GetMapping("/avatar/{username}")
    public ResponseEntity<byte[]> getAvatar(@PathVariable String username) throws IOException {
        Optional<Path> avatarPath = avatarStorage.findAvatar(username);
        if (avatarPath.isEmpty()) return ResponseEntity.notFound().build();

        Path   path     = avatarPath.get();
        byte[] bytes    = Files.readAllBytes(path);
        String filename = path.getFileName().toString();

        String contentType = filename.endsWith(".png")  ? "image/png"
                           : filename.endsWith(".webp") ? "image/webp"
                           : filename.endsWith(".gif")  ? "image/gif"
                           : "image/jpeg";

        return ResponseEntity.ok()
                .header("Content-Type", contentType)
                // Cache for 1 hour — frontend busts with ?t= timestamp on upload
                .header("Cache-Control", "max-age=3600, private")
                .body(bytes);
    }

    @PostMapping("/avatar")
    public ResponseEntity<Map<String, Object>> uploadAvatar(
            @RequestParam("avatar") MultipartFile file) throws IOException {
        String username = userContextService.getCurrentUsername();
        avatarStorage.saveAvatar(username, file);
        return ResponseEntity.ok(Map.of(
                "message",   "Avatar updated.",
                "avatarUrl", "/api/profile/avatar/" + username
        ));
    }
}
