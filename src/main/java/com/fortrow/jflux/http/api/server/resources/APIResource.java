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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-04 15:27:04 EDT
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
