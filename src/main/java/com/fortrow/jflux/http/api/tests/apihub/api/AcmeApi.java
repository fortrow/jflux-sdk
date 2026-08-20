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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-07 19:19:56 EDT
 * 
 * -------------------------------------------------------------------------

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
