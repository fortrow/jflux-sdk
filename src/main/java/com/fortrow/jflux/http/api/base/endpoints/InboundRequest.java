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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-07 20:17:03 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.http.api.base.endpoints;

import com.fortrow.jflux.http.api.server.containers.ClientSession;
import com.fortrow.jflux.http.api.server.containers.BodyParameters;
import com.fortrow.jflux.http.api.server.containers.RequestHeaders;
import com.fortrow.jflux.http.api.server.base.APIExecutionContext;
import com.fortrow.jflux.http.api.server.exceptions.APIExecutionCancelledException;
import com.sun.net.httpserver.HttpExchange;

import java.util.HashMap;

public class InboundRequest {

    ClientSession session;
    BodyParameters params;
    RequestHeaders headers;
    HashMap<String, String> pathVariables = new HashMap<>();
    String resourceMethod = "";
    APIExecutionContext executionContext = new APIExecutionContext(0);

    public InboundRequest(ClientSession session, BodyParameters params, RequestHeaders headers) {
        this.session = session;
        this.params = params;
        this.headers = headers;
    }

    public ClientSession getSession() {
        return session;
    }

    public BodyParameters getParams() {
        return params;
    }

    public String getBody() {
        return params == null ? "" : params.text();
    }

    public byte[] getBodyBytes() { return params == null ? new byte[0] : params.bytes(); }

    public String body() {
        return getBody();
    }

    public RequestHeaders getHeaders() {
        return headers;
    }

    public HashMap<String, String> getPathVariables() {
        return pathVariables;
    }

    public String getResourceMethod() {
        return resourceMethod;
    }

    public void setResourceMethod(String resourceMethod) {
        this.resourceMethod = resourceMethod == null ? "" : resourceMethod.toUpperCase();
    }

    public APIExecutionContext getExecutionContext() {
        return executionContext;
    }

    public void setExecutionContext(APIExecutionContext executionContext) {
        if(executionContext != null) this.executionContext = executionContext;
    }

    public void checkpoint() throws APIExecutionCancelledException {
        executionContext.checkpoint();
    }

}
