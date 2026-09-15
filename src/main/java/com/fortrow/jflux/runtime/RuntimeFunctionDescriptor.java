package com.fortrow.jflux.runtime;

import java.time.Instant;
import java.util.List;

/** Metadata controlling function identity, expiration, and authorized replacement. */
public record RuntimeFunctionDescriptor(String id, String version, RuntimeFunctionSource source,
                                        List<String> replaces, Instant expiresAt,
                                        String packageId, String keyId, boolean overrideRequested) {
    public RuntimeFunctionDescriptor(String id, String version, RuntimeFunctionSource source,
                                     List<String> replaces, Instant expiresAt,
                                     String packageId, String keyId) {
        this(id, version, source, replaces, expiresAt, packageId, keyId, false);
    }

    public RuntimeFunctionDescriptor {
        if(id == null || id.isBlank()) throw new IllegalArgumentException("function id is required");
        version = version == null || version.isBlank() ? "0" : version;
        source = source == null ? RuntimeFunctionSource.BUILTIN : source;
        replaces = replaces == null ? List.of() : List.copyOf(replaces);
        packageId = packageId == null || packageId.isBlank() ? id : packageId;
        keyId = keyId == null ? "" : keyId;
    }

    public boolean expired(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }
}
