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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-07 18:10:27 EDT
 */
package com.fortrow.jflux.http.api.server.base;

import com.fortrow.jflux.crypto.builtin.SHA;
import com.fortrow.jflux.crypto.builtin.SecureCompare;
import com.fortrow.jflux.encoding.Hex;
import com.fortrow.jflux.http.api.server.containers.APIResponse;
import com.fortrow.jflux.http.api.server.containers.ClientSession;
import com.fortrow.jflux.http.api.server.resources.APIResource;

import java.time.LocalDateTime;

public class AsyncRequest {
    private LocalDateTime lastTimestamp;
    private final long createdAtMs;
    private final String staticResponseId;
    private String dynamicRequestAuth;
    private APIResponse response;
    private final ClientSession clientSession;
    private boolean requestComplete = false;
    private final int minPollRateMs;
    private final int maxPollRateMs;
    private final int timeoutMs;
    private int pollRateMs;
    private long nextAllowedPollAtMs;
    private APIResourceExecution execution;

    private APIResource asyncResource;

    public AsyncRequest(ClientSession clientSession, LocalDateTime lastTimestamp, String staticResponseId, String initialRequestAuth, int perMinute) {
        this(clientSession, lastTimestamp, staticResponseId, initialRequestAuth, 100, Math.max(100, Math.round(perMinute / 60.0f) * 1000), 15000);
    }

    public AsyncRequest(ClientSession clientSession, LocalDateTime lastTimestamp, String staticResponseId, String initialRequestAuth, int minPollRateMs, int maxPollRateMs, int timeoutMs) {
        this.clientSession = clientSession;
        this.createdAtMs = System.currentTimeMillis();
        this.lastTimestamp = lastTimestamp;
        this.staticResponseId = staticResponseId;
        this.dynamicRequestAuth = initialRequestAuth;
        this.minPollRateMs = Math.max(1, minPollRateMs);
        this.maxPollRateMs = Math.max(this.minPollRateMs, maxPollRateMs);
        this.timeoutMs = Math.max(this.maxPollRateMs, timeoutMs);
        this.pollRateMs = this.minPollRateMs;
        this.nextAllowedPollAtMs = System.currentTimeMillis() + this.pollRateMs;
    }

    public String getDynamicRequestAuth() {
        return dynamicRequestAuth;
    }

    public String getRequestId() {
        return staticResponseId;
    }

    public ClientSession getClientSession() {
        return clientSession;
    }

    public boolean requestAuthVerifies(String received, boolean encrypted) {
        if(received == null || received.isBlank()) return false;

        if(encrypted) {
            String randomBytes = clientSession.getSession().getRandomBytes();
            if(randomBytes == null || randomBytes.isBlank()) return false;
            String hmac = SHA.getHmac384Hex(getDynamicRequestAuth(), Hex.fromHex(randomBytes));
            boolean res = SecureCompare.equals(hmac, received);
            if(res) this.dynamicRequestAuth = SHA.get384(hmac);
            return res;
        }

        boolean res = SecureCompare.equals(getDynamicRequestAuth(), received);
        if(res) this.dynamicRequestAuth = SHA.get384(received);
        return res;
    }

    public LocalDateTime getLastTimestamp() {
        return lastTimestamp;
    }

    public APIResponse getResponse() {
        if(execution != null && execution.isComplete()) {
            response = execution.getResponse();
            requestComplete = true;
        }
        return response;
    }

    public APIResponse getContinue() {
        int nextPollRate = advancePollRate();
        return APIResponse.getContinue(clientSession, getRequestId(), getDynamicRequestAuth(), nextPollRate);
    }

    public void requestComplete(APIResponse response) {
        this.response = response;
        requestComplete = true;
    }

    public boolean isRequestComplete() {
        return requestComplete || (execution != null && execution.isComplete());
    }

    public int getPollRate() {
        return pollRateMs;
    }

    public boolean newPoll() {
        clientSession.getSession().newRequest();
        clientSession.getClient().newRequest();
        lastTimestamp = LocalDateTime.now();
        return false;
    }

    public boolean pollTooSoon() {
        return System.currentTimeMillis() < nextAllowedPollAtMs;
    }

    public long getRetryAfterMs() {
        return Math.max(0, nextAllowedPollAtMs - System.currentTimeMillis());
    }

    public boolean isExpired() {
        return System.currentTimeMillis() - createdAtMs >= timeoutMs;
    }

    public APIResponse timeoutResponse() {
        cancel("Request timed out.");
        requestComplete = true;
        response = APIResponse.timeout(clientSession, "Request timed out.");
        return response;
    }

    public void setExecution(APIResourceExecution execution) {
        this.execution = execution;
    }

    public APIResourceExecution getExecution() {
        return execution;
    }

    public void cancel(String reason) {
        if(execution != null) execution.cancel(reason);
    }

    private int advancePollRate() {
        newPoll();
        int next = Math.min(maxPollRateMs, pollRateMs * 2);
        pollRateMs = next;
        nextAllowedPollAtMs = System.currentTimeMillis() + pollRateMs;
        return pollRateMs;
    }

    public void setAsyncResource(APIResource asyncResource) {
        this.asyncResource = asyncResource;
    }
}
