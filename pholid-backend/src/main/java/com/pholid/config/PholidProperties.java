/**
 * Copyright © 2026 Pholid
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pholid.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds all pholid.* properties from application.properties into a single typed record.
 * Registered automatically via @ConfigurationPropertiesScan on RenderApplication.
 */
@ConfigurationProperties("pholid")
public record PholidProperties(
        Manager manager,
        Storage storage,
        Frames frames,
        Download download,
        ProjectName projectName,
        File file,
        Http http,
        Delete delete,
        Zip zip
) {
    public record Manager(String url) {}
    public record Storage(String root) {}
    public record Frames(int limit, int cap) {}
    public record Download(int maxFiles) {}
    public record ProjectName(int maxLength) {}
    public record File(long maxSizeMb) {}
    public record Http(int connectTimeout, int readTimeout) {}
    public record Delete(boolean enabled) {}
    public record Zip(long maxUncompressedMb, int maxEntries) {}
}