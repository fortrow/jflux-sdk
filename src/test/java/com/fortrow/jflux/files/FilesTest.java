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

package com.fortrow.jflux.files;

import com.fortrow.jflux.exceptions.types.CommonIOException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilesTest {

    @Test
    void fileObjectPreservesBinaryBytesAndAppends() throws Throwable {
        Path root = java.nio.file.Files.createTempDirectory("jflux-files-");
        try {
            FileObject file = FilesHelper.open(root.resolve("data.bin"));
            byte[] first = {(byte) 0xff, 0, 1, (byte) 0x80};
            file.overwrite(first);
            file.append(new byte[]{2, 3});

            assertEquals(6, file.readToEnd());
            assertArrayEquals(new byte[]{(byte) 0xff, 0, 1, (byte) 0x80, 2, 3}, file.bytes());
            assertEquals(6, file.fileSize());
        } finally {
            FilesHelper.deleteTree(root);
        }
    }

    @Test
    void pathAndCopyHelpersHandleCommonEdgeCases() throws Throwable {
        Path root = java.nio.file.Files.createTempDirectory("jflux-path-");
        try {
            PathObject directory = PathUtil.fromString(root.toString());
            FileObject source = Files.create(directory, "plain", "π");
            assertNull(FilesApi.create(directory, "plain", "replacement", false));

            FileObject copy = FilesApi.duplicate(source, directory.clone("nested"));
            copy.readToEnd();
            assertEquals("π", copy.string());
            assertEquals("", PathUtil.isolateFile("README")[3]);
            assertEquals("archive.tar", PathUtil.isolateFile("/tmp/archive.tar.gz")[2]);

            PathObject first = PathUtil.fromString(root + "/{tenant}");
            PathObject second = first.clone().setVariable("tenant", "second");
            assertNotEquals(first.getString(), second.getString());
            assertTrue(FilesApi.rmTree(root.toString()));
            assertFalse(java.nio.file.Files.exists(root));
        } finally {
            if (java.nio.file.Files.exists(root)) FilesHelper.deleteTree(root);
        }
    }

    @Test
    void jsonFileUpdatesNestedObjectsAndArrays() throws Throwable {
        Path root = java.nio.file.Files.createTempDirectory("jflux-json-");
        try {
            JSONObject child = new JSONObject();
            child.put("name", "old");
            JSONArray values = new JSONArray();
            values.add("zero");
            child.put("values", values);
            JSONObject document = new JSONObject();
            document.put("child", child);

            JSONFile json = new JSONFile(root.resolve("config.json").toString(), document);
            json.setByPath("child.name", "new");
            json.setByPath("child.values[0]", "updated");

            assertEquals("new", json.<String>getByPath("child.name"));
            assertEquals("updated", json.<String>getByPath("child.values[0]"));
            assertTrue(json.getSignature() != null && !json.getSignature().isEmpty());
            assertTrue(java.nio.file.Files.readString(root.resolve("config.json"), StandardCharsets.UTF_8).contains("updated"));
        } finally {
            FilesHelper.deleteTree(root);
        }
    }

    @Test
    void defaultLogFileWritesToInjectedStderr() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        LogFile log = new LogFile(new PrintStream(output, true, StandardCharsets.UTF_8));

        log.write("fallback message");

        String text = output.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("fallback message"));
        assertTrue(text.matches("(?s)\\[\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}] :: fallback message\\R"));
    }

    private static final class FilesHelper {
        static FileObject open(Path path) throws CommonIOException { return com.fortrow.jflux.files.Files.open(path.toString()); }
        static void deleteTree(Path path) throws Exception {
            if (!java.nio.file.Files.exists(path)) return;
            com.fortrow.jflux.files.Files.rmTree(path.toString());
        }
    }

    private static final class FilesApi {
        static FileObject create(PathObject path, String name, String text, boolean overwrite) {
            return com.fortrow.jflux.files.Files.create(path, name, text, overwrite);
        }
        static FileObject duplicate(FileObject source, PathObject path) { return com.fortrow.jflux.files.Files.duplicate(source, path); }
        static boolean rmTree(String path) { return com.fortrow.jflux.files.Files.rmTree(path); }
    }
}
