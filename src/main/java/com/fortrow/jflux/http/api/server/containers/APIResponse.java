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
import com.fortrow.jflux.encoding.Hex;
import com.fortrow.jflux.http.api.server.base.AsyncRequest;
import com.fortrow.jflux.http.api.server.config.APIServerConfig;
import com.fortrow.jflux.http.api.server.encryption.APIKeys;
import com.fortrow.jflux.http.api.server.types.ContentType;
import com.fortrow.jflux.json.JSON;
import org.json.simple.JSONObject;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

public class APIResponse {

    private ResponseCode responseCode;
    private String response;
    private JSONObject responseObj = null;
    private JSONObject plainResponseObj = null;
    private ClientSession clientSession;
    private ContentType contentType = ContentType.JSON;
    private LocalDateTime timestamp;
    private boolean isAsync = false;
    private boolean isCompleted = false;
    private AsyncRequest asyncRequest;

    public APIResponse(ClientSession clientSession, String response, ResponseCode responseCode) {

        this.clientSession = clientSession;
        this.response = response;
        this.responseCode = responseCode;

    }

    public APIResponse(ClientSession clientSession, JSONObject responseObj, ResponseCode responseCode, boolean isEncrypted) {

        this.clientSession = clientSession;
        this.responseCode = responseCode;

        JSONObject sourceObj = responseObj == null ? new JSONObject() : new JSONObject(responseObj);
        this.plainResponseObj = new JSONObject(sourceObj);

        if(isEncrypted) {
            JSONObject encryptedSource = new JSONObject(sourceObj);
            JSONObject obj = new JSONObject();
            if(encryptedSource.containsKey("request_status")) {
                String status = encryptedSource.get("request_status").toString();
                encryptedSource.remove("request_status");
                obj.put("request_status", status);
            } else if(responseCode == ResponseCode.SUCCESSFUL){
                obj.put("request_status", "success");
            }
            obj.put("response", JSON.getString(encryptedSource));
            this.responseObj = obj;
        } else {
            this.responseObj = sourceObj;
            this.response = JSON.getString(sourceObj);
        }

    }

    public APIResponse(ClientSession clientSession, JSONObject responseObj, ResponseCode responseCode) {
        this(clientSession, responseObj, responseCode, true);
    }

    public APIResponse(ClientSession clientSession, ResponseCode responseCode, String message) {
        this.clientSession = clientSession;
        this.responseCode = responseCode;
        JSONObject obj = new JSONObject();
        obj.put("request_status", statusFor(responseCode));
        if(message != null && !message.isBlank()) obj.put("message", message);
        this.response = JSON.getString(obj);
    }

    public APIResponse(ClientSession clientSession, ResponseCode responseCode) {

        if(responseCode == ResponseCode.REDIRECT_START) {

            String staticResponseId = Hex.getHex(Rand.secure(32));
            String initialRequestAuth = Hex.getHex(Rand.secure(48));
            APIServerConfig config = clientSession.getClient().getServer().getConfig();

            asyncRequest =
                    new AsyncRequest(
                            clientSession, LocalDateTime.now(),
                            staticResponseId, initialRequestAuth,
                            config.getAsyncMinPollMs(), config.getAsyncMaxPollMs(), config.getAsyncTimeoutMs());

            this.clientSession = clientSession;
            this.responseCode = ResponseCode.SUCCESSFUL;

            JSONObject obj = new JSONObject();

            obj.put("request_status", "redirect");
            obj.put("response_id", staticResponseId);
            obj.put("initial_auth", initialRequestAuth);
            obj.put("poll_rate", asyncRequest.getPollRate());

            this.responseObj = obj;
            this.response = JSON.getString(obj);

        } else {

            this.clientSession = clientSession;
            this.responseCode = responseCode;

        }

    }

    public void encrypt() throws Exception {
        ensureResponseObject();
        if(!responseObj.containsKey("response")) {
            JSONObject payload = new JSONObject(responseObj);
            Object status = payload.remove("request_status");
            JSONObject wrapped = new JSONObject();
            if(status != null) wrapped.put("request_status", status.toString());
            wrapped.put("response", JSON.getString(payload));
            responseObj = wrapped;
        }
        APIKeys keyPair = clientSession.getSession().getApiKeys();
        EncryptedPayload encryptedPayload = new EncryptedPayload(responseObj.get("response").toString(), keyPair, clientSession.getSession().getCurrentRequestAad());
        responseObj.replace("response", encryptedPayload.encrypt());
        response = JSON.getString(responseObj);
    }

    public void encryptWithPsk() throws Exception {
        ensureResponseObject();
        APIKeys keys = clientSession.getSession().getApiKeys();
        byte[] iv = Rand.generateIv(96);
        responseObj.replace("response", keys.encryptWithPsk(responseObj.get("response").toString().getBytes(StandardCharsets.UTF_8), iv));
        responseObj.put("iv", Hex.getHex(iv));
        response = JSON.getString(responseObj);
    }

    public ContentType getContentType() {
        return contentType;
    }

    public void setContentType(ContentType type) {
        contentType = type;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp() {
        this.timestamp = LocalDateTime.now();
    }

    public void setResponseCode(ResponseCode responseCode) {
        this.responseCode = responseCode;
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    public void markCompleted() {
        isCompleted = true;
    }

    public boolean isAsync() {
        return isAsync;
    }

    public ResponseCode getResponseCode() {
        return responseCode;
    }

    public RemoteClient getClient() {
        return clientSession.getClient();
    }

    public APISession getApiSession() { return clientSession.getSession(); }

    public String getResponse() {
        if(response == null) {
            if(plainResponseObj != null) {
                response = JSON.getString(plainResponseObj);
            } else if(responseObj != null) {
                response = JSON.getString(responseObj);
            } else {
                JSONObject obj = new JSONObject();
                obj.put("request_status", statusFor(responseCode));
                response = JSON.getString(obj);
            }
        }
        return response;
    }

    public AsyncRequest getAsyncRequest() {
        return asyncRequest;
    }

    public static APIResponse getContinue(ClientSession clientSession, String requestId, String dynamicAuth, int pollRate) {

        JSONObject obj = new JSONObject();

        obj.put("request_status", "redirect");
        obj.put("response_id", requestId);
        obj.put("next_auth", dynamicAuth);
        obj.put("poll_rate", pollRate);

        return new APIResponse(clientSession, obj, ResponseCode.REDIRECT_CONTINUE, false);

    }

    public static APIResponse tooManyRequests(ClientSession clientSession, int pollRate) {
        return tooManyRequests(clientSession, pollRate, null);
    }

    public static APIResponse tooManyRequests(ClientSession clientSession, int pollRate, String nextAuth) {

        JSONObject obj = new JSONObject();
        obj.put("request_status", "too_many_requests");
        obj.put("poll_rate", pollRate);
        if(nextAuth != null && !nextAuth.isBlank()) obj.put("next_auth", nextAuth);
        return new APIResponse(clientSession, obj, ResponseCode.TOO_MANY_REQUESTS, false);

    }

    public static APIResponse timeout(ClientSession clientSession, String message) {

        JSONObject obj = new JSONObject();
        obj.put("request_status", "timeout");
        obj.put("message", message == null || message.isBlank() ? "Request timed out." : message);
        return new APIResponse(clientSession, obj, ResponseCode.REQUEST_TIMEOUT, false);

    }

    private static String statusFor(ResponseCode responseCode) {
        if(responseCode == null) return "server_error";
        return switch (responseCode) {
            case REQUEST_FAILED -> "fail";
            case FORBIDDEN -> "forbidden";
            case TOO_MANY_REQUESTS -> "too_many_requests";
            case UNAUTHORIZED -> "unauthorized";
            case SERVER_ERROR -> "server_error";
            case REQUEST_TIMEOUT -> "timeout";
            case SUCCESSFUL -> "success";
            case REDIRECT_START, REDIRECT_CONTINUE -> "redirect";
        };
    }

    @SuppressWarnings("unchecked")
    private void ensureResponseObject() {
        if(responseObj != null) return;
        JSONObject obj = new JSONObject();
        obj.put("request_status", statusFor(responseCode));
        responseObj = obj;
        if(plainResponseObj == null) plainResponseObj = new JSONObject(obj);
    }

}
