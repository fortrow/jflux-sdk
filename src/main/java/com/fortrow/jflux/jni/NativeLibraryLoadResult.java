package com.fortrow.jflux.jni;

import java.nio.file.Path;

/** Outcome of a native library load attempt. */
public record NativeLibraryLoadResult(
        String logicalName,
        JNIPlatform platform,
        Path loadedPath,
        boolean loadedFromSystem,
        boolean loadedFromResource,
        boolean alreadyLoaded) {

    public NativeLibraryLoadResult {
        logicalName = logicalName == null ? "" : logicalName;
        platform = platform == null ? JNIPlatform.current() : platform;
        loadedPath = loadedPath == null ? null : loadedPath.toAbsolutePath().normalize();
    }
}
