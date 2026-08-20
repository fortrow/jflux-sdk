package com.fortrow.jflux.shell;

import com.fortrow.jflux.exceptions.ExceptionHandling;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Cross-platform command execution with safe argument handling and bounded output capture.
 * Prefer the argument-list methods for all commands supplied by an application.
 */
public final class Shell {

    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    public static final int DEFAULT_MAX_OUTPUT_BYTES = 1_048_576;

    private Shell() { }

    /** Executes an executable and arguments without invoking a shell. */
    public static CommandResult execute(List<String> command) {
        return execute(command, DEFAULT_TIMEOUT);
    }

    /** Executes an executable and arguments without invoking a shell. */
    public static CommandResult execute(List<String> command, Duration timeout) {
        return execute(command, timeout, DEFAULT_MAX_OUTPUT_BYTES);
    }

    /**
     * Executes an executable and arguments without invoking a shell.
     * A non-positive output limit is rejected so accidental unbounded capture is avoided.
     */
    public static CommandResult execute(List<String> command, Duration timeout, int maxOutputBytes) {
        validate(command, timeout, maxOutputBytes);
        return executeProcess(List.copyOf(command), timeout, maxOutputBytes, null);
    }

    /** Executes an argument-list command in a specific working directory. */
    public static CommandResult execute(List<String> command, Path directory, Duration timeout) {
        validate(command, timeout, DEFAULT_MAX_OUTPUT_BYTES);
        if (directory == null) throw ExceptionHandling.invalid("Working directory is required.");
        return executeProcess(List.copyOf(command), timeout, DEFAULT_MAX_OUTPUT_BYTES, directory);
    }

    /**
     * Executes a legacy shell command through the platform shell.
     * This method exists for command strings that intentionally use pipes, redirects, or chaining.
     * Use the argument-list API for commands containing untrusted or dynamic values.
     */
    public static CommandResult execute(String command) {
        return execute(command, DEFAULT_TIMEOUT);
    }

    /** Executes a legacy shell command with a timeout. */
    public static CommandResult execute(String command, Duration timeout) {
        return execute(shellCommand(command, null), timeout);
    }

    /** Executes a legacy command using the selected Windows shell when running on Windows. */
    public static CommandResult execute(String command, WindowsShell windowsShell, Duration timeout) {
        return execute(shellCommand(command, windowsShell), timeout);
    }

    /** Starts a legacy command without waiting for completion. Output is inherited by the process. */
    public static void startDetached(String command) throws IOException {
        List<String> shellCommand = shellCommand(command, null);
        new ProcessBuilder(shellCommand).inheritIO().start();
    }

    /** Finds an executable in PATH without invoking a shell. */
    public static boolean executableAvailable(String executable) {
        if (executable == null || executable.isBlank()) return false;
        String path = System.getenv().getOrDefault("PATH", "");
        String separator = isWindows() ? ";" : ":";
        String[] extensions = isWindows()
                ? System.getenv().getOrDefault("PATHEXT", ".EXE;.CMD;.BAT;.COM").split(";")
                : new String[]{""};
        for (String directory : path.split(java.util.regex.Pattern.quote(separator))) {
            if (directory.isBlank()) continue;
            Path base = Path.of(directory).resolve(executable);
            if (isExecutable(base)) return true;
            if (isWindows() && !hasExtension(executable)) {
                for (String extension : extensions) {
                    if (isExecutable(Path.of(directory).resolve(executable + extension))) return true;
                }
            }
        }
        return false;
    }

    private static CommandResult executeProcess(List<String> command, Duration timeout,
                                                int maxOutputBytes, Path directory) {
        Process process = null;
        StreamCapture stdout = new StreamCapture(maxOutputBytes);
        StreamCapture stderr = new StreamCapture(maxOutputBytes);
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            if (directory != null) builder.directory(directory.toFile());
            Process started = builder.start();
            process = started;
            Thread stdoutReader = Thread.startVirtualThread(() -> stdout.read(started.getInputStream()));
            Thread stderrReader = Thread.startVirtualThread(() -> stderr.read(started.getErrorStream()));
            boolean finished = started.waitFor(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!finished) {
                started.destroyForcibly();
                started.waitFor(1, java.util.concurrent.TimeUnit.SECONDS);
            }
            joinReader(stdoutReader);
            joinReader(stderrReader);
            return new CommandResult(command, finished ? started.exitValue() : 124,
                    stdout.text(), stderr.text(), !finished, false,
                    stdout.truncated(), stderr.truncated());
        } catch (IOException e) {
            ExceptionHandling.report(e);
            return failure(command, 127, e.getMessage(), false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) process.destroyForcibly();
            ExceptionHandling.report(e);
            return failure(command, 130, e.getMessage(), true);
        }
    }

    private static void joinReader(Thread reader) throws InterruptedException {
        reader.join(1_000);
        if (reader.isAlive()) reader.interrupt();
    }

    private static CommandResult failure(List<String> command, int status, String message, boolean interrupted) {
        return new CommandResult(command, status, "", message == null ? "" : message,
                false, interrupted, false, false);
    }

    private static List<String> shellCommand(String command, WindowsShell windowsShell) {
        if (command == null || command.isBlank()) throw ExceptionHandling.invalid("Command is required.");
        if (isWindows()) {
            if (windowsShell == WindowsShell.POWERSHELL) {
                return List.of("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", command);
            }
            return List.of("cmd.exe", "/d", "/s", "/c", command);
        }
        return List.of(isMac() ? "/bin/zsh" : "/bin/sh", "-c", command);
    }

    private static void validate(List<String> command, Duration timeout, int maxOutputBytes) {
        if (command == null || command.isEmpty() || command.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw ExceptionHandling.invalid("A non-empty command and executable arguments are required.");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw ExceptionHandling.invalid("Command timeout must be positive.");
        }
        if (maxOutputBytes <= 0) throw ExceptionHandling.invalid("Output limit must be positive.");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    }

    private static boolean isExecutable(Path path) {
        try {
            return java.nio.file.Files.isRegularFile(path) && java.nio.file.Files.isExecutable(path);
        } catch (SecurityException | java.nio.file.InvalidPathException ignored) {
            return false;
        }
    }

    private static boolean hasExtension(String value) {
        return value.lastIndexOf('.') > value.lastIndexOf('\\') && value.lastIndexOf('.') > value.lastIndexOf('/');
    }

    private static final class StreamCapture {
        private final int limit;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private boolean truncated;

        private StreamCapture(int limit) {
            this.limit = limit;
        }

        private void read(InputStream input) {
            byte[] buffer = new byte[8192];
            try (input) {
                int count;
                while ((count = input.read(buffer)) != -1) {
                    int remaining = limit - output.size();
                    if (remaining > 0) output.write(buffer, 0, Math.min(count, remaining));
                    if (count > remaining) truncated = true;
                }
            } catch (IOException e) {
                ExceptionHandling.report(e);
            }
        }

        private String text() {
            return output.toString(StandardCharsets.UTF_8);
        }

        private boolean truncated() {
            return truncated;
        }
    }
}
