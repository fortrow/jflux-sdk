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

package com.fortrow.jflux.runtime;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Properties;

/** Versioned AES-GCM envelope for a runtime JAR. Plaintext is never written by this type. */
public final class EncryptedRuntimePackage {
    private static final byte[] MAGIC = "JFLXPKG1".getBytes(StandardCharsets.US_ASCII);
    private static final int VERSION = 1;
    private final String keyId;
    private final byte[] manifest;
    private final byte[] nonce;
    private final byte[] ciphertext;
    private final byte[] authenticatedHeader;

    private EncryptedRuntimePackage(String keyId, byte[] manifest, byte[] nonce,
                                    byte[] ciphertext, byte[] authenticatedHeader) {
        this.keyId = keyId;
        this.manifest = manifest;
        this.nonce = nonce;
        this.ciphertext = ciphertext;
        this.authenticatedHeader = authenticatedHeader;
    }

    public static EncryptedRuntimePackage parse(byte[] encoded) throws IOException {
        if(encoded == null || encoded.length < 32) throw new IOException("Encrypted runtime package is too small");
        try(DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded))) {
            byte[] magic = input.readNBytes(MAGIC.length);
            if(!Arrays.equals(magic, MAGIC) || input.readUnsignedByte() != VERSION) {
                throw new IOException("Unsupported JFlux runtime package format");
            }
            int keyLength = input.readUnsignedShort();
            int manifestLength = input.readInt();
            int nonceLength = input.readUnsignedByte();
            if(keyLength > 256 || manifestLength < 1 || manifestLength > 1_048_576
                    || nonceLength < 12 || nonceLength > 32) throw new IOException("Invalid encrypted runtime header");
            byte[] keyBytes = input.readNBytes(keyLength);
            byte[] manifest = input.readNBytes(manifestLength);
            byte[] nonce = input.readNBytes(nonceLength);
            byte[] ciphertext = input.readAllBytes();
            if(keyBytes.length != keyLength || manifest.length != manifestLength
                    || nonce.length != nonceLength || ciphertext.length < 16) {
                throw new IOException("Truncated encrypted runtime package");
            }
            int headerLength = MAGIC.length + 1 + 2 + 4 + 1 + keyLength + manifestLength + nonceLength;
            return new EncryptedRuntimePackage(new String(keyBytes, StandardCharsets.UTF_8), manifest, nonce,
                    ciphertext, Arrays.copyOf(encoded, headerLength));
        }
    }

    public String keyId() { return keyId; }

    public String packageId() { return manifestProperty("packageId", manifestProperty("id", "unknown")); }

    public String version() { return manifestProperty("version", "0"); }

    public String manifestProperty(String name, String fallback) {
        Properties properties = new Properties();
        try {
            properties.load(new ByteArrayInputStream(manifest));
        } catch(IOException failure) {
            throw new IllegalStateException("Invalid encrypted runtime manifest", failure);
        }
        return properties.getProperty(name, fallback);
    }

    public byte[] decrypt(SecretKey key) throws GeneralSecurityException {
        if(key == null) throw new IllegalArgumentException("key is required");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce));
        cipher.updateAAD(authenticatedHeader);
        return cipher.doFinal(ciphertext);
    }

    /** Test/tooling helper. Production packages should be created by a trusted package service. */
    public static byte[] seal(byte[] jar, String keyId, Properties source,
                              SecretKey key, byte[] nonce) throws GeneralSecurityException, IOException {
        if(jar == null || jar.length == 0 || keyId == null || keyId.isBlank() || key == null
                || nonce == null || nonce.length < 12 || nonce.length > 32) {
            throw new IllegalArgumentException("Invalid runtime package encryption input");
        }
        Properties properties = new Properties();
        if(source != null) properties.putAll(source);
        properties.setProperty("keyId", keyId);
        ByteArrayOutputStream manifestOutput = new ByteArrayOutputStream();
        properties.store(manifestOutput, null);
        byte[] manifest = manifestOutput.toByteArray();
        byte[] keyBytes = keyId.getBytes(StandardCharsets.UTF_8);
        if(keyBytes.length > 256) throw new IllegalArgumentException("keyId is too long");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DataOutputStream header = new DataOutputStream(output);
        header.write(MAGIC);
        header.writeByte(VERSION);
        header.writeShort(keyBytes.length);
        header.writeInt(manifest.length);
        header.writeByte(nonce.length);
        header.write(keyBytes);
        header.write(manifest);
        header.write(nonce);
        header.flush();
        byte[] authenticatedHeader = output.toByteArray();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
        cipher.updateAAD(authenticatedHeader);
        header.write(cipher.doFinal(jar));
        return output.toByteArray();
    }
}
