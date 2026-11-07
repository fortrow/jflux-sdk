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

import com.fortrow.jflux.crypto.adapters.impl.HmacAlgorithmImpl;
import com.fortrow.jflux.exceptions.ExceptionHandling;
import com.fortrow.jflux.files.PathObject;
import com.fortrow.jflux.runtime.lib.JarFileInputStream;
import com.fortrow.jflux.runtime.lib.JarFileObjectInputStream;
import com.fortrow.jflux.runtime.lib.StreamClassLoader;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class HmacAlgorithm extends HmacAlgorithmImpl {

    @FunctionalInterface
    public interface Operation {
        byte[] apply(byte[] input, byte[] key);
    }

    private String identifier;
    private int keySize;
    private int outSize;

    private Method hashMethod = null;
    private Operation hasher;

    public HmacAlgorithm(String identifier, int keySize, int outSize) {
        this.identifier = identifier;
        this.keySize = keySize;
        this.outSize = outSize;
    }

    public HmacAlgorithm(String identifier, int keySize, int outSize, Operation hasher) {
        this(identifier, keySize, outSize);
        this.hasher = hasher;
    }

    public String identifier() { return identifier; }
    public int keySize() { return keySize; }
    public int outputSize() { return outSize; }

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
    public byte[] hash(byte[] input, byte[] key) {
        if (hasher != null) return hasher.apply(input, key);
        if(hashMethod == null) {
            ExceptionHandling.report(new IllegalStateException("No HMAC operation is registered for " + identifier));
            return new byte[0];
        }
        //TODO make params be an object of known type
        try {
            return (byte[]) hashMethod.invoke(null, (Object) input, (Object) key);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw ExceptionHandling.failure("Unable to invoke HMAC operation for " + identifier, e);
        }
    }
}
