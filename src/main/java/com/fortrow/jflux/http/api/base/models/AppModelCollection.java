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
