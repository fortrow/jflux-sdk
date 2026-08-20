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
package com.fortrow.jflux.http.api.server.resources;

import com.fortrow.jflux.http.api.server.impl.APIExecute;

import java.util.ArrayList;

public abstract class APIResource implements APIExecute {

    private ArrayList<String> parameters = new ArrayList<String>();
    private String requestMethod = "GET";
    private String value = "";
    private boolean isAsync = false;

    public APIResource() {}

    public APIResource(String requestMethod) {
        this.requestMethod = requestMethod;
    }

    public void setParameters(String[] parameters) {
        for(int i=0; i<parameters.length; i++) {
            this.parameters.add(parameters[i]);
        }
    }

    public void setParameters(ArrayList<String> parameters) {
        this.parameters = parameters;
    }

    public void addParameter(String key) {
        if(!inArray(key))
            parameters.add(key);
    }

    public boolean inArray(String key) {
        return parameters.contains(key);
    }
    public ArrayList<String> getParameters() {
        return parameters;
    }

    public String getRequestMethod() {
        return requestMethod;
    }

    public void setRequestMethod(String requestMethod) {
        this.requestMethod = requestMethod.toUpperCase();
    }

    public void setName(String val) {
        value = val;
    }

    public String getName() {
        return value;
    }

    public boolean isAsync() {
        return isAsync;
    }

    public void isAsync(boolean val) {
        isAsync = val;
    }
}
