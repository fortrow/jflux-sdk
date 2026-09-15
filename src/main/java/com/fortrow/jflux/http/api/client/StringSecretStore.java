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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-11 19:05:33 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.http.api.client;

public class StringSecretStore implements SecretStore {
    private String value;

    public StringSecretStore(String value) {
        this.value = value;
    }

    @Override
    public String read() {
        return value;
    }

    @Override
    public void write(String value) {
        this.value = value;
    }

    @Override
    public void clear() {
        this.value = "";
    }
}
