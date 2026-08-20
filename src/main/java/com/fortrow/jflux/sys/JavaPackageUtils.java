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
