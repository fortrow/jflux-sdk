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
