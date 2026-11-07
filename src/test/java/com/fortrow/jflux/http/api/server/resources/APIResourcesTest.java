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
package com.fortrow.jflux.http.api.server.resources;

import com.fortrow.jflux.http.api.base.endpoints.InboundRequest;
import com.fortrow.jflux.http.api.server.containers.APIResponse;
import com.fortrow.jflux.http.api.server.containers.ResponseCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

class APIResourcesTest {

    @Test
    void resolvesSamePathByRequestMethod() {
        APIResources resources = new APIResources();
        APIResource get = resource("GET");
        APIResource post = resource("POST");

        resources.addResource("records", get);
        resources.addResource("records", post);

        assertSame(get, resources.getResource("/records", "GET"));
        assertSame(post, resources.getResource("records", "POST"));
        assertNull(resources.getResource("records", "DELETE"));
        assertTrue(resources.inList("records"));
        assertTrue(resources.inList("records", "POST"));
        assertFalse(resources.inList("records", "DELETE"));
    }

    private APIResource resource(String method) {
        return new APIResource(method) {
            @Override
            public APIResponse execute(InboundRequest request) {
                return new APIResponse(null, method, ResponseCode.SUCCESSFUL);
            }
        };
    }
}
