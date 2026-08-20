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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-05 13:58:02 EDT
 * 
 * -------------------------------------------------------------------------

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

public class HdfsVolume implements VolumeImpl {

    private final String nameNodeHost;
    private final int nameNodePort;
    private final String hdfsPath;
    private final String mountPoint;
    private final String username;

    public HdfsVolume(String nameNodeHost, String hdfsPath, String mountPoint) {
        this(nameNodeHost, 8020, hdfsPath, mountPoint, null);
    }

    public HdfsVolume(String nameNodeHost, int nameNodePort, String hdfsPath, String mountPoint) {
        this(nameNodeHost, nameNodePort, hdfsPath, mountPoint, null);
    }

    public HdfsVolume(String nameNodeHost, int nameNodePort, String hdfsPath, String mountPoint, String username) {
        this.nameNodeHost = nameNodeHost;
        this.nameNodePort = nameNodePort;
        this.hdfsPath = normalizeHdfsPath(hdfsPath);
        this.mountPoint = mountPoint;
        this.username = username;
    }


    @Override
    public VolumeStatus mountWindows() throws CommonIOException {
        if (isMountedWindows()) return VolumeStatus.MOUNTED;
        if (!hasWindowsNfsClient()) return VolumeStatus.UNSUPPORTED;

        String output = Run.Windows.runAndWait(buildWindowsMountCommand() + " 2>&1");
        if (isMountedWindows()) return VolumeStatus.MOUNTED;
        if (isBindFailure(output)) return VolumeStatus.BIND_FAILURE;
        if (isAuthFailure(output)) return VolumeStatus.AUTH_FAILURE;
        if (isNoExists(output)) return VolumeStatus.NO_EXISTS;
        return VolumeStatus.UNMOUNTED;
    }

    @Override
    public VolumeStatus mountMac() throws CommonIOException {
        return mountFuse(false);
    }

    @Override
    public VolumeStatus mountLinux() throws CommonIOException {
        return mountFuse(true);
    }

    @Override
    public VolumeStatus umountWindows() throws CommonIOException {
        if (!isMountedWindows()) return VolumeStatus.UNMOUNTED;

        int status = Run.Windows.waitForStatus("umount " + windowsQuote(mountPoint));
        return status == 0 || !isMountedWindows() ? VolumeStatus.UNMOUNTED : VolumeStatus.MOUNTED;
    }

    @Override
    public VolumeStatus umountMac() throws CommonIOException {
        return umountFuse(false);
    }

    @Override
    public VolumeStatus umountLinux() throws CommonIOException {
        return umountFuse(true);
    }

    private VolumeStatus mountFuse(boolean linux) throws CommonIOException {
        if (isMounted(linux)) return VolumeStatus.MOUNTED;
        if (!hasFuseMountCommand(linux)) return VolumeStatus.UNSUPPORTED;

        VolumeStatus hdfsStatus = checkHdfsPath(linux);
        if (hdfsStatus != VolumeStatus.MOUNTED) return hdfsStatus;

        ensureMountPoint();
        String output = run(linux, getFuseMountCommand(linux) + " " + unixQuote(getNameNodeUri()) + " " + unixQuote(mountPoint) + " 2>&1");
        if (isMounted(linux)) return VolumeStatus.MOUNTED;
        if (isBindFailure(output)) return VolumeStatus.BIND_FAILURE;
        if (isAuthFailure(output)) return VolumeStatus.AUTH_FAILURE;
        if (isNoExists(output)) return VolumeStatus.NO_EXISTS;
        return VolumeStatus.UNMOUNTED;
    }

    private VolumeStatus umountFuse(boolean linux) throws CommonIOException {
        if (!isMounted(linux)) return VolumeStatus.UNMOUNTED;

        int status = status(linux, "umount " + unixQuote(mountPoint));
        return status == 0 || !isMounted(linux) ? VolumeStatus.UNMOUNTED : VolumeStatus.MOUNTED;
    }

    private VolumeStatus checkHdfsPath(boolean linux) {
        if (!hasHdfsCommand(linux)) return VolumeStatus.MOUNTED;

        String userEnv = isBlank(username) ? "" : "HADOOP_USER_NAME=" + unixQuote(username) + " ";
        String output = run(linux, userEnv + "hdfs dfs -test -e " + unixQuote(getHdfsUri()) + " 2>&1");
        if (isBlank(output)) return VolumeStatus.MOUNTED;
        if (isBindFailure(output)) return VolumeStatus.BIND_FAILURE;
        if (isAuthFailure(output)) return VolumeStatus.AUTH_FAILURE;
        if (isNoExists(output)) return VolumeStatus.NO_EXISTS;
        return VolumeStatus.NO_EXISTS;
    }

    private boolean isMounted(boolean linux) throws CommonIOException {
        if (linux) return Run.Linux.waitForStatus("findmnt --mountpoint --noheadings --raw " + unixQuote(mountPoint)) == 0;
        return Run.Mac.waitForStatus("mount | grep -F " + unixQuote(" on " + mountPoint + " ")) == 0;
    }

    private boolean isMountedWindows() throws CommonIOException {
        return Run.Windows.waitForStatus("mount | findstr /I " + windowsQuote(mountPoint)) == 0;
    }

    private boolean hasWindowsNfsClient() {
        return Run.Windows.waitForStatus("where mount") == 0 && Run.Windows.waitForStatus("where umount") == 0;
    }

    private String buildWindowsMountCommand() {
        String command = "mount -o mtype=hard,nolock";
        if (!isBlank(username)) command += " -u:" + windowsQuote(username);
        command += " " + windowsQuote(getWindowsNfsTarget()) + " " + windowsQuote(mountPoint);
        return command;
    }

    private String getWindowsNfsTarget() {
        return nameNodeHost + ":" + hdfsPath;
    }

    private boolean hasFuseMountCommand(boolean linux) {
        return status(linux, "command -v fuse_dfs") == 0 || status(linux, "command -v hadoop-fuse-dfs") == 0;
    }

    private String getFuseMountCommand(boolean linux) {
        if (status(linux, "command -v fuse_dfs") == 0) return "fuse_dfs";
        return "hadoop-fuse-dfs";
    }

    private boolean hasHdfsCommand(boolean linux) {
        return status(linux, "command -v hdfs") == 0;
    }

    private String getNameNodeUri() {
        return "dfs://" + nameNodeHost + ":" + nameNodePort;
    }

    private String getHdfsUri() {
        return "hdfs://" + nameNodeHost + ":" + nameNodePort + hdfsPath;
    }

    private void ensureMountPoint() throws CommonIOException {
        Path path = Paths.get(mountPoint);
        try {
            if (!Files.exists(path)) Files.createDirectories(path);
        } catch (IOException e) {
            throw new CommonIOException("Unable to create mount point '" + mountPoint + "': " + e.getMessage());
        }
    }

    private String run(boolean linux, String command) {
        if (linux) return Run.Linux.runAndWait(command);
        return Run.Mac.runAndWait(command);
    }

    private int status(boolean linux, String command) {
        if (linux) return Run.Linux.waitForStatus(command);
        return Run.Mac.waitForStatus(command);
    }

    private static String normalizeHdfsPath(String value) {
        if (value == null || value.trim().isEmpty()) return "/";
        return value.startsWith("/") ? value : "/" + value;
    }

    private boolean isNoExists(String output) {
        if (output == null) return false;

        String lower = output.toLowerCase();
        return lower.contains("no such file")
                || lower.contains("file does not exist")
                || lower.contains("path does not exist")
                || lower.contains("not found");
    }

    private boolean isAuthFailure(String output) {
        if (output == null) return false;

        String lower = output.toLowerCase();
        return lower.contains("accesscontrol")
                || lower.contains("access denied")
                || lower.contains("permission denied")
                || lower.contains("authentication")
                || lower.contains("authorization")
                || lower.contains("kerberos")
                || lower.contains("sasl")
                || lower.contains("token");
    }

    private boolean isBindFailure(String output) {
        if (output == null) return false;

        String lower = output.toLowerCase();
        return lower.contains("could not resolve")
                || lower.contains("unknown host")
                || lower.contains("no such host")
                || lower.contains("connection refused")
                || lower.contains("connection timed out")
                || lower.contains("operation timed out")
                || lower.contains("host is down")
                || lower.contains("network is unreachable")
                || lower.contains("no route to host")
                || lower.contains("failed on local exception")
                || lower.contains("namenode")
                || lower.contains("standbyexception")
                || lower.contains("network path was not found")
                || lower.contains("system error 53")
                || lower.contains("system error 67");
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
