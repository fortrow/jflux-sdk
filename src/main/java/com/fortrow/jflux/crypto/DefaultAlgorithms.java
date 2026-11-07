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
package com.fortrow.jflux.crypto;

import com.fortrow.jflux.crypto.adapters.AsymmetricAlgorithm;
import com.fortrow.jflux.crypto.adapters.HashAlgorithm;
import com.fortrow.jflux.crypto.adapters.HmacAlgorithm;
import com.fortrow.jflux.crypto.adapters.KeyEncapsulationAlgorithm;
import com.fortrow.jflux.crypto.adapters.SignatureAlgorithm;
import com.fortrow.jflux.crypto.adapters.SymmetricAlgorithm;
import com.fortrow.jflux.crypto.builtin.AES;
import com.fortrow.jflux.crypto.builtin.ChaCha20Poly1305;
import com.fortrow.jflux.crypto.builtin.MLDSA;
import com.fortrow.jflux.crypto.builtin.MLKEM;
import com.fortrow.jflux.crypto.builtin.RSA;
import com.fortrow.jflux.crypto.builtin.SLHDSA;
import com.fortrow.jflux.crypto.builtin.SHA;
import com.fortrow.jflux.encoding.BaseX;
import com.fortrow.jflux.encoding.Hex;
import com.fortrow.jflux.exceptions.ExceptionHandling;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class DefaultAlgorithms {

    public static SymmetricAlgorithm aesCbc256() {
        return new SymmetricAlgorithm("aes-cbc-256", 16, 32) {
            @Override
            public byte[] encrypt(byte[] input, byte[] key, byte[]... aad) {
                byte[] iv = aad.length > 0 ? aad[0] : new byte[16];
                return AES.cbc(input, key, iv, true);
            }

            @Override
            public byte[] decrypt(byte[] ciphertext, byte[] key, byte[]... aad) {
                byte[] iv = aad.length > 0 ? aad[0] : new byte[16];
                return AES.cbc(ciphertext, key, iv, false);
            }
        };
    }

    public static SymmetricAlgorithm aesCbc128() {
        return new SymmetricAlgorithm("aes-cbc-128", 16, 16) {
            @Override
            public byte[] encrypt(byte[] input, byte[] key, byte[]... aad) {
                byte[] iv = new byte[16];
                if(aad.length > 0) iv = aad[0];
                return AES.cbc(input, key, iv, true);
            }

            @Override
            public byte[] decrypt(byte[] ciphertext, byte[] key, byte[]... aad) {
                byte[] iv = new byte[16];
                if(aad.length > 0) iv = aad[0];
                return AES.cbc(ciphertext, key, iv, false);
            }
        };
    }

    public static SymmetricAlgorithm aesGcm256() {
        return new SymmetricAlgorithm("aes-gcm-256", 16, 32) {
            @Override
            public byte[] encrypt(byte[] input, byte[] key, byte[]... aad) {
                byte[] iv = new byte[16];
                if(aad.length > 0) iv = aad[0];
                byte[] _aad = new byte[0];
                if(aad.length > 1) _aad = aad[1];
                return AES.gcm(input, key, iv, _aad, true);
            }

            @Override
            public byte[] decrypt(byte[] ciphertext, byte[] key, byte[]... aad) {
                byte[] iv = new byte[16];
                if(aad.length > 0) iv = aad[0];
                byte[] _aad = new byte[0];
                if(aad.length > 1) _aad = aad[1];
                return AES.gcm(ciphertext, key, iv, _aad,false);
            }
        };
    }

    public static SymmetricAlgorithm aesGcm128() {
        return new SymmetricAlgorithm("aes-gcm-128", 16, 16) {
            @Override
            public byte[] encrypt(byte[] input, byte[] key, byte[]... aad) {
                byte[] iv = new byte[16];
                if(aad.length > 0) iv = aad[0];
                byte[] _aad = new byte[0];
                if(aad.length > 1) _aad = aad[1];
                return AES.gcm(input, key, iv, _aad, true);
            }

            @Override
            public byte[] decrypt(byte[] ciphertext, byte[] key, byte[]... aad) {
                byte[] iv = new byte[16];
                if(aad.length > 0) iv = aad[0];
                byte[] _aad = new byte[0];
                if(aad.length > 1) _aad = aad[1];
                return AES.gcm(ciphertext, key, iv, _aad,false);
            }
        };
    }

    public static SymmetricAlgorithm aesEcb256() {
        return new SymmetricAlgorithm("aes-ecb-256", 16, 32) {
            @Override
            public byte[] encrypt(byte[] input, byte[] key, byte[]... aad) {
                return AES.ecb(input, key,true);
            }

            @Override
            public byte[] decrypt(byte[] ciphertext, byte[] key, byte[]... aad) {
                return AES.ecb(ciphertext, key, false);
            }
        };
    }

    public static SymmetricAlgorithm aesEcb128() {
        return new SymmetricAlgorithm("aes-ecb-128", 16, 16) {
            @Override
            public byte[] encrypt(byte[] input, byte[] key, byte[]... aad) {
                return AES.ecb(input, key,true);
            }

            @Override
            public byte[] decrypt(byte[] ciphertext, byte[] key, byte[]... aad) {
                return AES.ecb(ciphertext, key, false);
            }
        };
    }

    public static AsymmetricAlgorithm rsa() {
        return rsa(4096);
    }

    public static AsymmetricAlgorithm rsa(int bitSize) {
        return new AsymmetricAlgorithm("rsa-" + bitSize, bitSize) {
            @Override
            public byte[] encrypt(byte[] input, byte[] key, byte[]... aad) {
                return BaseX.bytesFrom64(RSA.encrypt(input, RSA.publicKeyFromBytes(key)));
            }

            @Override
            public byte[] decrypt(byte[] ciphertext, byte[] key, byte[]... aad) {
                return BaseX.bytesFrom64(RSA.decrypt(ciphertext, RSA.privateKeyFromBytes(key)));
            }

            @Override
            public byte[][] generateKeyPair(byte[]... params) {
                KeyPair kp = RSA.generateKeyPair(bitSize);
                byte[] publicKey = kp.getPublic().getEncoded();
                byte[] privateKey = kp.getPrivate().getEncoded();
                return new byte[][]{publicKey, privateKey};
            }
        };
    }

    public static HashAlgorithm sha512() {
        return new HashAlgorithm("sha3-512", 64) {
            @Override
            public byte[] hash(byte[] input) {
                return Hex.decode(SHA.get512(input));
            }
        };
    }

    public static HashAlgorithm sha384() {
        return new HashAlgorithm("sha3-384", 48) {
            @Override
            public byte[] hash(byte[] input) {
                return Hex.decode(SHA.get384(input));
            }
        };
    }

    public static HashAlgorithm sha256() {
        return new HashAlgorithm("sha3-256", 32) {
            @Override
            public byte[] hash(byte[] input) {
                return Hex.decode(SHA.get256(input));
            }
        };
    }

    public static HashAlgorithm sha1() {
        return new HashAlgorithm("sha1", 20) {
            @Override
            public byte[] hash(byte[] input) {
                return Hex.decode(SHA.getSha1(input));
            }
        };
    }

    public static HmacAlgorithm hmac512() {
        return new HmacAlgorithm("hmac-sha3-512", 64, 64) {
            @Override
            public byte[] hash(byte[] input, byte[] key) {
                return hmac("HmacSHA512", input, key);
            }
        };
    }

    public static HmacAlgorithm hmac384() {
        return new HmacAlgorithm("hmac-sha3-384", 48, 48) {
            @Override
            public byte[] hash(byte[] input, byte[] key) {
                return hmac("HmacSHA384", input, key);
            }
        };
    }

    public static HmacAlgorithm hmac256() {
        return new HmacAlgorithm("hmac-sha3-256", 32, 32) {
            @Override
            public byte[] hash(byte[] input, byte[] key) {
                return hmac("HmacSHA256", input, key);
            }
        };
    }

    public static HmacAlgorithm hmacSha1() {
        return new HmacAlgorithm("hmac-sha1", 20, 20) {
            @Override
            public byte[] hash(byte[] input, byte[] key) {
                return hmac("HmacSHA1", input, key);
            }
        };
    }

    public static SignatureAlgorithm ed25519() {
        return new SignatureAlgorithm(
                "ed25519",
                () -> {
                    try {
                        KeyPair pair = java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
                        return new byte[][]{pair.getPublic().getEncoded(), pair.getPrivate().getEncoded()};
                    } catch (Exception failure) {
                        throw ExceptionHandling.failure("Unable to generate Ed25519 key pair", failure);
                    }
                },
                (input, privateBytes) -> {
                    try {
                        PrivateKey privateKey = KeyFactory.getInstance("Ed25519")
                                .generatePrivate(new PKCS8EncodedKeySpec(privateBytes));
                        Signature signature = Signature.getInstance("Ed25519");
                        signature.initSign(privateKey);
                        signature.update(input);
                        return signature.sign();
                    } catch (Exception failure) {
                        throw ExceptionHandling.failure("Unable to sign Ed25519 payload", failure);
                    }
                },
                (input, signatureBytes, publicBytes) -> {
                    try {
                        PublicKey publicKey = KeyFactory.getInstance("Ed25519")
                                .generatePublic(new X509EncodedKeySpec(publicBytes));
                        Signature signature = Signature.getInstance("Ed25519");
                        signature.initVerify(publicKey);
                        signature.update(input);
                        return signature.verify(signatureBytes);
                    } catch (Exception failure) {
                        ExceptionHandling.report(failure);
                        return false;
                    }
                });
    }

    public static SymmetricAlgorithm chacha20Poly1305() {
        return new SymmetricAlgorithm("chacha20-poly1305", 12, 32,
                (input, key, aad) -> ChaCha20Poly1305.encrypt(input, key, context(aad, 0), context(aad, 1)),
                (input, key, aad) -> ChaCha20Poly1305.decrypt(input, key, context(aad, 0), context(aad, 1)));
    }

    public static KeyEncapsulationAlgorithm mlKem512() { return MLKEM.adapter("ml-kem-512"); }
    public static KeyEncapsulationAlgorithm mlKem768() { return MLKEM.adapter("ml-kem-768"); }
    public static KeyEncapsulationAlgorithm mlKem1024() { return MLKEM.adapter("ml-kem-1024"); }

    public static SignatureAlgorithm mlDsa44() { return MLDSA.adapter("ml-dsa-44"); }
    public static SignatureAlgorithm mlDsa65() { return MLDSA.adapter("ml-dsa-65"); }
    public static SignatureAlgorithm mlDsa87() { return MLDSA.adapter("ml-dsa-87"); }

    public static SignatureAlgorithm slhDsaSha2_128s() { return SLHDSA.adapter("slh-dsa-sha2-128s"); }
    public static SignatureAlgorithm slhDsaSha2_128f() { return SLHDSA.adapter("slh-dsa-sha2-128f"); }
    public static SignatureAlgorithm slhDsaSha2_192s() { return SLHDSA.adapter("slh-dsa-sha2-192s"); }
    public static SignatureAlgorithm slhDsaSha2_192f() { return SLHDSA.adapter("slh-dsa-sha2-192f"); }
    public static SignatureAlgorithm slhDsaSha2_256s() { return SLHDSA.adapter("slh-dsa-sha2-256s"); }
    public static SignatureAlgorithm slhDsaSha2_256f() { return SLHDSA.adapter("slh-dsa-sha2-256f"); }

    private static byte[] context(byte[][] values, int index) {
        return values.length > index ? values[index] : null;
    }

    private static byte[] hmac(String algorithm, byte[] input, byte[] key) {
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(key, algorithm));
            return mac.doFinal(input);
        } catch (GeneralSecurityException e) {
            throw ExceptionHandling.failure("Unable to run " + algorithm, e);
        }
    }

}
