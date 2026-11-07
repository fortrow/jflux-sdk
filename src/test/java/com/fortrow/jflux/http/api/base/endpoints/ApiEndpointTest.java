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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-11 20:23:08 EDT
 */
package com.fortrow.jflux.http.api.base.endpoints;

import com.fortrow.jflux.http.api.server.containers.APIResponse;
import com.fortrow.jflux.http.api.server.containers.ResponseCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiEndpointTest {

    @Test
    void mapsCrudMethodsToEndpointHandlers() throws Exception {
        TestEndpoint endpoint = new TestEndpoint();
        InboundRequest request = new InboundRequest(null, null, null);

        assertEquals("get", endpoint.index(request).getResponse());
        assertEquals("post", endpoint.create(request).getResponse());

        request.setResourceMethod("PUT");
        assertEquals("put", endpoint.update(request).getResponse());

        request.setResourceMethod("PATCH");
        assertEquals("patch", endpoint.update(request).getResponse());

        assertEquals("delete", endpoint.delete(request).getResponse());
    }

    private static class TestEndpoint extends ApiEndpoint {
        TestEndpoint() {
            super(0, "things", null);
        }

        @Override
        public APIResponse get(InboundRequest request) {
            return response("get");
        }

        @Override
        public APIResponse post(InboundRequest request) {
            return response("post");
        }

        @Override
        public APIResponse put(InboundRequest request) {
            return response("put");
        }

        @Override
        public APIResponse patch(InboundRequest request) {
            return response("patch");
        }

        @Override
        public APIResponse del(InboundRequest request) {
            return response("delete");
        }

        private APIResponse response(String value) {
            return new APIResponse(null, value, ResponseCode.SUCCESSFUL);
        }
    }
}
