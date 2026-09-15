package com.fortrow.jflux.runtime;

/** Limits used while expanding a runtime JAR into memory. */
public record RuntimeLoadOptions(int maxEntries, int maxClassBytes,
                                 int maxResourceBytes, long maxTotalBytes) {
    public RuntimeLoadOptions {
        if(maxEntries < 1 || maxClassBytes < 1 || maxResourceBytes < 1 || maxTotalBytes < 1) {
            throw new IllegalArgumentException("Runtime JAR limits must be positive");
        }
        if(maxTotalBytes < maxClassBytes) throw new IllegalArgumentException("Total limit is too small");
    }

    public static RuntimeLoadOptions defaults() {
        return new RuntimeLoadOptions(4096, 32 * 1024 * 1024, 16 * 1024 * 1024, 128L * 1024 * 1024);
    }
}
