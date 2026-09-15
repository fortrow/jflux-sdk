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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-11 19:05:33 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.http.api.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;

public class FileSecretStore implements SecretStore {
    private final Path path;
    private final String defaultValue;

    public FileSecretStore(Path path) {
        this(path, null);
    }

    public FileSecretStore(Path path, String defaultValue) {
        if(path == null) throw new IllegalArgumentException("Secret file path is required.");
        this.path = path;
        this.defaultValue = defaultValue;
    }

    @Override
    public String read() {
        try {
            if(!Files.exists(path)) {
                if(defaultValue == null || defaultValue.isBlank()) {
                    throw new IllegalArgumentException("Secret file does not exist: " + path);
                }
                write(defaultValue);
                return defaultValue;
            }
            String value = Files.readString(path, StandardCharsets.UTF_8).trim();
            if(value.isBlank()) {
                if(defaultValue == null || defaultValue.isBlank()) {
                    throw new IllegalArgumentException("Secret file is empty: " + path);
                }
                write(defaultValue);
                return defaultValue;
            }
            return value;
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read secret file: " + path, e);
        }
    }

    @Override
    public void write(String value) {
        if(value == null || value.isBlank()) throw new IllegalArgumentException("Secret value is required.");
        try {
            Path parent = path.getParent();
            if(parent != null) Files.createDirectories(parent);
            Files.writeString(path, value + System.lineSeparator(), StandardCharsets.UTF_8);
            secureOwnerOnly(path);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to write secret file: " + path, e);
        }
    }

    @Override
    public void clear() {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to clear secret file: " + path, e);
        }
    }

    private static void secureOwnerOnly(Path path) {
        try {
            Files.setPosixFilePermissions(path, EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException | IOException ignored) {
        }
    }
}
