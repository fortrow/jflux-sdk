/*
 * -------------------------------------------------------------------------
 * 
 * Copyright (c) 2026 Fortrow LLC. All rights reserved.
 *  
 * NOTICE: All information contained herein is, and remains the property of 
 * Fortrow, LLC. The intellectual and technical concepts contained herein are 
 * proprietary to [Company Name] and may be covered by U.S. and Foreign Patents, 
 * patents in process, and are protected by trade secret or copyright law.
 * 
 * Dissemination of this information or reproduction of this material is 
 * strictly forbidden unless prior written permission is obtained from 
 * Fortrow, LLC.
 * 
 * -------------------------------------------------------------------------
 * 
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-08 17:12:47 EDT
 * 
 * -------------------------------------------------------------------------

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

    public EncryptedApiKeyPairs(String apiSecret) {

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

        clientPrivate.clearKeys();
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
            this.publicKey = null;
            this.privateKey = null;
            this.privateKeyId = "";
            this.publicKeyId = "";
        }

    }

}
