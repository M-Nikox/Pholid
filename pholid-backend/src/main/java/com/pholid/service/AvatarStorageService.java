/**
 * Copyright © 2026 Pholid
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pholid.service;

import com.pholid.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Manages user avatar images, stored at {pholid.userdata.root}/avatars/.
 *
 * Avatars are stored as {sanitized-username}.{ext}. Only one avatar per user
 * is kept — uploading a new one deletes the previous regardless of extension.
 */
@Service
public class AvatarStorageService {

    private static final Logger log = LoggerFactory.getLogger(AvatarStorageService.class);

    private static final long MAX_SIZE_BYTES = 2 * 1024 * 1024; // 2 MB
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif");
    private static final Map<String, String> MIME_TO_EXT = Map.of(
            "image/jpeg", "jpg",
            "image/png",  "png",
            "image/webp", "webp",
            "image/gif",  "gif");

    @Value("${pholid.userdata.root:/userdata}")
    private String userdataRoot;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns the path to the avatar file for the given username if one exists,
     * or empty if no avatar has been uploaded.
     */
    public Optional<Path> findAvatar(String username) {
        String safe = sanitize(username);
        Path dir = avatarsDir();
        for (String ext : List.of("jpg", "png", "webp", "gif")) {
            Path candidate = dir.resolve(safe + "." + ext);
            if (Files.exists(candidate)) return Optional.of(candidate);
        }
        return Optional.empty();
    }

    /**
     * Validates and saves an avatar for the given username, replacing any
     * previously uploaded avatar regardless of file extension.
     */
    public void saveAvatar(String username, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("No file provided.");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new ValidationException("Avatar must be under 2 MB.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new ValidationException("Only JPEG, PNG, WebP, or GIF images are allowed.");
        }

        String ext  = MIME_TO_EXT.get(contentType);
        String safe = sanitize(username);
        Path   dir  = avatarsDir();
        Files.createDirectories(dir);

        // Remove any existing avatar for this user (different extension)
        for (String oldExt : MIME_TO_EXT.values()) {
            Files.deleteIfExists(dir.resolve(safe + "." + oldExt));
        }

        Path target = dir.resolve(safe + "." + ext);
        file.transferTo(target);
        log.info("Avatar saved for user {} → {}", username, target.getFileName());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Path avatarsDir() {
        return Path.of(userdataRoot, "avatars");
    }

    /**
     * Strips anything that isn't alphanumeric, hyphen, underscore, or dot
     * to prevent path traversal via the username.
     */
    private String sanitize(String username) {
        return username.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
