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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-11 20:23:08 EDT
 * 
 * -------------------------------------------------------------------------

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
