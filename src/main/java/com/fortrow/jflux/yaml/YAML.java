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
 */
package com.fortrow.jflux.yaml;

import com.fortrow.jflux.exceptions.ExceptionHandling;
import com.fortrow.jflux.exceptions.types.CommonIOException;
import com.fortrow.jflux.files.FileObject;
import com.google.gson.Gson;
import org.snakeyaml.engine.v2.api.Dump;
import org.snakeyaml.engine.v2.api.DumpSettings;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Small YAML facade backed by SnakeYAML Engine.
 *
 * <p>The historical string, file, map, list, and JSON conversion methods are
 * retained. New stream and bounded-input methods avoid copying an entire
 * {@link FileObject} into its mutable byte buffer before parsing.</p>
 */
public class YAML {

    /** Default maximum number of YAML code points accepted by a parser. */
    public static final int DEFAULT_MAX_CODE_POINTS = 8 * 1024 * 1024;

    /** Default maximum number of bytes accepted from a stream or file. */
    public static final int DEFAULT_MAX_INPUT_BYTES = 32 * 1024 * 1024;

    /** Default alias limit to reduce YAML alias expansion risk. */
    public static final int DEFAULT_MAX_ALIASES = 50;

    private static final LoadSettings DEFAULT_LOAD_SETTINGS = loadSettings(DEFAULT_MAX_CODE_POINTS);
    private static final DumpSettings DUMP_SETTINGS = DumpSettings.builder().build();
    private static final Dump DUMPER = new Dump(DUMP_SETTINGS);
    private static final Gson GSON = new Gson();

    /** Creates the compatibility facade; all operations are also static. */
    public YAML() {
    }

    /**
     * Parses one YAML document using the safe default input limits.
     *
     * <p>An empty YAML document continues to return {@code null}. Null input
     * is invalid caller input.</p>
     */
    public static Object parse(String yaml) {
        requireInput(yaml);
        return loadString(yaml, newDefaultLoader());
    }

    /** Parses one YAML document with an explicit code-point limit. */
    public static Object parse(String yaml, int maxCodePoints) {
        requireInput(yaml);
        validateLimit(maxCodePoints, "maxCodePoints");
        Load loader = maxCodePoints == DEFAULT_MAX_CODE_POINTS
                ? newDefaultLoader()
                : new Load(loadSettings(maxCodePoints));
        return loadString(yaml, loader);
    }

    /**
     * Parses a file without first loading it into {@link FileObject}'s buffer.
     * The caller retains ownership of the file object.
     */
    public static Object parse(FileObject file) {
        if (file == null) throw ExceptionHandling.invalid("YAML file is required.");
        try (InputStream input = Files.newInputStream(file.path())) {
            return loadStream(input, DEFAULT_MAX_INPUT_BYTES, newDefaultLoader());
        } catch (InputLimitExceededException e) {
            throw ExceptionHandling.invalid("YAML input exceeds the configured byte limit.", e);
        } catch (IOException e) {
            throw ExceptionHandling.failure("Unable to read YAML file '" + file.path() + "'.", e);
        }
    }

    /** Parses a file object with an explicit byte limit. */
    public static Object parse(FileObject file, int maxInputBytes) {
        if (file == null) throw ExceptionHandling.invalid("YAML file is required.");
        validateLimit(maxInputBytes, "maxInputBytes");
        try (InputStream input = Files.newInputStream(file.path())) {
            return loadStream(input, maxInputBytes, newDefaultLoader());
        } catch (InputLimitExceededException e) {
            throw ExceptionHandling.invalid("YAML input exceeds the configured byte limit.", e);
        } catch (IOException e) {
            throw ExceptionHandling.failure("Unable to read YAML file '" + file.path() + "'.", e);
        }
    }

    /**
     * Parses one YAML document from a caller-owned stream. The stream is not
     * closed by this method.
     */
    public static Object parseStream(InputStream input) {
        return parseStream(input, DEFAULT_MAX_INPUT_BYTES);
    }

    /** Parses a caller-owned stream with an explicit byte limit. */
    public static Object parseStream(InputStream input, int maxInputBytes) {
        if (input == null) throw ExceptionHandling.invalid("YAML input stream is required.");
        validateLimit(maxInputBytes, "maxInputBytes");
        try {
            return loadStream(input, maxInputBytes, newDefaultLoader());
        } catch (InputLimitExceededException e) {
            throw ExceptionHandling.invalid("YAML input exceeds the configured byte limit.", e);
        } catch (IOException e) {
            throw ExceptionHandling.failure("Unable to read YAML input stream.", e);
        }
    }

    /**
     * Parses a filesystem path without the historical create-if-missing
     * behavior of {@code Files.open}.
     */
    public static Object parsePath(Path path) {
        if (path == null) throw ExceptionHandling.invalid("YAML path is required.");
        try (InputStream input = Files.newInputStream(path)) {
            return loadStream(input, DEFAULT_MAX_INPUT_BYTES, newDefaultLoader());
        } catch (InputLimitExceededException e) {
            throw ExceptionHandling.invalid("YAML input exceeds the configured byte limit.", e);
        } catch (IOException e) {
            throw ExceptionHandling.failure("Unable to read YAML file '" + path + "'.", e);
        }
    }

    /** Parses a YAML file by path, preserving the checked compatibility API. */
    public static Object parseFile(String filepath) throws CommonIOException {
        if (filepath == null || filepath.isBlank()) throw ExceptionHandling.invalid("YAML file path is required.");
        Path path = Path.of(filepath);
        try (InputStream input = Files.newInputStream(path)) {
            return loadStream(input, DEFAULT_MAX_INPUT_BYTES, newDefaultLoader());
        } catch (InputLimitExceededException e) {
            throw ExceptionHandling.invalid("YAML input exceeds the configured byte limit.", e);
        } catch (IOException e) {
            throw new CommonIOException("Unable to read YAML file '" + path + "'.", e);
        }
    }

    /** Converts a parsed YAML document into a Java type through cached Gson. */
    public static <Any> Any parse(String yaml, Class<Any> c) {
        return convert(parse(yaml), c);
    }

    /** Converts a parsed file into a Java type. */
    public static <Any> Any parse(FileObject file, Class<Any> c) {
        return convert(parse(file), c);
    }

    /** Converts a parsed YAML file into a Java type. */
    public static <Any> Any parseFile(String filepath, Class<Any> c) throws CommonIOException {
        return convert(parseFile(filepath), c);
    }

    /** Returns the first YAML document as a string-keyed map. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(String yaml) {
        Object parsed = parse(yaml);
        if (parsed instanceof Map<?, ?>) return (Map<String, Object>) parsed;
        throw ExceptionHandling.invalid("YAML root is not an object/map.");
    }

    /** Returns the first YAML document as a list. */
    @SuppressWarnings("unchecked")
    public static List<Object> list(String yaml) {
        Object parsed = parse(yaml);
        if (parsed instanceof List<?>) return (List<Object>) parsed;
        throw ExceptionHandling.invalid("YAML root is not a list.");
    }

    /** Parses all YAML documents in a bounded string. */
    public static List<Object> parseAll(String yaml) {
        requireInput(yaml);
        try {
            Iterable<Object> documents = newDefaultLoader().loadAllFromString(yaml);
            List<Object> values = new ArrayList<>();
            for (Object document : documents) values.add(document);
            return values;
        } catch (RuntimeException e) {
            throw ExceptionHandling.invalid("Invalid YAML input.", e);
        }
    }

    /** Serializes a Java/YAML-compatible value using a shared dumper. */
    public static String string(Object value) {
        synchronized (DUMPER) {
            return DUMPER.dumpToString(value);
        }
    }

    /** Converts YAML to compact JSON through the JSON-Simple-first facade. */
    public static String jsonString(String yaml) {
        return com.fortrow.jflux.output.json.JSON.string(parse(yaml));
    }

    /** Converts YAML to formatted JSON through the shared JSON formatter. */
    public static String prettyJsonString(String yaml) {
        return com.fortrow.jflux.output.json.JSON.prettyString(parse(yaml));
    }

    private static LoadSettings loadSettings(int maxCodePoints) {
        return LoadSettings.builder()
                .setCodePointLimit(maxCodePoints)
                .setMaxAliasesForCollections(DEFAULT_MAX_ALIASES)
                .build();
    }

    private static Load newDefaultLoader() {
        return new Load(DEFAULT_LOAD_SETTINGS);
    }

    private static Object loadString(String yaml, Load loader) {
        try {
            return loader.loadFromString(yaml);
        } catch (RuntimeException e) {
            throw ExceptionHandling.invalid("Invalid YAML input.", e);
        }
    }

    private static Object loadStream(InputStream input, int maxInputBytes, Load loader) throws IOException {
        try {
            return loader.loadFromInputStream(new BoundedInputStream(input, maxInputBytes));
        } catch (RuntimeException e) {
            InputLimitExceededException limit = findInputLimit(e);
            if (limit != null) throw limit;
            throw ExceptionHandling.invalid("Invalid YAML input.", e);
        }
    }

    private static InputLimitExceededException findInputLimit(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof InputLimitExceededException limit) return limit;
            current = current.getCause();
        }
        return null;
    }

    private static <Any> Any convert(Object parsed, Class<Any> type) {
        if (type == null) throw ExceptionHandling.invalid("YAML target type is required.");
        return convert(parsed, (Type) type);
    }

    private static <Any> Any convert(Object parsed, Type type) {
        if (type == null) throw ExceptionHandling.invalid("YAML target type is required.");
        try {
            return GSON.fromJson(GSON.toJson(parsed), type);
        } catch (RuntimeException e) {
            throw ExceptionHandling.invalid("Unable to convert YAML to the requested type.", e);
        }
    }

    private static void requireInput(String yaml) {
        if (yaml == null) throw ExceptionHandling.invalid("YAML input is required.");
    }

    private static void validateLimit(int limit, String name) {
        if (limit <= 0) throw ExceptionHandling.invalid(name + " must be greater than zero.");
    }

    private static final class InputLimitExceededException extends IOException {
        private InputLimitExceededException(long maxBytes) {
            super("YAML input exceeds " + maxBytes + " bytes.");
        }
    }

    /** Bounded, non-closing stream wrapper for parser input. */
    private static final class BoundedInputStream extends FilterInputStream {
        private final long maxBytes;
        private long count;

        private BoundedInputStream(InputStream input, long maxBytes) {
            super(input);
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            if (count >= maxBytes) {
                if (super.read() == -1) return -1;
                throw new InputLimitExceededException(maxBytes);
            }
            int value = super.read();
            if (value != -1) count++;
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            if (length == 0) return 0;
            if (count >= maxBytes) {
                if (super.read() == -1) return -1;
                throw new InputLimitExceededException(maxBytes);
            }
            int allowed = (int) Math.min(length, maxBytes - count);
            int read = super.read(bytes, offset, allowed);
            if (read > 0) count += read;
            return read;
        }

        @Override
        public long skip(long length) throws IOException {
            if (length <= 0) return 0;
            if (count >= maxBytes) {
                if (super.read() == -1) return 0;
                throw new InputLimitExceededException(maxBytes);
            }
            long allowed = Math.min(length, maxBytes - count);
            long skipped = super.skip(allowed);
            if (skipped > 0) count += skipped;
            return skipped;
        }
    }
}
