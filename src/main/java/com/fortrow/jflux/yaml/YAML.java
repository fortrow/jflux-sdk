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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-07 14:01:51 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.yaml;

import com.fortrow.jflux.exceptions.types.CommonIOException;
import com.fortrow.jflux.files.FileObject;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.snakeyaml.engine.v2.api.Dump;
import org.snakeyaml.engine.v2.api.DumpSettings;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

import java.util.List;
import java.util.Map;

public class YAML {

    public static Object parse(String yaml) {
        Load loader = new Load(LoadSettings.builder().build());
        return loader.loadFromString(yaml);
    }

    public static Object parse(FileObject file) {
        file.readToEnd();
        return parse(new String(file.getBytes(file.size())));
    }

    public static Object parseFile(String filepath) throws CommonIOException {
        return parse(com.fortrow.jflux.files.Files.open(filepath));
    }

    public static <Any> Any parse(String yaml, Class<Any> c) {
        Object parsed = parse(yaml);
        Gson gson = new Gson();
        return gson.fromJson(gson.toJson(parsed), c);
    }

    public static <Any> Any parse(FileObject file, Class<Any> c) {
        Object parsed = parse(file);
        Gson gson = new Gson();
        return gson.fromJson(gson.toJson(parsed), c);
    }

    public static Map<String, Object> map(String yaml) {
        Object parsed = parse(yaml);
        if(parsed instanceof Map<?, ?>) return (Map<String, Object>) parsed;
        throw new IllegalArgumentException("YAML root is not an object/map.");
    }

    public static List<Object> list(String yaml) {
        Object parsed = parse(yaml);
        if(parsed instanceof List<?>) return (List<Object>) parsed;
        throw new IllegalArgumentException("YAML root is not a list.");
    }

    public static String string(Object value) {
        Dump dumper = new Dump(DumpSettings.builder().build());
        return dumper.dumpToString(value);
    }

    public static String jsonString(String yaml) {
        return new Gson().toJson(parse(yaml));
    }

    public static String prettyJsonString(String yaml) {
        return new GsonBuilder().setPrettyPrinting().create().toJson(parse(yaml));
    }
}
