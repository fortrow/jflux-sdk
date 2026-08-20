package com.fortrow.jflux.files;

import com.fortrow.jflux.exceptions.ExceptionHandling;
import com.fortrow.jflux.sys.PlatformUtils;

import java.nio.file.Paths;

/** Path construction and compatibility helpers used by the files package. */
public final class PathUtil {

    private PathUtil() { }

    public static PathObject cwd() {
        return fromString(Paths.get(".").toAbsolutePath().normalize().toString());
    }

    /** Returns directory, filename, basename, and final extension in that order. */
    public static String[] isolateFile(String path) {
        return isolateFile(path, PlatformUtils.fileSeparator());
    }

    public static String[] isolateFile(String path, String fileSeparator) {
        if (path == null || path.isEmpty()) throw ExceptionHandling.invalid("Path is required.");
        String normalized = path.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String directory = slash < 0 ? "" : normalized.substring(0, slash + 1);
        String filename = normalized.substring(slash + 1);
        int dot = filename.lastIndexOf('.');
        boolean hasExtension = dot > 0 && dot < filename.length() - 1;
        String base = hasExtension ? filename.substring(0, dot) : filename;
        String suffix = hasExtension ? filename.substring(dot + 1) : "";
        String separator = fileSeparator == null || fileSeparator.isEmpty()
                ? PlatformUtils.fileSeparator() : fileSeparator;
        directory = directory.replace("/", separator).replace("\\", separator);
        return new String[]{directory, filename, base, suffix};
    }

    public static PathObject fromString(String path) {
        return new PathObject(path);
    }

    public static PathObject fromString(String path, boolean mkdirs) {
        PathObject p = new PathObject(path);
        if (mkdirs) p.mkdirs();
        return p;
    }

    /** Builds an absolute-style path for compatibility with the original helper. */
    public static PathObject dir(String... dirs) {
        return fromString(join(dirs));
    }

    public static PathObject mkdirs(String... dirs) {
        PathObject path = dir(dirs);
        path.mkdirs();
        return path;
    }

    public static String fixDirectoryPath(String dir) {
        if (dir == null || dir.isEmpty()) return "." + PlatformUtils.fileSeparator();
        String separator = PlatformUtils.fileSeparator();
        String normalized = dir.replace('\\', '/').replaceAll("/+", "/");
        if (!normalized.endsWith("/")) normalized += "/";
        return normalized.replace("/", separator);
    }

    public static PathVariable createVar(String key, String value) {
        return new PathVariable(key, value);
    }

    private static String join(String... dirs) {
        if (dirs == null || dirs.length == 0) return PlatformUtils.fileSeparator();
        String separator = PlatformUtils.fileSeparator();
        StringBuilder result = new StringBuilder(separator);
        for (String dir : dirs) {
            if (dir == null || dir.isBlank()) continue;
            if (result.length() > 1 && result.charAt(result.length() - 1) != separator.charAt(0)) {
                result.append(separator);
            }
            result.append(dir.replace('\\', separator.charAt(0)).replace('/', separator.charAt(0)));
        }
        return result.toString();
    }
}
