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
package com.fortrow.jflux.http.api.base.models;

import com.fortrow.jflux.http.api.base.prereqs.ApiBase;
import com.fortrow.jflux.http.api.base.prereqs.ApiService;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AppModelCollection {

    private final LinkedHashMap<String, ApiModel> models = new LinkedHashMap<>();
    private final ArrayList<Class<? extends ApiModel>> modelTypes = new ArrayList<>();

    public AppModelCollection() { }

    public AppModelCollection(ApiModel[] models) {
        if(models == null) return;
        for(ApiModel model : models) put(model);
    }

    public AppModelCollection(List<Class<? extends ApiModel>> list) {
        if(list != null) modelTypes.addAll(list);
    }

    public static AppModelCollection of(ApiModel... models) {
        return new AppModelCollection(models);
    }

    public static AppModelCollection ofTypes(List<Class<? extends ApiModel>> list) {
        return new AppModelCollection(list);
    }

    public AppModelCollection add(ApiModel model) {
        put(model);
        return this;
    }

    public AppModelCollection addType(Class<? extends ApiModel> modelType) {
        if(modelType != null) modelTypes.add(modelType);
        return this;
    }

    public void bind(ApiService service) {
        for(Class<? extends ApiModel> modelType : modelTypes) {
            put(newModel(modelType, service));
        }
    }

    public void register(ApiBase base) {
        if(base == null) return;
        int index = 0;
        for(ApiModel model : models.values()) {
            base.addEndpoint(new ApiModelResource(index++, model, base));
        }
    }

    public ApiModel get(String modelName) {
        return models.get(normalize(modelName));
    }

    public boolean contains(String modelName) {
        return get(modelName) != null;
    }

    public Collection<ApiModel> values() {
        return List.copyOf(models.values());
    }

    public int size() {
        return models.size();
    }

    private void put(ApiModel model) {
        if(model == null) return;
        models.put(normalize(model.getModelName()), model);
    }

    private ApiModel newModel(Class<? extends ApiModel> modelType, ApiService service) {
        try {
            Constructor<? extends ApiModel> constructor = modelType.getConstructor(ApiService.class);
            return constructor.newInstance(service);
        } catch (NoSuchMethodException ignored) {
            try {
                Constructor<? extends ApiModel> constructor = modelType.getConstructor();
                ApiModel model = constructor.newInstance();
                model.setService(service);
                return model;
            } catch (Exception e) {
                throw new IllegalArgumentException("API model '" + modelType.getName() + "' must provide a public no-arg constructor or a public constructor that accepts ApiService.", e);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to initialize API model '" + modelType.getName() + "'.", e);
        }
    }

    static String normalize(String input) {
        if(input == null || input.isBlank()) return "";
        String output = input.trim().replaceAll("\\s+", "").replaceAll("\\.", "/");
        while(output.startsWith("/")) output = output.substring(1);
        while(output.endsWith("/") && output.length() > 1) output = output.substring(0, output.length() - 1);
        return output.toLowerCase();
    }
}
