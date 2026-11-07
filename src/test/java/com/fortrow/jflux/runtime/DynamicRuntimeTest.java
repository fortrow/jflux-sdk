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

import org.junit.jupiter.api.Test;

import javax.crypto.KeyGenerator;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicRuntimeTest {

    @Test
    void loadsAndExecutesAFunctionFromAnInMemoryJar() throws Exception {
        Properties manifest = manifest("sample", "1.0");
        try(DynamicRuntime runtime = new DynamicRuntime()) {
            runtime.load(functionJar(manifest), RuntimeFunctionSource.LOCAL_PACKAGE);
            RuntimeResult result = runtime.execute(new RuntimeRequest("sample", List.of("one"), Duration.ofSeconds(2)));
            assertTrue(result.successful());
            assertEquals("one", result.output());
        }
    }

    @Test
    void decryptsAndLoadsAFunctionWithoutWritingPlaintext() throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(128);
        var key = generator.generateKey();
        byte[] encrypted = EncryptedRuntimePackage.seal(functionJar(manifest("sample", "2.0")),
                "test-key", manifest("sample", "2.0"), key, nonce());

        try(DynamicRuntime runtime = new DynamicRuntime()) {
            runtime.loadEncrypted(encrypted, (keyId, packageId, version) -> key,
                    RuntimeFunctionSource.SERVER_EPHEMERAL);
            assertEquals("two", runtime.execute(new RuntimeRequest("sample", List.of("two"),
                    Duration.ofSeconds(2))).output());
        }
    }

    @Test
    void timesOutFunctionsAndCancelsTheirVirtualThread() {
        try(RuntimeFunctionRegistry registry = new RuntimeFunctionRegistry()) {
            registry.registerBuiltin(new RuntimeFunction() {
                public String id() { return "slow"; }
                public RuntimeResult execute(RuntimeContext context) throws Exception {
                    Thread.sleep(Duration.ofSeconds(5));
                    return RuntimeResult.success("finished");
                }
            });
            RuntimeResult result = registry.execute(new RuntimeRequest("slow", List.of(), Duration.ofMillis(20)));
            assertTrue(result.error().contains("timed out"));
        }
    }

    private static Properties manifest(String id, String version) {
        Properties properties = new Properties();
        properties.setProperty("packageId", id + "-package");
        properties.setProperty("id", id);
        properties.setProperty("version", version);
        return properties;
    }

    private static byte[] nonce() {
        byte[] nonce = new byte[12];
        nonce[0] = 7;
        return nonce;
    }

    private static byte[] functionJar(Properties manifest) throws Exception {
        String className = "com/fortrow/jflux/runtime/DynamicRuntimeTest$PackagedFunction.class";
        String service = PackagedFunction.class.getName() + "\n";
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try(JarOutputStream jar = new JarOutputStream(bytes)) {
            add(jar, "META-INF/jflux-runtime.properties", properties(manifest));
            add(jar, "META-INF/services/" + RuntimeFunction.class.getName(),
                    service.getBytes(StandardCharsets.UTF_8));
            try(var input = DynamicRuntimeTest.class.getClassLoader().getResourceAsStream(className)) {
                add(jar, className, input.readAllBytes());
            }
        }
        return bytes.toByteArray();
    }

    private static byte[] properties(Properties source) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        source.store(bytes, null);
        return bytes.toByteArray();
    }

    private static void add(JarOutputStream jar, String name, byte[] data) throws Exception {
        jar.putNextEntry(new JarEntry(name));
        jar.write(data);
        jar.closeEntry();
    }

    public static final class PackagedFunction implements RuntimeFunction {
        @Override
        public String id() { return "sample"; }

        @Override
        public RuntimeResult execute(RuntimeContext context) {
            return RuntimeResult.success(context.request().arguments().get(0));
        }
    }
}
