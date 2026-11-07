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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-04 15:27:04 EDT
 */
package com.fortrow.jflux.crypto.adapters;

import com.fortrow.jflux.crypto.adapters.impl.AsymmetricAlgorithmImpl;
import com.fortrow.jflux.exceptions.ExceptionHandling;
import com.fortrow.jflux.files.PathObject;
import com.fortrow.jflux.runtime.lib.JarFileInputStream;
import com.fortrow.jflux.runtime.lib.JarFileObjectInputStream;
import com.fortrow.jflux.runtime.lib.StreamClassLoader;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class AsymmetricAlgorithm extends AsymmetricAlgorithmImpl {

    @FunctionalInterface
    public interface Operation {
        byte[] apply(byte[] input, byte[] key, byte[]... aad);
    }

    @FunctionalInterface
    public interface KeyPairGenerator {
        byte[][] generate();
    }

    private String identifier;
    private int keySize;

    private Method encryptMethod = null;
    private Method decryptMethod = null;
    private Method generatePairMethod = null;
    private Operation encryptor;
    private Operation decryptor;
    private KeyPairGenerator keyPairGenerator;

    public AsymmetricAlgorithm(String identifier, int keySize) {
        this.identifier = identifier;
        this.keySize = keySize;
    }

    public AsymmetricAlgorithm(String identifier, int keySize, Operation encryptor,
                               Operation decryptor, KeyPairGenerator keyPairGenerator) {
        this(identifier, keySize);
        this.encryptor = encryptor;
        this.decryptor = decryptor;
        this.keyPairGenerator = keyPairGenerator;
    }

    public String identifier() { return identifier; }
    public int keySize() { return keySize; }

    public void register(PathObject jarFile, String className) throws Exception {
        JarFileInputStream jarIn = new JarFileInputStream(jarFile.getString());
        JarFileObjectInputStream jarObj = new JarFileObjectInputStream(jarIn);
        StreamClassLoader scl = new StreamClassLoader(jarObj);
        jarObj.close();
        jarIn.close();
        String[] classes = scl.getAllClassNames();
        for(String cl : classes) {
            if(!cl.replace(".class", "").equals(className)) continue;
            Class c = scl.loadClass(cl);
            //TODO set private method varaibles using c.getMethod(...)
        }
    }

    @Override
    public byte[] encrypt(byte[] input, byte[] key, byte[]... aad) {
        if (encryptor != null) return encryptor.apply(input, key, aad);
        if(encryptMethod == null) {
            ExceptionHandling.report(new IllegalStateException("No encrypt operation is registered for " + identifier));
            return new byte[0];
        }
        //TODO make params be an object of known type
        String[] params = new String[]{};
        try {
            return (byte[]) encryptMethod.invoke(null, (Object) params);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw ExceptionHandling.failure("Unable to invoke encrypt operation for " + identifier, e);
        }
    }

    @Override
    public byte[] decrypt(byte[] ciphertext, byte[] key, byte[]... aad) {
        if (decryptor != null) return decryptor.apply(ciphertext, key, aad);
        if(decryptMethod == null) {
            ExceptionHandling.report(new IllegalStateException("No decrypt operation is registered for " + identifier));
            return new byte[0];
        }
        //TODO make params be an object of known type
        String[] params = new String[]{};
        try {
            return (byte[]) decryptMethod.invoke(null, (Object) params);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw ExceptionHandling.failure("Unable to invoke decrypt operation for " + identifier, e);
        }
    }

    @Override
    public byte[][] generateKeyPair(byte[]... params) {
        if (keyPairGenerator != null) return keyPairGenerator.generate();
        if(generatePairMethod == null) {
            ExceptionHandling.report(new IllegalStateException("No key-pair operation is registered for " + identifier));
            return new byte[0][0];
        }
        //TODO make params be an object of known type
        try {
            return (byte[][]) generatePairMethod.invoke(null, (Object) params);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw ExceptionHandling.failure("Unable to invoke key-pair operation for " + identifier, e);
        }
    }
}
