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
 *
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-04 15:27:04 EDT
 */
package com.fortrow.jflux.runtime.lib;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.Arrays;

/**
 * @deprecated Use {@code com.fortrow.jflux.runtime.RuntimeClassLoader}.
 */
@Deprecated(since = "1.1.0", forRemoval = false)
public class StreamClassLoader extends ClassLoader implements AutoCloseable {

    private final Map<String, byte[]> classData = new LinkedHashMap<>();
    private boolean closed;

    public StreamClassLoader(JarFileObjectInputStream is) throws IOException {
        if(is == null) throw new IOException("JAR stream is required");
        JarEntry jarEntry;
        while((jarEntry = is.getNextJarEntry()) != null) {
            if(jarEntry.isDirectory()) continue;
            String entryName = jarEntry.getName();
            if (entryName.endsWith(".class")) {
                String className = entryName.substring(0, entryName.length() - 6).replace('/', '.');
                classData.put(className, is.readAllBytes());
            }
        }
    }

    public String[] getAllClassNames() {
        return classData.keySet().toArray(String[]::new);
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        // note that it is required to first try loading the class using parent loader
        try {
            return super.loadClass(name);
        } catch (ClassNotFoundException e) {
            return findClass(name);
        }
    }

    @Override
    public Class findClass(String name) throws ClassNotFoundException {
        if(closed) throw new ClassNotFoundException(name + " (loader closed)");
        byte[] data = classData.get(name);
        if(data == null) throw new ClassNotFoundException(name);
        return defineClass(name, data, 0, data.length);
    }

    @Override
    public void close() {
        if(closed) return;
        closed = true;
        classData.values().stream().distinct().forEach(bytes -> Arrays.fill(bytes, (byte) 0));
        classData.clear();
    }
}
