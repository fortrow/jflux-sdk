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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-08 18:16:33 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.http.api.base;

import com.fortrow.jflux.db.connectors.details.DbConnectionDetails;
import com.fortrow.jflux.files.LogFile;
import com.fortrow.jflux.http.api.base.endpoints.permissions.ApiEndpointPermissions;
import com.fortrow.jflux.http.api.base.models.ApiModel;
import com.fortrow.jflux.http.api.base.models.AppModelCollection;
import com.fortrow.jflux.http.api.base.prereqs.ApiBase;
import com.fortrow.jflux.http.api.base.prereqs.ApiService;
import com.fortrow.jflux.http.api.server.config.APIServerConfig;
import com.fortrow.jflux.http.api.server.exceptions.APIException;
import com.fortrow.jflux.http.api.types.DatabaseServerType;

import java.util.ArrayList;

public abstract class ApiMain {

    private String appName;
    private ApiBase base;

    public ApiMain(String appName) throws APIException {
        this.appName = appName;
        APIServerConfig config = getConfig();
        if(config.getApplicationDbType() != null) {
            config.setApplicationDbDetails(() -> getApplicationDbConnectionDetails(config.getApplicationDbType()));
        }
        if(config.getSessionDbType() != null) {
            config.setSessionDbDetails(() -> getSessionDbConnectionDetails(config.getSessionDbType()));
        }
        ApiService service = buildService();
        this.base = new ApiBase(config, service) { };
        AppModelCollection models = fillModels(new ArrayList<>());
        if(models != null) this.base.setModels(models);
    }

    public void start() throws APIException {
        this.base.start();
    }

    public ApiBase getApi() {
        return base;
    }

    public void setLogFile(LogFile file) {
        this.base.setLogFile(file);
    }

    protected abstract ApiService buildService();
    protected abstract APIServerConfig getConfig();

    protected abstract DbConnectionDetails getSessionDbConnectionDetails(DatabaseServerType sessionDbType) throws APIException;
    protected abstract AppModelCollection fillModels(ArrayList<Class<? extends ApiModel>> models);
    protected abstract ApiEndpointPermissions setEndpointPermissions();

    protected DbConnectionDetails getApplicationDbConnectionDetails(DatabaseServerType applicationDbType) throws APIException {
        return null;
    }

}
