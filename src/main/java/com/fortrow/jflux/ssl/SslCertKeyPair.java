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
package com.fortrow.jflux.ssl;


import java.io.File;
import java.util.Objects;

/** Files and credentials used to build a Java TLS context. */
public final class SslCertKeyPair {

    private final File key;
    private final File cert;
    private final String password;

    public SslCertKeyPair(File key, File cert) {
        this(key, cert, "");
    }

    public SslCertKeyPair(File key, File cert, String password) {
        this.key = Objects.requireNonNull(key, "Private key file is required.");
        this.cert = Objects.requireNonNull(cert, "Certificate file is required.");
        this.password = password == null ? "" : password;
    }

    public File getKey() {
        return key;
    }

    public File getCert() {
        return cert;
    }

    public String getPassword() {
        return password;
    }
}
