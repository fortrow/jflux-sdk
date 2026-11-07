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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-11 20:40:55 EDT
 */
package com.fortrow.jflux.http.api.base.endpoints;

import com.fortrow.jflux.http.api.base.endpoints.expectations.ApiExpectationResult;
import com.fortrow.jflux.http.api.base.endpoints.expectations.ApiExpectations;
import com.fortrow.jflux.http.api.base.endpoints.permissions.ApiEndpointPermissions;
import com.fortrow.jflux.http.api.base.prereqs.ApiBase;
import com.fortrow.jflux.http.api.server.containers.APIResponse;
import com.fortrow.jflux.http.api.server.containers.ResponseCode;
import com.fortrow.jflux.http.api.server.types.RequestType;

import java.util.ArrayList;

public class ApiResource {

    private final int index;
    private final String path;
    private final EndpointType type;
    private final ApiEndpointAccepts endpointAccepts;
    private final ApiExpectations expectations;
    private final ApiEndpointPermissions permissions;
    private boolean async = false;

    public ApiResource(int index, String path, EndpointType type, ApiBase api) {
        this.index = index;
        this.path = normalizePath(path);
        this.type = type;
        this.endpointAccepts = defaultAccepts(endpointAccepts());
        this.expectations = defaultExpectations(expectations());
        this.permissions = defaultPermissions(permissions());
    }

    public ApiResource(int index, String path, ApiBase api) {
        this(index, path, EndpointType.MODEL_RESOURCE, api);
    }

    protected ApiEndpointAccepts endpointAccepts() { return ApiEndpointAccepts.crud(); }
    protected ApiExpectations expectations() { return new ApiExpectations(); }
    protected ApiEndpointPermissions permissions() { return ApiEndpointPermissions.none(); }
    protected ArrayList<String> parameters() { return new ArrayList<>(); }

    public int getIndex() { return index; }
    public String getPath() { return path; }
    public EndpointType getType() { return type; }
    public ApiEndpointAccepts getEndpointAccepts() { return endpointAccepts; }
    public ApiExpectations getExpectations() { return expectations; }
    public ApiEndpointPermissions getPermissions() { return permissions; }
    public boolean isAsync() { return async; }

    public ApiResource setAsync(boolean async) {
        this.async = async;
        return this;
    }

    public void register(ApiBase base) {
        if(base == null) return;
        if(endpointAccepts.accepts(RequestType.GET)) __index(base);
        if(endpointAccepts.accepts(RequestType.POST)) __create(base);
        if(endpointAccepts.accepts(RequestType.PUT)) __update(base);
        if(endpointAccepts.accepts(RequestType.PATCH)) __patch(base);
        if(endpointAccepts.accepts(RequestType.DELETE)) __delete(base);
    }

    public com.fortrow.jflux.http.api.server.resources.APIResource __index(ApiBase base) {
        return registerMethod(base, "GET", this::index);
    }

    public com.fortrow.jflux.http.api.server.resources.APIResource __update(ApiBase base) {
        return registerMethod(base, "PUT", this::update);
    }

    public com.fortrow.jflux.http.api.server.resources.APIResource __patch(ApiBase base) {
        return registerMethod(base, "PATCH", this::update);
    }

    public com.fortrow.jflux.http.api.server.resources.APIResource __create(ApiBase base) {
        return registerMethod(base, "POST", this::create);
    }

    public com.fortrow.jflux.http.api.server.resources.APIResource __delete(ApiBase base) {
        return registerMethod(base, "DELETE", this::delete);
    }

    public APIResponse index(InboundRequest request) throws Exception {
        return unsupported(request, "GET");
    }

    public APIResponse update(InboundRequest request) throws Exception {
        return unsupported(request, "UPDATE");
    }

    public APIResponse create(InboundRequest request) throws Exception {
        return unsupported(request, "POST");
    }

    public APIResponse delete(InboundRequest request) throws Exception {
        return unsupported(request, "DELETE");
    }

    protected APIResponse modelResourceNotImplemented(InboundRequest request) {
        return new APIResponse(request.getSession(), ResponseCode.REQUEST_FAILED, "Model resources are not implemented yet.");
    }

    protected APIResponse unsupported(InboundRequest request, String method) {
        if(type == EndpointType.MODEL_RESOURCE) return modelResourceNotImplemented(request);
        return new APIResponse(request.getSession(), ResponseCode.REQUEST_FAILED, method + " is not supported by this endpoint.");
    }

    private com.fortrow.jflux.http.api.server.resources.APIResource registerMethod(ApiBase base, String method, EndpointMethod endpointMethod) {
        com.fortrow.jflux.http.api.server.resources.APIResource definition = new com.fortrow.jflux.http.api.server.resources.APIResource(method) {
            @Override
            public APIResponse execute(InboundRequest request) throws Exception {
                request.setResourceMethod(method);
                APIResponse validationResponse = base.validateEndpointRequest(ApiResource.this, request);
                if(validationResponse != null) return validationResponse;
                return endpointMethod.execute(request);
            }
        };
        definition.setName(path + "#" + method);
        return base.addResource(path, method, async, parameters(), definition);
    }

    private static ApiEndpointAccepts defaultAccepts(ApiEndpointAccepts accepts) {
        if(accepts == null || accepts.isEmpty()) return ApiEndpointAccepts.crud();
        return accepts;
    }

    private static ApiExpectations defaultExpectations(ApiExpectations expectations) {
        return expectations == null ? new ApiExpectations() : expectations;
    }

    public APIResponse validateRequest(InboundRequest request) {
        ApiExpectationResult expectationResult = expectations.validate(request);
        if(!expectationResult.isValid()) {
            return new APIResponse(request == null ? null : request.getSession(), ResponseCode.REQUEST_FAILED, expectationResult.getMessage());
        }

        if(!permissions.isPermitted(request)) {
            return new APIResponse(request == null ? null : request.getSession(), ResponseCode.FORBIDDEN, permissions.failureMessage());
        }

        return null;
    }

    private static ApiEndpointPermissions defaultPermissions(ApiEndpointPermissions permissions) {
        return permissions == null ? ApiEndpointPermissions.none() : permissions;
    }

    private static String normalizePath(String input) {
        if(input == null || input.isBlank()) return "/";
        String output = input.trim().replaceAll("\\s+", "").replaceAll("\\.", "/");
        if(output.startsWith("/")) output = output.substring(1);
        while(output.endsWith("/") && output.length() > 1) output = output.substring(0, output.length() - 1);
        return output;
    }

    private interface EndpointMethod {
        APIResponse execute(InboundRequest request) throws Exception;
    }

    public enum EndpointType {
        MODEL_RESOURCE,
        ENDPOINT
    }

}
