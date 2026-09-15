package com.fortrow.jflux.runtime;

import java.nio.file.Path;
import java.util.Map;

/** Restricted context supplied to a runtime function. */
public record RuntimeContext(RuntimeRequest request, RuntimeFunctionSource source,
                             String packageId, Path packageDirectory,
                             Map<String, String> metadata) {
    public RuntimeContext {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
