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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-10 18:49:03 EDT
 */
package com.fortrow.jflux.sys.installers;

import com.fortrow.jflux.exceptions.ExceptionHandling;
import com.fortrow.jflux.shell.Shell;
import com.fortrow.jflux.exceptions.types.CommonIOException;
import com.fortrow.jflux.files.FileObject;
import com.fortrow.jflux.files.Files;
import com.fortrow.jflux.files.PathObject;
import com.fortrow.jflux.files.PathUtil;
import com.fortrow.jflux.sys.PlatformUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class RedisInstaller {

    public static final int DEFAULT_REDIS_PORT = 6379;
    public static final String DEFAULT_INSTANCE_NAME = "jflux";

    private final int port;
    private final String instanceName;
    private final boolean installRedisPackage;
    private final boolean applyLinuxProductionTuning;

    public RedisInstaller() {
        this(DEFAULT_REDIS_PORT);
    }

    public RedisInstaller(int port) {
        this(port, DEFAULT_INSTANCE_NAME);
    }

    public RedisInstaller(int port, String instanceName) {
        this(port, instanceName, true, true);
    }

    public RedisInstaller(int port, String instanceName, boolean installRedisPackage, boolean applyLinuxProductionTuning) {
        this.port = port;
        this.instanceName = safeName(instanceName);
        this.installRedisPackage = installRedisPackage;
        this.applyLinuxProductionTuning = applyLinuxProductionTuning;
    }

    public InstallResult install() throws CommonIOException {
        if(redisResponds(port)) {
            return InstallResult.alreadyInstalled(port, "Redis is already responding on port " + port + ".");
        }

        PlatformUtils.OpSys os = PlatformUtils.getOsType(true);
        if(os == PlatformUtils.OpSys.MAC_OSX) {
            requireRootForMacSystemInstall();
            return installOnMac();
        }
        if(os == PlatformUtils.OpSys.WINDOWS) return installOnWindows();
        if(isLinux(os)) return installOnLinux(os);
        return InstallResult.failed(port, "Unsupported operating system: " + PlatformUtils.osInfo());
    }

    public InstallResult start() {
        if(redisResponds(port)) {
            return InstallResult.alreadyInstalled(port, "Redis is already responding on port " + port + ".");
        }

        PlatformUtils.OpSys os = PlatformUtils.getOsType(true);
        if(os == PlatformUtils.OpSys.MAC_OSX) {
            requireRootForMacSystemInstall();
            return execute("macOS Redis start", macStartCommands(), "Redis started on port " + port + ".", "Start commands completed, but Redis did not respond on port " + port + ".");
        }
        if(os == PlatformUtils.OpSys.WINDOWS) return execute("Windows Redis start", windowsStartCommands(), "Redis started on port " + port + ".", "Start commands completed, but Redis did not respond on port " + port + ".");
        if(isLinux(os)) return execute("Linux Redis start", linuxStartCommands(), "Redis started on port " + port + ".", "Start commands completed, but Redis did not respond on port " + port + ".");
        return InstallResult.failed(port, "Unsupported operating system: " + PlatformUtils.osInfo());
    }

    public List<String> commands() throws CommonIOException {
        PlatformUtils.OpSys os = PlatformUtils.getOsType(true);
        if(os == PlatformUtils.OpSys.MAC_OSX) return macCommands(macConfigPath());
        if(os == PlatformUtils.OpSys.WINDOWS) return windowsCommands();
        if(isLinux(os)) return linuxCommands(os, linuxConfigPath(), linuxServicePath(), linuxThpServicePath());
        ArrayList<String> commands = new ArrayList<>();
        commands.add("# Unsupported operating system: " + PlatformUtils.osInfo());
        return commands;
    }

    private void requireRootForMacSystemInstall() {
        if(!"root".equals(System.getProperty("user.name"))) {
            throw ExceptionHandling.failure("RedisInstaller on macOS installs Redis as a system LaunchDaemon and must be run as root.", null);
        }
    }

    private InstallResult installOnLinux(PlatformUtils.OpSys os) throws CommonIOException {
        writePrivilegedFile(linuxConfigPath(), redisConfig(linuxDataDirectory(), linuxLogPath(), linuxPidPath()));
        writePrivilegedFile(linuxServicePath(), linuxService());

        if(applyLinuxProductionTuning) {
            writePrivilegedFile(linuxThpServicePath(), linuxTransparentHugePagesService());
        }

        return execute("Linux Redis install", linuxCommands(os, linuxConfigPath(), linuxServicePath(), linuxThpServicePath()));
    }

    private InstallResult installOnMac() throws CommonIOException {
        String configPath = macConfigPath();
        writePrivilegedFile(configPath, redisConfig(macDataDirectory(), macLogPath(), macPidPath()));
        writePrivilegedFile(macLaunchDaemonPath(), macLaunchDaemon(configPath));
        return execute("macOS Redis install", macCommands(configPath));
    }

    private InstallResult installOnWindows() {
        return execute("Windows Redis install", windowsCommands());
    }

    private List<String> linuxCommands(PlatformUtils.OpSys os, String configPath, String servicePath, String thpServicePath) {
        ArrayList<String> commands = new ArrayList<>();

        if(installRedisPackage) commands.addAll(linuxPackageInstallCommands(os));

        commands.add("sudo mkdir -p " + quote(linuxDataDirectory()) + " /var/log/redis");
        commands.add("sudo chown -R redis:redis " + quote(linuxDataDirectory()) + " /var/log/redis");
        commands.add("sudo chmod 750 " + quote(linuxDataDirectory()));

        if(applyLinuxProductionTuning) {
            commands.add("printf '%s\\n' 'vm.overcommit_memory = 1' | sudo tee /etc/sysctl.d/99-" + instanceName + "-redis.conf >/dev/null");
            commands.add("sudo sysctl vm.overcommit_memory=1");
            commands.add("test -w /sys/kernel/mm/transparent_hugepage/enabled && echo never | sudo tee /sys/kernel/mm/transparent_hugepage/enabled >/dev/null || true");
            commands.add("sudo systemctl daemon-reload");
            commands.add("sudo systemctl enable --now " + quote(serviceName("disable-thp")));
        } else {
            commands.add("sudo systemctl daemon-reload");
        }

        commands.add("sudo systemctl enable --now " + quote(serviceName("redis")));
        commands.add(macRedisPingCommand());
        return commands;
    }

    private List<String> linuxStartCommands() {
        ArrayList<String> commands = new ArrayList<>();
        commands.add("sudo systemctl start " + quote(serviceName("redis")));
        commands.add(macRedisPingCommand());
        return commands;
    }

    private List<String> linuxPackageInstallCommands(PlatformUtils.OpSys os) {
        ArrayList<String> commands = new ArrayList<>();
        commands.add("command -v redis-server >/dev/null 2>&1 && command -v redis-cli >/dev/null 2>&1");
        commands.add("if ! command -v redis-server >/dev/null 2>&1 || ! command -v redis-cli >/dev/null 2>&1; then " + linuxPackageInstallCommand(os) + "; sudo systemctl disable --now redis-server >/dev/null 2>&1 || true; sudo systemctl disable --now redis >/dev/null 2>&1 || true; fi");
        commands.add("command -v redis-server >/dev/null 2>&1");
        commands.add("command -v redis-cli >/dev/null 2>&1");
        commands.add("id redis >/dev/null 2>&1");
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
                return "sudo DEBIAN_FRONTEND=noninteractive apt-get update && sudo DEBIAN_FRONTEND=noninteractive apt-get install -y lsb-release curl gpg && " +
                        "sudo rm -f /usr/share/keyrings/redis-archive-keyring.gpg && curl -fsSL https://packages.redis.io/gpg | sudo gpg --batch --yes --dearmor -o /usr/share/keyrings/redis-archive-keyring.gpg && " +
                        "sudo chmod 644 /usr/share/keyrings/redis-archive-keyring.gpg && " +
                        "echo \"deb [signed-by=/usr/share/keyrings/redis-archive-keyring.gpg] https://packages.redis.io/deb $(lsb_release -cs) main\" | sudo tee /etc/apt/sources.list.d/redis.list >/dev/null && " +
                        "sudo DEBIAN_FRONTEND=noninteractive apt-get update && sudo DEBIAN_FRONTEND=noninteractive apt-get install -y redis redis-server redis-tools";
            case FEDORA_LINUX:
                return "sudo dnf install -y redis";
            case REDHAT_LINUX:
            case CENTOS_LINUX:
            case ROCKY_LINUX:
            case ALMA_LINUX:
            case ORACLE_LINUX:
            case AMAZON_LINUX:
                return "sudo yum install -y redis";
            default:
                return "sudo sh -c 'if command -v apt-get >/dev/null 2>&1; then apt-get update && apt-get install -y redis; " +
                        "elif command -v dnf >/dev/null 2>&1; then dnf install -y redis; " +
                        "elif command -v yum >/dev/null 2>&1; then yum install -y redis; " +
                        "else echo \"No supported package manager found for Redis install\" >&2; exit 1; fi'; " +
                        "command -v redis-server >/dev/null 2>&1 && command -v redis-cli >/dev/null 2>&1";
        }
    }

    private List<String> macCommands(String configPath) {
        ArrayList<String> commands = new ArrayList<>();
        if(installRedisPackage) {
            commands.add(macRequireBrewOrRedisCommand());
            commands.add(macInstallRedisPackageCommand());
        }
        commands.add("mkdir -p " + quote(macDataDirectory()) + " " + quote(macLogDirectory()) + " " + quote(parentOf(macPidPath())));
        commands.add("chown -R root:wheel " + quote(macBaseDirectory()) + " " + quote(macLogDirectory()));
        commands.add("chmod -R 755 " + quote(macBaseDirectory()) + " " + quote(macLogDirectory()));
        commands.add("launchctl bootout system " + quote(macLaunchDaemonPath()) + " >/dev/null 2>&1 || true");
        commands.add("launchctl bootstrap system " + quote(macLaunchDaemonPath()));
        commands.add("launchctl enable system/" + macLaunchDaemonLabel());
        commands.add("launchctl kickstart -k system/" + macLaunchDaemonLabel());
        commands.add(macRedisPingCommand());
        return commands;
    }

    private List<String> macStartCommands() {
        ArrayList<String> commands = new ArrayList<>();
        commands.add("launchctl kickstart -k system/" + macLaunchDaemonLabel());
        commands.add(macRedisPingCommand());
        return commands;
    }

    private String macRequireBrewOrRedisCommand() {
        return macRedisBinaryTest("redis-server") + " || " + macBrewBinaryTest();
    }

    private String macInstallRedisPackageCommand() {
        return "if ! " + macRedisBinaryTest("redis-server") + "; then BREW=" + macBrewCommand() + "; "
                + "if [ -n \"$SUDO_USER\" ] && [ \"$SUDO_USER\" != \"root\" ]; then sudo -u \"$SUDO_USER\" \"$BREW\" install redis; else \"$BREW\" install redis; fi; fi";
    }

    private String macRedisServerCommand() {
        return macRedisBinaryCommand("REDIS_SERVER", "redis-server", true);
    }

    private String macRedisCliCommand() {
        return macRedisBinaryCommand("REDIS_CLI", "redis-cli", false);
    }

    private String macRedisPingCommand() {
        return "for i in 1 2 3 4 5 6 7 8 9 10; do " + macRedisCliCommand() + " -p " + port + " ping && exit 0; sleep 1; done; exit 1";
    }

    private String macRedisBinaryCommand(String variable, String binary, boolean exec) {
        return variable + "=$(if [ -x /opt/homebrew/bin/" + binary + " ]; then echo /opt/homebrew/bin/" + binary
                + "; elif [ -x /usr/local/bin/" + binary + " ]; then echo /usr/local/bin/" + binary
                + "; else command -v " + binary + "; fi); " + (exec ? "exec " : "") + "\"$" + variable + "\"";
    }

    private String macRedisBinaryTest(String binary) {
        return "test -x /opt/homebrew/bin/" + binary + " || test -x /usr/local/bin/" + binary + " || command -v " + binary + " >/dev/null 2>&1";
    }

    private String macBrewCommand() {
        return "$(if [ -x /opt/homebrew/bin/brew ]; then echo /opt/homebrew/bin/brew; elif [ -x /usr/local/bin/brew ]; then echo /usr/local/bin/brew; else command -v brew; fi)";
    }

    private String macBrewBinaryTest() {
        return "test -x /opt/homebrew/bin/brew || test -x /usr/local/bin/brew || command -v brew >/dev/null 2>&1";
    }

    private List<String> windowsCommands() {
        ArrayList<String> commands = new ArrayList<>();
        commands.add("docker --version");
        commands.add("docker inspect " + dockerContainerName() + " >nul 2>nul && docker start " + dockerContainerName() + " || docker run -d --name " + dockerContainerName() + " -p 127.0.0.1:" + port + ":6379 -v " + dockerVolumeName() + ":/data redis:latest redis-server --appendonly yes");
        return commands;
    }

    private List<String> windowsStartCommands() {
        ArrayList<String> commands = new ArrayList<>();
        commands.add("docker --version");
        commands.add("docker start " + dockerContainerName());
        return commands;
    }

    private InstallResult execute(String label, List<String> commands) {
        return execute(label, commands, "Redis installed and configured on port " + port + ".", "Install commands completed, but Redis did not respond on port " + port + ".");
    }

    private InstallResult execute(String label, List<String> commands, String successMessage, String notRespondingMessage) {
        ArrayList<CommandResult> results = new ArrayList<>();
        for(String command : commands) {
            CommandResult result = run(command);
            results.add(result);
            if(result.status != 0 && !isOptionalRedisPreflight(command)) {
                if(PlatformUtils.isWindows() && command.equals("docker --version")) {
                    return InstallResult.failed(port, "Docker is required to install Redis on Windows.", results);
                }
                if(PlatformUtils.isMac() && command.equals("command -v redis-server >/dev/null 2>&1 || command -v brew >/dev/null 2>&1")) {
                    return InstallResult.failed(port, "Homebrew is required to install Redis on macOS when redis-server is not already installed.", results);
                }
                return InstallResult.failed(port, label + " failed while running: " + command, results);
            }
        }

        boolean running = redisResponds(port);
        if(!running && PlatformUtils.isWindows()) running = run("docker ps --filter name=" + dockerContainerName() + " --format \"{{.Names}}\"").stdout.contains(dockerContainerName());
        return running
                ? InstallResult.installed(port, successMessage, results)
                : InstallResult.failed(port, notRespondingMessage, results);
    }

    private boolean isOptionalRedisPreflight(String command) {
        return command.equals("command -v redis-server >/dev/null 2>&1 && command -v redis-cli >/dev/null 2>&1");
    }

    private void writePrivilegedFile(String targetPath, String content) throws CommonIOException {
        String tmpPath = createTmpFile(content);
        CommandResult mkdir = run("sudo mkdir -p " + quote(parentOf(targetPath)));
        if(mkdir.status != 0) throw new CommonIOException("Unable to create directory for " + targetPath + ": " + mkdir.stderr);
        CommandResult copy = run("sudo cp " + quote(tmpPath) + " " + quote(targetPath));
        if(copy.status != 0) throw new CommonIOException("Unable to write " + targetPath + ": " + copy.stderr);
        run("rm -f " + quote(tmpPath));
    }

    private void writeUserFile(String targetPath, String content) throws CommonIOException {
        PathObject dir = PathUtil.fromString(parentOf(targetPath), true);
        String filename = filenameOf(targetPath);
        FileObject file = Files.create(dir, filename, content);
        file.overwrite(content.getBytes(StandardCharsets.UTF_8));
    }

    private String createTmpFile(String content) throws CommonIOException {
        FileObject file = Files.createTmpFile(instanceName + "-redis-" + port, ".tmp");
        file.overwrite(content.getBytes(StandardCharsets.UTF_8));
        return file.getFile().getAbsolutePath();
    }

    public boolean redisResponds(int redisPort) {
        CommandResult result = run("redis-cli -p " + redisPort + " ping");
        return result.status == 0 && result.stdout.trim().equalsIgnoreCase("PONG");
    }

    private CommandResult run(String command) {
        com.fortrow.jflux.shell.CommandResult result = Shell.execute(command);
        return new CommandResult(command, result.exitCode(), result.stdout(), result.stderr());
    }

    private String redisConfigValue(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private String redisConfig(String dataDirectory, String logPath, String pidPath) {
        return String.join("\n",
                "bind 127.0.0.1 ::1",
                "protected-mode yes",
                "port " + port,
                "daemonize no",
                "supervised no",
                "pidfile " + redisConfigValue(pidPath),
                "dir " + redisConfigValue(dataDirectory),
                "dbfilename dump-" + port + ".rdb",
                "appendonly yes",
                "appendfilename appendonly-" + port + ".aof",
                "logfile " + redisConfigValue(logPath),
                "") ;
    }

    private String linuxService() {
        String binary = "/usr/bin/redis-server";
        return String.join("\n",
                "[Unit]",
                "Description=" + instanceName + " Redis instance on port " + port,
                "After=network.target",
                "",
                "[Service]",
                "Type=simple",
                "User=redis",
                "Group=redis",
                "ExecStart=" + binary + " " + linuxConfigPath(),
                "ExecStop=/usr/bin/redis-cli -p " + port + " shutdown",
                "Restart=always",
                "LimitNOFILE=10032",
                "",
                "[Install]",
                "WantedBy=multi-user.target",
                "");
    }

    private String linuxTransparentHugePagesService() {
        return String.join("\n",
                "[Unit]",
                "Description=Disable Transparent Huge Pages for Redis",
                "DefaultDependencies=no",
                "After=sysinit.target local-fs.target",
                "Before=redis.service " + serviceName("redis") + ".service",
                "",
                "[Service]",
                "Type=oneshot",
                "ExecStart=/bin/sh -c 'test -f /sys/kernel/mm/transparent_hugepage/enabled && echo never > /sys/kernel/mm/transparent_hugepage/enabled || true'",
                "",
                "[Install]",
                "WantedBy=basic.target",
                "");
    }

    private String macLaunchDaemon(String configPath) {
        String label = macLaunchDaemonLabel();
        return String.join("\n",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
                "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">",
                "<plist version=\"1.0\">",
                "<dict>",
                "    <key>Label</key>",
                "    <string>" + label + "</string>",
                "    <key>ProgramArguments</key>",
                "    <array>",
                "        <string>/bin/sh</string>",
                "        <string>-c</string>",
                "        <string>" + macRedisServerCommand() + " " + quote(configPath) + "</string>",
                "    </array>",
                "    <key>RunAtLoad</key>",
                "    <true/>",
                "    <key>KeepAlive</key>",
                "    <true/>",
                "</dict>",
                "</plist>",
                "");
    }

    private String linuxConfigPath() {
        return "/etc/redis/" + serviceName("redis") + ".conf";
    }

    private String linuxServicePath() {
        return "/etc/systemd/system/" + serviceName("redis") + ".service";
    }

    private String linuxThpServicePath() {
        return "/etc/systemd/system/" + serviceName("disable-thp") + ".service";
    }

    private String linuxDataDirectory() {
        return "/var/lib/" + serviceName("redis");
    }

    private String linuxLogPath() {
        return "/var/log/redis/" + serviceName("redis") + ".log";
    }

    private String linuxPidPath() {
        return "/run/redis/" + serviceName("redis") + ".pid";
    }

    private String macBaseDirectory() {
        return "/Library/Application Support/" + instanceName + "/redis";
    }

    private String macConfigPath() {
        return macBaseDirectory() + PlatformUtils.fileSeparator() + "redis-" + port + ".conf";
    }

    private String macDataDirectory() {
        return macBaseDirectory() + PlatformUtils.fileSeparator() + "data-" + port;
    }

    private String macLogDirectory() {
        return "/Library/Logs/" + instanceName;
    }

    private String macLogPath() {
        return macLogDirectory() + PlatformUtils.fileSeparator() + "redis-" + port + ".log";
    }

    private String macPidPath() {
        return "/var/run/" + instanceName + "-redis-" + port + ".pid";
    }

    private String macLaunchDaemonDirectory() {
        return "/Library/LaunchDaemons";
    }

    private String macLaunchDaemonPath() {
        return macLaunchDaemonDirectory() + PlatformUtils.fileSeparator() + macLaunchDaemonLabel() + ".plist";
    }

    private String macLaunchDaemonLabel() {
        return instanceName + ".redis." + port;
    }

    private String dockerContainerName() {
        return instanceName + "-redis-" + port;
    }

    private String dockerVolumeName() {
        return instanceName + "-redis-" + port + "-data";
    }

    private String serviceName(String prefix) {
        return prefix + "-" + instanceName + "-" + port;
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

    private static String safeName(String value) {
        if(value == null || value.isBlank()) return DEFAULT_INSTANCE_NAME;
        return value.toLowerCase().replaceAll("[^a-z0-9-]", "-").replaceAll("-+", "-").replaceAll("^-|-$", "");
    }

    private static String parentOf(String path) {
        int index = path.replace("\\", "/").lastIndexOf("/");
        if(index <= 0) return ".";
        return path.substring(0, index);
    }

    private static String filenameOf(String path) {
        String fixed = path.replace("\\", "/");
        return fixed.substring(fixed.lastIndexOf("/") + 1);
    }

    private static String quote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    public static class InstallResult {
        private final boolean success;
        private final boolean alreadyInstalled;
        private final int port;
        private final String message;
        private final List<CommandResult> commands;

        private InstallResult(boolean success, boolean alreadyInstalled, int port, String message, List<CommandResult> commands) {
            this.success = success;
            this.alreadyInstalled = alreadyInstalled;
            this.port = port;
            this.message = message;
            this.commands = List.copyOf(commands == null ? List.of() : commands);
        }

        public static InstallResult installed(int port, String message, List<CommandResult> commands) {
            return new InstallResult(true, false, port, message, commands);
        }

        public static InstallResult alreadyInstalled(int port, String message) {
            return new InstallResult(true, true, port, message, new ArrayList<>());
        }

        public static InstallResult failed(int port, String message) {
            return failed(port, message, new ArrayList<>());
        }

        public static InstallResult failed(int port, String message, List<CommandResult> commands) {
            return new InstallResult(false, false, port, message, commands);
        }

        public boolean success() {
            return success;
        }

        public boolean alreadyInstalled() {
            return alreadyInstalled;
        }

        public int port() {
            return port;
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
