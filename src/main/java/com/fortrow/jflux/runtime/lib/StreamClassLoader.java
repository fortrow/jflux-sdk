/*
 * -------------------------------------------------------------------------
 * 
 * Copyright (c) 2026 Fortrow LLC. All rights reserved.
 *  
 * NOTICE: All information contained herein is, and remains the property of 
 * Fortrow, LLC. The intellectual and technical concepts contained herein are 
 * proprietary to [Company Name] and may be covered by U.S. and Foreign Patents, 
 * patents in process, and are protected by trade secret or copyright law.
 * 
 * Dissemination of this information or reproduction of this material is 
 * strictly forbidden unless prior written permission is obtained from 
 * Fortrow, LLC.
 * 
 * -------------------------------------------------------------------------
 * 
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-04 15:27:04 EDT
 * 
 * -------------------------------------------------------------------------

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
