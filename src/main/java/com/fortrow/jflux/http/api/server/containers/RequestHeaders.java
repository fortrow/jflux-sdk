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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-10 22:51:05 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.http.api.server.containers;

import com.fortrow.jflux.todash.__;
import com.sun.net.httpserver.Headers;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Map.*;

public class RequestHeaders {

    private HashMap<String, String> values = new HashMap<String, String>();
    private HashSet<String> duplicates = new HashSet<String>();

    public RequestHeaders(Headers headers) {
        if (headers == null) return;
        Set<Entry<String, List<String>>> all = headers.entrySet();
        Iterator<Entry<String, List<String>>> iterator = all.iterator();

        while(iterator.hasNext()) {
            Entry<String, List<String>> curr = iterator.next();
            String key = curr.getKey();
            String normalizedKey = key.toUpperCase(Locale.ROOT);
            List<String> headerValues = curr.getValue();
            if (headerValues == null || headerValues.isEmpty()) continue;
            if (headerValues.size() > 1 || values.containsKey(normalizedKey)) {
                duplicates.add(normalizedKey);
            }
            String value = "";
            for(int i=0; i<headerValues.size(); i++) {
                if(i==0) value += headerValues.get(i);
                else value += "," + headerValues.get(i);
            }
            this.values.put(normalizedKey, value);
        }
    }

    public boolean contains(String key) { return key != null && values.containsKey(key.toUpperCase(Locale.ROOT)); }
    public boolean hasDuplicate(String key) { return key != null && duplicates.contains(key.toUpperCase(Locale.ROOT)); }

    public boolean hasAnyDuplicate(String... keys) {
        for (String key : keys) {
            if (hasDuplicate(key)) {
                return true;
            }
        }
        return false;
    }

    public int getInt(String key) { return Integer.parseInt(values.get(key.toUpperCase())); }
    public long getLong(String key) { return Long.parseLong(values.get(key.toUpperCase())); }

    public String getString(String key) {
        if(!contains(key)) return null;
        String value = values.get(key.toUpperCase(Locale.ROOT));
        return value == null ? null : value.contains(",") ? value.replace(",", ", ") : value;
    }

    public double getDouble(String key) { return Double.parseDouble(values.get(key.toUpperCase())); }

    public boolean getBoolean(String key) throws Exception {
        String val = values.get(key.toUpperCase()).toUpperCase();
        if(val.equals("TRUE") || val.equals("1")) return true;
        else if(val.equals("FALSE") || val.equals("0")) return false;
        throw new Exception();
    }

    public String[] getArr(String key) { return getArr(key, ","); }
    public String[] getArr(String key, String delimiter) {
        String value = getString(key);
        return value == null ? new String[0] : __.split(value, delimiter);
    }
    public Instant getInstantFromTimestamp(String key) {
        String value = getString(key);
        if(value == null || value.isBlank()) return null;
        try {
            return Instant.ofEpochSecond(Long.parseLong(value.trim()));
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
