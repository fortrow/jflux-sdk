package com.fortrow.jflux.http.api.client;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MtlsConfigTest {
    @Test
    void keepsIdentityAndTrustStoreSettingsWithoutExposingPasswords() {
        char[] identityPassword = "identity-secret".toCharArray();
        char[] trustPassword = "trust-secret".toCharArray();
        MtlsConfig config = MtlsConfig.keyStores(Path.of("client.p12"), "PKCS12", identityPassword,
                Path.of("ca.p12"), "PKCS12", trustPassword);

        identityPassword[0] = 'x';
        trustPassword[0] = 'x';

        assertEquals(Path.of("client.p12"), config.identityStore());
        assertArrayEquals("identity-secret".toCharArray(), config.identityPassword());
        assertEquals(Path.of("ca.p12"), config.trustStore());
        assertArrayEquals("trust-secret".toCharArray(), config.trustStorePassword());
        assertFalse(config.toString().contains("secret"));
    }

    @Test
    void supportsAnIdentityWithoutAnExplicitTrustStore() {
        MtlsConfig config = MtlsConfig.pkcs12(Path.of("client.p12"), new char[0]);

        assertFalse(config.hasTrustStore());
        assertEquals("PKCS12", config.identityStoreType());
    }
}
