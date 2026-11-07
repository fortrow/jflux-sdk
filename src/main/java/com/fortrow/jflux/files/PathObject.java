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
 */

package com.fortrow.jflux.files;

import com.fortrow.jflux.exceptions.ExceptionHandling;

import com.fortrow.jflux.sys.PlatformUtils;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A directory-like path with optional {variable} substitutions. */
public class PathObject {

    private final String value;
    private final ArrayList<PathVariable> variables;

    public PathObject(String value) {
        this(value, parseVariables(value));
    }

    public PathObject(String value, ArrayList<PathVariable> variables) {
        if (value == null) throw ExceptionHandling.invalid("Path is required.");
        this.value = PathUtil.fixDirectoryPath(value);
        this.variables = copyVariables(variables);
    }

    public PathObject(String value, ArrayList<PathVariable> variables, String... append) {
        this(join(value, append), variables);
    }

    private static ArrayList<PathVariable> parseVariables(String value) {
        ArrayList<PathVariable> list = new ArrayList<>();
        Matcher matcher = Pattern.compile("\\{([^{}]+)}").matcher(value == null ? "" : value);
        while (matcher.find()) list.add(new PathVariable(matcher.group(1)));
        return list;
    }

    public PathObject setVariable(String key, String value) {
        for (PathVariable variable : variables) {
            if (variable.getKey().equals(key)) {
                variable.set(value);
                break;
            }
        }
        return this;
    }

    public String getString() {
        String result = value;
        for (PathVariable variable : variables) {
            if (variable.isset()) result = result.replace("{" + variable.getKey() + "}", variable.getValue());
        }
        return result;
    }

    public Path toPath() {
        return Path.of(getString()).normalize();
    }

    public URI getUri() {
        return toPath().toUri();
    }

    public void mkdirs() {
        try {
            Files.createDirectories(toPath());
        } catch (Exception e) {
            throw ExceptionHandling.failure("Unable to create directory '" + getString() + "'.", e);
        }
    }

    public PathObject clone() {
        return new PathObject(this.value, this.variables);
    }

    public PathObject clone(String... append) {
        return new PathObject(this.value, this.variables, append);
    }

    public PathObject cloneAndCreate(String... append) {
        PathObject path = clone(append);
        path.mkdirs();
        return path;
    }

    private static ArrayList<PathVariable> copyVariables(ArrayList<PathVariable> source) {
        ArrayList<PathVariable> result = new ArrayList<>();
        if (source != null) for (PathVariable variable : source) result.add(variable.copy());
        return result;
    }

    private static String join(String base, String... append) {
        String result = base;
        if (append != null) {
            for (String part : append) {
                if (part == null || part.isEmpty()) continue;
                if (!result.endsWith(PlatformUtils.fileSeparator())) result += PlatformUtils.fileSeparator();
                result += part;
            }
        }
        return result;
    }
}
