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
package com.fortrow.jflux.cloud.gcloud;

import com.fortrow.jflux.cloud.ObjectStorageDriver;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

public class GoogleCloudObjectStorage extends ObjectStorageDriver {

    public GoogleCloudObjectStorage(Storage client) {
        super(client);
    }

    public GoogleCloudObjectStorage() {
        this(StorageOptions.getDefaultInstance().getService());
    }
}
