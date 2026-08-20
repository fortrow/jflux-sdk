/*
 * -------------------------------------------------------------------------
 *
 * Copyright (c) 2026 Fortrow LLC. All rights reserved.
 *
 * -------------------------------------------------------------------------
 */
package com.fortrow.jflux.cloud.aws;

import com.fortrow.jflux.cloud.ObjectStorageDriver;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/** AWS S3 object-storage adapter. */
public class S3ObjectStorage extends ObjectStorageDriver {

    public S3ObjectStorage(S3Client client) {
        super(client);
    }

    public S3ObjectStorage(Region region) {
        this(S3Client.builder().region(region).build());
    }

    public S3ObjectStorage(AWSRawCredentials credentials) {
        this(S3Client.builder()
                .region(credentials.getRegion())
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(credentials.getKeyId(), credentials.getAccessKey())))
                .build());
    }
}
