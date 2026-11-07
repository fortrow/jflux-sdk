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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-07 20:17:03 EDT
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
    private String resourcePath = "";
    public String getResourcePath() { return resourcePath; }
    public void setResourcePath(String path) { resourcePath = path == null ? "" : path; }
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
