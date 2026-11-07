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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-08 17:12:47 EDT
 */
package com.fortrow.jflux.http.api.server.encryption;

import com.fortrow.jflux.crypto.builtin.RSA;
import com.fortrow.jflux.crypto.builtin.SHA;
import com.fortrow.jflux.encoding.BaseX;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

public class EncryptedApiKeyPairs {

    private AsymmetricKey clientPrivate;
    private AsymmetricKey serverPrivate;
    private AsymmetricKey clientPublic;
    private AsymmetricKey serverPublic;

    private String keySetId;

    private EncryptedApiCryptoSuite suite = EncryptedApiCryptoSuite.RSA_2048;

    /** Uses a client-owned public key; the corresponding private key never reaches the server. */
    public EncryptedApiKeyPairs(String apiSecret, EncryptedApiCryptoSuite suite, byte[] clientPublicKey) {
        if (suite == null) throw com.fortrow.jflux.exceptions.ExceptionHandling.invalid("Encrypted API crypto suite is required.");
        if (clientPublicKey == null && suite == EncryptedApiCryptoSuite.RSA_2048) {
            initializeLegacy(apiSecret);
            return;
        }
        this.suite = suite;
        this.clientPublic = new AsymmetricKey(suite.publicKey(clientPublicKey), apiSecret);
        byte[][] serverKeys = suite.generateKeyPair();
        try {
            this.serverPublic = new AsymmetricKey(suite.publicKey(serverKeys[0]), apiSecret);
            this.serverPrivate = new AsymmetricKey(suite.privateKey(serverKeys[1]), apiSecret);
        } finally {
            java.util.Arrays.fill(serverKeys[1], (byte) 0);
        }
        this.keySetId = com.fortrow.jflux.encoding.Hex.getHex(com.fortrow.jflux.crypto.Rand.secure(32));
    }

    public EncryptedApiCryptoSuite getSuite() { return suite; }

    /** Erases keys held by this endpoint when the session ends. */
    public void destroy() {
        if (clientPrivate != null) clientPrivate.clearKeys();
        if (serverPrivate != null) serverPrivate.clearKeys();
        if (clientPublic != null) clientPublic.clearKeys();
        if (serverPublic != null) serverPublic.clearKeys();
    }

    public EncryptedApiKeyPairs(String apiSecret) {

        initializeLegacy(apiSecret);
    }

    private void initializeLegacy(String apiSecret) {

        KeyPair[] keyPairs = getKeyPairs();
        clientPublic = new AsymmetricKey(keyPairs[0].getPublic(), apiSecret);
        clientPrivate = new AsymmetricKey(keyPairs[0].getPrivate(), apiSecret);
        serverPublic = new AsymmetricKey(keyPairs[1].getPublic(), apiSecret);
        serverPrivate = new AsymmetricKey(keyPairs[1].getPrivate(), apiSecret);
        String keySetIdHashVal = clientPublic.getPublicKeyId() +
                clientPrivate.getPrivateKeyId() +
                serverPublic.getPublicKeyId() +
                serverPrivate.getPrivateKey();
        keySetId = SHA.getSha1(keySetIdHashVal);
        keyPairs = null;

    }

    public void clear() {

        if (clientPrivate != null) clientPrivate.clearKeys();
        serverPublic.clearKeys();

    }

    public AsymmetricKey getClientPrivate() {

        return clientPrivate;

    }

    public String getKeySetId() {
        return keySetId;
    }

    public AsymmetricKey getServerPrivate() {

        return serverPrivate;

    }

    public AsymmetricKey getClientPublic() {

        return clientPublic;

    }

    public AsymmetricKey getServerPublic() {

        return serverPublic;

    }

    private KeyPair[] getKeyPairs() {

        KeyPair kp1 = RSA.generateKeyPair(2048);
        KeyPair kp2 = RSA.generateKeyPair(2048);

        return new KeyPair[]{kp1, kp2};

    }

    public class AsymmetricKey {

        private PrivateKey privateKey;
        private PublicKey publicKey;
        private String privateKeyId;
        private String publicKeyId;

        public AsymmetricKey(PrivateKey privateKey, String apiSecret) {

            this.privateKey = privateKey;
            byte[] bites = privateKey.getEncoded();
            String base64 = BaseX.encode64(bites);
            String key384 = SHA.get384(apiSecret);
            privateKeyId = SHA.getSha1(SHA.getHmac384Hex(base64, key384));

        }

        public AsymmetricKey(PublicKey publicKey, String apiSecret) {

            this.publicKey = publicKey;
            byte[] bites = publicKey.getEncoded();
            String base64 = BaseX.encode64(bites);
            String key384 = SHA.get384(apiSecret);
            publicKeyId = SHA.getSha1(SHA.getHmac384Hex(base64, key384));

        }
        public PrivateKey getPrivateKey() {
            return privateKey;
        }

        public PublicKey getPublicKey() {
            return publicKey;
        }

        public String getPrivateKeyId() {
            return privateKeyId;
        }

        public String getPublicKeyId() {
            return publicKeyId;
        }

        public void clearKeys() {
            if (this.privateKey != null) {
                try { this.privateKey.destroy(); }
                catch (javax.security.auth.DestroyFailedException ignored) { }
            }
            this.publicKey = null;
            this.privateKey = null;
            this.privateKeyId = "";
            this.publicKeyId = "";
        }

    }

}
