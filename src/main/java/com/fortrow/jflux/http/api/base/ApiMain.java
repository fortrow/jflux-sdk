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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-08 18:16:33 EDT
 */
package com.fortrow.jflux.http.api.base;

import com.fortrow.jflux.db.connectors.details.DbConnectionDetails;
import com.fortrow.jflux.files.LogFile;
import com.fortrow.jflux.http.api.base.endpoints.permissions.ApiEndpointPermissions;
import com.fortrow.jflux.http.api.base.models.ApiModel;
import com.fortrow.jflux.http.api.base.models.AppModelCollection;
import com.fortrow.jflux.http.api.base.prereqs.ApiBase;
import com.fortrow.jflux.http.api.base.prereqs.ApiService;
import com.fortrow.jflux.http.api.server.config.APIServerConfig;
import com.fortrow.jflux.http.api.server.exceptions.APIException;
import com.fortrow.jflux.http.api.types.DatabaseServerType;

import java.util.ArrayList;

public abstract class ApiMain {

    private String appName;
    private ApiBase base;

    public ApiMain(String appName) throws APIException {
        this.appName = appName;
        APIServerConfig config = getConfig();
        if(config.getApplicationDbType() != null) {
            config.setApplicationDbDetails(() -> getApplicationDbConnectionDetails(config.getApplicationDbType()));
        }
        if(config.getSessionDbType() != null) {
            config.setSessionDbDetails(() -> getSessionDbConnectionDetails(config.getSessionDbType()));
        }
        ApiService service = buildService();
        this.base = new ApiBase(config, service) { };
        AppModelCollection models = fillModels(new ArrayList<>());
        if(models != null) this.base.setModels(models);
    }

    public void start() throws APIException {
        this.base.start();
    }

    public ApiBase getApi() {
        return base;
    }

    public void setLogFile(LogFile file) {
        this.base.setLogFile(file);
    }

    protected abstract ApiService buildService();
    protected abstract APIServerConfig getConfig();

    protected abstract DbConnectionDetails getSessionDbConnectionDetails(DatabaseServerType sessionDbType) throws APIException;
    protected abstract AppModelCollection fillModels(ArrayList<Class<? extends ApiModel>> models);
    protected abstract ApiEndpointPermissions setEndpointPermissions();

    protected DbConnectionDetails getApplicationDbConnectionDetails(DatabaseServerType applicationDbType) throws APIException {
        return null;
    }

}
