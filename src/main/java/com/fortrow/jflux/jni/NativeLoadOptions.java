package com.fortrow.jflux.jni;

import com.fortrow.jflux.exceptions.ExceptionHandling;
import com.fortrow.jflux.sys.PlatformUtils;

import java.nio.file.Path;

/** Immutable safety and extraction settings for {@link NativeLibraryLoader}. */
public record NativeLoadOptions(
        Path extractionDirectory,
        String resourceRoot,
        long maxLibraryBytes,
        boolean preferSystemLibrary,
        boolean deleteOnExit) {

    public NativeLoadOptions {
        if (extractionDirectory == null) {
            throw ExceptionHandling.invalid("Native library extraction directory is required.");
        }
        extractionDirectory = extractionDirectory.toAbsolutePath().normalize();
        if (resourceRoot == null || resourceRoot.isBlank()) {
            throw ExceptionHandling.invalid("Native library resource root is required.");
        }
        resourceRoot = normalizeResourceRoot(resourceRoot);
        if (maxLibraryBytes <= 0) {
            throw ExceptionHandling.invalid("Native library size limit must be positive.");
        }
    }

    /** Conservative defaults suitable for bundled application libraries. */
    public static NativeLoadOptions defaults() {
        return new NativeLoadOptions(
                PlatformUtils.tempDirectory().resolve("jflux-native"),
                "native",
                64L * 1024L * 1024L,
                true,
                false);
    }

    /** Returns a copy with a different extraction directory. */
    public NativeLoadOptions withExtractionDirectory(Path directory) {
        return new NativeLoadOptions(directory, resourceRoot, maxLibraryBytes, preferSystemLibrary, deleteOnExit);
    }

    /** Returns a copy with a different classpath resource root. */
    public NativeLoadOptions withResourceRoot(String root) {
        return new NativeLoadOptions(extractionDirectory, root, maxLibraryBytes, preferSystemLibrary, deleteOnExit);
    }

    /** Returns a copy with a different maximum extracted library size. */
    public NativeLoadOptions withMaxLibraryBytes(long maxBytes) {
        return new NativeLoadOptions(extractionDirectory, resourceRoot, maxBytes, preferSystemLibrary, deleteOnExit);
    }

    /** Returns a copy that controls whether the system search is attempted first. */
    public NativeLoadOptions withPreferSystemLibrary(boolean preferSystem) {
        return new NativeLoadOptions(extractionDirectory, resourceRoot, maxLibraryBytes, preferSystem, deleteOnExit);
    }

    /** Returns a copy that schedules extracted files for JVM-exit cleanup. */
    public NativeLoadOptions withDeleteOnExit(boolean delete) {
        return new NativeLoadOptions(extractionDirectory, resourceRoot, maxLibraryBytes, preferSystemLibrary, delete);
    }

    private static String normalizeResourceRoot(String root) {
        String normalized = root.trim().replace('\\', '/');
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        if (normalized.isBlank() || normalized.equals(".") || normalized.contains("..")) {
            throw ExceptionHandling.invalid("Native library resource root must be a safe classpath path.");
        }
        return normalized;
    }
}
