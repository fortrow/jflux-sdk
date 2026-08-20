package com.fortrow.jflux.shell;

import java.util.List;

/** Immutable result from a shell command execution. */
public record CommandResult(
        List<String> command,
        int exitCode,
        String stdout,
        String stderr,
        boolean timedOut,
        boolean interrupted,
        boolean stdoutTruncated,
        boolean stderrTruncated) {

    public CommandResult {
        command = List.copyOf(command == null ? List.of() : command);
        stdout = stdout == null ? "" : stdout;
        stderr = stderr == null ? "" : stderr;
    }

    public boolean success() {
        return !timedOut && !interrupted && exitCode == 0;
    }

    public boolean failed() {
        return !success();
    }

    /** Returns stdout followed by stderr, useful for legacy APIs that had one output channel. */
    public String output() {
        if (stderr.isBlank()) return stdout;
        if (stdout.isBlank()) return stderr;
        return stdout + (stdout.endsWith("\n") ? "" : "\n") + stderr;
    }
}
