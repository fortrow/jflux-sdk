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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-07 14:15:48 EDT
 */
package com.fortrow.jflux.sys.installers;

import com.fortrow.jflux.exceptions.ExceptionHandling;
import com.fortrow.jflux.shell.Shell;
import com.fortrow.jflux.sys.PlatformUtils;
import com.fortrow.jflux.sys.PackageUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class LetsEncryptInstaller {

    public InstallResult install() {
        PlatformUtils.OpSys os = PlatformUtils.getOsType(true);

        if(os == PlatformUtils.OpSys.WINDOWS) {
            if(poshAcmeInstalled()) return InstallResult.alreadyInstalled("Posh-ACME is already installed.");
            return execute("Windows Posh-ACME install", windowsCommands());
        }

        if(certbotInstalled()) return InstallResult.alreadyInstalled("Certbot is already installed.");
        if(os == PlatformUtils.OpSys.MAC_OSX) return execute("macOS Certbot install", macCommands());
        if(isLinux(os)) return execute("Linux Certbot install", linuxCommands(os));
        return InstallResult.failed("Unsupported operating system: " + PlatformUtils.osInfo());
    }

    public List<String> commands() {
        PlatformUtils.OpSys os = PlatformUtils.getOsType(true);
        if(os == PlatformUtils.OpSys.WINDOWS) return windowsCommands();
        if(os == PlatformUtils.OpSys.MAC_OSX) return macCommands();
        if(isLinux(os)) return linuxCommands(os);
        ArrayList<String> commands = new ArrayList<>();
        commands.add("# Unsupported operating system: " + PlatformUtils.osInfo());
        return commands;
    }

    private List<String> linuxCommands(PlatformUtils.OpSys os) {
        ArrayList<String> commands = new ArrayList<>();
        commands.add("command -v certbot >/dev/null 2>&1 || " + linuxPackageInstallCommand(os));
        commands.add("certbot --version");
        return commands;
    }

    private String linuxPackageInstallCommand(PlatformUtils.OpSys os) {
        switch(os) {
            case UBUNTU_LINUX:
            case DEBIAN_LINUX:
            case MINT_LINUX:
            case ZORIN_LINUX:
            case POP_OS_LINUX:
            case ELEMENTARY_LINUX:
                return "sudo apt-get update && sudo apt-get install -y snapd && sudo snap install core && sudo snap refresh core && sudo apt-get remove -y certbot || true && sudo snap install --classic certbot && sudo ln -sf /snap/bin/certbot /usr/bin/certbot";
            case FEDORA_LINUX:
                return "sudo dnf install -y snapd && sudo systemctl enable --now snapd.socket && sudo ln -sf /var/lib/snapd/snap /snap && sudo snap install core && sudo snap refresh core && sudo dnf remove -y certbot || true && sudo snap install --classic certbot && sudo ln -sf /snap/bin/certbot /usr/bin/certbot";
            case REDHAT_LINUX:
            case CENTOS_LINUX:
            case ROCKY_LINUX:
            case ALMA_LINUX:
            case ORACLE_LINUX:
            case AMAZON_LINUX:
                return "sudo yum install -y snapd && sudo systemctl enable --now snapd.socket && sudo ln -sf /var/lib/snapd/snap /snap && sudo snap install core && sudo snap refresh core && sudo yum remove -y certbot || true && sudo snap install --classic certbot && sudo ln -sf /snap/bin/certbot /usr/bin/certbot";
            case ARCH_LINUX:
                return "sudo pacman -Sy --noconfirm certbot";
            case ALPINE_LINUX:
                return "sudo apk add certbot";
            case OPENSUS_LINUX:
                return "sudo zypper install -y certbot";
            default:
                return "sudo sh -c 'if command -v snap >/dev/null 2>&1; then snap install core && snap refresh core && snap install --classic certbot && ln -sf /snap/bin/certbot /usr/bin/certbot; " +
                        "elif command -v apt-get >/dev/null 2>&1; then apt-get update && apt-get install -y certbot; " +
                        "elif command -v dnf >/dev/null 2>&1; then dnf install -y certbot; " +
                        "elif command -v yum >/dev/null 2>&1; then yum install -y certbot; " +
                        "elif command -v apk >/dev/null 2>&1; then apk add certbot; " +
                        "elif command -v pacman >/dev/null 2>&1; then pacman -Sy --noconfirm certbot; " +
                        "elif command -v zypper >/dev/null 2>&1; then zypper install -y certbot; " +
                        "else echo \"No supported package manager found for Certbot install\" >&2; exit 1; fi'";
        }
    }

    private List<String> macCommands() {
        ArrayList<String> commands = new ArrayList<>();
        commands.add("command -v certbot >/dev/null 2>&1 || command -v brew >/dev/null 2>&1");
        commands.add("command -v certbot >/dev/null 2>&1 || brew install certbot");
        commands.add("certbot --version");
        return commands;
    }

    private List<String> windowsCommands() {
        ArrayList<String> commands = new ArrayList<>();
        commands.add("powershell -NoProfile -ExecutionPolicy Bypass -Command \"if (Get-Module -ListAvailable -Name Posh-ACME) { exit 0 }; if (Get-Command Install-Module -ErrorAction SilentlyContinue) { exit 0 }; exit 1\"");
        commands.add("powershell -NoProfile -ExecutionPolicy Bypass -Command \"if (-not (Get-Module -ListAvailable -Name Posh-ACME)) { Install-Module -Name Posh-ACME -Scope CurrentUser -Force -AllowClobber }\"");
        commands.add("powershell -NoProfile -ExecutionPolicy Bypass -Command \"Import-Module Posh-ACME; Get-Command New-PACertificate | Out-Null\"");
        return commands;
    }

    private InstallResult execute(String label, List<String> commands) {
        ArrayList<CommandResult> results = new ArrayList<>();
        for(String command : commands) {
            CommandResult result = run(command);
            results.add(result);
            if(result.status != 0) {
                if(PlatformUtils.isMac() && command.equals("command -v certbot >/dev/null 2>&1 || command -v brew >/dev/null 2>&1")) {
                    return InstallResult.failed("Homebrew is required to install Certbot on macOS when certbot is not already installed.", results);
                }
                if(PlatformUtils.isWindows() && command.startsWith("powershell -NoProfile -ExecutionPolicy Bypass -Command \"if (Get-Module")) {
                    return InstallResult.failed("PowerShellGet Install-Module is required to install Posh-ACME on Windows when Posh-ACME is not already installed.", results);
                }
                return InstallResult.failed(label + " failed while running: " + command, results);
            }
        }

        if(PlatformUtils.isWindows()) {
            return poshAcmeInstalled()
                    ? InstallResult.installed("Posh-ACME installed and available.", results)
                    : InstallResult.failed("Install commands completed, but Posh-ACME was not found.", results);
        }

        return certbotInstalled()
                ? InstallResult.installed("Certbot installed and available.", results)
                : InstallResult.failed("Install commands completed, but certbot was not found.", results);
    }

    private boolean certbotInstalled() {
        return PackageUtils.binary("certbot").installed();
    }

    private boolean poshAcmeInstalled() {
        return PlatformUtils.isWindows() && PackageUtils.executableAvailable("powershell");
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
