package com.fortrow.jflux.files;

import com.fortrow.jflux.exceptions.ExceptionHandling;
import com.fortrow.jflux.exceptions.types.CommonIOException;

import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Lightweight timestamped append-only log helper. */
public class LogFile {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final File file;
    private final PrintStream stderr;

    public LogFile() {
        this(System.err);
    }

    /** Creates a stderr-backed logger. The stream is injectable for tests and hosts. */
    public LogFile(PrintStream stderr) {
        if (stderr == null) throw ExceptionHandling.invalid("stderr logger is required.");
        this.file = null;
        this.stderr = stderr;
    }

    public LogFile(String filepath) {
        this(filepath, System.err);
    }

    /** Creates a file-backed logger; the supplied stream is retained for future fallback use. */
    public LogFile(String filepath, PrintStream stderr) {
        if (stderr == null) throw ExceptionHandling.invalid("stderr logger is required.");
        try {
            this.file = Files.open(filepath).getFile();
            this.stderr = stderr;
        } catch (CommonIOException e) {
            throw ExceptionHandling.failure("Unable to open log file '" + filepath + "'.", e);
        }
    }

    public synchronized void write(String line) {
        String message = "[" + LocalDateTime.now().format(FORMATTER) + "] :: " + String.valueOf(line) + System.lineSeparator();
        if (file == null) {
            stderr.print(message);
            stderr.flush();
            return;
        }
        try {
            java.nio.file.Files.writeString(
                    file.toPath(),
                    message,
                    StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND
            );
        } catch (java.io.IOException e) {
            throw ExceptionHandling.failure("Unable to append log file '" + file + "'.", e);
        }
    }
}
