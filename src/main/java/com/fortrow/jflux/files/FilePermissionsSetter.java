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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-05 11:04:12 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.files;

import com.fortrow.jflux.exceptions.ExceptionHandling;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class FilePermissionsSetter {

    private FilePermissionsSetter() { }

    public static void setPermissions(String path, int read, int write, int execute)
            throws IOException {
        validatePermissionInput(read, write, execute);

        Path file = Paths.get(path);

        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("File does not exist: " + path);
        }

        FileStore store = Files.getFileStore(file);

        if (store.supportsFileAttributeView(PosixFileAttributeView.class)) {
            setPosixPermissions(file, read, write, execute);
            return;
        }

        if (store.supportsFileAttributeView(AclFileAttributeView.class)) {
            setWindowsAclEquivalent(file, read, write, execute);
            return;
        }

        throw ExceptionHandling.unsupported("Unsupported file permission model: " + path);
    }

    public static void setOwner(String path, String ownerName) throws IOException {
        Path file = Paths.get(path);

        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("File does not exist: " + path);
        }

        UserPrincipalLookupService lookupService = file.getFileSystem().getUserPrincipalLookupService();
        UserPrincipal owner = lookupService.lookupPrincipalByName(ownerName);
        Files.setOwner(file, owner);
    }

    public static void setPermissionsRecursive(String path, int read, int write, int execute)
            throws IOException {
        validatePermissionInput(read, write, execute);

        Path root = Paths.get(path);

        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("File does not exist: " + path);
        }

        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                setPermissions(file.toString(), read, write, execute);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) {
                    throw exc;
                }

                setPermissions(dir.toString(), read, write, execute);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static void setOwnerRecursive(String path, String ownerName) throws IOException {
        Path root = Paths.get(path);

        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("File does not exist: " + path);
        }

        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                setOwner(dir.toString(), ownerName);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                setOwner(file.toString(), ownerName);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static void setOwnerAndPermissions(String path, String ownerName, int read, int write, int execute)
            throws IOException {
        setOwner(path, ownerName);
        setPermissions(path, read, write, execute);
    }

    private static void validatePermissionInput(int read, int write, int execute) {
        if ((read != 0 && read != 4)
                || (write != 0 && write != 2)
                || (execute != 0 && execute != 1)) {
            throw ExceptionHandling.invalid(
                    "Expected POSIX owner bits: read=4 or 0, write=2 or 0, execute=1 or 0"
            );
        }
    }

    private static void setPosixPermissions(Path file, int read, int write, int execute)
            throws IOException {
        Set<PosixFilePermission> permissions = EnumSet.noneOf(PosixFilePermission.class);

        if (read == 4) {
            permissions.add(PosixFilePermission.OWNER_READ);
        }
        if (write == 2) {
            permissions.add(PosixFilePermission.OWNER_WRITE);
        }
        if (execute == 1) {
            permissions.add(PosixFilePermission.OWNER_EXECUTE);
        }

        Files.setPosixFilePermissions(file, permissions);
    }

    private static void setWindowsAclEquivalent(Path file, int read, int write, int execute)
            throws IOException {
        AclFileAttributeView aclView = Files.getFileAttributeView(
                file,
                AclFileAttributeView.class,
                LinkOption.NOFOLLOW_LINKS
        );

        if (aclView == null) {
            throw ExceptionHandling.unsupported("ACL file attribute view is unavailable: " + file);
        }

        UserPrincipal owner = Files.getOwner(file, LinkOption.NOFOLLOW_LINKS);
        List<AclEntry> acl = new ArrayList<>();

        Set<AclEntryPermission> ownerPermissions = getWindowsPermissions(read, write, execute);
        if (!ownerPermissions.isEmpty()) {
            acl.add(buildAllowEntry(owner, ownerPermissions));
        }

        addTrustedWindowsPrincipalIfPresent(file, acl, "NT AUTHORITY\\SYSTEM");
        addTrustedWindowsPrincipalIfPresent(file, acl, "BUILTIN\\Administrators");

        aclView.setAcl(acl);
    }

    private static Set<AclEntryPermission> getWindowsPermissions(int read, int write, int execute) {
        Set<AclEntryPermission> permissions = EnumSet.noneOf(AclEntryPermission.class);

        if (read == 4) {
            permissions.add(AclEntryPermission.READ_DATA);
            permissions.add(AclEntryPermission.READ_ATTRIBUTES);
            permissions.add(AclEntryPermission.READ_NAMED_ATTRS);
            permissions.add(AclEntryPermission.READ_ACL);
            permissions.add(AclEntryPermission.SYNCHRONIZE);
        }
        if (write == 2) {
            permissions.add(AclEntryPermission.WRITE_DATA);
            permissions.add(AclEntryPermission.APPEND_DATA);
            permissions.add(AclEntryPermission.WRITE_ATTRIBUTES);
            permissions.add(AclEntryPermission.WRITE_NAMED_ATTRS);
            permissions.add(AclEntryPermission.SYNCHRONIZE);
        }
        if (execute == 1) {
            permissions.add(AclEntryPermission.EXECUTE);
            permissions.add(AclEntryPermission.READ_ATTRIBUTES);
            permissions.add(AclEntryPermission.READ_ACL);
            permissions.add(AclEntryPermission.SYNCHRONIZE);
        }

        return permissions;
    }

    private static void addTrustedWindowsPrincipalIfPresent(Path file, List<AclEntry> acl, String principalName) {
        try {
            UserPrincipalLookupService lookupService = file.getFileSystem().getUserPrincipalLookupService();
            UserPrincipal principal = lookupService.lookupPrincipalByName(principalName);
            acl.add(buildAllowEntry(principal, getTrustedWindowsPermissions()));
        } catch (IOException ignored) {
            // Some Windows principal names can vary by domain or localization.
        }
    }

    private static Set<AclEntryPermission> getTrustedWindowsPermissions() {
        return EnumSet.of(
                AclEntryPermission.READ_DATA,
                AclEntryPermission.WRITE_DATA,
                AclEntryPermission.APPEND_DATA,
                AclEntryPermission.READ_NAMED_ATTRS,
                AclEntryPermission.WRITE_NAMED_ATTRS,
                AclEntryPermission.EXECUTE,
                AclEntryPermission.DELETE_CHILD,
                AclEntryPermission.READ_ATTRIBUTES,
                AclEntryPermission.WRITE_ATTRIBUTES,
                AclEntryPermission.DELETE,
                AclEntryPermission.READ_ACL,
                AclEntryPermission.WRITE_ACL,
                AclEntryPermission.WRITE_OWNER,
                AclEntryPermission.SYNCHRONIZE
        );
    }

    private static AclEntry buildAllowEntry(UserPrincipal principal, Set<AclEntryPermission> permissions) {
        return AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(principal)
                .setPermissions(permissions)
                .build();
    }
}
