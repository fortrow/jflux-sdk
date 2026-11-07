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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-11 19:05:33 EDT
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
        Path temporary = null;
        try {
            Path target=path.toAbsolutePath().normalize(),parent=target.getParent();
            Files.createDirectories(parent);
            if(Files.exists(target,java.nio.file.LinkOption.NOFOLLOW_LINKS)
                    && !Files.isRegularFile(target,java.nio.file.LinkOption.NOFOLLOW_LINKS))throw new IOException("Regular checkpoint required");
            temporary=Files.createTempFile(parent,".jflux-checkpoint-",".tmp");
            var posix=Files.getFileAttributeView(temporary,java.nio.file.attribute.PosixFileAttributeView.class);
            if(posix!=null)Files.setPosixFilePermissions(temporary,EnumSet.of(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE));
            else {
                var acl=Files.getFileAttributeView(temporary,java.nio.file.attribute.AclFileAttributeView.class);
                if(acl==null)throw new IOException("Private checkpoint ACL unavailable");
                acl.setAcl(java.util.List.of(java.nio.file.attribute.AclEntry.newBuilder()
                    .setType(java.nio.file.attribute.AclEntryType.ALLOW).setPrincipal(Files.getOwner(temporary))
                    .setPermissions(EnumSet.allOf(java.nio.file.attribute.AclEntryPermission.class)).build()));
            }
            if(Files.exists(target,java.nio.file.LinkOption.NOFOLLOW_LINKS) && !Files.getOwner(target).equals(Files.getOwner(temporary)))throw new IOException("Checkpoint owner mismatch");
            try(var channel=java.nio.channels.FileChannel.open(temporary,java.nio.file.StandardOpenOption.WRITE,java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                var bytes=StandardCharsets.UTF_8.encode(value+System.lineSeparator());
                while(bytes.hasRemaining())channel.write(bytes);channel.force(true);
            }
            Files.move(temporary,target,java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING);temporary=null;
            if(posix!=null)try(var directory=java.nio.channels.FileChannel.open(parent,java.nio.file.StandardOpenOption.READ)){directory.force(true);}
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to write secret file: " + path, e);
        } finally {
            if(temporary!=null)try{Files.deleteIfExists(temporary);}catch(IOException ignored){}
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
