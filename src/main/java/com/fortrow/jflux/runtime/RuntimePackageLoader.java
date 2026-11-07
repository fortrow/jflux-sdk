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

package com.fortrow.jflux.runtime;

import com.fortrow.jflux.exceptions.ExceptionHandling;

import javax.crypto.SecretKey;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/** Loads plain or encrypted function JARs without writing decrypted bytes to disk. */
public final class RuntimePackageLoader {
    private final ClassLoader parent;
    private final RuntimeLoadOptions options;

    public RuntimePackageLoader() {
        this(RuntimePackageLoader.class.getClassLoader(), RuntimeLoadOptions.defaults());
    }

    public RuntimePackageLoader(ClassLoader parent, RuntimeLoadOptions options) {
        this.parent = parent == null ? RuntimePackageLoader.class.getClassLoader() : parent;
        this.options = options == null ? RuntimeLoadOptions.defaults() : options;
    }

    public RuntimePackage load(Path jar, RuntimeFunctionSource source) throws IOException {
        if(jar == null || !Files.isRegularFile(jar)) throw new IOException("Runtime JAR does not exist: " + jar);
        byte[] bytes = Files.readAllBytes(jar);
        try {
            return load(bytes, source);
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    public RuntimePackage load(byte[] jar, RuntimeFunctionSource source) throws IOException {
        if(jar == null) throw new IOException("Runtime JAR is null");
        Properties manifest = readManifest(jar);
        RuntimeFunctionDescriptor descriptor = descriptor(manifest, source, "", "");
        RuntimeClassLoader loader = new RuntimeClassLoader(jar, parent, options);
        try {
            List<RuntimeFunction> functions = new ArrayList<>(loader.loadServices(RuntimeFunction.class));
            validateFunctions(functions);
            return new RuntimePackage(descriptor, functions, loader);
        } catch(Exception failure) {
            loader.close();
            ExceptionHandling.report(failure);
            throw new IOException("Unable to load runtime function package", failure);
        }
    }

    public RuntimePackage loadEncrypted(byte[] encoded, RuntimePackageKeyProvider keys,
                                        RuntimeFunctionSource source) throws Exception {
        if(keys == null) throw new IllegalArgumentException("keys is required");
        EncryptedRuntimePackage encrypted = EncryptedRuntimePackage.parse(encoded);
        SecretKey key = keys.keyFor(encrypted.keyId(), encrypted.packageId(), encrypted.version());
        if(key == null) throw new IOException("No key returned for runtime package " + encrypted.packageId());
        byte[] jar = encrypted.decrypt(key);
        try {
            Properties manifest = readManifest(jar);
            RuntimeFunctionDescriptor descriptor = descriptor(manifest, source, encrypted.packageId(), encrypted.keyId());
            RuntimeClassLoader loader = new RuntimeClassLoader(jar, parent, options);
            try {
                List<RuntimeFunction> functions = new ArrayList<>(loader.loadServices(RuntimeFunction.class));
                validateFunctions(functions);
                return new RuntimePackage(descriptor, functions, loader);
            } catch(Exception failure) {
                loader.close();
                throw failure;
            }
        } finally {
            Arrays.fill(jar, (byte) 0);
        }
    }

    private static Properties readManifest(byte[] jar) throws IOException {
        try(JarStream input = new JarStream(jar)) {
            java.util.jar.JarEntry entry;
            while((entry = input.getNextJarEntry()) != null) {
                if(entry.getName().equals("META-INF/jflux-runtime.properties")
                        || entry.getName().equals("META-INF/fortrow-function.properties")) {
                    Properties properties = new Properties();
                    properties.load(new ByteArrayInputStream(readEntry(input, 1_048_576)));
                    return properties;
                }
            }
        }
        throw new IOException("Runtime package is missing META-INF/jflux-runtime.properties");
    }

    private static byte[] readEntry(InputStream input, int limit) throws IOException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while((read = input.read(buffer)) != -1) {
            total += read;
            if(total > limit) throw new IOException("Runtime manifest exceeds memory limit");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static RuntimeFunctionDescriptor descriptor(Properties manifest, RuntimeFunctionSource source,
                                                         String encryptedPackageId, String keyId) {
        String packageId = property(manifest, "packageId", encryptedPackageId);
        if(packageId.isBlank()) packageId = property(manifest, "id", "runtime-package");
        String id = property(manifest, "id", packageId);
        String version = property(manifest, "version", "0");
        String replacesValue = property(manifest, "replaces", "");
        List<String> replaces = replacesValue.isBlank() ? List.of() : Arrays.stream(replacesValue.split(","))
                .map(String::trim).filter(value -> !value.isBlank()).toList();
        String expiry = property(manifest, "expiresAt", "");
        Instant expiresAt = expiry.isBlank() ? null : Instant.parse(expiry);
        boolean override = Boolean.parseBoolean(property(manifest, "override", "false"));
        return new RuntimeFunctionDescriptor(id, version, source, replaces, expiresAt,
                packageId, property(manifest, "keyId", keyId), override);
    }

    private static String property(Properties properties, String key, String fallback) {
        String value = properties.getProperty(key, fallback);
        return value == null ? "" : value.trim();
    }

    private static void validateFunctions(List<RuntimeFunction> functions) throws IOException {
        if(functions.isEmpty()) throw new IOException("Runtime package has no RuntimeFunction providers");
        for(RuntimeFunction function : functions) {
            if(function == null || function.id() == null || function.id().isBlank()) {
                throw new IOException("Runtime package contains a blank function ID");
            }
        }
    }

    private static final class JarStream extends java.util.jar.JarInputStream {
        private JarStream(byte[] bytes) throws IOException {
            super(new ByteArrayInputStream(bytes));
        }
    }
}
