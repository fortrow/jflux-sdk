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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;

public class StreamClassLoader extends ClassLoader {

    private final Map<String, byte[]> classData;

    public StreamClassLoader(JarFileObjectInputStream is) throws IOException {
        classData = new HashMap();
        JarEntry jarEntry = null;

        while ((jarEntry = is.getNextJarEntry()) != null) {
            String entryName = jarEntry.getName();
            ByteArrayOutputStream ba = new ByteArrayOutputStream();
            byte[] bite = new byte[1];

            while(is.read(bite, 0, 1) > 0) {
                ba.write(bite[0]);
                bite = new byte[1];
            }

            byte[] entryData = ba.toByteArray();

            if (entryName.endsWith(".class")) {
                String className = entryName.replace("/", ".").replace(".class", "");
                classData.put(className, entryData);
            }
        }
    }

    public String[] getAllClassNames() {
        Set<String> keyset = classData.keySet();
        return keyset.toArray(new String[keyset.size()]);
    }

    @Override
    public Class loadClass(String name) throws ClassNotFoundException {
        // note that it is required to first try loading the class using parent loader
        try {
            return super.loadClass(name);
        } catch (ClassNotFoundException e) {
            return findClass(name);
        }
    }

    @Override
    public Class findClass(String name) throws ClassNotFoundException {
        Class loadedClass = null;

        byte[] data = classData.getOrDefault(name, new byte[0]);
        if (data.length == 0) {
            throw new ClassNotFoundException();
        }

        loadedClass = defineClass(name, data, 0, data.length, null);

        return loadedClass;
    }
}
