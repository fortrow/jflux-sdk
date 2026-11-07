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
