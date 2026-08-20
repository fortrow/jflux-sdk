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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-08 18:14:41 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.http.api.server.impl;

import com.fortrow.jflux.http.api.base.prereqs.ApiService;
import com.fortrow.jflux.http.api.server.containers.ClientSession;
import com.fortrow.jflux.http.api.server.containers.BodyParameters;
import com.fortrow.jflux.http.api.server.containers.RequestHeaders;
import com.fortrow.jflux.http.api.server.encryption.APIKeys;
import com.fortrow.jflux.http.api.server.resources.APIResource;
import com.fortrow.jflux.http.api.server.types.ContentType;

import java.util.HashMap;

public interface APIMethods {
    boolean isAuthenticated(RequestHeaders headers, APIResource resource, BodyParameters params, ClientSession clientSession);
    boolean inTimeout(ClientSession clientSession, int perMinute);
    boolean inBlacklist(ClientSession clientSession);
    APIKeys lookupApiKey(String apiKey);
    HashMap<String, String> getParameters(String body, ContentType contentType);
    void addToBlacklist(ClientSession clientSession, ApiService.BlacklistEntryType entryType);
    void removeFromBlacklist(ClientSession clientSession);
    void addToTimeout(ClientSession clientSession, ApiService.BlacklistEntryType entryType);
    void removeFromTimeout(ClientSession clientSession);
    void lockApiKey(ClientSession clientSession, String reason);
    void lockApiKey(APIKeys apiKeys, String reason);
}
