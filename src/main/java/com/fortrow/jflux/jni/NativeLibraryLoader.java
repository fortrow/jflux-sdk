package com.fortrow.jflux.jni;

import com.fortrow.jflux.exceptions.ExceptionHandling;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads JNI libraries from the system or from platform-specific classpath
 * resources. Resource extraction is bounded and uses absolute, private file
 * paths so a library name cannot escape the configured temporary directory.
 */
public final class NativeLibraryLoader {

    private static final Set<String> LOADED = ConcurrentHashMap.newKeySet();

    private NativeLibraryLoader() { }

    /** Loads a logical library name using the loader's class as the resource anchor. */
    public static NativeLibraryLoadResult load(String logicalName) {
        return load(logicalName, NativeLibraryLoader.class, NativeLoadOptions.defaults());
    }

    /** Loads a logical library using resources visible to {@code resourceAnchor}. */
    public static NativeLibraryLoadResult load(String logicalName, Class<?> resourceAnchor) {
        return load(logicalName, resourceAnchor, NativeLoadOptions.defaults());
    }

    /** Loads a logical library with explicit resource and extraction settings. */
    public static NativeLibraryLoadResult load(String logicalName, Class<?> resourceAnchor,
                                               NativeLoadOptions options) {
        String name = validateName(logicalName);
        Class<?> anchor = Objects.requireNonNull(resourceAnchor, "resourceAnchor");
        NativeLoadOptions settings = options == null ? NativeLoadOptions.defaults() : options;
        JNIPlatform platform = JNIPlatform.current();
        String key = name + "@" + platform.identifier();

        if (LOADED.contains(key)) {
            return new NativeLibraryLoadResult(name, platform, null, false, false, true);
        }

        UnsatisfiedLinkError systemFailure = null;
        if (settings.preferSystemLibrary()) {
            try {
                System.loadLibrary(name);
                LOADED.add(key);
                return new NativeLibraryLoadResult(name, platform, null, true, false, false);
            } catch (UnsatisfiedLinkError error) {
                // A bundled resource is a supported fallback; report only if both paths fail.
                systemFailure = error;
            } catch (SecurityException error) {
                systemFailure = new UnsatisfiedLinkError(error.getMessage());
                systemFailure.initCause(error);
            }
        }

        Path extracted = null;
        try {
            extracted = extract(name, anchor, settings);
            System.load(extracted.toString());
            LOADED.add(key);
            if (settings.deleteOnExit()) extracted.toFile().deleteOnExit();
            return new NativeLibraryLoadResult(name, platform, extracted, false, true, false);
        } catch (IOException | UnsatisfiedLinkError | SecurityException error) {
            if (extracted != null) deleteQuietly(extracted);
            if (systemFailure != null) error.addSuppressed(systemFailure);
            throw ExceptionHandling.failure(
                    "Unable to load JNI library '" + name + "' for " + platform.identifier(), error);
        }
    }

    /**
     * Extracts a bundled library without loading it. This is useful for tests,
     * diagnostics, or applications that manage native loading themselves.
     */
    public static Path extract(String logicalName, Class<?> resourceAnchor, NativeLoadOptions options)
            throws IOException {
        String name = validateName(logicalName);
        Class<?> anchor = Objects.requireNonNull(resourceAnchor, "resourceAnchor");
        NativeLoadOptions settings = options == null ? NativeLoadOptions.defaults() : options;
        JNIPlatform platform = JNIPlatform.current();
        String resourcePath = settings.resourceRoot() + "/" + platform.resourcePath(name);

        InputStream input = anchor.getResourceAsStream("/" + resourcePath);
        if (input == null && anchor.getClassLoader() != null) {
            input = anchor.getClassLoader().getResourceAsStream(resourcePath);
        }
        if (input == null) {
            throw new IOException("JNI library resource was not found: " + resourcePath);
        }

        Files.createDirectories(settings.extractionDirectory());
        Path output = Files.createTempFile(
                settings.extractionDirectory(), "jflux-" + safePrefix(name) + "-", platform.libraryFileName(name));
        try (InputStream source = input) {
            copyBounded(source, output, settings.maxLibraryBytes());
            if (settings.deleteOnExit()) output.toFile().deleteOnExit();
            return output;
        } catch (IOException | RuntimeException error) {
            deleteQuietly(output);
            throw error;
        }
    }

    /** Returns the expected classpath resource for the current platform. */
    public static String resourcePath(String logicalName, NativeLoadOptions options) {
        NativeLoadOptions settings = options == null ? NativeLoadOptions.defaults() : options;
        return settings.resourceRoot() + "/" + JNIPlatform.current().resourcePath(validateName(logicalName));
    }

    private static void copyBounded(InputStream input, Path output, long limit) throws IOException {
        byte[] buffer = new byte[16 * 1024];
        long copied = 0;
        try (var target = Files.newOutputStream(output)) {
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (count > limit - copied) {
                    throw new IOException("JNI library exceeds the configured size limit of " + limit + " bytes.");
                }
                target.write(buffer, 0, count);
                copied += count;
            }
        }
    }

    private static String validateName(String name) {
        if (name == null || name.isBlank() || !name.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,95}")) {
            throw ExceptionHandling.invalid("JNI library name must be a safe logical name.");
        }
        return name;
    }

    private static String safePrefix(String value) {
        return value.length() <= 32 ? value : value.substring(0, 32);
    }

    private static void deleteQuietly(Path path) {
        try {
            if (path != null) Files.deleteIfExists(path);
        } catch (IOException | SecurityException ignored) {
            // Preserve the original native loading failure.
        }
    }
}
