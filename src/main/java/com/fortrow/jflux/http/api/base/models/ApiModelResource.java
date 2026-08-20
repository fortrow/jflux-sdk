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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-11 20:44:14 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.http.api.base.models;

import com.fortrow.jflux.http.api.base.endpoints.ApiResource;
import com.fortrow.jflux.http.api.base.endpoints.InboundRequest;
import com.fortrow.jflux.http.api.base.endpoints.expectations.ApiExpectations;
import com.fortrow.jflux.http.api.base.endpoints.permissions.ApiEndpointPermissions;
import com.fortrow.jflux.http.api.base.prereqs.ApiBase;
import com.fortrow.jflux.http.api.server.containers.APIResponse;

public class ApiModelResource extends ApiResource {

    private final ApiModel model;
    private final ApiBase api;

    public ApiModelResource(int index, ApiModel model, ApiBase api) {
        super(index, model == null ? "" : model.getResourcePath(), EndpointType.MODEL_RESOURCE, api);
        this.model = model;
        this.api = api;
    }

    public ApiModel getModel() {
        return model;
    }

    @Override
    protected ApiExpectations expectations() {
        return model == null ? ApiExpectations.none() : model.getExpectations();
    }

    @Override
    protected ApiEndpointPermissions permissions() {
        return model == null ? ApiEndpointPermissions.none() : model.getPermissions();
    }

    @Override
    public APIResponse index(InboundRequest request) throws Exception {
        return api.executeModelResource(model, request, "GET");
    }

    @Override
    public APIResponse create(InboundRequest request) throws Exception {
        return api.executeModelResource(model, request, "POST");
    }

    @Override
    public APIResponse update(InboundRequest request) throws Exception {
        String method = request == null ? "PUT" : request.getResourceMethod();
        return api.executeModelResource(model, request, "PATCH".equalsIgnoreCase(method) ? "PATCH" : "PUT");
    }

    @Override
    public APIResponse delete(InboundRequest request) throws Exception {
        return api.executeModelResource(model, request, "DELETE");
    }
}
