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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-11 15:14:17 EDT
 */
package com.fortrow.jflux.http.api.server.base;

import com.fortrow.jflux.http.api.base.endpoints.InboundRequest;
import com.fortrow.jflux.http.api.server.containers.APIResponse;
import com.fortrow.jflux.http.api.server.containers.ResponseCode;
import com.fortrow.jflux.http.api.server.resources.APIResource;
import com.fortrow.jflux.threading.ThreadExecutor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class APIResourceExecutionTest {

    @Test
    void runsResourceUnderExecutionContext() throws Exception {
        ThreadExecutor executor = new ThreadExecutor(1);
        InboundRequest request = new InboundRequest(null, null, null);
        APIResource resource = new APIResource() {
            @Override
            public APIResponse execute(InboundRequest request) throws Exception {
                request.checkpoint();
                return new APIResponse(null, "{}", ResponseCode.SUCCESSFUL);
            }
        };

        try {
            APIResourceExecution execution = new APIResourceExecution(resource, request, 0);
            execution.start(executor);
            APIResponse response = execution.await();

            assertEquals(ResponseCode.SUCCESSFUL, response.getResponseCode());
            assertTrue(execution.isComplete());
        } finally {
            executor.close();
        }
    }

    @Test
    void timeoutCancelsCooperativeResource() throws Exception {
        ThreadExecutor executor = new ThreadExecutor(1);
        InboundRequest request = new InboundRequest(null, null, null);
        APIResource resource = new APIResource() {
            @Override
            public APIResponse execute(InboundRequest request) throws Exception {
                while(true) {
                    request.getExecutionContext().sleep(25);
                    request.checkpoint();
                }
            }
        };

        try {
            APIResourceExecution execution = new APIResourceExecution(resource, request, 25);
            execution.start(executor);
            APIResponse response = execution.await();

            assertEquals(ResponseCode.REQUEST_TIMEOUT, response.getResponseCode());
            assertTrue(execution.isComplete());
        } finally {
            executor.close();
        }
    }
}
