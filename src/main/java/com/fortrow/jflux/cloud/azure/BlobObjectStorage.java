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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-05 12:55:15 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.cloud.azure;

import com.azure.core.credential.TokenCredential;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.fortrow.jflux.cloud.ObjectStorageDriver;

public class BlobObjectStorage extends ObjectStorageDriver {

    public BlobObjectStorage(BlobServiceClient client) {
        super(client);
    }

    public BlobObjectStorage(String connectionString) {
        this(new BlobServiceClientBuilder().connectionString(connectionString).buildClient());
    }

    public BlobObjectStorage(String accountUrl, TokenCredential credential) {
        this(new BlobServiceClientBuilder().endpoint(accountUrl).credential(credential).buildClient());
    }
}
