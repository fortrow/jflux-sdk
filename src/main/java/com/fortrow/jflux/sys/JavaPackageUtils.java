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

package com.fortrow.jflux.sys;

import com.fortrow.jflux.exceptions.ExceptionHandling;

import java.net.URL;
import java.security.CodeSource;

/** Java package, classpath resource, module, and implementation metadata helpers. */
public final class JavaPackageUtils {

    private JavaPackageUtils() { }

    public static String packageName(Class<?> type) {
        requireType(type);
        Package value = type.getPackage();
        return value == null ? "" : value.getName();
    }

    public static String simpleName(Class<?> type) {
        requireType(type);
        return type.getSimpleName();
    }

    public static String className(Class<?> type) {
        requireType(type);
        return type.getName();
    }

    public static String packagePath(String packageName) {
        if (packageName == null || packageName.isBlank()) return "";
        return packageName.replace('.', '/');
    }

    /** Returns a classpath resource path relative to an anchor class's package. */
    public static String resourcePath(Class<?> anchor, String resource) {
        requireType(anchor);
        if (resource == null || resource.isBlank()) throw ExceptionHandling.invalid("Resource name is required.");
        String clean = resource.startsWith("/") ? resource.substring(1) : resource;
        if (resource.startsWith("/")) return clean;
        String packagePath = packagePath(packageName(anchor));
        return packagePath.isEmpty() ? clean : packagePath + "/" + clean;
    }

    public static URL resource(Class<?> anchor, String resource) {
        String path = resourcePath(anchor, resource);
        ClassLoader loader = anchor.getClassLoader();
        return loader == null ? ClassLoader.getSystemResource(path) : loader.getResource(path);
    }

    public static URL classLocation(Class<?> type) {
        requireType(type);
        try {
            CodeSource source = type.getProtectionDomain().getCodeSource();
            return source == null ? null : source.getLocation();
        } catch (SecurityException e) {
            ExceptionHandling.report(e);
            return null;
        }
    }

    public static String implementationVersion(Class<?> type) {
        requireType(type);
        Package value = type.getPackage();
        return value == null ? null : value.getImplementationVersion();
    }

    public static String moduleName(Class<?> type) {
        requireType(type);
        Module module = type.getModule();
        return module.isNamed() ? module.getName() : null;
    }

    public static boolean isInPackage(Class<?> type, String packageName, boolean includeChildren) {
        requireType(type);
        if (packageName == null || packageName.isBlank()) return false;
        String actual = packageName(type);
        return includeChildren
                ? actual.equals(packageName) || actual.startsWith(packageName + ".")
                : actual.equals(packageName);
    }

    private static void requireType(Class<?> type) {
        if (type == null) throw ExceptionHandling.invalid("Class type is required.");
    }
}
