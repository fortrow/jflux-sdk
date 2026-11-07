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

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class APIResources {

    private ArrayList<String> resources = new ArrayList<String>();
    private ArrayList<APIResource> definitions = new ArrayList<APIResource>();

    public void removeAt(int index) {
        resources.remove(index);
        definitions.remove(index);
    }

    public ArrayList<String> getResourcesList() {
        return resources;
    }

    public int getSize() {
        return resources.size();
    }

    public int getIndex(String resource) {
        return getIndex(resource, null);
    }

    public int getIndex(String resource, String requestMethod) {
        for(int i = 0; i < resources.size(); i++) {
            if(!pathMatches(resources.get(i), resource)) continue;
            if(requestMethod == null || requestMethod.isBlank()) return i;
            if(definitions.get(i).getRequestMethod().equalsIgnoreCase(requestMethod)) return i;
        }
        return -1;
    }

    public boolean inList(String resource) {
        if(resource == null) return false;
        if(resource.equals("/") || resource.equals("")) return resources.contains("/") || resources.contains("");
        return getIndex(resource) >= 0;
    }

    public boolean inList(String resource, String requestMethod) {
        return getIndex(resource, requestMethod) >= 0;
    }

    public APIResource getResource(String value) {
        int index = getIndex(value);
        return index < 0 ? null : definitions.get(index);
    }

    public APIResource getResource(String value, String requestMethod) {
        int index = getIndex(value, requestMethod);
        return index < 0 ? null : definitions.get(index);
    }

    public String getResourcePath(String value) {
        int index = getIndex(value);
        if(index < 0) return value;
        return resources.get(index);
    }

    public void addResource(String path, APIResource definition) {
        resources.add(normalize(path));
        definitions.add(definition);
    }

    public void addResource(String path, String[] parameters, APIResource definition) {
        addResource(path, definition);
        definition.setParameters(parameters);
    }

    public void addResource(String path, ArrayList<String> parameters, APIResource definition) {
        addResource(path, definition);
        definition.setParameters(parameters);
    }

    public void addResource(String path, boolean isAsync, APIResource definition) {
        addResource(path, definition);
        definition.isAsync(isAsync);
    }

    public void addResource(String path, boolean isAsync, String[] parameters, APIResource definition) {
        addResource(path, definition);
        definition.setParameters(parameters);
        definition.isAsync(isAsync);
    }

    public void addResource(String path, boolean isAsync, ArrayList<String> parameters, APIResource definition) {
        addResource(path, definition);
        definition.setParameters(parameters);
        definition.isAsync(isAsync);
    }

    private static boolean pathMatches(String expected, String actual) {
        String expectedNormalized = normalize(expected);
        String actualNormalized = normalize(actual);
        if(expectedNormalized.equals(actualNormalized)) return true;

        String[] expectedParts = split(expectedNormalized);
        String[] actualParts = split(actualNormalized);
        if(expectedParts.length != actualParts.length) return false;

        Pattern variablePattern = Pattern.compile("\\{.*\\}");
        for(int i = 0; i < expectedParts.length; i++) {
            String expectedPart = expectedParts[i];
            Matcher matcher = variablePattern.matcher(expectedPart);
            if(matcher.matches()) continue;
            if(!expectedPart.equals(actualParts[i])) return false;
        }
        return true;
    }

    private static String[] split(String input) {
        if(input == null || input.isBlank()) return new String[0];
        return input.split("/");
    }

    private static String normalize(String input) {
        if(input == null || input.isBlank()) return "";
        String output = input.trim().replaceAll("\\s+", "").replaceAll("\\.", "/");
        while(output.startsWith("/")) output = output.substring(1);
        while(output.endsWith("/") && output.length() > 1) output = output.substring(0, output.length() - 1);
        return output;
    }

}
