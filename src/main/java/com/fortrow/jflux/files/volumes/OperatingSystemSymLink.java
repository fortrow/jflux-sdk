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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-05 12:08:20 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.files.volumes;

import com.fortrow.jflux.exceptions.types.CommonIOException;
import com.fortrow.jflux.files.Files;
import com.fortrow.jflux.files.volumes.supporting.VolumeImpl;
import com.fortrow.jflux.files.volumes.supporting.VolumeStatus;

import java.io.IOException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;

public class OperatingSystemSymLink implements VolumeImpl {

    private final String diskPath;
    private final String mountPoint;

    public OperatingSystemSymLink(String diskPath, String mountPoint) {
        this.diskPath = diskPath;
        this.mountPoint = mountPoint;
    }

    @Override
    public VolumeStatus mountWindows() throws CommonIOException {
        return mountSymLink();
    }

    @Override
    public VolumeStatus mountMac() throws CommonIOException {
        return mountSymLink();
    }

    @Override
    public VolumeStatus mountLinux() throws CommonIOException {
        return mountSymLink();
    }

    @Override
    public VolumeStatus umountWindows() throws CommonIOException {
        return umountSymLink();
    }

    @Override
    public VolumeStatus umountMac() throws CommonIOException {
        return umountSymLink();
    }

    @Override
    public VolumeStatus umountLinux() throws CommonIOException {
        return umountSymLink();
    }

    private VolumeStatus mountSymLink() throws CommonIOException {
        Path targetPath = Paths.get(diskPath);
        Path linkPath = Paths.get(mountPoint);

        if (!java.nio.file.Files.exists(targetPath)) return VolumeStatus.NO_EXISTS;
        if (java.nio.file.Files.isSymbolicLink(linkPath)) return VolumeStatus.MOUNTED;
        if (java.nio.file.Files.exists(linkPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new CommonIOException("The mount point '" + mountPoint + "' already exists and is not a symlink.");
        }

        ensureLinkParent(linkPath);
        Files.createSymLink(diskPath, mountPoint);
        return java.nio.file.Files.isSymbolicLink(linkPath) ? VolumeStatus.MOUNTED : VolumeStatus.UNMOUNTED;
    }

    private VolumeStatus umountSymLink() throws CommonIOException {
        Path linkPath = Paths.get(mountPoint);

        if (!java.nio.file.Files.exists(linkPath, LinkOption.NOFOLLOW_LINKS)) return VolumeStatus.UNMOUNTED;
        if (!java.nio.file.Files.isSymbolicLink(linkPath)) return VolumeStatus.UNMOUNTED;

        Files.removeSymLink(mountPoint);
        return java.nio.file.Files.exists(linkPath, LinkOption.NOFOLLOW_LINKS) ? VolumeStatus.MOUNTED : VolumeStatus.UNMOUNTED;
    }

    private void ensureLinkParent(Path linkPath) throws CommonIOException {
        Path parent = linkPath.toAbsolutePath().normalize().getParent();
        if (parent == null || java.nio.file.Files.exists(parent)) return;

        try {
            java.nio.file.Files.createDirectories(parent);
        } catch (IOException e) {
            throw new CommonIOException("Unable to create mount point parent '" + parent + "': " + e.getMessage());
        }
    }

}
