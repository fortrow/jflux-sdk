package com.fortrow.jflux.csv;

import com.fortrow.jflux.exceptions.ExceptionHandling;
import de.siegmar.fastcsv.reader.CsvReader;
import de.siegmar.fastcsv.reader.CsvRecord;
import de.siegmar.fastcsv.writer.CsvWriter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** FastCSV-backed helpers with path validation and bounded convenience methods. */
public final class Csv {

    private Csv() { }

    public static CsvWriter getWriter(String path) throws IOException {
        return getWriter(toPath(path));
    }

    public static CsvWriter getWriter(Path path) throws IOException {
        Path file = validatePath(path);
        Path parent = file.getParent();
        if (parent != null) Files.createDirectories(parent);
        return CsvWriter.builder().build(file);
    }

    public static CsvReader<CsvRecord> getReader(String path) throws IOException {
        return getReader(toPath(path));
    }

    public static CsvReader<CsvRecord> getReader(Path path) throws IOException {
        return CsvReader.builder().ofCsvRecord(validatePath(path));
    }

    /** Reads records into memory for small exports; use {@link #getReader(Path)} for streaming. */
    public static List<List<String>> readAll(Path path) throws IOException {
        ArrayList<List<String>> rows = new ArrayList<>();
        try (CsvReader<CsvRecord> reader = getReader(path)) {
            for (CsvRecord record : reader) rows.add(List.copyOf(record.getFields()));
        }
        return List.copyOf(rows);
    }

    public static List<List<String>> readAll(String path) throws IOException {
        return readAll(toPath(path));
    }

    /** Writes all rows and closes the writer deterministically. */
    public static void writeAll(Path path, Iterable<? extends Iterable<String>> rows) throws IOException {
        if (rows == null) throw ExceptionHandling.invalid("CSV rows are required.");
        try (CsvWriter writer = getWriter(path)) {
            for (Iterable<String> row : rows) {
                if (row == null) throw ExceptionHandling.invalid("CSV rows cannot contain null rows.");
                writer.writeRecord(row);
            }
        }
    }

    public static void writeAll(String path, Iterable<? extends Iterable<String>> rows) throws IOException {
        writeAll(toPath(path), rows);
    }

    private static Path toPath(String path) {
        if (path == null || path.isBlank()) throw ExceptionHandling.invalid("CSV path is required.");
        try {
            return Path.of(path);
        } catch (RuntimeException e) {
            throw ExceptionHandling.invalid("CSV path is invalid.", e);
        }
    }

    private static Path validatePath(Path path) {
        if (path == null) throw ExceptionHandling.invalid("CSV path is required.");
        return path.toAbsolutePath().normalize();
    }
}
