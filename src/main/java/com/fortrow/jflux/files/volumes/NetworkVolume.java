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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-05 12:12:59 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.files.volumes;

import com.fortrow.jflux.exceptions.types.CommonIOException;
import com.fortrow.jflux.files.volumes.supporting.VolumeImpl;
import com.fortrow.jflux.files.volumes.supporting.VolumeStatus;
import com.fortrow.jflux.shell.Run;
import com.fortrow.jflux.sys.PlatformUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class NetworkVolume implements VolumeImpl {

    private final String ipAddress;
    private final String shareName;
    private final String mountPoint;
    private final String username;
    private final String password;

    public NetworkVolume(String ipAddress, String shareName, String username, String password) {
        this(ipAddress, shareName, defaultMountPoint(shareName), username, password);
    }

    public NetworkVolume(String ipAddress, String shareName, String mountPoint, String username, String password) {
        this.ipAddress = ipAddress;
        this.shareName = shareName;
        this.mountPoint = mountPoint;
        this.username = username;
        this.password = password;
    }

    @Override
    public VolumeStatus mountWindows() throws CommonIOException {
        if (isMountedWindows()) return VolumeStatus.MOUNTED;

        String output = Run.Windows.runAndWait(buildWindowsMountCommand() + " 2>&1");
        if (isMountedWindows()) return VolumeStatus.MOUNTED;
        if (isBindFailure(output)) return VolumeStatus.BIND_FAILURE;
        return isAuthFailure(output) ? VolumeStatus.AUTH_FAILURE : VolumeStatus.UNMOUNTED;
    }

    @Override
    public VolumeStatus mountMac() throws CommonIOException {
        if (isMountedMac()) return VolumeStatus.MOUNTED;

        ensureMountPoint();
        String output = Run.Mac.runAndWait("mount_smbfs " + unixQuote(getMacSmbUrl()) + " " + unixQuote(mountPoint) + " 2>&1");
        if (isMountedMac()) return VolumeStatus.MOUNTED;
        if (isBindFailure(output)) return VolumeStatus.BIND_FAILURE;
        return isAuthFailure(output) ? VolumeStatus.AUTH_FAILURE : VolumeStatus.UNMOUNTED;
    }

    @Override
    public VolumeStatus mountLinux() throws CommonIOException {
        if (isMountedLinux()) return VolumeStatus.MOUNTED;

        ensureMountPoint();
        String output = Run.Linux.runAndWait("mount -t cifs " + unixQuote(getUnixSmbUrl()) + " " + unixQuote(mountPoint) + " -o " + unixQuote(getLinuxOptions()) + " 2>&1");
        if (isMountedLinux()) return VolumeStatus.MOUNTED;
        if (isBindFailure(output)) return VolumeStatus.BIND_FAILURE;
        return isAuthFailure(output) ? VolumeStatus.AUTH_FAILURE : VolumeStatus.UNMOUNTED;
    }

    @Override
    public VolumeStatus umountWindows() throws CommonIOException {
        if (!isMountedWindows()) return VolumeStatus.UNMOUNTED;

        int status = Run.Windows.waitForStatus("net use " + windowsQuote(getWindowsMountTarget()) + " /delete /y");
        return status == 0 || !isMountedWindows() ? VolumeStatus.UNMOUNTED : VolumeStatus.MOUNTED;
    }

    @Override
    public VolumeStatus umountMac() throws CommonIOException {
        if (!isMountedMac()) return VolumeStatus.UNMOUNTED;

        int status = Run.Mac.waitForStatus("umount " + unixQuote(mountPoint));
        return status == 0 || !isMountedMac() ? VolumeStatus.UNMOUNTED : VolumeStatus.MOUNTED;
    }

    @Override
    public VolumeStatus umountLinux() throws CommonIOException {
        if (!isMountedLinux()) return VolumeStatus.UNMOUNTED;

        int status = Run.Linux.waitForStatus("umount " + unixQuote(mountPoint));
        return status == 0 || !isMountedLinux() ? VolumeStatus.UNMOUNTED : VolumeStatus.MOUNTED;
    }

    private boolean isMountedWindows() throws CommonIOException {
        return Run.Windows.waitForStatus("net use " + windowsQuote(getWindowsMountTarget())) == 0;
    }

    private boolean isMountedMac() throws CommonIOException {
        return Run.Mac.waitForStatus("mount | grep -F " + unixQuote(" on " + mountPoint + " ")) == 0;
    }

    private boolean isMountedLinux() throws CommonIOException {
        return Run.Linux.waitForStatus("findmnt --mountpoint --noheadings --raw " + unixQuote(mountPoint)) == 0;
    }

    private String buildWindowsMountCommand() {
        StringBuilder command = new StringBuilder("net use ");
        command.append(windowsQuote(mountPoint)).append(" ");
        command.append(windowsQuote(getWindowsUncPath())).append(" ");
        if (!isBlank(password)) command.append(windowsQuote(password)).append(" ");
        if (!isBlank(username)) command.append("/user:").append(windowsQuote(username)).append(" ");
        command.append("/persistent:no");
        return command.toString();
    }

    private String getWindowsMountTarget() {
        if (isBlank(mountPoint) || mountPoint.equals("*")) return getWindowsUncPath();
        return mountPoint;
    }

    private String getWindowsUncPath() {
        return "\\\\" + ipAddress + "\\" + shareName;
    }

    private String getUnixSmbUrl() {
        return "//" + ipAddress + "/" + shareName;
    }

    private String getMacSmbUrl() {
        if (isBlank(username)) return getUnixSmbUrl();
        if (isBlank(password)) return "//" + username + "@" + ipAddress + "/" + shareName;
        return "//" + username + ":" + password + "@" + ipAddress + "/" + shareName;
    }

    private String getLinuxOptions() {
        if (isBlank(username)) return "guest";
        String options = "username=" + username;
        if (!isBlank(password)) options += ",password=" + password;
        return options;
    }

    private boolean isAuthFailure(String output) {
        if (output == null) return false;

        String lower = output.toLowerCase();
        return lower.contains("access is denied")
                || lower.contains("access denied")
                || lower.contains("permission denied")
                || lower.contains("authentication")
                || lower.contains("auth failed")
                || lower.contains("login failure")
                || lower.contains("logon failure")
                || lower.contains("status_logon_failure")
                || lower.contains("status_access_denied")
                || lower.contains("invalid user")
                || lower.contains("bad password")
                || lower.contains("wrong password")
                || lower.contains("credential");
    }

    private boolean isBindFailure(String output) {
        if (output == null) return false;

        String lower = output.toLowerCase();
        return lower.contains("could not resolve")
                || lower.contains("name or service not known")
                || lower.contains("no such host")
                || lower.contains("host is down")
                || lower.contains("network is unreachable")
                || lower.contains("no route to host")
                || lower.contains("connection refused")
                || lower.contains("connection timed out")
                || lower.contains("operation timed out")
                || lower.contains("unable to find")
                || lower.contains("cannot find")
                || lower.contains("system error 53")
                || lower.contains("system error 67")
                || lower.contains("bad network name")
                || lower.contains("bad network path");
    }

    private void ensureMountPoint() throws CommonIOException {
        Path path = Paths.get(mountPoint);
        try {
            if (!Files.exists(path)) Files.createDirectories(path);
        } catch (IOException e) {
            throw new CommonIOException("Unable to create mount point '" + mountPoint + "': " + e.getMessage());
        }
    }

    private static String defaultMountPoint(String shareName) {
        if (PlatformUtils.isWindows()) return "*";
        if (PlatformUtils.isMac()) return "/Volumes/" + shareName;
        return "/mnt/" + shareName;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String unixQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private String windowsQuote(String value) {
        return "\"" + value.replace("\"", "\\\"") + "\"";
    }

}
