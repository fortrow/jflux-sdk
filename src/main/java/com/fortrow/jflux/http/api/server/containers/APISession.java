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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-10 22:51:50 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.http.api.server.containers;

import com.fortrow.jflux.crypto.Rand;
import com.fortrow.jflux.crypto.builtin.SHA;
import com.fortrow.jflux.crypto.builtin.SecureCompare;
import com.fortrow.jflux.encoding.Hex;
import com.fortrow.jflux.http.api.server.encryption.APIKeys;
import com.fortrow.jflux.http.api.server.encryption.EncryptedApiKeyPairs;
import com.fortrow.jflux.http.api.server.exceptions.APIException;
import com.fortrow.jflux.http.api.server.types.APIServerType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class APISession {

    private String sessionId;
    private String sessionAuth;

    private byte[] randomBytes;
    private final LocalDateTime started;
    private volatile LocalDateTime lastRequest;
    private final AtomicInteger requestCount = new AtomicInteger(0);
    private APIKeys apiKeys;
    private EncryptedApiKeyPairs zeroTrustKeyPairs;
    private boolean isActive = true;
    private final List<LocalDateTime> recentRequests = Collections.synchronizedList(new ArrayList<>());
    private int minutesTillInvalid = 30;
    private boolean expirationEnabled = true;
    private long lastRequestSequence = 0;
    private byte[] currentRequestAad;

    public record State(String sessionAuth, long lastRequestSequence, byte[] currentRequestAad) { }

    public APISession(APIKeys apiKeys, String ipAddr, APIServerType serverType, int minutesTillInvalid, boolean isZeroTrust) throws APIException {
        this(apiKeys, ipAddr, serverType, minutesTillInvalid, true, isZeroTrust);
    }

    public APISession(APIKeys apiKeys, String ipAddr, APIServerType serverType, int minutesTillInvalid,
                      boolean expirationEnabled, boolean isZeroTrust) throws APIException {
        this.apiKeys = apiKeys;
        this.started = LocalDateTime.now();
        this.lastRequest = LocalDateTime.now();
        this.minutesTillInvalid = Math.max(1, minutesTillInvalid);
        this.expirationEnabled = expirationEnabled;

        if(isZeroTrust) {
            this.sessionId = createSessionId(ipAddr);
            this.randomBytes = Rand.secure(96);
            this.sessionAuth = createInitialSessionAuth();
            this.zeroTrustKeyPairs = new EncryptedApiKeyPairs(this.getApiKeys().getSecret());
        }
    }

    public APIKeys getApiKeys() { return apiKeys; }
    public LocalDateTime getLastRequestDateTime() { return lastRequest; }
    public LocalDateTime getStartedDateTime() { return started; }
    public String getSessionId() { return sessionId; }

    public boolean invalidateByTime() {
        if(!expirationEnabled || !isActive) return false;
        LocalDateTime limit = lastRequest.plusMinutes(minutesTillInvalid);
        if(LocalDateTime.now().isAfter(limit)) {
            deactivate();
            return true;
        }
        return false;
    }

    public int getRecentRequests() {
        synchronized (recentRequests) {
            for(int i=recentRequests.size() - 1; i>=0; i--) {
                LocalDateTime limit = LocalDateTime.now().minusMinutes(1);
                if(recentRequests.get(i).isBefore(limit)) recentRequests.remove(i);
            }
            return recentRequests.size();
        }
    }

    public String getDynamicSessionAuth() { return sessionAuth; }

    public State snapshot() {
        return new State(
                sessionAuth,
                lastRequestSequence,
                currentRequestAad == null ? null : java.util.Arrays.copyOf(currentRequestAad, currentRequestAad.length)
        );
    }

    public synchronized void restore(State state) {
        if(state == null) return;
        this.sessionAuth = state.sessionAuth();
        this.lastRequestSequence = state.lastRequestSequence();
        this.currentRequestAad = state.currentRequestAad() == null ? null : java.util.Arrays.copyOf(state.currentRequestAad(), state.currentRequestAad().length);
    }

    public boolean checkDynamicSessionAuth(String received) {
        String hmac = SHA.getHmac384Hex(getDynamicSessionAuth(), sessionId);
        boolean res = SecureCompare.equals(hmac, received);
        if(res) this.sessionAuth = SHA.get384(hmac);
        return res;
    }

    public synchronized boolean acceptRequestSequence(long sequence) {
        if (sequence <= lastRequestSequence) {
            return false;
        }
        lastRequestSequence = sequence;
        return true;
    }

    public byte[] getCurrentRequestAad() { return currentRequestAad; }
    public void setCurrentRequestAad(byte[] currentRequestAad) { this.currentRequestAad = currentRequestAad; }

    public boolean isActive() { return isActive; }
    public synchronized void deactivate() {
        if(!isActive) return;
        isActive = false;
        if(apiKeys != null) apiKeys.invalidateSessionMaterial();
        if(randomBytes != null) java.util.Arrays.fill(randomBytes, (byte) 0);
        randomBytes = null;
        if(currentRequestAad != null) java.util.Arrays.fill(currentRequestAad, (byte) 0);
        currentRequestAad = null;
        if(zeroTrustKeyPairs != null) zeroTrustKeyPairs.clear();
    }
    public String getRandomBytes() { return Hex.getHex(this.randomBytes); }

    public void newRequest() {
        requestCount.incrementAndGet();
        lastRequest = LocalDateTime.now();
        recentRequests.add(0, lastRequest);
    }

    public boolean isExpirationEnabled() { return expirationEnabled; }
    public int getMinutesTillInvalid() { return minutesTillInvalid; }
    public boolean isExpired() {
        return expirationEnabled && isActive && LocalDateTime.now().isAfter(lastRequest.plusMinutes(minutesTillInvalid));
    }

    public int getRequestCount() { return requestCount.get(); }
    public void setApiKeys(APIKeys apiKeys) { this.apiKeys = apiKeys; }
    public String getKeySetId() { return zeroTrustKeyPairs.getKeySetId(); }

    public void keysTransferred() { zeroTrustKeyPairs.clear(); }
    public EncryptedApiKeyPairs.AsymmetricKey getClientPublicKey() { return zeroTrustKeyPairs.getClientPublic(); }
    public EncryptedApiKeyPairs.AsymmetricKey getServerPublicKey() { return zeroTrustKeyPairs.getServerPublic(); }
    public EncryptedApiKeyPairs.AsymmetricKey getClientPrivateKey() { return zeroTrustKeyPairs.getClientPrivate(); }
    public EncryptedApiKeyPairs.AsymmetricKey getServerPrivateKey() { return zeroTrustKeyPairs.getServerPrivate(); }

    private String createSessionId(String ipAddr) throws APIException {
        String apiSecret = apiKeys.getSecret();
        String value = ipAddr + ";" + apiSecret;
        return SHA.getSha1(SHA.getHmac384Hex(value, Rand.secure(96)));
    }

    private String createInitialSessionAuth() throws APIException {
        return SHA.getHmac384Hex(sessionId, randomBytes);
    }
}
