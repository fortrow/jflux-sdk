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

import com.fortrow.jflux.http.api.server.exceptions.APIExecutionCancelledException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class APIExecutionContextTest {

    @Test
    void checkpointThrowsAfterCancel() {
        APIExecutionContext context = new APIExecutionContext(0);

        context.cancel("stop");

        assertThrows(APIExecutionCancelledException.class, context::checkpoint);
    }

    @Test
    void timeoutMarksContextCancelled() throws InterruptedException {
        APIExecutionContext context = new APIExecutionContext(5);

        Thread.sleep(20);

        assertTrue(context.isTimedOut());
        assertThrows(APIExecutionCancelledException.class, context::checkpoint);
    }
}
