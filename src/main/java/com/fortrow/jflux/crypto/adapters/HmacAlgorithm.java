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
