/*
 * Copyright (c) 2026 Fortrow LLC.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Fortrow-Commercial
 *
 * JFlux is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License, version 3, as published by
 * the Free Software Foundation.
 *
 * JFlux is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with JFlux. If not, see <https://www.gnu.org/licenses/>.
 *
 * Alternatively, a separate commercial license is available from Fortrow LLC.
 * For terms permitting use without the AGPL copyleft requirements, contact
 * ryan@fortrow.com. Commercial rights require a separate written agreement.
 * See LICENSE and COMMERCIAL-LICENSE.md.
 */

package com.fortrow.jflux.jni;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JNITest {

    @Test
    void normalizesCommonPlatformAliases() {
        assertEquals(JNIPlatform.OperatingSystem.WINDOWS,
                JNIPlatform.of("Windows 11", "AMD64").operatingSystem());
        assertEquals(JNIPlatform.Architecture.ARM64,
                JNIPlatform.of("Mac OS X", "arm64").architecture());
        assertEquals("native/linux/x86_64/libdemo.so",
                JNIPlatform.of("Linux", "x86_64").resourcePath("demo"));
        assertEquals("libdemo.dylib",
                JNIPlatform.of("Darwin", "aarch64").libraryFileName("demo"));
    }

    @Test
    void optionsRejectUnsafeResourceRootsAndBoundExtraction() {
        assertThrows(IllegalArgumentException.class,
                () -> NativeLoadOptions.defaults().withResourceRoot("../native"));
        assertTrue(NativeLibraryLoader.resourcePath("demo", NativeLoadOptions.defaults())
                .startsWith("native/"));
    }

    @Test
    void generatesHeaderAndSourceForOverloadedNativeMethods() {
        String header = JNIHeaderGenerator.generate(NativeExample.class);
        String source = JNIHeaderGenerator.generateSourceTemplate(NativeExample.class);

        assertTrue(header.contains("#include <jni.h>"));
        assertTrue(header.contains("Java_com_fortrow_jflux_jni_JNITest_00024NativeExample_add__II"));
        assertTrue(header.contains("Java_com_fortrow_jflux_jni_JNITest_00024NativeExample_add__JJ"));
        assertTrue(source.contains("JNIEXPORT jint JNICALL"));
        assertTrue(source.contains("return 0;"));
    }

    @Test
    void builderCreatesPortableArgumentListWithoutRequiringCompilation() {
        JNIBuilder builder = JNI.builder("demo")
                .compiler("cc")
                .source(Path.of("native", "bridge.c"))
                .includeDirectory(Path.of("native", "include"))
                .define("JFLUX_TEST")
                .outputDirectory(Path.of("target", "jni-test"));

        List<String> command = builder.command();

        assertEquals("cc", command.getFirst());
        assertTrue(command.stream().anyMatch(value -> value.endsWith("bridge.c")));
        assertTrue(command.stream().anyMatch(value -> value.endsWith("libdemo.so")
                || value.endsWith("libdemo.dylib") || value.endsWith("demo.dll")));
        assertTrue(command.contains("-shared") || command.contains("-dynamiclib") || command.contains("/LD"));
        assertFalse(command.stream().anyMatch(value -> value.contains(";")));
    }

    private static final class NativeExample {
        private native int add(int left, int right);

        private native long add(long left, long right);
    }
}
