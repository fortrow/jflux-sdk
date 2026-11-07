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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-04 15:27:04 EDT
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
