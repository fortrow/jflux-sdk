/*
 * Copyright (c) 2026 Fortrow LLC.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Fortrow-Commercial
 *
 * JFlux is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License, version 3, as published by
 * the Free Software Foundation.
 *
 * JFlux is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with JFlux. If not, see <https://www.gnu.org/licenses/>.
 *
 * Alternatively, a separate commercial license is available from Fortrow LLC.
 * For terms permitting use without the AGPL copyleft requirements, contact
 * ryan@fortrow.com. Commercial rights require a separate written agreement.
 * See LICENSE and COMMERCIAL-LICENSE.md.
 */

package com.fortrow.jflux.http.api.client;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

/** Internal TLS material loader shared by the HTTP/3 and upgrade clients. */
final class TlsMaterial {
    private TlsMaterial() { }

    static KeyStore loadTrustStore(MtlsConfig config) throws GeneralSecurityException, IOException {
        if (!config.hasTrustStore()) return null;
        char[] password = config.trustStorePassword();
        try {
            return loadStore(config.trustStore(), config.trustStoreType(), password);
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
    }

    static X509ExtendedKeyManager loadIdentityKeyManager(MtlsConfig config)
            throws GeneralSecurityException, IOException {
        char[] password = config.identityPassword();
        try {
            KeyManagerFactory factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            factory.init(loadStore(config.identityStore(), config.identityStoreType(), password), password);
            for (KeyManager manager : factory.getKeyManagers()) {
                if (manager instanceof X509ExtendedKeyManager x509Manager) return x509Manager;
            }
            throw new GeneralSecurityException("Configured client identity store did not provide an X509 key manager.");
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
    }

    static SSLContext buildSslContext(MtlsConfig config, boolean allowSelfSigned)
            throws GeneralSecurityException, IOException {
        KeyManager[] keyManagers = new KeyManager[]{loadIdentityKeyManager(config)};
        TrustManager[] trustManagers;
        if (allowSelfSigned) {
            trustManagers = new TrustManager[]{new TrustAllManager()};
        } else {
            KeyStore trustStore = loadTrustStore(config);
            TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init(trustStore);
            trustManagers = factory.getTrustManagers();
        }

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keyManagers, trustManagers, new SecureRandom());
        return context;
    }

    private static KeyStore loadStore(java.nio.file.Path path, String type, char[] password)
            throws GeneralSecurityException, IOException {
        KeyStore store = KeyStore.getInstance(type);
        try (var input = Files.newInputStream(path)) {
            store.load(input, password);
        }
        return store;
    }

    private static final class TrustAllManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) { }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) { }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
