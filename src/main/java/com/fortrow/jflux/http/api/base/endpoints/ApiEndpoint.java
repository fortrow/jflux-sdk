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
package com.fortrow.jflux.http.api.base.endpoints;

import com.fortrow.jflux.http.api.base.prereqs.ApiBase;
import com.fortrow.jflux.http.api.server.containers.APIResponse;

public abstract class ApiEndpoint extends ApiResource {

    public ApiEndpoint(int index, String path, ApiBase api) {
        super(index, path, EndpointType.ENDPOINT, api);
    }

    @Override
    public APIResponse index(InboundRequest request) throws Exception {
        return get(request);
    }

    @Override
    public APIResponse update(InboundRequest request) throws Exception {
        String method = request == null ? "PUT" : request.getResourceMethod();
        return "PATCH".equalsIgnoreCase(method) ? patch(request) : put(request);
    }

    @Override
    public APIResponse create(InboundRequest request) throws Exception {
        return post(request);
    }

    @Override
    public APIResponse delete(InboundRequest request) throws Exception {
        return del(request);
    }

    public APIResponse get(InboundRequest request) throws Exception { return unsupported(request, "GET"); }
    public APIResponse put(InboundRequest request) throws Exception { return unsupported(request, "PUT"); }
    public APIResponse patch(InboundRequest request) throws Exception { return put(request); }
    public APIResponse post(InboundRequest request) throws Exception { return unsupported(request, "POST"); }
    public APIResponse del(InboundRequest request) throws Exception { return unsupported(request, "DELETE"); }

}
