/*
 * -------------------------------------------------------------------------
 *
 * Copyright (c) 2026 Fortrow LLC. All rights reserved.
 *
 * -------------------------------------------------------------------------
 */
package com.fortrow.jflux.ctl.firewall;

import com.fortrow.jflux.exceptions.ExceptionHandling;
import com.fortrow.jflux.shell.CommandResult;
import com.fortrow.jflux.shell.Shell;
import com.fortrow.jflux.sys.PackageUtils;
import com.fortrow.jflux.sys.PlatformUtils;
import com.fortrow.jflux.todash.__;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Cross-platform firewall controls with safe argument-list execution.
 *
 * <p>The class intentionally exposes a plan through {@link FirewallResult}
 * before it changes system state. Use {@link Options#dryRun()} for startup
 * checks, installers, and tests. Real operations normally require an
 * administrator on Windows or root privileges on Unix-like systems.</p>
 */
public final class FirewallControl {

    private static final String MAC_SOCKET_FILTER = "/usr/libexec/ApplicationFirewall/socketfilterfw";
    private static final String DEFAULT_RULE_PREFIX = "jflux";

    private final Options options;
    private final Backend backend;

    /** Creates a controller using the first supported backend on this host. */
    public FirewallControl() {
        this(Options.defaults());
    }

    /** Creates a controller with an explicit backend, execution policy, or dry-run mode. */
    public FirewallControl(Options options) {
        this.options = options == null ? Options.defaults() : options;
        this.backend = this.options.backend() == Backend.AUTO
                ? detectBackend() : this.options.backend();
    }

    /** Returns the selected backend. */
    public Backend backend() {
        return backend;
    }

    /** Returns the immutable execution options. */
    public Options options() {
        return options;
    }

    /** Detects the preferred firewall implementation without changing state. */
    public static Backend detectBackend() {
        if (PlatformUtils.isWindows() && available("netsh")) return Backend.WINDOWS_DEFENDER;
        if (PlatformUtils.isMac() && available(MAC_SOCKET_FILTER)) return Backend.MACOS_SOCKET_FILTER;
        if (PlatformUtils.isLinux()) {
            if (available("ufw")) return Backend.LINUX_UFW;
            if (available("firewall-cmd")) return Backend.LINUX_FIREWALLD;
            if (available("nft")) return Backend.LINUX_NFTABLES;
            if (available("iptables")) return Backend.LINUX_IPTABLES;
        }
        return Backend.UNSUPPORTED;
    }

    /** Returns available native firewall backends in preference order. */
    public static List<Backend> availableBackends() {
        List<Backend> available = new ArrayList<>();
        if (PlatformUtils.isWindows() && available("netsh")) available.add(Backend.WINDOWS_DEFENDER);
        if (PlatformUtils.isMac() && available(MAC_SOCKET_FILTER)) available.add(Backend.MACOS_SOCKET_FILTER);
        if (PlatformUtils.isLinux()) {
            if (available("ufw")) available.add(Backend.LINUX_UFW);
            if (available("firewall-cmd")) available.add(Backend.LINUX_FIREWALLD);
            if (available("nft")) available.add(Backend.LINUX_NFTABLES);
            if (available("iptables")) available.add(Backend.LINUX_IPTABLES);
        }
        return List.copyOf(available);
    }

    /** Reads the current firewall state using the selected native tool. */
    public FirewallResult status() {
        return run(Operation.STATUS, commandsFor(Operation.STATUS, null, null, null, null, null));
    }

    /** Enables the selected firewall where the backend has a global enable switch. */
    public FirewallResult enable() {
        return run(Operation.ENABLE, commandsFor(Operation.ENABLE, null, null, null, null, null));
    }

    /** Disables the selected firewall where the backend has a global disable switch. */
    public FirewallResult disable() {
        return run(Operation.DISABLE, commandsFor(Operation.DISABLE, null, null, null, null, null));
    }

    /** Allows an inbound TCP or UDP port using an automatically generated rule name. */
    public FirewallResult allowPort(int port, Protocol protocol) {
        return allowPort(port, protocol, null, Direction.INBOUND);
    }

    /** Allows an inbound TCP or UDP port using the supplied rule name. */
    public FirewallResult allowPort(int port, Protocol protocol, String ruleName) {
        return allowPort(port, protocol, ruleName, Direction.INBOUND);
    }

    /** Allows a TCP or UDP port in the requested direction. */
    public FirewallResult allowPort(int port, Protocol protocol, String ruleName, Direction direction) {
        validatePort(port);
        if (protocol == null) throw ExceptionHandling.invalid("Firewall protocol is required.");
        if (direction == null) throw ExceptionHandling.invalid("Firewall direction is required.");
        String name = ruleName(ruleName, port, protocol, direction);
        return run(Operation.ALLOW_PORT,
                commandsFor(Operation.ALLOW_PORT, port, protocol, direction, name, null));
    }

    /** Removes a port rule using the same values used to create it. */
    public FirewallResult removePort(int port, Protocol protocol, String ruleName, Direction direction) {
        validatePort(port);
        if (protocol == null) throw ExceptionHandling.invalid("Firewall protocol is required.");
        if (direction == null) throw ExceptionHandling.invalid("Firewall direction is required.");
        String name = ruleName(ruleName, port, protocol, direction);
        return run(Operation.REMOVE_PORT,
                commandsFor(Operation.REMOVE_PORT, port, protocol, direction, name, null));
    }

    /** Removes an inbound port rule using its generated name. */
    public FirewallResult removePort(int port, Protocol protocol) {
        return removePort(port, protocol, null, Direction.INBOUND);
    }

    /** Allows an executable through application-aware firewalls. */
    public FirewallResult allowApplication(Path executable, String ruleName) {
        Path path = validateExecutable(executable);
        String name = ruleName(ruleName, path.getFileName().toString(), "application");
        return run(Operation.ALLOW_APPLICATION,
                commandsFor(Operation.ALLOW_APPLICATION, null, null, null, name, path));
    }

    /** Removes an executable from application-aware firewalls. */
    public FirewallResult removeApplication(Path executable) {
        Path path = validateExecutable(executable);
        return run(Operation.REMOVE_APPLICATION,
                commandsFor(Operation.REMOVE_APPLICATION, null, null, null, "", path));
    }

    /** Removes a named rule on backends that support named rule deletion. */
    public FirewallResult removeRule(String ruleName) {
        String name = requiredRuleName(ruleName);
        return run(Operation.REMOVE_RULE,
                commandsFor(Operation.REMOVE_RULE, null, null, null, name, null));
    }

    private FirewallResult run(Operation operation, List<List<String>> commands) {
        if (commands == null) {
            return unsupported(operation, "The selected firewall backend does not support this operation.");
        }
        if (options.dryRun()) {
            return new FirewallResult(backend, operation, true, false, true, true,
                    commands, List.of(), "Dry run: no firewall command was executed.");
        }

        List<CommandResult> results = new ArrayList<>(commands.size());
        for (List<String> command : commands) {
            CommandResult result = Shell.execute(command, options.timeout(), options.maxOutputBytes());
            results.add(result);
            if (!result.success()) {
                String diagnostic = result.output().isBlank()
                        ? "Firewall command failed with exit code " + result.exitCode() + "."
                        : result.output().strip();
                return new FirewallResult(backend, operation, true, true, false,
                        false, commands, results, diagnostic);
            }
        }
        return new FirewallResult(backend, operation, true, true, false,
                true, commands, results, "Firewall operation completed.");
    }

    private FirewallResult unsupported(Operation operation, String diagnostic) {
        return new FirewallResult(backend, operation, false, false, false, false,
                List.of(), List.of(), diagnostic);
    }

    private List<List<String>> commandsFor(Operation operation, Integer port, Protocol protocol,
                                           Direction direction, String ruleName, Path executable) {
        if (backend == Backend.AUTO || backend == Backend.UNSUPPORTED) return null;
        String tool = tool(backend);
        String proto = protocol == null ? "" : protocol.value();
        String portValue = port == null ? "" : Integer.toString(port);
        String name = ruleName == null ? "" : ruleName;
        List<List<String>> commands = new ArrayList<>();

        switch (backend) {
            case WINDOWS_DEFENDER -> windowsCommands(commands, tool, operation, portValue, proto,
                    direction, name, executable);
            case MACOS_SOCKET_FILTER -> macCommands(commands, tool, operation, executable);
            case LINUX_UFW -> ufwCommands(commands, tool, operation, portValue, proto, direction, name);
            case LINUX_FIREWALLD -> firewalldCommands(commands, tool, operation, portValue, proto, direction);
            case LINUX_NFTABLES -> nftCommands(commands, tool, operation, portValue, proto, direction);
            case LINUX_IPTABLES -> iptablesCommands(commands, tool, operation, portValue, proto, direction);
            default -> { return null; }
        }
        if (commands.isEmpty()) return null;
        return commands.stream().map(this::withPrivilege).map(List::copyOf).toList();
    }

    private static void windowsCommands(List<List<String>> commands, String tool, Operation operation,
                                        String port, String protocol, Direction direction,
                                        String name, Path executable) {
        switch (operation) {
            case STATUS -> commands.add(List.of(tool, "advfirewall", "show", "allprofiles"));
            case ENABLE -> commands.add(List.of(tool, "advfirewall", "set", "allprofiles", "state", "on"));
            case DISABLE -> commands.add(List.of(tool, "advfirewall", "set", "allprofiles", "state", "off"));
            case ALLOW_PORT -> {
                String portKey = direction == Direction.INBOUND ? "localport=" : "remoteport=";
                commands.add(List.of(tool, "advfirewall", "firewall", "add", "rule", "name=" + name,
                        "dir=" + direction.value(), "action=allow", "protocol=" + protocol,
                        portKey + port, "profile=any", "enable=yes"));
            }
            case REMOVE_PORT -> commands.add(List.of(tool, "advfirewall", "firewall", "delete", "rule",
                    "name=" + name, "protocol=" + protocol));
            case ALLOW_APPLICATION -> commands.add(List.of(tool, "advfirewall", "firewall", "add", "rule",
                    "name=" + name, "dir=in", "action=allow", "program=" + executable,
                    "profile=any", "enable=yes"));
            case REMOVE_APPLICATION, REMOVE_RULE -> commands.add(List.of(tool, "advfirewall", "firewall",
                    "delete", "rule", "name=" + name));
        }
    }

    private static void macCommands(List<List<String>> commands, String tool, Operation operation, Path executable) {
        switch (operation) {
            case STATUS -> commands.add(List.of(tool, "--getglobalstate"));
            case ENABLE -> commands.add(List.of(tool, "--setglobalstate", "on"));
            case DISABLE -> commands.add(List.of(tool, "--setglobalstate", "off"));
            case ALLOW_APPLICATION -> {
                commands.add(List.of(tool, "--add", executable.toString()));
                commands.add(List.of(tool, "--unblockapp", executable.toString()));
            }
            case REMOVE_APPLICATION -> commands.add(List.of(tool, "--remove", executable.toString()));
            default -> { }
        }
    }

    private static void ufwCommands(List<List<String>> commands, String tool, Operation operation,
                                    String port, String protocol, Direction direction, String name) {
        String target = port + "/" + protocol;
        switch (operation) {
            case STATUS -> commands.add(List.of(tool, "status", "verbose"));
            case ENABLE -> commands.add(List.of(tool, "--force", "enable"));
            case DISABLE -> commands.add(List.of(tool, "disable"));
            case ALLOW_PORT -> commands.add(List.of(tool, "allow", direction.value(), target, "comment", name));
            case REMOVE_PORT -> commands.add(List.of(tool, "delete", "allow", direction.value(), target));
            default -> { }
        }
    }

    private static void firewalldCommands(List<List<String>> commands, String tool, Operation operation,
                                           String port, String protocol, Direction direction) {
        if (direction == Direction.OUTBOUND && (operation == Operation.ALLOW_PORT
                || operation == Operation.REMOVE_PORT)) return;
        switch (operation) {
            case STATUS -> commands.add(List.of(tool, "--state"));
            case ENABLE -> commands.add(List.of("systemctl", "enable", "--now", "firewalld"));
            case DISABLE -> commands.add(List.of("systemctl", "disable", "--now", "firewalld"));
            case ALLOW_PORT -> {
                commands.add(List.of(tool, "--permanent", "--add-port=" + port + "/" + protocol));
                commands.add(List.of(tool, "--reload"));
            }
            case REMOVE_PORT -> {
                commands.add(List.of(tool, "--permanent", "--remove-port=" + port + "/" + protocol));
                commands.add(List.of(tool, "--reload"));
            }
            default -> { }
        }
    }

    private static void nftCommands(List<List<String>> commands, String tool, Operation operation,
                                    String port, String protocol, Direction direction) {
        if (direction == Direction.OUTBOUND && operation == Operation.ALLOW_PORT) return;
        switch (operation) {
            case STATUS -> commands.add(List.of(tool, "list", "ruleset"));
            case ALLOW_PORT -> commands.add(List.of(tool, "add", "rule", "inet", "filter", "input",
                    protocol, "dport", port, "counter", "accept"));
            default -> { }
        }
    }

    private static void iptablesCommands(List<List<String>> commands, String tool, Operation operation,
                                         String port, String protocol, Direction direction) {
        String chain = direction == Direction.INBOUND ? "INPUT" : "OUTPUT";
        switch (operation) {
            case STATUS -> commands.add(List.of(tool, "-L", chain, "-n", "-v"));
            case ALLOW_PORT -> commands.add(List.of(tool, "-I", chain, "-p", protocol,
                    "--dport", port, "-j", "ACCEPT"));
            case REMOVE_PORT -> commands.add(List.of(tool, "-D", chain, "-p", protocol,
                    "--dport", port, "-j", "ACCEPT"));
            default -> { }
        }
    }

    private List<String> withPrivilege(List<String> command) {
        if (options.privilegeMode() != PrivilegeMode.NON_INTERACTIVE_SUDO
                || !PlatformUtils.isUnix() || PlatformUtils.executorIsPrivileged()) return command;
        Path sudo = PackageUtils.which("sudo");
        if (sudo == null) return command;
        List<String> elevated = new ArrayList<>(command.size() + 2);
        elevated.add(sudo.toString());
        elevated.add("-n");
        elevated.addAll(command);
        return elevated;
    }

    private static String tool(Backend backend) {
        return switch (backend) {
            case WINDOWS_DEFENDER -> executable("netsh");
            case MACOS_SOCKET_FILTER -> MAC_SOCKET_FILTER;
            case LINUX_UFW -> executable("ufw");
            case LINUX_FIREWALLD -> executable("firewall-cmd");
            case LINUX_NFTABLES -> executable("nft");
            case LINUX_IPTABLES -> executable("iptables");
            default -> "";
        };
    }

    private static String executable(String name) {
        Path found = PackageUtils.which(name);
        return found == null ? name : found.toString();
    }

    private static boolean available(String executable) {
        try {
            return PackageUtils.executableAvailable(executable);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static void validatePort(int port) {
        if (port < 1 || port > 65_535) throw ExceptionHandling.invalid("Firewall port must be between 1 and 65535.");
    }

    private static Path validateExecutable(Path executable) {
        if (executable == null) throw ExceptionHandling.invalid("Firewall executable is required.");
        Path absolute = executable.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absolute) || !Files.isExecutable(absolute)) {
            throw ExceptionHandling.invalid("Firewall executable must be an executable file: " + absolute);
        }
        return absolute;
    }

    private static String ruleName(String requested, int port, Protocol protocol, Direction direction) {
        return ruleName(requested, DEFAULT_RULE_PREFIX + "-" + direction.value() + "-"
                + protocol.value() + "-" + port);
    }

    private static String ruleName(String requested, String fallback, String... ignored) {
        String value = __.defaultTo(requested == null ? null : requested.trim(), fallback);
        if (value.isBlank() || value.length() > 128 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0
                || value.chars().anyMatch(Character::isISOControl)) {
            throw ExceptionHandling.invalid("Firewall rule name must be 1-128 printable characters.");
        }
        return value;
    }

    private static String requiredRuleName(String value) {
        if (value == null || value.isBlank()) throw ExceptionHandling.invalid("Firewall rule name is required.");
        return ruleName(value, "");
    }

    /** Native firewall implementations supported by this controller. */
    public enum Backend {
        AUTO,
        WINDOWS_DEFENDER,
        MACOS_SOCKET_FILTER,
        LINUX_UFW,
        LINUX_FIREWALLD,
        LINUX_NFTABLES,
        LINUX_IPTABLES,
        UNSUPPORTED
    }

    /** Firewall operations represented in results and command plans. */
    public enum Operation {
        STATUS,
        ENABLE,
        DISABLE,
        ALLOW_PORT,
        REMOVE_PORT,
        ALLOW_APPLICATION,
        REMOVE_APPLICATION,
        REMOVE_RULE
    }

    /** Network direction used by port-based backends. */
    public enum Direction {
        INBOUND("in"),
        OUTBOUND("out");

        private final String value;

        Direction(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    /** Protocol supported by portable port rules. */
    public enum Protocol {
        TCP("tcp"),
        UDP("udp");

        private final String value;

        Protocol(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    /** Controls whether Unix commands are direct or prefixed with non-interactive sudo. */
    public enum PrivilegeMode {
        NONE,
        REQUIRE_ELEVATED,
        NON_INTERACTIVE_SUDO
    }

    /** Immutable firewall execution policy. */
    public record Options(Backend backend, boolean dryRun, Duration timeout,
                          int maxOutputBytes, PrivilegeMode privilegeMode) {
        public Options {
            backend = backend == null ? Backend.AUTO : backend;
            timeout = timeout == null ? Duration.ofSeconds(15) : timeout;
            privilegeMode = privilegeMode == null ? PrivilegeMode.NON_INTERACTIVE_SUDO : privilegeMode;
            if (timeout.isNegative() || timeout.isZero()) throw ExceptionHandling.invalid("Firewall timeout must be positive.");
            if (maxOutputBytes <= 0) throw ExceptionHandling.invalid("Firewall output limit must be positive.");
        }

        public static Options defaults() {
            return new Options(Backend.AUTO, false, Duration.ofSeconds(15),
                    Shell.DEFAULT_MAX_OUTPUT_BYTES, PrivilegeMode.NON_INTERACTIVE_SUDO);
        }

        public Options withBackend(Backend value) {
            return new Options(value, dryRun, timeout, maxOutputBytes, privilegeMode);
        }

        public Options withDryRun(boolean value) {
            return new Options(backend, value, timeout, maxOutputBytes, privilegeMode);
        }

        public Options withPrivilegeMode(PrivilegeMode value) {
            return new Options(backend, dryRun, timeout, maxOutputBytes, value);
        }
    }

    /** Structured, bounded result from a firewall plan or execution. */
    public record FirewallResult(Backend backend, Operation operation, boolean supported,
                                 boolean attempted, boolean dryRun, boolean success,
                                 List<List<String>> commands, List<CommandResult> commandResults,
                                 String diagnostic) {
        public FirewallResult {
            backend = backend == null ? Backend.UNSUPPORTED : backend;
            operation = operation == null ? Operation.STATUS : operation;
            commands = commands == null ? List.of() : commands.stream().map(List::copyOf).toList();
            commandResults = List.copyOf(commandResults == null ? List.of() : commandResults);
            diagnostic = diagnostic == null ? "" : diagnostic;
        }

        /** Returns all captured stdout and stderr in operation order. */
        public String output() {
            return __.join(commandResults.stream().map(CommandResult::output).toList(), System.lineSeparator());
        }

        /** Returns the first command, useful for logs and audit output. */
        public List<String> command() {
            return commands.isEmpty() ? List.of() : commands.getFirst();
        }

        /** Indicates that the result contains an auditable plan without execution. */
        public boolean planned() {
            return dryRun && !commands.isEmpty();
        }
    }
}
