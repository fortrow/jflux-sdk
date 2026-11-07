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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/** Parent-first in-memory JAR classloader with bounded expansion and cleanup. */
public final class RuntimeClassLoader extends ClassLoader implements AutoCloseable {
    private final Map<String, byte[]> classes = new LinkedHashMap<>();
    private final Map<String, byte[]> resources = new LinkedHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    public RuntimeClassLoader(byte[] jar) throws IOException {
        this(jar, RuntimeClassLoader.class.getClassLoader(), RuntimeLoadOptions.defaults());
    }

    public RuntimeClassLoader(byte[] jar, ClassLoader parent, RuntimeLoadOptions options) throws IOException {
        super(parent == null ? ClassLoader.getSystemClassLoader() : parent);
        if(jar == null || jar.length == 0) throw new IOException("Runtime JAR is empty");
        RuntimeLoadOptions limits = options == null ? RuntimeLoadOptions.defaults() : options;
        long total = 0;
        int entries = 0;
        try(JarInputStream input = new JarInputStream(new ByteArrayInputStream(jar))) {
            JarEntry entry;
            while((entry = input.getNextJarEntry()) != null) {
                if(entry.isDirectory()) continue;
                if(++entries > limits.maxEntries()) throw new IOException("Runtime JAR has too many entries");
                String name = entry.getName();
                if(name == null || name.isBlank() || name.indexOf('\0') >= 0) throw new IOException("Invalid JAR entry name");
                boolean classFile = name.endsWith(".class");
                int limit = classFile ? limits.maxClassBytes() : limits.maxResourceBytes();
                byte[] data = readEntry(input, limit);
                total += data.length;
                if(total > limits.maxTotalBytes()) throw new IOException("Runtime JAR exceeds memory limit");
                resources.put(name, data);
                if(classFile) {
                    String className = name.substring(0, name.length() - 6).replace('/', '.');
                    classes.put(className, data);
                }
            }
        } catch(IOException | RuntimeException failure) {
            clear();
            if(failure instanceof IOException io) throw io;
            throw new IOException("Unable to read runtime JAR", failure);
        }
    }

    private static byte[] readEntry(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(limit, 8192));
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while((read = input.read(buffer)) != -1) {
            total += read;
            if(total > limit) throw new IOException("Runtime JAR entry exceeds memory limit");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        if(closed.get()) throw new ClassNotFoundException(name + " (runtime package closed)");
        byte[] bytes = classes.get(name);
        if(bytes == null) throw new ClassNotFoundException(name);
        return defineClass(name, bytes, 0, bytes.length);
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        if(closed.get()) return null;
        byte[] data = resources.get(name);
        return data == null ? super.getResourceAsStream(name) : new ByteArrayInputStream(data);
    }

    public List<String> classNames() {
        return Collections.unmodifiableList(new ArrayList<>(classes.keySet()));
    }

    public <T> List<T> loadServices(Class<T> serviceType) throws Exception {
        if(serviceType == null) throw new IllegalArgumentException("serviceType is required");
        InputStream descriptor = getResourceAsStream("META-INF/services/" + serviceType.getName());
        if(descriptor == null) return List.of();
        ArrayList<T> providers = new ArrayList<>();
        try(BufferedReader reader = new BufferedReader(new InputStreamReader(descriptor, StandardCharsets.UTF_8))) {
            String line;
            while((line = reader.readLine()) != null) {
                int comment = line.indexOf('#');
                if(comment >= 0) line = line.substring(0, comment);
                line = line.trim();
                if(line.isBlank()) continue;
                providers.add(serviceType.cast(loadClass(line).getDeclaredConstructor().newInstance()));
            }
        }
        return List.copyOf(providers);
    }

    @Override
    public void close() {
        if(closed.compareAndSet(false, true)) clear();
    }

    private void clear() {
        classes.values().stream().distinct().forEach(bytes -> Arrays.fill(bytes, (byte) 0));
        resources.values().stream().filter(bytes -> !classes.containsValue(bytes))
                .forEach(bytes -> Arrays.fill(bytes, (byte) 0));
        classes.clear();
        resources.clear();
    }
}
