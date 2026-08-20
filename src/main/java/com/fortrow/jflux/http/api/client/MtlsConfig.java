package com.fortrow.jflux.http.api.client;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

/** Optional client-certificate configuration for API calls over HTTP/1 or HTTP/3. */
public final class MtlsConfig {
    private final Path identityStore;
    private final String identityStoreType;
    private final char[] identityPassword;
    private final Path trustStore;
    private final String trustStoreType;
    private final char[] trustStorePassword;

    private MtlsConfig(Path identityStore, String identityStoreType, char[] identityPassword,
                       Path trustStore, String trustStoreType, char[] trustStorePassword) {
        this.identityStore = Objects.requireNonNull(identityStore, "client identity store is required");
        this.identityStoreType = normalizeType(identityStoreType, "PKCS12");
        this.identityPassword = copy(identityPassword);
        this.trustStore = trustStore;
        this.trustStoreType = normalizeType(trustStoreType, "PKCS12");
        this.trustStorePassword = copy(trustStorePassword);
    }

    public static MtlsConfig pkcs12(Path identityStore, char[] identityPassword) {
        return pkcs12(identityStore, identityPassword, null, null);
    }

    public static MtlsConfig pkcs12(Path identityStore, char[] identityPassword,
                                    Path trustStore, char[] trustStorePassword) {
        return new MtlsConfig(identityStore, "PKCS12", identityPassword,
                trustStore, "PKCS12", trustStorePassword);
    }

    public static MtlsConfig keyStores(Path identityStore, String identityStoreType,
                                       char[] identityPassword, Path trustStore,
                                       String trustStoreType, char[] trustStorePassword) {
        return new MtlsConfig(identityStore, identityStoreType, identityPassword,
                trustStore, trustStoreType, trustStorePassword);
    }

    public Path identityStore() { return identityStore; }
    public String identityStoreType() { return identityStoreType; }
    public char[] identityPassword() { return copy(identityPassword); }
    public Path trustStore() { return trustStore; }
    public String trustStoreType() { return trustStoreType; }
    public char[] trustStorePassword() { return copy(trustStorePassword); }
    public boolean hasTrustStore() { return trustStore != null; }

    @Override
    public String toString() {
        return "MtlsConfig[identityStore=" + identityStore + ", identityStoreType=" + identityStoreType
                + ", trustStore=" + trustStore + ", trustStoreType=" + trustStoreType + "]";
    }

    private static String normalizeType(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static char[] copy(char[] value) {
        return value == null ? new char[0] : Arrays.copyOf(value, value.length);
    }
}
