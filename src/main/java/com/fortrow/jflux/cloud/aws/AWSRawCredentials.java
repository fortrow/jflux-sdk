/*
 * -------------------------------------------------------------------------
 *
 * Copyright (c) 2026 Fortrow LLC. All rights reserved.
 *
 * -------------------------------------------------------------------------
 */
package com.fortrow.jflux.cloud.aws;

import software.amazon.awssdk.regions.Region;

/** Raw AWS credentials used when constructing an S3 object-storage client. */
public final class AWSRawCredentials {

    private final String keyId;
    private final String accessKey;
    private final Region region;

    public AWSRawCredentials(String keyId, String accessKey, Region region) {
        this.keyId = keyId;
        this.accessKey = accessKey;
        this.region = region;
    }

    public String getKeyId() {
        return keyId;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public Region getRegion() {
        return region;
    }
}
