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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @deprecated Use {@code Files.newInputStream} or the JFlux runtime package loader.
 */
@Deprecated(since = "1.1.0", forRemoval = false)
public class JarFileInputStream extends InputStream {

    private final InputStream input;

    public JarFileInputStream(String path) throws IOException {
        if(path == null || path.isBlank()) throw new IOException("JAR path is required");
        input = new BufferedInputStream(Files.newInputStream(Path.of(path)));
    }

    @Override
    public int read() throws IOException {
        return input.read();
    }

    @Override
    public int read(byte[] toStore) throws IOException {
        return input.read(toStore);
    }

    @Override
    public int read(byte[] toStore, int off, int len) throws IOException {
        return input.read(toStore, off, len);
    }

    @Override
    public void close() throws IOException {
        input.close();
    }

}
