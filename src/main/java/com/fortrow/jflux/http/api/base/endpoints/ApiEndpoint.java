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
