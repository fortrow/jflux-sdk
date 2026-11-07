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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-08 19:22:08 EDT
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
        return createClient(clientInfo, apiKeys, server, isZeroTrust, null);
    }

    public static ClientSession createClient(ClientInfo clientInfo, APIKeys apiKeys, APIServer server,
                                             boolean isZeroTrust, byte[] clientPublicKey) throws APIException {
        APIServerType serverType = server.getConfig().getServerType();
        APISession session = new APISession(apiKeys.copyForSession(), clientInfo.getRemoteIp(), serverType,
                server.getConfig().getMinutesTillInvalid(), server.getConfig().isSessionExpirationEnabled(), isZeroTrust,
                server.getConfig().getEncryptedCryptoSuite(), clientPublicKey
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
