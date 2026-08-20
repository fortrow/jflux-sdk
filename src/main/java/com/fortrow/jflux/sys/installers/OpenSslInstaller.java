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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-07 14:15:48 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.sys.installers;

import com.fortrow.jflux.exceptions.ExceptionHandling;
import com.fortrow.jflux.shell.Shell;
import com.fortrow.jflux.sys.PackageUtils;
import com.fortrow.jflux.sys.PlatformUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class OpenSslInstaller {

    public InstallResult install() {
        if(openSslInstalled()) return InstallResult.alreadyInstalled("OpenSSL is already installed.");

        PlatformUtils.OpSys os = PlatformUtils.getOsType(true);
        if(os == PlatformUtils.OpSys.MAC_OSX) return execute("macOS OpenSSL install", macCommands());
        if(os == PlatformUtils.OpSys.WINDOWS) return execute("Windows OpenSSL install", windowsCommands());
        if(isLinux(os)) return execute("Linux OpenSSL install", linuxCommands(os));
        return InstallResult.failed("Unsupported operating system: " + PlatformUtils.osInfo());
    }

    public List<String> commands() {
        PlatformUtils.OpSys os = PlatformUtils.getOsType(true);
        if(os == PlatformUtils.OpSys.MAC_OSX) return macCommands();
        if(os == PlatformUtils.OpSys.WINDOWS) return windowsCommands();
        if(isLinux(os)) return linuxCommands(os);
        ArrayList<String> commands = new ArrayList<>();
        commands.add("# Unsupported operating system: " + PlatformUtils.osInfo());
        return commands;
    }

    private List<String> linuxCommands(PlatformUtils.OpSys os) {
        ArrayList<String> commands = new ArrayList<>();
        commands.add("command -v openssl >/dev/null 2>&1 || " + linuxPackageInstallCommand(os));
        commands.add("openssl version");
        return commands;
    }

    private String linuxPackageInstallCommand(PlatformUtils.OpSys os) {
        switch(os) {
            case ALPINE_LINUX:
                return "sudo apk add openssl";
            case ARCH_LINUX:
                return "sudo pacman -Sy --noconfirm openssl";
            case FEDORA_LINUX:
                return "sudo dnf install -y openssl";
            case REDHAT_LINUX:
            case CENTOS_LINUX:
            case ROCKY_LINUX:
            case ALMA_LINUX:
            case ORACLE_LINUX:
            case AMAZON_LINUX:
                return "sudo yum install -y openssl";
            case OPENSUS_LINUX:
                return "sudo zypper install -y openssl";
            default:
                return "sudo sh -c 'if command -v apt-get >/dev/null 2>&1; then apt-get update && apt-get install -y openssl; " +
                        "elif command -v dnf >/dev/null 2>&1; then dnf install -y openssl; " +
                        "elif command -v yum >/dev/null 2>&1; then yum install -y openssl; " +
                        "elif command -v apk >/dev/null 2>&1; then apk add openssl; " +
                        "elif command -v pacman >/dev/null 2>&1; then pacman -Sy --noconfirm openssl; " +
                        "elif command -v zypper >/dev/null 2>&1; then zypper install -y openssl; " +
                        "else echo \"No supported package manager found for OpenSSL install\" >&2; exit 1; fi'";
        }
    }

    private List<String> macCommands() {
        ArrayList<String> commands = new ArrayList<>();
        commands.add("command -v openssl >/dev/null 2>&1 || command -v brew >/dev/null 2>&1");
        commands.add("command -v openssl >/dev/null 2>&1 || brew install openssl");
        commands.add("openssl version");
        return commands;
    }

    private List<String> windowsCommands() {
        ArrayList<String> commands = new ArrayList<>();
        commands.add("where openssl >nul 2>nul || winget --version >nul 2>nul || choco --version >nul 2>nul");
        commands.add("where openssl >nul 2>nul || (winget --version >nul 2>nul && winget install -e --id ShiningLight.OpenSSL --accept-package-agreements --accept-source-agreements) || choco install openssl -y");
        commands.add("openssl version");
        return commands;
    }

    private InstallResult execute(String label, List<String> commands) {
        ArrayList<CommandResult> results = new ArrayList<>();
        for(String command : commands) {
            CommandResult result = run(command);
            results.add(result);
            if(result.status != 0) {
                if(PlatformUtils.isMac() && command.equals("command -v openssl >/dev/null 2>&1 || command -v brew >/dev/null 2>&1")) {
                    return InstallResult.failed("Homebrew is required to install OpenSSL on macOS when openssl is not already installed.", results);
                }
                if(PlatformUtils.isWindows() && command.equals("where openssl >nul 2>nul || winget --version >nul 2>nul || choco --version >nul 2>nul")) {
                    return InstallResult.failed("Winget or Chocolatey is required to install OpenSSL on Windows when openssl is not already installed.", results);
                }
                return InstallResult.failed(label + " failed while running: " + command, results);
            }
        }

        return openSslInstalled()
                ? InstallResult.installed("OpenSSL installed and openssl is available.", results)
                : InstallResult.failed("Install commands completed, but openssl was not found.", results);
    }

    private boolean openSslInstalled() {
        return PackageUtils.binary("openssl").installed();
    }

    private CommandResult run(String command) {
        com.fortrow.jflux.shell.CommandResult result = Shell.execute(command);
        return new CommandResult(command, result.exitCode(), result.stdout(), result.stderr());
    }

    private static boolean isLinux(PlatformUtils.OpSys os) {
        switch(os) {
            case LINUX:
            case ALPINE_LINUX:
            case ARCH_LINUX:
            case DEBIAN_LINUX:
            case FREE_BSD_LINUX:
            case REDHAT_LINUX:
            case MINT_LINUX:
            case UBUNTU_LINUX:
            case ZORIN_LINUX:
            case POP_OS_LINUX:
            case ELEMENTARY_LINUX:
            case FEDORA_LINUX:
            case OPENSUS_LINUX:
            case CENTOS_LINUX:
            case ROCKY_LINUX:
            case ALMA_LINUX:
            case ORACLE_LINUX:
            case AMAZON_LINUX:
            case KALI_LINUX:
            case PARROT_LINUX:
            case MX_LINUX:
            case RASPBIAN_LINUX:
            case MANJARO_LINUX:
            case ENDEAVOUROS_LINUX:
            case GARUDA_LINUX:
            case GENTOO_LINUX:
            case NIXOS_LINUX:
            case VOID_LINUX:
            case SLACKWARE_LINUX:
                return true;
            default:
                return false;
        }
    }

    public static class InstallResult {
        private final boolean success;
        private final boolean alreadyInstalled;
        private final String message;
        private final List<CommandResult> commands;

        private InstallResult(boolean success, boolean alreadyInstalled, String message, List<CommandResult> commands) {
            this.success = success;
            this.alreadyInstalled = alreadyInstalled;
            this.message = message;
            this.commands = List.copyOf(commands == null ? List.of() : commands);
        }

        public static InstallResult installed(String message, List<CommandResult> commands) {
            return new InstallResult(true, false, message, commands);
        }

        public static InstallResult alreadyInstalled(String message) {
            return new InstallResult(true, true, message, new ArrayList<>());
        }

        public static InstallResult failed(String message) {
            return failed(message, new ArrayList<>());
        }

        public static InstallResult failed(String message, List<CommandResult> commands) {
            return new InstallResult(false, false, message, commands);
        }

        public boolean success() {
            return success;
        }

        public boolean alreadyInstalled() {
            return alreadyInstalled;
        }

        public String message() {
            return message;
        }

        public List<CommandResult> commands() {
            return commands;
        }
    }

    public static class CommandResult {
        private final String command;
        private final int status;
        private final String stdout;
        private final String stderr;

        private CommandResult(String command, int status, String stdout, String stderr) {
            this.command = command;
            this.status = status;
            this.stdout = stdout == null ? "" : stdout;
            this.stderr = stderr == null ? "" : stderr;
        }

        public String command() {
            return command;
        }

        public int status() {
            return status;
        }

        public String stdout() {
            return stdout;
        }

        public String stderr() {
            return stderr;
        }
    }
}
