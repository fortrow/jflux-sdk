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
 *
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-07 19:19:56 EDT
 */
package com.fortrow.jflux.http.api.tests.apihub.api;

import com.fortrow.jflux.db.connectors.details.DbConnectionDetails;
import com.fortrow.jflux.http.api.base.ApiMain;
import com.fortrow.jflux.http.api.base.endpoints.permissions.ApiEndpointPermissions;
import com.fortrow.jflux.http.api.base.models.ApiModel;
import com.fortrow.jflux.http.api.base.models.AppModelCollection;
import com.fortrow.jflux.http.api.base.prereqs.ApiService;
import com.fortrow.jflux.http.api.server.config.APIServerConfig;
import com.fortrow.jflux.http.api.server.exceptions.APIException;
import com.fortrow.jflux.http.api.server.types.APIServerType;
import com.fortrow.jflux.http.api.server.types.CORSPolicy;
import com.fortrow.jflux.http.api.types.DatabaseServerType;
import com.fortrow.jflux.ssl.SslCertKeyPair;
import com.fortrow.jflux.ssl.keystore.JavaKeyStore;
import com.fortrow.jflux.ssl.selfsigned.SelfSignedSSL;

import java.util.ArrayList;

public class AcmeApi {

    private ApiMain api;

    public AcmeApi() {

        try {

            this.api = new ApiMain("Acme") {
                @Override
                protected ApiService buildService() {
                    return null;
                }

                @Override
                protected APIServerConfig getConfig() {
                    APIServerConfig config = new APIServerConfig("127.0.0.1", 8181, "/api");
                    config.setServerType(APIServerType.ENCRYPTED_SYNC);
                    config.setCorsPolicy(new CORSPolicy(true));
                    config.setRateLimit(25);
                    config.useDynamicApiKey(true);

                    SslCertKeyPair selfSigned = SelfSignedSSL.create("/tmp", "testkey", "US", "Waynesboro", "Virginia", "Emphic, LLC", "IT", "localhost");
                    JavaKeyStore keystore = new JavaKeyStore(selfSigned, "v1.3");
                    config.setApiServerSslContext(keystore.ssl());

                    return config;
                }

                @Override
                protected DbConnectionDetails getSessionDbConnectionDetails(DatabaseServerType sessionDbType) throws APIException {
                    throw new APIException("Acme API session database details are not configured.");
                }

                @Override
                protected AppModelCollection fillModels(ArrayList<Class<? extends ApiModel>> models) {
                    return null;
                }

                @Override
                protected ApiEndpointPermissions setEndpointPermissions() {
                    return null;
                }
            };
            this.api.start();

        } catch (APIException e) {
            e.printStackTrace();
        }

    }

}
