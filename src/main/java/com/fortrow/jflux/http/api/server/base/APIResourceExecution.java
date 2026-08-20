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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-11 15:09:04 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.http.api.server.base;

import com.fortrow.jflux.http.api.base.endpoints.InboundRequest;
import com.fortrow.jflux.http.api.server.containers.APIResponse;
import com.fortrow.jflux.http.api.server.containers.ResponseCode;
import com.fortrow.jflux.http.api.server.exceptions.APIExecutionCancelledException;
import com.fortrow.jflux.http.api.server.resources.APIResource;
import com.fortrow.jflux.threading.ThreadExecutor;
import com.fortrow.jflux.threading.types.DetachedThread;

import java.util.concurrent.CountDownLatch;

public class APIResourceExecution {

    private final APIResource resource;
    private final InboundRequest request;
    private final APIExecutionContext context;
    private final CountDownLatch done = new CountDownLatch(1);
    private volatile APIResponse response;
    private volatile Throwable failure;
    private volatile boolean started;
    private volatile boolean complete;

    public APIResourceExecution(APIResource resource, InboundRequest request, int timeoutMs) {
        this.resource = resource;
        this.request = request;
        this.context = new APIExecutionContext(timeoutMs);
        this.request.setExecutionContext(context);
    }

    public APIExecutionContext getContext() {
        return context;
    }

    public void start(ThreadExecutor executor) {
        if(started) throw new IllegalStateException("APIResourceExecution can only be started once.");
        started = true;
        executor.execute(this::run);
        startTimeoutWatcher();
    }

    private void run() {
        context.attach(Thread.currentThread());
        try {
            context.checkpoint();
            response = resource.execute(request);
            context.checkpoint();
            if(response == null) response = new APIResponse(request.getSession(), ResponseCode.SERVER_ERROR);
        } catch (APIExecutionCancelledException e) {
            response = APIResponse.timeout(request.getSession(), e.getMessage());
        } catch (Throwable e) {
            failure = e;
            response = new APIResponse(request.getSession(), ResponseCode.SERVER_ERROR);
        } finally {
            complete = true;
            onComplete(response, failure);
            done.countDown();
        }
    }

    protected void onComplete(APIResponse response, Throwable failure) {
    }

    private void startTimeoutWatcher() {
        int timeoutMs = context.getTimeoutMs();
        if(timeoutMs <= 0) return;
        new DetachedThread() {
            @Override
            protected boolean execute() {
                try {
                    Thread.sleep(timeoutMs);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return false;
                }
                if(!complete) cancel("Request timed out.");
                return false;
            }
        }.start();
    }

    public void cancel(String reason) {
        context.cancel(reason);
    }

    public boolean isComplete() {
        return complete;
    }

    public boolean isTimedOut() {
        return context.isTimedOut();
    }

    public APIResponse getResponse() {
        return response;
    }

    public Throwable getFailure() {
        return failure;
    }

    public APIResponse await() throws InterruptedException {
        done.await();
        return response;
    }
}
