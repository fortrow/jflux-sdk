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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-08 19:22:08 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.http.api.server.containers;

import com.fortrow.jflux.http.api.server.encryption.APIKeys;
import com.fortrow.jflux.http.api.server.base.APIServer;
import com.fortrow.jflux.http.api.server.exceptions.APIException;
import com.fortrow.jflux.http.api.server.types.APIServerType;

public class ClientSession {

    private RemoteClient client;
    private APISession session;
    private ClientInfo clientInfo;

    public ClientSession(ClientInfo info, APISession session) {
        this.session = session;
        this.clientInfo = info;
    }

    public ClientSession(ClientInfo info, RemoteClient client, APISession session) {
        this.client = client;
        this.session = session;
        this.clientInfo = info;
    }

    public static ClientSession createClient(ClientInfo clientInfo, APIKeys apiKeys, APIServer server, boolean isZeroTrust) throws APIException {
        APIServerType serverType = server.getConfig().getServerType();
        APISession session = new APISession(apiKeys, clientInfo.getRemoteIp(), serverType,
                server.getConfig().getMinutesTillInvalid(), isZeroTrust
        );
        ClientSession retVal = new ClientSession(clientInfo, session);

        RemoteClient client = new RemoteClient(apiKeys, server) {
            @Override
            public boolean isLimited(int perMinute) {
                return this.getServer().isPastRateLimit(retVal, perMinute);
            }

            @Override
            public boolean inBlacklist() {
                return this.getServer().inBlacklist(retVal);
            }

        };

        client.setClientInfo(clientInfo);
        retVal.setClient(client);

        return retVal;
    }

    public void setClient(RemoteClient client) {
        this.client = client;
    }

    public RemoteClient getClient() {
        return client;
    }

    public APISession getSession() {
        return session;
    }

    public void newRequest() {
        getSession().newRequest();
        getClient().newRequest();
    }

}
