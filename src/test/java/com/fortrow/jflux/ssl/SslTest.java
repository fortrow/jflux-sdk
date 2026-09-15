package com.fortrow.jflux.ssl;

import com.fortrow.jflux.ssl.keystore.JavaKeyStore;
import com.fortrow.jflux.ssl.selfsigned.SelfSignedSSL;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SSLContext;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SslTest {

    @Test
    void selfSignedCertificateBuildsTlsContext(@TempDir Path directory) {
        if (!SelfSignedSSL.isAvailable()) return;

        SslCertKeyPair pair = SelfSignedSSL.create(directory.toString(), "test-key.pem",
                "US", "Virginia", "Richmond", "Fortrow", "IT", "localhost");

        assertTrue(pair.getKey().isFile());
        assertTrue(pair.getCert().isFile());
        JavaKeyStore keyStore = new JavaKeyStore(pair, "v1.3");
        assertNotNull(keyStore.getNative());
        assertEquals("TLSv1.3", keyStore.ssl().getProtocol());
    }

    @Test
    void tlsVersionAndInputFilesAreValidated(@TempDir Path directory) {
        SslCertKeyPair missing = new SslCertKeyPair(directory.resolve("missing-key.pem").toFile(),
                directory.resolve("missing-cert.pem").toFile());

        assertThrows(IllegalArgumentException.class, () -> new JavaKeyStore(missing, "v1.1"));
        assertThrows(IllegalArgumentException.class, () -> new JavaKeyStore(missing, "v1.3"));
    }

    @Test
    void compatibilityCaOverloadDoesNotPretendToSignWithOnlyACertificate(@TempDir Path directory) {
        assertThrows(UnsupportedOperationException.class, () -> SelfSignedSSL.createWithCA(
                directory.toString(), "server", directory.resolve("ca.pem").toFile(),
                "US", "Virginia", "Richmond", "Fortrow", "IT", "localhost"));
    }
}
