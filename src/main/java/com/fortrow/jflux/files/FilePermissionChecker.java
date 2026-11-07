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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-05 10:38:59 EDT
 */
package com.fortrow.jflux.files;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.util.EnumSet;
import java.util.Set;

public final class FilePermissionChecker {

    private FilePermissionChecker() { }

    public static boolean checkPermissions(String path, int read, int write, int execute)
            throws IOException {

        Path file = Paths.get(path);

        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }

        FileStore store = Files.getFileStore(file);

        if (store.supportsFileAttributeView(PosixFileAttributeView.class)) {
            return hasExactPosixPermissions(file, read, write, execute);
        }

        if (store.supportsFileAttributeView(AclFileAttributeView.class)) {
            return hasWindowsAclEquivalent(file, read, write, execute);
        }

        return false;
    }

    public static boolean isPrivilegedOwner(String path) throws IOException {
        Path file = Paths.get(path);

        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }

        FileStore store = Files.getFileStore(file);

        if (store.supportsFileAttributeView(PosixFileAttributeView.class)) {
            return isPrivilegedPosixOwner(file);
        }

        if (store.supportsFileAttributeView(AclFileAttributeView.class)) {
            UserPrincipal owner = Files.getOwner(file, LinkOption.NOFOLLOW_LINKS);
            return isTrustedWindowsPrincipal(owner);
        }

        return false;
    }

    private static boolean isPrivilegedPosixOwner(Path file) throws IOException {
        try {
            Object uid = Files.getAttribute(file, "unix:uid", LinkOption.NOFOLLOW_LINKS);

            if (uid instanceof Number) {
                return ((Number) uid).longValue() == 0L;
            }
        } catch (UnsupportedOperationException ignored) {
            // Fall back to owner name below.
        }

        UserPrincipal owner = Files.getOwner(file, LinkOption.NOFOLLOW_LINKS);
        return "root".equals(owner.getName());
    }

    private static boolean hasExactPosixPermissions(Path file, int read, int write, int execute)
            throws IOException {
        Set<PosixFilePermission> expected = EnumSet.noneOf(PosixFilePermission.class);

        if (read == 4) {
            expected.add(PosixFilePermission.OWNER_READ);
        }
        if (write == 2) {
            expected.add(PosixFilePermission.OWNER_WRITE);
        }
        if (execute == 1) {
            expected.add(PosixFilePermission.OWNER_EXECUTE);
        }

        Set<PosixFilePermission> actual =
                Files.getPosixFilePermissions(file, LinkOption.NOFOLLOW_LINKS);

        return actual.equals(expected);
    }

    private static boolean hasWindowsAclEquivalent(Path file, int read, int write, int execute)
            throws IOException {
        AclFileAttributeView aclView = Files.getFileAttributeView(
                file,
                AclFileAttributeView.class,
                LinkOption.NOFOLLOW_LINKS
        );

        if (aclView == null) {
            return false;
        }

        UserPrincipal owner = Files.getOwner(file, LinkOption.NOFOLLOW_LINKS);

        boolean ownerCanRead = false;
        boolean ownerCanWrite = false;
        boolean ownerCanExecute = false;

        for (AclEntry entry : aclView.getAcl()) {
            if (entry.type() != AclEntryType.ALLOW) {
                continue;
            }

            UserPrincipal principal = entry.principal();
            Set<AclEntryPermission> permissions = entry.permissions();

            boolean relevantAccess =
                    grantsRead(permissions) || grantsWrite(permissions) || grantsExecute(permissions);

            if (!relevantAccess) {
                continue;
            }

            boolean isOwner = principal.equals(owner);

            if (!isOwner && !isTrustedWindowsPrincipal(principal)) {
                return false;
            }

            if (isOwner) {
                ownerCanRead |= grantsRead(permissions);
                ownerCanWrite |= grantsWrite(permissions);
                ownerCanExecute |= grantsExecute(permissions);
            }
        }

        return ownerCanRead == (read == 4)
                && ownerCanWrite == (write == 2)
                && ownerCanExecute == (execute == 1);
    }

    private static boolean grantsRead(Set<AclEntryPermission> permissions) {
        return permissions.contains(AclEntryPermission.READ_DATA);
    }

    private static boolean grantsWrite(Set<AclEntryPermission> permissions) {
        return permissions.contains(AclEntryPermission.WRITE_DATA)
                || permissions.contains(AclEntryPermission.APPEND_DATA)
                || permissions.contains(AclEntryPermission.WRITE_ACL)
                || permissions.contains(AclEntryPermission.WRITE_OWNER);
    }

    private static boolean grantsExecute(Set<AclEntryPermission> permissions) {
        return permissions.contains(AclEntryPermission.EXECUTE);
    }

    private static boolean isTrustedWindowsPrincipal(UserPrincipal principal) {
        String name = principal.getName();

        return name.equalsIgnoreCase("NT AUTHORITY\\SYSTEM")
                || name.equalsIgnoreCase("SYSTEM")
                || name.equalsIgnoreCase("BUILTIN\\Administrators")
                || name.endsWith("\\Administrators");
    }
}