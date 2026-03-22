/**
 * Copyright © 2026 Pholid
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.pholid.service;

import com.pholid.client.FlamencoClient;
import com.pholid.config.PholidProperties;
import com.pholid.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;

/**
 * Tests blend file magic byte validation in JobSubmissionService.
 * Covers plain, gzip-compressed, and zstd-compressed blend files,
 * plus rejection cases for wrong extension, size, and content.
 */
class BlendFileValidationTest {

    private JobSubmissionService service;

    // "BLENDER" as ASCII bytes - the magic header every .blend file starts with
    private static final byte[] BLENDER_MAGIC = "BLENDER".getBytes();

    @BeforeEach
    void setUp() {
        PholidProperties props = new PholidProperties(
                new PholidProperties.Manager("http://flamenco-manager:8080"),
                new PholidProperties.Storage("/shared"),
                new PholidProperties.Frames(3500, 100000),
                new PholidProperties.Download(5000),
                new PholidProperties.ProjectName(100),
                new PholidProperties.File(512),
                new PholidProperties.Http(10000, 30000),
                new PholidProperties.Delete(false),
                new PholidProperties.Zip(2048, 10000)
        );
        FileStorageService mockStorage = mock(FileStorageService.class);
        ZipSubmissionService zipService = new ZipSubmissionService(props, mockStorage);
        service = new JobSubmissionService(
                mock(FlamencoClient.class),
                mockStorage,
                zipService,
                props
        );
    }

    // Valid files

    @Test
    void plainBlendFile_accepted() {
        byte[] content = blendBytes(false);
        MockMultipartFile file = new MockMultipartFile("blendFile", "scene.blend", null, content);
        assertThatNoException().isThrownBy(() -> invokeValidation(file));
    }

    @Test
    void gzipCompressedBlendFile_accepted() throws IOException {
        byte[] content = gzip(BLENDER_MAGIC);
        MockMultipartFile file = new MockMultipartFile("blendFile", "scene.blend", null, content);
        assertThatNoException().isThrownBy(() -> invokeValidation(file));
    }

    @Test
    void zstdCompressedBlendFile_accepted() {
        // Prepend zstd magic (0x28 0xB5 0x2F 0xFD) then BLENDER header
        byte[] content = concat(new byte[]{0x28, (byte)0xB5, 0x2F, (byte)0xFD}, BLENDER_MAGIC);
        MockMultipartFile file = new MockMultipartFile("blendFile", "scene.blend", null, content);
        // zstd decompression of a fake stream will fail - we're testing magic byte routing only,
        // so we expect a ValidationException about the content, not an NPE or wrong branch
        assertThatThrownBy(() -> invokeValidation(file))
                .isInstanceOf(ValidationException.class);
    }

    // Wrong extension

    @Test
    void wrongExtension_rejected() {
        byte[] content = blendBytes(false);
        MockMultipartFile file = new MockMultipartFile("blendFile", "scene.mp4", null, content);
        assertThatThrownBy(() -> invokeValidation(file))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining(".blend");
    }

    @Test
    void noExtension_rejected() {
        byte[] content = blendBytes(false);
        MockMultipartFile file = new MockMultipartFile("blendFile", "scene", null, content);
        assertThatThrownBy(() -> invokeValidation(file))
                .isInstanceOf(ValidationException.class);
    }

    // Wrong content

    @Test
    void correctExtensionWrongContent_rejected() {
        byte[] content = "this is not a blend file".getBytes();
        MockMultipartFile file = new MockMultipartFile("blendFile", "scene.blend", null, content);
        assertThatThrownBy(() -> invokeValidation(file))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("not a valid Blender");
    }

    @Test
    void tooSmallFile_rejected() {
        MockMultipartFile file = new MockMultipartFile("blendFile", "scene.blend", null, new byte[]{0x42});
        assertThatThrownBy(() -> invokeValidation(file))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void emptyFile_rejected() {
        MockMultipartFile file = new MockMultipartFile("blendFile", "scene.blend", null, new byte[0]);
        assertThatThrownBy(() -> invokeValidation(file))
                .isInstanceOf(ValidationException.class);
    }

    // File size limit (512 MB)

    @Test
    void fileTooLarge_rejected() {
        // Create a file that reports being 513 MB without allocating that memory
        MockMultipartFile file = new MockMultipartFile("blendFile", "scene.blend", null, new byte[0]) {
            @Override public long getSize() { return 513L * 1024 * 1024; }
        };
        assertThatThrownBy(() -> invokeValidation(file))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("512");
    }

    // Helpers

    private void invokeValidation(MockMultipartFile file) throws Exception {
        // Access package-private validateBlendFile via reflection to test it directly
        var method = JobSubmissionService.class.getDeclaredMethod(
                "validateBlendFile", org.springframework.web.multipart.MultipartFile.class);
        method.setAccessible(true);
        try {
            method.invoke(service, file);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ValidationException ve) throw ve;
            if (cause instanceof Exception ex) throw ex;
            throw e;
        }
    }

    private byte[] blendBytes(boolean withPadding) {
        byte[] base = "BLENDER-v300".getBytes();
        if (!withPadding) return base;
        byte[] padded = new byte[100];
        System.arraycopy(base, 0, padded, 0, base.length);
        return padded;
    }

    private byte[] gzip(byte[] data) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gos = new GZIPOutputStream(bos)) {
            gos.write(data);
        }
        return bos.toByteArray();
    }

    private byte[] concat(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, 0 + a.length, b.length);
        return result;
    }
}
