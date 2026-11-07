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

package com.fortrow.jflux.files;

import com.fortrow.jflux.exceptions.ExceptionHandling;
import com.fortrow.jflux.exceptions.types.CommonIOException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.UUID;

/** High-level file helpers with UTF-8 text defaults and safe NIO operations. */
public final class Files {

    private Files() { }

    public static FileObject touch(PathObject path, String filename) throws CommonIOException {
        return new FileObject(path, filename);
    }

    public static String getContent(String filepath) throws CommonIOException {
        return getContent(filepath, StandardCharsets.UTF_8);
    }

    public static String getContent(String filepath, Charset charset) throws CommonIOException {
        FileObject file = open(filepath);
        file.readToEnd();
        return file.string(charset);
    }

    public static FileObject create(PathObject path, String filename, String content) throws CommonIOException {
        FileObject file = new FileObject(path, filename);
        file.overwrite(content);
        return file;
    }

    /** Creates a file, returning null when overwrite is false and the file already exists. */
    public static FileObject create(PathObject path, String filename, String content, boolean overwrite) {
        Path target = path.clone(filename).toPath();
        try {
            if (!overwrite && java.nio.file.Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return null;
            FileObject file = new FileObject(path, filename);
            file.overwrite(content);
            return file;
        } catch (CommonIOException e) {
            throw ExceptionHandling.failure("Unable to create file '" + target + "'.", e);
        }
    }

    public static boolean mkdir(String directory) {
        try {
            Path path = Paths.get(directory);
            if (java.nio.file.Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return java.nio.file.Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS);
            java.nio.file.Files.createDirectories(path);
            return true;
        } catch (IOException | RuntimeException e) {
            ExceptionHandling.report(e);
            return false;
        }
    }

    public static FileObject overwrite(PathObject path, String filename, String content) {
        try {
            FileObject file = new FileObject(path, filename);
            file.overwrite(content);
            return file;
        } catch (CommonIOException e) {
            throw ExceptionHandling.failure("Unable to overwrite '" + path.clone(filename).getString() + "'.", e);
        }
    }

    public static FileObject duplicate(FileObject file, PathObject path) {
        if (file == null || path == null) throw ExceptionHandling.invalid("Source file and destination path are required.");
        Path destination = path.clone(file.getFile().getName()).toPath();
        try {
            Path parent = destination.getParent();
            if (parent != null) java.nio.file.Files.createDirectories(parent);
            java.nio.file.Files.copy(file.path(), destination, StandardCopyOption.REPLACE_EXISTING);
            return new FileObject(destination.toFile());
        } catch (IOException | CommonIOException e) {
            throw ExceptionHandling.failure("Unable to duplicate file to '" + destination + "'.", e);
        }
    }

    public static boolean rm(PathObject path, String filename) throws CommonIOException {
        return new FileObject(path, filename).delete();
    }

    /** Removes an empty directory. Use {@link #rmTree(String)} for recursive removal. */
    public static boolean rmDir(String directory) {
        Path path = Paths.get(directory);
        try {
            return java.nio.file.Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) && java.nio.file.Files.deleteIfExists(path);
        } catch (IOException | RuntimeException e) {
            ExceptionHandling.report(e);
            return false;
        }
    }

    /** Deletes a directory and its contents without following symbolic links. */
    public static boolean rmTree(String directory) {
        Path root = Paths.get(directory);
        if (!java.nio.file.Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return false;
        try {
            java.nio.file.Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    java.nio.file.Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult postVisitDirectory(Path dir, IOException failure) throws IOException {
                    if (failure != null) throw failure;
                    java.nio.file.Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
            return true;
        } catch (IOException | RuntimeException e) {
            ExceptionHandling.report(e);
            return false;
        }
    }

    public static boolean exists(String path) {
        return java.nio.file.Files.exists(Paths.get(path), LinkOption.NOFOLLOW_LINKS);
    }

    public static FileObject open(String filepath) throws CommonIOException {
        if (filepath == null || filepath.isBlank()) throw ExceptionHandling.invalid("File path is required.");
        Path path = Paths.get(filepath);
        try {
            Path parent = path.toAbsolutePath().normalize().getParent();
            if (parent != null) java.nio.file.Files.createDirectories(parent);
            if (!java.nio.file.Files.exists(path, LinkOption.NOFOLLOW_LINKS)) java.nio.file.Files.createFile(path);
            return new FileObject(path.toFile());
        } catch (IOException | RuntimeException e) {
            throw new CommonIOException("Unable to open file '" + filepath + "'.", e);
        }
    }

    public static FileObject createTmpFile() throws CommonIOException {
        return createTempFile("jflux-", "");
    }

    public static FileObject createTmpFile(String uuid) throws CommonIOException {
        return createTmpFile(uuid, "");
    }

    public static FileObject createTmpFile(String uuid, String suffix) throws CommonIOException {
        if (uuid == null || uuid.isBlank()) throw ExceptionHandling.invalid("Temporary filename is required.");
        try {
            Path directory = standardTmpDirectory();
            java.nio.file.Files.createDirectories(directory);
            Path tmpFile = directory.resolve(uuid + (suffix == null ? "" : suffix)).normalize();
            if (!tmpFile.startsWith(directory.normalize())) throw new CommonIOException("Temporary file escaped its directory.");
            java.nio.file.Files.createFile(tmpFile);
            return new FileObject(tmpFile.toFile());
        } catch (CommonIOException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new CommonIOException("Unable to create tmp file: " + e.getMessage(), e);
        }
    }

    /** Uses the platform temporary-file generator and avoids filename collisions. */
    public static FileObject createTempFile(String prefix, String suffix) throws CommonIOException {
        try {
            Path directory = standardTmpDirectory();
            java.nio.file.Files.createDirectories(directory);
            return new FileObject(java.nio.file.Files.createTempFile(directory, prefixOrDefault(prefix), suffix == null ? "" : suffix).toFile());
        } catch (IOException | RuntimeException e) {
            throw new CommonIOException("Unable to create temporary file.", e);
        }
    }

    public static FileObject tmpFile() throws CommonIOException {
        return createTmpFile();
    }

    public static boolean deleteTmpFile(String uuid) throws CommonIOException {
        return deleteTmpFile(uuid, "");
    }

    public static boolean deleteTmpFile(String uuid, String suffix) throws CommonIOException {
        try {
            Path directory = standardTmpDirectory().toAbsolutePath().normalize();
            Path tmpFile = directory.resolve(uuid + (suffix == null ? "" : suffix)).normalize();
            if (!tmpFile.startsWith(directory)) throw new CommonIOException("Tmp file must be inside the standard tmp directory.");
            return java.nio.file.Files.deleteIfExists(tmpFile);
        } catch (CommonIOException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new CommonIOException("Unable to delete tmp file '" + uuid + "': " + e.getMessage(), e);
        }
    }

    private static Path standardTmpDirectory() {
        return Paths.get(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
    }

    private static String prefixOrDefault(String prefix) {
        return prefix == null || prefix.length() < 3 ? "jflux-" : prefix;
    }

    public static void createSymLink(String target, String link) throws CommonIOException {
        Path targetPath = Paths.get(target);
        Path linkPath = Paths.get(link);
        try {
            if (!java.nio.file.Files.exists(targetPath)) throw new CommonIOException("The symlink target '" + target + "' does not exist.");
            if (java.nio.file.Files.exists(linkPath, LinkOption.NOFOLLOW_LINKS)) throw new CommonIOException("The system path '" + link + "' already exists.");
            Path parent = linkPath.toAbsolutePath().normalize().getParent();
            if (parent != null) java.nio.file.Files.createDirectories(parent);
            java.nio.file.Files.createSymbolicLink(linkPath, targetPath);
        } catch (CommonIOException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new CommonIOException("Unable to create symlink '" + link + "' to '" + target + "'.", e);
        }
    }

    public static void removeSymLink(String link) throws CommonIOException {
        Path linkPath = Paths.get(link);
        try {
            if (!java.nio.file.Files.exists(linkPath, LinkOption.NOFOLLOW_LINKS)) return;
            if (!java.nio.file.Files.isSymbolicLink(linkPath)) throw new CommonIOException("The system path '" + link + "' is not a symlink.");
            java.nio.file.Files.deleteIfExists(linkPath);
        } catch (CommonIOException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new CommonIOException("Unable to remove symlink '" + link + "'.", e);
        }
    }

    public static boolean checkPermissions(String path, int read, int write, int execute) {
        try { return FilePermissionChecker.checkPermissions(path, read, write, execute); }
        catch (IOException e) { ExceptionHandling.report(e); return false; }
    }

    public static boolean checkFileOwnerIsPrivileged(String path) {
        try { return FilePermissionChecker.isPrivilegedOwner(path); }
        catch (IOException e) { ExceptionHandling.report(e); return false; }
    }

    public static boolean setPermissions(String path, int read, int write, int execute, boolean recursive) {
        try {
            if (recursive) FilePermissionsSetter.setPermissionsRecursive(path, read, write, execute);
            else FilePermissionsSetter.setPermissions(path, read, write, execute);
            return true;
        } catch (IOException e) { ExceptionHandling.report(e); return false; }
    }

    public static boolean setOwner(String path, String ownerName, boolean recursive) {
        try {
            if (recursive) FilePermissionsSetter.setOwnerRecursive(path, ownerName);
            else FilePermissionsSetter.setOwner(path, ownerName);
            return true;
        } catch (IOException e) { ExceptionHandling.report(e); return false; }
    }
}
