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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-04 15:27:04 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.crypto;

import com.fortrow.jflux.crypto.adapters.AsymmetricAlgorithm;
import com.fortrow.jflux.crypto.adapters.HashAlgorithm;
import com.fortrow.jflux.crypto.adapters.HmacAlgorithm;
import com.fortrow.jflux.crypto.adapters.KeyEncapsulationAlgorithm;
import com.fortrow.jflux.crypto.adapters.EncapsulatedSecret;
import com.fortrow.jflux.crypto.adapters.SignatureAlgorithm;
import com.fortrow.jflux.crypto.adapters.SymmetricAlgorithm;
import com.fortrow.jflux.exceptions.ExceptionHandling;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class CryptographyRegister {

    private HashMap<String, SymmetricAlgorithm> symmetricMap;
    private HashMap<String, AsymmetricAlgorithm> asymmetricMap;
    private HashMap<String, HashAlgorithm> hashMap;
    private HashMap<String, HmacAlgorithm> hmacMap;
    private HashMap<String, KeyEncapsulationAlgorithm> kemMap;
    private HashMap<String, SignatureAlgorithm> signatureMap;

    public CryptographyRegister() {
        symmetricMap = new HashMap<>();
        asymmetricMap = new HashMap<>();
        hashMap = new HashMap<>();
        hmacMap = new HashMap<>();
        kemMap = new HashMap<>();
        signatureMap = new HashMap<>();
    }

    public static CryptographyRegister fromDefaults() {
        CryptographyRegister register = new CryptographyRegister();
        register.register("aes-cbc-256", DefaultAlgorithms.aesCbc256());
        register.register("aes-cbc-128", DefaultAlgorithms.aesCbc128());
        register.register("aes-gcm-256", DefaultAlgorithms.aesGcm256());
        register.register("aes-gcm-128", DefaultAlgorithms.aesGcm128());
        register.register("chacha20-poly1305", DefaultAlgorithms.chacha20Poly1305());
        register.register("aes-ecb-256", DefaultAlgorithms.aesEcb256());
        register.register("aes-ecb-128", DefaultAlgorithms.aesEcb128());
        register.register("sha3-512", DefaultAlgorithms.sha512());
        register.register("sha3-384", DefaultAlgorithms.sha384());
        register.register("sha3-256", DefaultAlgorithms.sha256());
        register.register("sha1", DefaultAlgorithms.sha1());
        register.register("hmac-sha3-512", DefaultAlgorithms.hmac512());
        register.register("hmac-sha3-384", DefaultAlgorithms.hmac384());
        register.register("hmac-sha3-256", DefaultAlgorithms.hmac256());
        register.register("hmac-sha1", DefaultAlgorithms.hmacSha1());
        register.register("ed25519", DefaultAlgorithms.ed25519());
        register.register("rsa-2048", DefaultAlgorithms.rsa(2048));
        register.register("rsa-4096", DefaultAlgorithms.rsa());
        if (PqcAvailability.available("org.bouncycastle.pqc.crypto.mlkem.MLKEMParameters")) {
            register.register("ml-kem-512", DefaultAlgorithms.mlKem512());
            register.register("ml-kem-768", DefaultAlgorithms.mlKem768());
            register.register("ml-kem-1024", DefaultAlgorithms.mlKem1024());
        }
        if (PqcAvailability.available("org.bouncycastle.pqc.crypto.mldsa.MLDSAParameters")) {
            register.register("ml-dsa-44", DefaultAlgorithms.mlDsa44());
            register.register("ml-dsa-65", DefaultAlgorithms.mlDsa65());
            register.register("ml-dsa-87", DefaultAlgorithms.mlDsa87());
        }
        if (PqcAvailability.available("org.bouncycastle.pqc.crypto.slhdsa.SLHDSAParameters")) {
            register.register("slh-dsa-sha2-128s", DefaultAlgorithms.slhDsaSha2_128s());
            register.register("slh-dsa-sha2-128f", DefaultAlgorithms.slhDsaSha2_128f());
            register.register("slh-dsa-sha2-192s", DefaultAlgorithms.slhDsaSha2_192s());
            register.register("slh-dsa-sha2-192f", DefaultAlgorithms.slhDsaSha2_192f());
            register.register("slh-dsa-sha2-256s", DefaultAlgorithms.slhDsaSha2_256s());
            register.register("slh-dsa-sha2-256f", DefaultAlgorithms.slhDsaSha2_256f());
        }
        return register;
    }

    public void register(String name, SymmetricAlgorithm algo) {
        symmetricMap.put(normalize(name), algo);
    }

    public void register(String name, AsymmetricAlgorithm algo) {
        asymmetricMap.put(normalize(name), algo);
    }

    public void register(String name, HashAlgorithm algo) {
        hashMap.put(normalize(name), algo);
    }

    public void register(String name, HmacAlgorithm algo) {
        hmacMap.put(normalize(name), algo);
    }

    public void register(String name, KeyEncapsulationAlgorithm algo) {
        kemMap.put(normalize(name), algo);
    }

    public void register(String name, SignatureAlgorithm algo) {
        signatureMap.put(normalize(name), algo);
    }

    public byte[] encrypt(String name, byte[] input, byte[] key, byte[]...aad) {
        SymmetricAlgorithm symmetric = symmetricMap.get(normalize(name));
        if (symmetric != null) return symmetric.encrypt(input, key, aad);
        AsymmetricAlgorithm asymmetric = asymmetricMap.get(normalize(name));
        if (asymmetric != null) return asymmetric.encrypt(input, key, aad);
        throw unknown(name);
    }

    public byte[] decrypt(String name, byte[] input, byte[] key, byte[]...aad) {
        SymmetricAlgorithm symmetric = symmetricMap.get(normalize(name));
        if (symmetric != null) return symmetric.decrypt(input, key, aad);
        AsymmetricAlgorithm asymmetric = asymmetricMap.get(normalize(name));
        if (asymmetric != null) return asymmetric.decrypt(input, key, aad);
        throw unknown(name);
    }

    public byte[] hash(String name, byte[] input) {
        HashAlgorithm hash = hashMap.get(normalize(name));
        if (hash != null) return hash.hash(input);
        throw unknown(name);
    }

    public byte[] hmac(String name, byte[] input, byte[] key) {
        HmacAlgorithm hmac = hmacMap.get(normalize(name));
        if (hmac != null) return hmac.hash(input, key);
        throw unknown(name);
    }

    public EncapsulatedSecret encapsulate(String name, byte[] publicKey) {
        KeyEncapsulationAlgorithm kem = kemMap.get(normalize(name));
        if (kem == null) throw unknown(name);
        return kem.encapsulate(publicKey);
    }

    public byte[] decapsulate(String name, byte[] encapsulation, byte[] privateKey) {
        KeyEncapsulationAlgorithm kem = kemMap.get(normalize(name));
        if (kem == null) throw unknown(name);
        return kem.decapsulate(encapsulation, privateKey);
    }

    public byte[][] generateKeyPair(String name) {
        String normalized = normalize(name);
        AsymmetricAlgorithm asymmetric = asymmetricMap.get(normalized);
        if (asymmetric != null) return asymmetric.generateKeyPair();
        KeyEncapsulationAlgorithm kem = kemMap.get(normalized);
        if (kem != null) return kem.generateKeyPair();
        SignatureAlgorithm signature = signatureMap.get(normalized);
        if (signature != null) return signature.generateKeyPair();
        throw unknown(name);
    }

    public byte[] sign(String name, byte[] input, byte[] privateKey) {
        SignatureAlgorithm signature = signatureMap.get(normalize(name));
        if (signature == null) throw unknown(name);
        return signature.sign(input, privateKey);
    }

    public boolean verify(String name, byte[] input, byte[] signatureBytes, byte[] publicKey) {
        SignatureAlgorithm signature = signatureMap.get(normalize(name));
        if (signature == null) throw unknown(name);
        return signature.verify(input, signatureBytes, publicKey);
    }

    public Map<String, SymmetricAlgorithm> symmetricAlgorithms() {
        return Collections.unmodifiableMap(symmetricMap);
    }

    public Map<String, KeyEncapsulationAlgorithm> keyEncapsulationAlgorithms() {
        return Collections.unmodifiableMap(kemMap);
    }

    public Map<String, SignatureAlgorithm> signatureAlgorithms() {
        return Collections.unmodifiableMap(signatureMap);
    }

    private static String normalize(String name) {
        if (name == null || name.isBlank()) {
            throw ExceptionHandling.invalid("Cryptographic algorithm name must not be blank");
        }
        return name.toLowerCase(Locale.ROOT);
    }

    private static IllegalArgumentException unknown(String name) {
        return ExceptionHandling.invalid("Unknown cryptographic algorithm: " + name);
    }

}
