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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-11 20:44:14 EDT
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
