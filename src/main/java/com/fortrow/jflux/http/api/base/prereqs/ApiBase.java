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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-04 15:27:04 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.http.api.base.prereqs;

import com.fortrow.jflux.http.api.base.endpoints.AppEndpointCollection;
import com.fortrow.jflux.http.api.base.endpoints.ApiResource;
import com.fortrow.jflux.http.api.base.endpoints.InboundRequest;
import com.fortrow.jflux.http.api.base.models.ApiModel;
import com.fortrow.jflux.http.api.base.models.AppModelCollection;
import com.fortrow.jflux.http.api.server.containers.APIResponse;
import com.fortrow.jflux.http.api.server.API;
import com.fortrow.jflux.http.api.server.config.APIServerConfig;
import com.fortrow.jflux.http.api.server.encryption.APIKeys;
import com.fortrow.jflux.http.api.server.exceptions.APIException;

public abstract class ApiBase extends API {

    private final AppEndpointCollection endpoints = new AppEndpointCollection();
    private AppModelCollection models = new AppModelCollection();

    public ApiBase(APIServerConfig config, ApiService service) throws APIException {
        super(config, service);
    }

    public <T extends ApiResource> T addEndpoint(T endpoint) {
        if(endpoint == null) return null;
        endpoints.putEndpoint(endpoint.getPath(), endpoint);
        endpoint.register(this);
        return endpoint;
    }

    public ApiResource getEndpoint(String path) {
        return endpoints.getEndpoint(path);
    }

    public void setModels(AppModelCollection models) {
        this.models = models == null ? new AppModelCollection() : models;
        this.models.bind(service);
        if(service != null) service.setModels(this.models);
        this.models.register(this);
    }

    public AppModelCollection getModels() {
        return models;
    }

    public APIResponse executeModelResource(ApiModel model, InboundRequest request, String method) throws Exception {
        if(service == null) return modelResourceNotConfigured(request);
        return service.executeModelResource(model, request, method);
    }

    private APIResponse modelResourceNotConfigured(InboundRequest request) {
        return new APIResponse(request == null ? null : request.getSession(), com.fortrow.jflux.http.api.server.containers.ResponseCode.SERVER_ERROR, "API model resources require an ApiService.");
    }

    public APIResponse validateEndpointRequest(ApiResource resource, InboundRequest request) {
        if(service != null) return service.validateEndpointRequest(resource, request);
        return resource.validateRequest(request);
    }

    @Override
    public void updateSessionSecret(APIKeys keys, String sessionSecret) throws APIException {
        if(service != null) {
            service.updateSessionSecret(keys, sessionSecret);
        }
    }

}
