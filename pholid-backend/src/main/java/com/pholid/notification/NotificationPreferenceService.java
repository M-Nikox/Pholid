/**
 * Copyright © 2026 Pholid
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pholid.notification;

import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

/**
 * Service for loading and saving per-user notification preferences.
 *
 * <p>When a user has no preferences record yet, a default one is created on first access.
 * When auth is disabled, {@code "anonymous"} is used as the username so a single shared
 * preferences entry is maintained.
 */
@Service
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository repository;

    public NotificationPreferenceService(NotificationPreferenceRepository repository) {
        this.repository = repository;
    }

    /**
     * Returns the notification preferences for the given username, creating a default entry
     * if none exists.
     */
    public NotificationPreference getOrCreate(String username) {
        return repository.findByUsername(username)
                .orElseGet(() -> {
                    NotificationPreference pref = new NotificationPreference();
                    pref.setUsername(username);
                    pref.setCreatedAt(OffsetDateTime.now());
                    pref.setUpdatedAt(OffsetDateTime.now());
                    return repository.save(pref);
                });
    }

    /**
     * Saves updated notification preferences for the given username.
     * The username on the preference object is always set from the provided {@code username}
     * parameter to prevent unauthorized cross-user updates.
     */
    public NotificationPreference save(String username, NotificationPreference updated) {
        NotificationPreference existing = getOrCreate(username);
        existing.setEmailEnabled(updated.isEmailEnabled());
        existing.setEmailAddress(updated.getEmailAddress());
        existing.setWebhookEnabled(updated.isWebhookEnabled());
        existing.setWebhookUrl(updated.getWebhookUrl());
        existing.setNotifyOnComplete(updated.isNotifyOnComplete());
        existing.setNotifyOnFailure(updated.isNotifyOnFailure());
        existing.setUpdatedAt(OffsetDateTime.now());
        return repository.save(existing);
    }
}
