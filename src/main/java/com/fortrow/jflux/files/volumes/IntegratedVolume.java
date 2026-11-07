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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-05 12:03:56 EDT
 */
package com.fortrow.jflux.files.volumes;

import com.fortrow.jflux.exceptions.types.CommonIOException;
import com.fortrow.jflux.files.volumes.supporting.VolumeImpl;
import com.fortrow.jflux.files.volumes.supporting.VolumeStatus;
import com.fortrow.jflux.shell.Run;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class IntegratedVolume implements VolumeImpl {

    private final String device;
    private final String mountPoint;

    public IntegratedVolume(String device, String mountPoint) {
        this.device = device;
        this.mountPoint = mountPoint;
    }

    @Override
    public VolumeStatus mountWindows() throws CommonIOException {
        if (isMountedWindows()) return VolumeStatus.MOUNTED;

        int status = Run.Windows.waitForStatus("mountvol " + windowsQuote(mountPoint) + " " + windowsQuote(device));
        return status == 0 ? VolumeStatus.MOUNTED : VolumeStatus.NO_EXISTS;
    }

    @Override
    public VolumeStatus mountMac() throws CommonIOException {
        if (!exists(device)) return VolumeStatus.NO_EXISTS;
        if (isMountedMac()) return VolumeStatus.MOUNTED;

        ensureMountPoint();
        int status = Run.Mac.waitForStatus("diskutil mount -mountPoint " + unixQuote(mountPoint) + " " + unixQuote(device));
        return status == 0 || isMountedMac() ? VolumeStatus.MOUNTED : VolumeStatus.UNMOUNTED;
    }

    @Override
    public VolumeStatus mountLinux() throws CommonIOException {
        if (!exists(device)) return VolumeStatus.NO_EXISTS;
        if (isMountedLinux()) return VolumeStatus.MOUNTED;

        ensureMountPoint();
        int status = Run.Linux.waitForStatus("mount " + unixQuote(device) + " " + unixQuote(mountPoint));
        return status == 0 || isMountedLinux() ? VolumeStatus.MOUNTED : VolumeStatus.UNMOUNTED;
    }

    @Override
    public VolumeStatus umountWindows() throws CommonIOException {
        if (!isMountedWindows()) return VolumeStatus.UNMOUNTED;

        int status = Run.Windows.waitForStatus("mountvol " + windowsQuote(mountPoint) + " /D");
        return status == 0 || !isMountedWindows() ? VolumeStatus.UNMOUNTED : VolumeStatus.MOUNTED;
    }

    @Override
    public VolumeStatus umountMac() throws CommonIOException {
        if (!isMountedMac()) return VolumeStatus.UNMOUNTED;

        int status = Run.Mac.waitForStatus("diskutil unmount " + unixQuote(mountPoint));
        return status == 0 || !isMountedMac() ? VolumeStatus.UNMOUNTED : VolumeStatus.MOUNTED;
    }

    @Override
    public VolumeStatus umountLinux() throws CommonIOException {
        if (!isMountedLinux()) return VolumeStatus.UNMOUNTED;

        int status = Run.Linux.waitForStatus("umount " + unixQuote(mountPoint));
        return status == 0 || !isMountedLinux() ? VolumeStatus.UNMOUNTED : VolumeStatus.MOUNTED;
    }

    private boolean isMountedLinux() throws CommonIOException {
        return Run.Linux.waitForStatus("findmnt --mountpoint " + unixQuote(mountPoint) + " --noheadings --raw") == 0;
    }

    private boolean isMountedMac() throws CommonIOException {
        return Run.Mac.waitForStatus("diskutil info " + unixQuote(mountPoint)) == 0;
    }

    private boolean isMountedWindows() throws CommonIOException {
        return Run.Windows.waitForStatus("mountvol " + windowsQuote(mountPoint)) == 0;
    }

    private boolean exists(String path) {
        return Files.exists(Paths.get(path));
    }

    private void ensureMountPoint() throws CommonIOException {
        Path path = Paths.get(mountPoint);
        try {
            if (!Files.exists(path)) Files.createDirectories(path);
        } catch (IOException e) {
            throw new CommonIOException("Unable to create mount point '" + mountPoint + "': " + e.getMessage());
        }
    }

    private String unixQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private String windowsQuote(String value) {
        return "\"" + value.replace("\"", "\\\"") + "\"";
    }

}
