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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-11 15:14:17 EDT
 * 
 * -------------------------------------------------------------------------

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
