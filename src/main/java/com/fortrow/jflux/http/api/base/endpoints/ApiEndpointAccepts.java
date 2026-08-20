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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-04 15:27:04 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.http.api.base.endpoints;

import com.fortrow.jflux.http.api.server.types.RequestType;

import java.util.EnumSet;

public class ApiEndpointAccepts {

    private final EnumSet<RequestType> requestTypes = EnumSet.noneOf(RequestType.class);

    public ApiEndpointAccepts(RequestType...requestTypes) {
        if(requestTypes == null) return;
        for(RequestType requestType : requestTypes) {
            if(requestType != null) this.requestTypes.add(requestType);
        }
    }

    public static ApiEndpointAccepts crud() {
        return new ApiEndpointAccepts(RequestType.GET, RequestType.POST, RequestType.PUT, RequestType.PATCH, RequestType.DELETE);
    }

    public boolean accepts(RequestType requestType) {
        return requestTypes.contains(requestType);
    }

    public boolean isEmpty() {
        return requestTypes.isEmpty();
    }

}
