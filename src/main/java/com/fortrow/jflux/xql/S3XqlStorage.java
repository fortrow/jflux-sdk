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
 */

package com.fortrow.jflux.xql;

import com.fortrow.jflux.exceptions.types.CommonIOException;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.nio.charset.StandardCharsets;

/** Amazon S3-backed XQL storage. Credentials use the AWS SDK default provider chain. */
public final class S3XqlStorage implements XqlStorage, AutoCloseable {
    private final String bucket;
    private final S3Client client;
    private final boolean closeClient;

    public S3XqlStorage(String bucket, S3Client client) {
        if (bucket == null || bucket.isBlank()) throw new IllegalArgumentException("S3 bucket is required.");
        if (client == null) throw new IllegalArgumentException("S3 client is required.");
        this.bucket = bucket;
        this.client = client;
        this.closeClient = false;
    }

    public S3XqlStorage(String bucket, software.amazon.awssdk.regions.Region region) {
        if (bucket == null || bucket.isBlank()) throw new IllegalArgumentException("S3 bucket is required.");
        this.bucket = bucket;
        this.client = S3Client.builder().region(region).build();
        this.closeClient = true;
    }

    @Override
    public void put(String key, String content) throws CommonIOException {
        requireKey(key);
        try {
            client.putObject(request -> request.bucket(bucket).key(key).contentType("application/xml; charset=utf-8"),
                    RequestBody.fromString(content == null ? "" : content, StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            throw new CommonIOException("Unable to write S3 XQL object: " + key, e);
        }
    }

    @Override
    public String get(String key) throws CommonIOException {
        requireKey(key);
        try {
            ResponseBytes<GetObjectResponse> result = client.getObject(
                    request -> request.bucket(bucket).key(key), ResponseTransformer.toBytes());
            return result.asString(StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            throw new CommonIOException("Unable to read S3 XQL object: " + key, e);
        }
    }

    @Override
    public void close() {
        if (closeClient) client.close();
    }

    private static void requireKey(String key) throws CommonIOException {
        if (key == null || key.isBlank() || key.startsWith("/") || key.contains("..")) {
            throw new CommonIOException("Invalid S3 XQL object key.");
        }
    }
}
