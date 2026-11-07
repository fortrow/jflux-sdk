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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-05 14:36:24 EDT
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NvmeOverTcpVolume implements VolumeImpl {

    private final String namespaceDevice;
    private String mountPoint;
    private final String targetAddress;
    private final int targetPort;
    private final String subsystemNqn;
    private int windowsInitiatorAdapter;
    private String windowsHostGateway;
    private int windowsSubsystemPortId;
    private String windowsSubsystemPort;
    private int windowsDiskNumber;
    private int windowsPartitionNumber;

    public NvmeOverTcpVolume(String targetAddress, String subsystemNqn, String namespaceDevice, String mountPoint) {
        this(targetAddress, 4420, subsystemNqn, namespaceDevice, mountPoint);
    }

    public static NvmeOverTcpVolume createVolumeLinux(String targetAddress, String subsystemNqn, String namespaceDevice, String mountPoint) {
        return new NvmeOverTcpVolume(targetAddress, subsystemNqn, namespaceDevice, mountPoint);
    }

    public static NvmeOverTcpVolume createVolumeLinux(String targetAddress, int targetPort, String subsystemNqn, String namespaceDevice, String mountPoint) {
        return new NvmeOverTcpVolume(targetAddress, targetPort, subsystemNqn, namespaceDevice, mountPoint);
    }

    public static NvmeOverTcpVolume createVolumeWindows(String targetAddress, int targetPort, String subsystemNqn) {
        return new NvmeOverTcpVolume(targetAddress, targetPort, subsystemNqn, null, null);
    }

    public static NvmeOverTcpVolume createVolumeWindows(
            String targetAddress,
            int targetPort,
            String subsystemNqn,
            String namespaceDevice,
            String mountPoint,
            int windowsInitiatorAdapter,
            int windowsHostGateway,
            int windowsSubsystemPort
    ) {
        return new NvmeOverTcpVolume(targetAddress, targetPort, subsystemNqn, namespaceDevice, mountPoint, windowsInitiatorAdapter, windowsHostGateway, windowsSubsystemPort);
    }

    public static NvmeOverTcpVolume createVolumeWindows(
            String targetAddress,
            int targetPort,
            String subsystemNqn,
            String namespaceDevice,
            String mountPoint,
            int windowsInitiatorAdapter,
            int windowsHostGateway,
            int windowsSubsystemPort,
            int windowsDiskNumber,
            int windowsPartitionNumber
    ) {
        return new NvmeOverTcpVolume(
                targetAddress,
                targetPort,
                subsystemNqn,
                namespaceDevice,
                mountPoint,
                windowsInitiatorAdapter,
                windowsHostGateway,
                windowsSubsystemPort,
                windowsDiskNumber,
                windowsPartitionNumber
        );
    }

    public NvmeOverTcpVolume(String targetAddress, int targetPort, String subsystemNqn, String namespaceDevice, String mountPoint) {
        this(targetAddress, targetPort, subsystemNqn, namespaceDevice, mountPoint, -1, -1, -1, -1, -1);
    }

    public NvmeOverTcpVolume(
            String targetAddress,
            int targetPort,
            String subsystemNqn,
            String namespaceDevice,
            String mountPoint,
            int windowsInitiatorAdapter,
            int windowsHostGateway,
            int windowsSubsystemPort
    ) {
        this(targetAddress, targetPort, subsystemNqn, namespaceDevice, mountPoint, windowsInitiatorAdapter, windowsHostGateway, windowsSubsystemPort, -1, -1);
    }

    public NvmeOverTcpVolume(
            String targetAddress,
            int targetPort,
            String subsystemNqn,
            String namespaceDevice,
            String mountPoint,
            int windowsInitiatorAdapter,
            int windowsHostGateway,
            int windowsSubsystemPort,
            int windowsDiskNumber,
            int windowsPartitionNumber
    ) {
        this.namespaceDevice = namespaceDevice;
        this.mountPoint = mountPoint;
        this.targetAddress = targetAddress;
        this.targetPort = targetPort;
        this.subsystemNqn = subsystemNqn;
        this.windowsInitiatorAdapter = windowsInitiatorAdapter;
        this.windowsHostGateway = windowsHostGateway < 0 ? null : String.valueOf(windowsHostGateway);
        this.windowsSubsystemPortId = windowsSubsystemPort;
        this.windowsSubsystemPort = windowsSubsystemPort < 0 ? null : String.valueOf(windowsSubsystemPort);
        this.windowsDiskNumber = windowsDiskNumber;
        this.windowsPartitionNumber = windowsPartitionNumber;
    }

    @Override
    public VolumeStatus mountWindows() throws CommonIOException {
        if (isMountedWindows()) return VolumeStatus.MOUNTED;
        if (!hasNvmeOfUtil()) return VolumeStatus.UNSUPPORTED;

        VolumeStatus configureStatus = configureWindowsTarget();
        if (configureStatus != VolumeStatus.MOUNTED) return configureStatus;

        String output = "";
        if (!isConnectedWindows()) {
            output = Run.Windows.runAndWait(buildWindowsConnectCommand() + " 2>&1");
        }

        if (isConnectedWindows() && !hasWindowsPartitionInfo()) {
            detectWindowsPartition();
        }

        if (isConnectedWindows() && hasWindowsPartitionInfo()) {
            if (isBlank(mountPoint)) mountPoint = getAvailableWindowsDriveLetter();
            if (!isBlank(mountPoint)) Run.Windows.runAndWait(buildWindowsMountPartitionCommand() + " 2>&1");
        }

        if (isMountedWindows()) return VolumeStatus.MOUNTED;
        if (isBindFailure(output)) return VolumeStatus.BIND_FAILURE;
        if (isAuthFailure(output)) return VolumeStatus.AUTH_FAILURE;
        if (isNoExists(output)) return VolumeStatus.NO_EXISTS;
        return VolumeStatus.UNMOUNTED;
    }

    @Override
    public VolumeStatus mountMac() throws CommonIOException {
        return VolumeStatus.UNSUPPORTED;
    }

    @Override
    public VolumeStatus mountLinux() throws CommonIOException {
        if (isMountedLinux()) return VolumeStatus.MOUNTED;
        if (!hasNvmeCli()) return VolumeStatus.UNSUPPORTED;

        if (hasTargetInfo() && !isConnectedLinux()) {
            String output = Run.Linux.runAndWait(buildConnectCommand() + " 2>&1");
            if (!isConnectedLinux()) {
                if (isBindFailure(output)) return VolumeStatus.BIND_FAILURE;
                if (isAuthFailure(output)) return VolumeStatus.AUTH_FAILURE;
                if (isNoExists(output)) return VolumeStatus.NO_EXISTS;
                return VolumeStatus.UNMOUNTED;
            }
        }

        if (isBlank(namespaceDevice) || !Files.exists(Paths.get(namespaceDevice))) return VolumeStatus.NO_EXISTS;

        ensureMountPoint();
        String output = Run.Linux.runAndWait("mount " + unixQuote(namespaceDevice) + " " + unixQuote(mountPoint) + " 2>&1");
        if (isMountedLinux()) return VolumeStatus.MOUNTED;
        if (isNoExists(output)) return VolumeStatus.NO_EXISTS;
        return VolumeStatus.UNMOUNTED;
    }

    @Override
    public VolumeStatus umountWindows() throws CommonIOException {
        if (!isMountedWindows()) {
            disconnectWindows();
            return VolumeStatus.UNMOUNTED;
        }

        if (hasWindowsPartitionInfo()) {
            Run.Windows.runAndWait(buildWindowsUnmountPartitionCommand() + " 2>&1");
        }

        disconnectWindows();
        return isMountedWindows() ? VolumeStatus.MOUNTED : VolumeStatus.UNMOUNTED;
    }

    @Override
    public VolumeStatus umountMac() throws CommonIOException {
        return VolumeStatus.UNSUPPORTED;
    }

    @Override
    public VolumeStatus umountLinux() throws CommonIOException {
        if (!isMountedLinux()) {
            disconnectLinux();
            return VolumeStatus.UNMOUNTED;
        }

        int status = Run.Linux.waitForStatus("umount " + unixQuote(mountPoint));
        if (status == 0 || !isMountedLinux()) {
            disconnectLinux();
            return VolumeStatus.UNMOUNTED;
        }
        return VolumeStatus.MOUNTED;
    }

    public String getMountPoint() {
        return mountPoint;
    }

    private boolean hasNvmeCli() {
        return Run.Linux.waitForStatus("command -v nvme") == 0;
    }

    private boolean hasNvmeOfUtil() {
        return Run.Windows.waitForStatus("where nvmeofutil.exe") == 0;
    }

    private VolumeStatus configureWindowsTarget() throws CommonIOException {
        if (!hasTargetInfo()) return VolumeStatus.UNSUPPORTED;

        if (windowsInitiatorAdapter < 0) {
            windowsInitiatorAdapter = detectWindowsInitiatorAdapter();
        }
        if (windowsInitiatorAdapter < 0) return VolumeStatus.UNSUPPORTED;

        if (isBlank(windowsHostGateway)) {
            windowsHostGateway = detectWindowsHostGateway();
        }
        if (isBlank(windowsHostGateway)) return VolumeStatus.UNSUPPORTED;

        if (windowsSubsystemPortId < 0) {
            windowsSubsystemPortId = 1;
        }

        if (isBlank(windowsSubsystemPort)) {
            windowsSubsystemPort = detectWindowsSubsystemPort();
        }

        if (isBlank(windowsSubsystemPort)) {
            String output = Run.Windows.runAndWait(buildWindowsAddSubsystemPortCommand() + " 2>&1");
            if (isBindFailure(output)) return VolumeStatus.BIND_FAILURE;
            if (isAuthFailure(output)) return VolumeStatus.AUTH_FAILURE;
            if (isNoExists(output)) return VolumeStatus.NO_EXISTS;

            windowsSubsystemPort = detectWindowsSubsystemPort();
        }

        return isBlank(windowsSubsystemPort) ? VolumeStatus.UNMOUNTED : VolumeStatus.MOUNTED;
    }

    private int detectWindowsInitiatorAdapter() {
        String output = Run.Windows.runAndWait("nvmeofutil.exe list -t ia 2>&1");
        return parseFirstDecimal(output);
    }

    private String detectWindowsHostGateway() {
        String output = Run.Windows.runAndWait("nvmeofutil.exe list -t hg -ia " + windowsInitiatorAdapter + " 2>&1");
        return parseFirstHexOrDecimal(output);
    }

    private String detectWindowsSubsystemPort() {
        String output = Run.Windows.runAndWait("nvmeofutil.exe list -t sp -ia " + windowsInitiatorAdapter + " 2>&1");
        if (isBlank(output) || !output.contains(subsystemNqn)) return null;

        String bestLine = findLineContaining(output, subsystemNqn);
        if (isBlank(bestLine)) bestLine = findLineContaining(output, targetAddress);
        if (isBlank(bestLine)) bestLine = output;

        return parseFirstHexOrDecimal(bestLine);
    }

    private boolean isMountedWindows() throws CommonIOException {
        if (isBlank(mountPoint)) return isConnectedWindows();
        return Run.Windows.waitForStatus("powershell.exe -NoProfile -Command \"Test-Path " + powerShellQuote(mountPoint) + "\"") == 0 && isConnectedWindows();
    }

    private boolean isMountedLinux() throws CommonIOException {
        return Run.Linux.waitForStatus("findmnt --mountpoint --noheadings --raw " + unixQuote(mountPoint)) == 0;
    }

    private boolean isConnectedLinux() {
        if (isBlank(subsystemNqn)) return !isBlank(namespaceDevice) && Files.exists(Paths.get(namespaceDevice));

        String output = Run.Linux.runAndWait("nvme list-subsys 2>&1");
        return output != null && output.contains(subsystemNqn);
    }

    private boolean isConnectedWindows() {
        if (isBlank(subsystemNqn) || !hasNvmeOfUtil()) return false;

        String command = windowsInitiatorAdapter >= 0
                ? "nvmeofutil.exe list -t sp -ia " + windowsInitiatorAdapter + " 2>&1"
                : "nvmeofutil.exe list -t sp 2>&1";
        String output = Run.Windows.runAndWait(command);
        return output != null && output.contains(subsystemNqn);
    }

    private void disconnectLinux() {
        if (!hasTargetInfo() || !isConnectedLinux()) return;
        Run.Linux.waitForStatus("nvme disconnect -n " + unixQuote(subsystemNqn));
    }

    private void disconnectWindows() {
        if (!hasWindowsTargetInfo() || !hasNvmeOfUtil()) return;
        Run.Windows.waitForStatus("nvmeofutil.exe disconnect -ia " + windowsInitiatorAdapter + " -sp " + windowsSubsystemPort);
    }

    private String buildConnectCommand() {
        return "nvme connect -t tcp -a " + unixQuote(targetAddress) + " -s " + targetPort + " -n " + unixQuote(subsystemNqn);
    }

    private String buildWindowsAddSubsystemPortCommand() {
        return "nvmeofutil.exe add -t sp -ia " + windowsInitiatorAdapter
                + " -hg " + windowsHostGateway
                + " -dy true"
                + " -pi " + windowsSubsystemPortId
                + " -nq " + windowsQuote(subsystemNqn)
                + " -ta " + windowsQuote(targetAddress)
                + " -ts " + targetPort;
    }

    private String buildWindowsConnectCommand() {
        return "nvmeofutil.exe connect -ia " + windowsInitiatorAdapter + " -sp " + windowsSubsystemPort;
    }

    private String buildWindowsMountPartitionCommand() {
        if (isWindowsDriveLetterMount()) {
            return "powershell.exe -NoProfile -Command \"Set-Partition -DiskNumber " + windowsDiskNumber
                    + " -PartitionNumber " + windowsPartitionNumber
                    + " -NewDriveLetter " + windowsDriveLetter()
                    + " -ErrorAction SilentlyContinue\"";
        }

        return "powershell.exe -NoProfile -Command \"Add-PartitionAccessPath -DiskNumber " + windowsDiskNumber
                + " -PartitionNumber " + windowsPartitionNumber
                + " -AccessPath " + powerShellQuote(mountPoint)
                + " -ErrorAction SilentlyContinue\"";
    }

    private String buildWindowsUnmountPartitionCommand() {
        return "powershell.exe -NoProfile -Command \"Remove-PartitionAccessPath -DiskNumber " + windowsDiskNumber
                + " -PartitionNumber " + windowsPartitionNumber
                + " -AccessPath " + powerShellQuote(mountPoint)
                + " -ErrorAction SilentlyContinue\"";
    }

    private boolean hasTargetInfo() {
        return !isBlank(targetAddress) && !isBlank(subsystemNqn);
    }

    private boolean hasWindowsTargetInfo() {
        return hasTargetInfo() && windowsInitiatorAdapter >= 0 && !isBlank(windowsHostGateway) && !isBlank(windowsSubsystemPort);
    }

    private boolean hasWindowsPartitionInfo() {
        return windowsDiskNumber >= 0 && windowsPartitionNumber >= 0;
    }

    private void detectWindowsPartition() {
        String command = "powershell.exe -NoProfile -Command \"$d = Get-Disk | Where-Object BusType -eq 'NVMe' | Sort-Object Number -Descending | Select-Object -First 1; if ($d) { $p = Get-Partition -DiskNumber $d.Number | Sort-Object PartitionNumber | Select-Object -First 1; if ($p) { Write-Output ($d.Number.ToString() + ',' + $p.PartitionNumber.ToString()) } }\"";
        String output = Run.Windows.runAndWait(command);
        if (isBlank(output) || !output.contains(",")) return;

        String[] parts = output.trim().split(",");
        if (parts.length < 2) return;

        try {
            windowsDiskNumber = Integer.parseInt(parts[0].trim());
            windowsPartitionNumber = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException ignored) { }
    }

    private boolean isWindowsSubsystemPortExists(String output) {
        if (output == null) return false;

        String lower = output.toLowerCase();
        return lower.contains("already exists")
                || lower.contains("already configured")
                || lower.contains("duplicate");
    }

    private void ensureMountPoint() throws CommonIOException {
        Path path = Paths.get(mountPoint);
        try {
            if (!Files.exists(path)) Files.createDirectories(path);
        } catch (IOException e) {
            throw new CommonIOException("Unable to create mount point '" + mountPoint + "': " + e.getMessage());
        }
    }

    private boolean isNoExists(String output) {
        if (output == null) return false;

        String lower = output.toLowerCase();
        return lower.contains("no such file")
                || lower.contains("does not exist")
                || lower.contains("not found")
                || lower.contains("invalid subnqn")
                || lower.contains("failed to write to /dev/nvme-fabrics");
    }

    private boolean isAuthFailure(String output) {
        if (output == null) return false;

        String lower = output.toLowerCase();
        return lower.contains("authentication")
                || lower.contains("permission denied")
                || lower.contains("access denied")
                || lower.contains("dhchap")
                || lower.contains("secret");
    }

    private boolean isBindFailure(String output) {
        if (output == null) return false;

        String lower = output.toLowerCase();
        return lower.contains("could not resolve")
                || lower.contains("no such host")
                || lower.contains("connection refused")
                || lower.contains("connection timed out")
                || lower.contains("operation timed out")
                || lower.contains("host is down")
                || lower.contains("network is unreachable")
                || lower.contains("no route to host")
                || lower.contains("failed to connect")
                || lower.contains("connect failed")
                || lower.contains("transport endpoint is not connected");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private int parseFirstDecimal(String output) {
        if (isBlank(output)) return -1;

        Matcher matcher = Pattern.compile("\\b\\d+\\b").matcher(output);
        if (!matcher.find()) return -1;

        try {
            return Integer.parseInt(matcher.group());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String parseFirstHexOrDecimal(String output) {
        if (isBlank(output)) return null;

        Matcher matcher = Pattern.compile("\\b[0-9A-Fa-f]{4,}\\b|\\b\\d+\\b").matcher(output);
        if (!matcher.find()) return null;
        return matcher.group();
    }

    private String findLineContaining(String output, String needle) {
        if (isBlank(output) || isBlank(needle)) return null;

        String[] lines = output.split("\\R");
        for (String line : lines) {
            if (line.contains(needle)) return line;
        }
        return null;
    }

    private String unixQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private String windowsQuote(String value) {
        return "\"" + value.replace("\"", "\\\"") + "\"";
    }

    private String powerShellQuote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private boolean isWindowsDriveLetterMount() {
        return mountPoint != null && mountPoint.matches("^[A-Za-z]:\\\\?$");
    }

    private String windowsDriveLetter() {
        return mountPoint.substring(0, 1).toUpperCase();
    }

    private String getAvailableWindowsDriveLetter() {
        for (char drive = 'D'; drive <= 'Z'; drive++) {
            String candidate = drive + ":\\";
            int status = Run.Windows.waitForStatus("powershell.exe -NoProfile -Command \"if (Test-Path " + powerShellQuote(candidate) + ") { exit 1 } else { exit 0 }\"");
            if (status == 0) return candidate;
        }
        return null;
    }

}
