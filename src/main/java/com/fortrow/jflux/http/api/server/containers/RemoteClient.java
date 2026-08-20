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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-08 18:13:50 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.http.api.server.containers;

import com.fortrow.jflux.crypto.builtin.SecureCompare;
import com.fortrow.jflux.http.api.server.encryption.APIKeys;
import com.fortrow.jflux.http.api.server.base.APIServer;
import com.fortrow.jflux.http.api.server.exceptions.APIException;

public abstract class RemoteClient {

    private final APIServer server;
    private final APIKeys apiKeys;
    private ClientInfo clientInfo;

    public static final RemoteClient NONE = null;

    public RemoteClient(APIKeys apiKeys, APIServer server) throws APIException {
        this.server = server;
        this.apiKeys = apiKeys;
    }

    public String getApiKey() {
        return apiKeys.getKey();
    }

    public APIServer getServer() {
        return this.server;
    }

    public ClientInfo getClientInfo() {
        return clientInfo;
    }

    public void setClientInfo(ClientInfo clientInfo) {
        this.clientInfo = clientInfo;
    }

    public boolean equals(APIKeys apiKeys) {
        return matchesApiKeys(apiKeys);
    }

    @Override
    public boolean equals(Object obj) {
        if(this == obj) return true;
        if(obj instanceof APIKeys) return matchesApiKeys((APIKeys) obj);
        if(obj instanceof RemoteClient) return matchesApiKeys(((RemoteClient) obj).apiKeys);
        return false;
    }

    @Override
    public int hashCode() {
        String key = getApiKey();
        return key == null ? 0 : key.hashCode();
    }

    public boolean matchesApiKeys(APIKeys apiKeys) {
        return apiKeys != null && SecureCompare.equals(getApiKey(), apiKeys.getKey());
    }

    public boolean guessSame(RemoteClient client) {
        if(client == null || client.getClientInfo() == null || this.clientInfo == null) return false;
        if(!matchesApiKeys(client.apiKeys)) return false;
        return SecureCompare.equals(client.getClientInfo().getRemoteIp(), this.clientInfo.getRemoteIp());
    }

    public boolean observeRequestMetadata(RequestHeaders headers, String hostname) {
        boolean changed = false;
        if(clientInfo == null) return false;
        if(hostname != null && !hostname.isBlank()) {
            changed = !clientInfo.recordHostname(hostname);
        }
        if(headers != null && headers.contains("User-Agent")) {
            changed = !clientInfo.recordUserAgent(headers.getString("User-Agent")) || changed;
        }
        return changed;
    }

    public void newRequest() {
        clientInfo.incrementIpRequests();
    }

    public abstract boolean isLimited(int perMinute);

    public abstract boolean inBlacklist();

}
