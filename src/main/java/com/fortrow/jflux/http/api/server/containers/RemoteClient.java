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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-08 18:13:50 EDT
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
