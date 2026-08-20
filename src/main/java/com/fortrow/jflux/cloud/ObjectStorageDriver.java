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
package com.fortrow.jflux.cloud;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobServiceClient;
import com.fortrow.jflux.cloud.aws.AWSRawCredentials;
import com.fortrow.jflux.exceptions.types.CommonIOException;
import com.fortrow.jflux.files.FileObject;
import com.fortrow.jflux.files.Files;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.IOException;
import java.nio.file.Path;

public class ObjectStorageDriver {

    private final S3Client s3Client;
    private final BlobServiceClient blobServiceClient;
    private final Storage googleStorage;
    private final Provider provider;

    protected ObjectStorageDriver() {
        this.s3Client = null;
        this.blobServiceClient = null;
        this.googleStorage = null;
        this.provider = Provider.DISK;
    }

    public ObjectStorageDriver(S3Client client) {
        this.s3Client = client;
        this.blobServiceClient = null;
        this.googleStorage = null;
        this.provider = Provider.AWS_S3;
    }

    public ObjectStorageDriver(Region region) {
        this(S3Client.builder().region(region).build());
    }

    public ObjectStorageDriver(AWSRawCredentials credentials) {
        this(S3Client.builder()
                .region(credentials.getRegion())
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(credentials.getKeyId(), credentials.getAccessKey())))
                .build());
    }

    public ObjectStorageDriver(BlobServiceClient client) {
        this.s3Client = null;
        this.blobServiceClient = client;
        this.googleStorage = null;
        this.provider = Provider.AZURE_BLOB;
    }

    public ObjectStorageDriver(Storage client) {
        this.s3Client = null;
        this.blobServiceClient = null;
        this.googleStorage = client;
        this.provider = Provider.GOOGLE_CLOUD_STORAGE;
    }

    public Provider getProvider() {
        return provider;
    }

    public void put(String containerName, String objectName, FileObject sourceFile) throws IOException {
        put(containerName, objectName, sourceFile.getFile().toPath());
    }

    public void put(String containerName, String objectName, Path sourceFile) throws IOException {
        switch(provider) {
            case AWS_S3:
                s3Client.putObject(request -> request.bucket(containerName).key(objectName), RequestBody.fromFile(sourceFile));
                return;
            case AZURE_BLOB:
                getBlobClient(containerName, objectName).uploadFromFile(sourceFile.toString(), true);
                return;
            case GOOGLE_CLOUD_STORAGE:
                googleStorage.createFrom(BlobInfo.newBuilder(BlobId.of(containerName, objectName)).build(), sourceFile);
                return;
            case DISK:
            default:
                throw new UnsupportedOperationException("DISK object storage must override put().");
        }
    }

    public FileObject get(String containerName, String objectName) throws IOException, CommonIOException {
        FileObject destinationFile = Files.createTmpFile();
        switch(provider) {
            case AWS_S3:
                s3Client.getObject(request -> request.bucket(containerName).key(objectName),
                        ResponseTransformer.toFile(destinationFile.getFile()));
                break;
            case AZURE_BLOB:
                getBlobClient(containerName, objectName).downloadToFile(destinationFile.getFile().getAbsolutePath(), true);
                break;
            case GOOGLE_CLOUD_STORAGE:
                byte[] content = googleStorage.readAllBytes(BlobId.of(containerName, objectName));
                java.nio.file.Files.write(destinationFile.getFile().toPath(), content);
                break;
            case DISK:
            default:
                throw new UnsupportedOperationException("DISK object storage must override get().");
        }
        destinationFile.readToEnd();
        return destinationFile;
    }

    public void putFile(String containerName, String objectName, Path sourceFile) throws IOException {
        put(containerName, objectName, sourceFile);
    }

    public void putFile(String containerName, String objectName, FileObject sourceFile) throws IOException {
        put(containerName, objectName, sourceFile);
    }

    public FileObject getFile(String containerName, String objectName) throws IOException, CommonIOException {
        return get(containerName, objectName);
    }

    private BlobClient getBlobClient(String containerName, String objectName) {
        return blobServiceClient.getBlobContainerClient(containerName).getBlobClient(objectName);
    }

    public enum Provider {
        AWS_S3,
        AZURE_BLOB,
        GOOGLE_CLOUD_STORAGE,
        DISK,
    }
}
