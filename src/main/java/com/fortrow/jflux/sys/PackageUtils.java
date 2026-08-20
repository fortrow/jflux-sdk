package com.fortrow.jflux.sys;

import com.fortrow.jflux.exceptions.ExceptionHandling;
import com.fortrow.jflux.shell.Shell;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Cross-platform package-manager, executable, and version auditing helpers. */
public final class PackageUtils {

    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(5);

    private PackageUtils() { }

    public enum Manager {
        APT_GET("apt-get"),
        DPKG("dpkg"),
        DNF("dnf"),
        YUM("yum"),
        RPM("rpm"),
        PACMAN("pacman"),
        APK("apk"),
        ZYPPER("zypper"),
        BREW("brew"),
        WINGET("winget"),
        CHOCO("choco"),
        UNKNOWN("");

        private final String executable;

        Manager(String executable) {
            this.executable = executable;
        }

        public String executable() {
            return executable;
        }
    }

    public record BinaryInfo(String name, Path path, boolean installed, String version, String diagnostic) {
        public BinaryInfo {
            name = name == null ? "" : name;
            version = version == null ? "" : version;
            diagnostic = diagnostic == null ? "" : diagnostic;
        }
    }

    public record PackageInfo(String name, Manager manager, boolean installed, String version, String diagnostic) {
        public PackageInfo {
            name = name == null ? "" : name;
            manager = manager == null ? Manager.UNKNOWN : manager;
            version = version == null ? "" : version;
            diagnostic = diagnostic == null ? "" : diagnostic;
        }
    }

    public record AuditResult(Manager manager, List<PackageInfo> packages, List<BinaryInfo> binaries) {
        public AuditResult {
            manager = manager == null ? Manager.UNKNOWN : manager;
            packages = List.copyOf(packages == null ? List.of() : packages);
            binaries = List.copyOf(binaries == null ? List.of() : binaries);
        }

        public boolean allAvailable() {
            return packages.stream().allMatch(PackageInfo::installed)
                    && binaries.stream().allMatch(BinaryInfo::installed);
        }
    }

    public record InstallResult(String packageName, Manager manager, boolean attempted,
                                boolean success, int status, String output) {
        public InstallResult {
            packageName = packageName == null ? "" : packageName;
            manager = manager == null ? Manager.UNKNOWN : manager;
            output = output == null ? "" : output;
        }
    }

    /** Finds an executable without invoking a shell or trusting shell quoting. */
    public static Path which(String executable) {
        if (executable == null || executable.isBlank()) {
            throw ExceptionHandling.invalid("Executable name is required.");
        }
        Path direct;
        try {
            direct = Path.of(executable);
        } catch (InvalidPathException e) {
            throw ExceptionHandling.invalid("Executable name is not a valid path.", e);
        }
        if (direct.getNameCount() > 1 || executable.contains("/" ) || executable.contains("\\")) {
            return executablePath(direct);
        }

        String path = PlatformUtils.environment("PATH", "");
        if (path.isBlank()) return null;
        String[] extensions = PlatformUtils.isWindows()
                ? PlatformUtils.environment("PATHEXT", ".EXE;.CMD;.BAT;.COM").split(";")
                : new String[]{""};
        for (String directory : path.split(java.util.regex.Pattern.quote(PlatformUtils.pathSeparator()))) {
            if (directory.isBlank()) continue;
            try {
                Path base = Path.of(directory).resolve(executable);
                Path found = executablePath(base);
                if (found != null) return found;
                if (PlatformUtils.isWindows() && !hasExtension(executable)) {
                    for (String extension : extensions) {
                        found = executablePath(Path.of(directory).resolve(executable + extension));
                        if (found != null) return found;
                    }
                }
            } catch (InvalidPathException e) {
                ExceptionHandling.report(e);
            }
        }
        return null;
    }

    public static boolean executableAvailable(String executable) {
        return which(executable) != null;
    }

    public static BinaryInfo binary(String executable) {
        Path path = which(executable);
        if (path == null) return new BinaryInfo(executable, null, false, "", "Executable was not found in PATH.");
        CommandResult version = execute(List.of(path.toString(), "--version"));
        if (version.status != 0) version = execute(List.of(path.toString(), "-version"));
        String output = firstLine(version.output);
        return new BinaryInfo(executable, path, true, output, version.status == 0 ? "" : version.output);
    }

    public static Manager preferredManager() {
        for (Manager manager : preferenceOrder()) {
            if (manager != Manager.UNKNOWN && executableAvailable(manager.executable())) return manager;
        }
        return Manager.UNKNOWN;
    }

    public static Set<Manager> availableManagers() {
        EnumSet<Manager> result = EnumSet.noneOf(Manager.class);
        for (Manager manager : Manager.values()) {
            if (manager != Manager.UNKNOWN && executableAvailable(manager.executable())) result.add(manager);
        }
        return Collections.unmodifiableSet(result);
    }

    public static boolean packageInstalled(String packageName) {
        return packageInfo(packageName).installed();
    }

    public static PackageInfo packageInfo(String packageName) {
        if (packageName == null || packageName.isBlank()) {
            throw ExceptionHandling.invalid("Package name is required.");
        }
        Manager manager = preferredManager();
        if (manager == Manager.UNKNOWN) {
            return new PackageInfo(packageName, manager, false, "", "No supported package manager was found.");
        }
        return packageInfo(packageName, manager);
    }

    public static PackageInfo packageInfo(String packageName, Manager manager) {
        if (packageName == null || packageName.isBlank()) throw ExceptionHandling.invalid("Package name is required.");
        if (manager == null || manager == Manager.UNKNOWN) {
            return new PackageInfo(packageName, Manager.UNKNOWN, false, "", "No package manager was selected.");
        }
        List<String> command = packageQuery(manager, packageName);
        if (command.isEmpty() || !executableAvailable(manager.executable())) {
            return new PackageInfo(packageName, manager, false, "", "Package manager is not available.");
        }
        CommandResult result = execute(command);
        boolean installed = result.status == 0 && !result.output.isBlank();
        return new PackageInfo(packageName, manager, installed,
                installed ? firstLine(result.output) : "", installed ? "" : firstLine(result.output));
    }

    /** Installs a named package using the first available platform manager. */
    public static InstallResult install(String packageName) {
        return install(packageName, preferredManager());
    }

    /** Installs a named package with an explicitly selected manager. */
    public static InstallResult install(String packageName, Manager manager) {
        validatePackageName(packageName);
        if (manager == null || manager == Manager.UNKNOWN) {
            return new InstallResult(packageName, Manager.UNKNOWN, false, false, 127,
                    "No supported package manager was found.");
        }
        if (!executableAvailable(manager.executable())) {
            return new InstallResult(packageName, manager, false, false, 127,
                    "Package manager is not available: " + manager.executable());
        }
        List<String> command = installCommand(packageName, manager);
        if (command.isEmpty()) {
            return new InstallResult(packageName, manager, false, false, 126,
                    "The selected manager cannot install named packages.");
        }
        CommandResult result = execute(command);
        return new InstallResult(packageName, manager, true, result.status == 0, result.status, result.output);
    }

    /** Installs a local Debian package with dpkg. */
    public static InstallResult installDeb(Path debFile) {
        return installLocalFile(debFile, Manager.DPKG, "deb");
    }

    /** Installs or upgrades a local RPM package with rpm. */
    public static InstallResult installRpm(Path rpmFile) {
        return installLocalFile(rpmFile, Manager.RPM, "rpm");
    }

    public static AuditResult audit(List<String> packageNames, List<String> binaries) {
        List<PackageInfo> packages = new ArrayList<>();
        if (packageNames != null) for (String name : packageNames) packages.add(packageInfo(name));
        List<BinaryInfo> executableInfo = new ArrayList<>();
        if (binaries != null) for (String name : binaries) executableInfo.add(binary(name));
        return new AuditResult(preferredManager(), packages, executableInfo);
    }

    public static AuditResult audit(String[] packageNames, String[] binaries) {
        return audit(packageNames == null ? List.of() : Arrays.asList(packageNames),
                binaries == null ? List.of() : Arrays.asList(binaries));
    }

    private static List<String> packageQuery(Manager manager, String name) {
        return switch (manager) {
            case APT_GET -> List.of("dpkg-query", "-W", "-f=${Status} ${Version}", name);
            case DPKG -> List.of("dpkg-query", "-W", "-f=${Status} ${Version}", name);
            case DNF, YUM, RPM -> List.of("rpm", "-q", "--qf", "%{NAME} %{VERSION}-%{RELEASE}\\n", name);
            case PACMAN -> List.of("pacman", "-Q", name);
            case APK -> List.of("apk", "info", "-e", name);
            case ZYPPER -> List.of("rpm", "-q", "--qf", "%{NAME} %{VERSION}-%{RELEASE}\\n", name);
            case BREW -> List.of("brew", "list", "--versions", name);
            case WINGET -> List.of("winget", "list", "--id", name, "--exact");
            case CHOCO -> List.of("choco", "list", "--local-only", "--exact", name);
            case UNKNOWN -> List.of();
        };
    }

    private static List<String> installCommand(String packageName, Manager manager) {
        boolean needsPrivilege = !PlatformUtils.executorIsPrivileged()
                && manager != Manager.BREW && manager != Manager.WINGET && manager != Manager.CHOCO;
        String prefix = needsPrivilege ? "sudo" : "";
        return switch (manager) {
            case APT_GET -> command(prefix, "apt-get", "install", "-y", packageName);
            case DNF -> command(prefix, "dnf", "install", "-y", packageName);
            case YUM -> command(prefix, "yum", "install", "-y", packageName);
            case PACMAN -> command(prefix, "pacman", "-Sy", "--noconfirm", packageName);
            case APK -> command(prefix, "apk", "add", packageName);
            case ZYPPER -> command(prefix, "zypper", "--non-interactive", "install", packageName);
            case BREW -> command("", "brew", "install", packageName);
            case WINGET -> command("", "winget", "install", "--id", packageName, "--exact",
                    "--accept-package-agreements", "--accept-source-agreements");
            case CHOCO -> command("", "choco", "install", packageName, "-y");
            case DPKG, RPM, UNKNOWN -> List.of();
        };
    }

    private static InstallResult installLocalFile(Path file, Manager manager, String extension) {
        if (file == null) throw ExceptionHandling.invalid("Package file is required.");
        if (!Files.isRegularFile(file)) {
            return new InstallResult(file.toString(), manager, false, false, 2, "Package file was not found.");
        }
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!name.endsWith("." + extension)) {
            return new InstallResult(file.toString(), manager, false, false, 2,
                    "Expected a ." + extension + " package file.");
        }
        if (!executableAvailable(manager.executable())) {
            return new InstallResult(file.toString(), manager, false, false, 127,
                    "Package installer is not available: " + manager.executable());
        }
        boolean needsPrivilege = !PlatformUtils.executorIsPrivileged();
        List<String> command = manager == Manager.DPKG
                ? command(needsPrivilege ? "sudo" : "", "dpkg", "-i", file.toAbsolutePath().normalize().toString())
                : command(needsPrivilege ? "sudo" : "", "rpm", "-Uvh", file.toAbsolutePath().normalize().toString());
        CommandResult result = execute(command);
        return new InstallResult(file.toString(), manager, true, result.status == 0, result.status, result.output);
    }

    private static List<String> command(String prefix, String... arguments) {
        ArrayList<String> result = new ArrayList<>();
        if (prefix != null && !prefix.isBlank()) result.add(prefix);
        result.addAll(Arrays.asList(arguments));
        return List.copyOf(result);
    }

    private static void validatePackageName(String packageName) {
        if (packageName == null || packageName.isBlank() || packageName.startsWith("-")
                || !packageName.matches("[A-Za-z0-9][A-Za-z0-9_.+:@/%=-]*")) {
            throw ExceptionHandling.invalid("Invalid package name.");
        }
    }

    private static Manager[] preferenceOrder() {
        if (PlatformUtils.isMac()) return new Manager[]{Manager.BREW};
        if (PlatformUtils.isWindows()) return new Manager[]{Manager.WINGET, Manager.CHOCO};
        if (PlatformUtils.isLinux()) {
            return new Manager[]{Manager.APT_GET, Manager.DNF, Manager.YUM, Manager.APK,
                    Manager.PACMAN, Manager.ZYPPER, Manager.RPM};
        }
        return Manager.values();
    }

    private static Path executablePath(Path path) {
        try {
            return Files.isRegularFile(path) && Files.isExecutable(path) ? path.toAbsolutePath().normalize() : null;
        } catch (SecurityException e) {
            ExceptionHandling.report(e);
            return null;
        }
    }

    private static boolean hasExtension(String value) {
        return value.lastIndexOf('.') > value.lastIndexOf('\\') && value.lastIndexOf('.') > value.lastIndexOf('/');
    }

    private static String firstLine(String output) {
        if (output == null || output.isBlank()) return "";
        return output.strip().split("\\R", 2)[0].strip();
    }

    private static CommandResult execute(List<String> command) {
        com.fortrow.jflux.shell.CommandResult result = Shell.execute(command, COMMAND_TIMEOUT);
        return new CommandResult(result.exitCode(), result.output());
    }

    private record CommandResult(int status, String output) {
        private CommandResult {
            output = output == null ? "" : output;
        }
    }
}
