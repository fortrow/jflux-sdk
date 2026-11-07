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
 *
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-07 23:18:22 EDT
 */
package com.fortrow.jflux.ssl.keystore;

import com.fortrow.jflux.exceptions.ExceptionHandling;
import com.fortrow.jflux.ssl.SslCertKeyPair;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class JavaKeyStore {

    private SSLContext context;
    private KeyStore keyStore;

    /** @deprecated A Let's Encrypt request requires domain and account configuration. */
    @Deprecated
    public static JavaKeyStore createLetsEncrypt() {
        throw ExceptionHandling.unsupported("Use LetsEncrypt.request(...) before constructing JavaKeyStore.");
    }

    public JavaKeyStore(SslCertKeyPair pair) {
        this.context = getContext(pair, "");
    }

    public JavaKeyStore(SslCertKeyPair pair, String tlsVersion) {
        this.context = getContext(pair, tlsVersion);
    }

    public SSLContext ssl() {
        return this.context;
    }

    public KeyStore getNative() {
        return keyStore;
    }

    private SSLContext getContext(SslCertKeyPair pair, String tlsVersion) {
        if (pair == null) throw ExceptionHandling.invalid("TLS certificate and key pair is required.");
        if (!pair.getKey().isFile() || !pair.getCert().isFile()) {
            throw ExceptionHandling.invalid("TLS private key and certificate files must exist.");
        }
        String password = pair.getPassword();
        try {
            SSLContext context = SSLContext.getInstance(normalizeTlsVersion(tlsVersion));
            List<X509Certificate> certificates = generateCertificates(Files.readAllBytes(pair.getCert().toPath()));
            if (certificates.isEmpty()) throw new CertificateException("Certificate PEM did not contain an X.509 certificate.");
            byte[] keyBytes = parsePrivateKey(Files.readAllBytes(pair.getKey().toPath()));
            var key = generatePrivateKeyFromDER(keyBytes);

            KeyStore keystore = KeyStore.getInstance("JKS");
            keystore.load(null);
            Certificate[] chain = certificates.toArray(Certificate[]::new);
            keystore.setCertificateEntry("cert-alias", chain[0]);
            keystore.setKeyEntry("key-alias", key, password.toCharArray(), chain);
            this.keyStore = keystore;

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keystore, password.toCharArray());

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(keystore);
            context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
            return context;
        } catch (Exception e) {
            throw ExceptionHandling.failure("Unable to initialize TLS key store.", e);
        }
    }

    private static String normalizeTlsVersion(String value) {
        if (value == null || value.isBlank()) return "TLS";
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        if (!normalized.startsWith("TLS")) normalized = "TLS" + normalized;
        if (!normalized.matches("TLSV1\\.(2|3)")) {
            throw ExceptionHandling.invalid("Only TLSv1.2 and TLSv1.3 are supported.");
        }
        return normalized.equals("TLSV1.2") ? "TLSv1.2" : "TLSv1.3";
    }

    private static byte[] parsePrivateKey(byte[] pem) {
        String data = new String(pem, StandardCharsets.US_ASCII);
        String begin = "-----BEGIN PRIVATE KEY-----";
        String end = "-----END PRIVATE KEY-----";
        int start = data.indexOf(begin);
        int finish = data.indexOf(end, start + begin.length());
        if (start < 0 || finish < 0) {
            throw ExceptionHandling.invalid("Only PKCS#8 PEM private keys are supported.");
        }
        String encoded = data.substring(start + begin.length(), finish).replaceAll("\\s", "");
        try {
            return Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            throw ExceptionHandling.invalid("TLS private key PEM is not valid Base64.", e);
        }
    }

    private static List<X509Certificate> generateCertificates(byte[] pem) throws CertificateException {
        String data = new String(pem, StandardCharsets.US_ASCII);
        String begin = "-----BEGIN CERTIFICATE-----";
        String end = "-----END CERTIFICATE-----";
        ArrayList<X509Certificate> certificates = new ArrayList<>();
        int cursor = 0;
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        while (true) {
            int start = data.indexOf(begin, cursor);
            if (start < 0) break;
            int finish = data.indexOf(end, start + begin.length());
            if (finish < 0) throw new CertificateException("Certificate PEM is missing its end marker.");
            String encoded = data.substring(start + begin.length(), finish).replaceAll("\\s", "");
            try {
                byte[] der = Base64.getDecoder().decode(encoded);
                certificates.add((X509Certificate) factory.generateCertificate(new java.io.ByteArrayInputStream(der)));
            } catch (IllegalArgumentException e) {
                throw new CertificateException("Certificate PEM is not valid Base64.", e);
            }
            cursor = finish + end.length();
        }
        return certificates;
    }

    private static java.security.PrivateKey generatePrivateKeyFromDER(byte[] keyBytes)
            throws InvalidKeySpecException, java.security.NoSuchAlgorithmException {
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);

        KeyFactory factory = KeyFactory.getInstance("RSA");

        return factory.generatePrivate(spec);
    }

}
