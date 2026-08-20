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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-07 14:11:12 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.sys;

import com.fortrow.jflux.exceptions.ExceptionHandling;
import com.fortrow.jflux.shell.Run;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Platform, runtime, environment, and user-directory helpers. */
public final class PlatformUtils {

    private static volatile String linuxRelease;

    private PlatformUtils() { }

    public static String javaVersion() {
        return property("java.version", "unknown");
    }

    public static String userDirectory() {
        return property("user.dir", ".");
    }

    public static String userHome() {

        return property("user.home", userDirectory());

    }

    public static String arch() {
        return property("os.arch", "unknown");
    }

    public static String user() {
        return property("user.name", "unknown");
    }

    public static String javaHome() {
        return property("java.home", "");
    }

    public static String lineSeparator() {
        return System.lineSeparator();
    }

    public static String fileSeparator() {
        return FileSystems.getDefault().getSeparator();
    }

    public static String pathSeparator() {
        return File.pathSeparator;
    }

    public static boolean isWindows() {
        return osName().toLowerCase(java.util.Locale.ROOT).contains("win");
    }

    public static boolean isMac() {
        String name = osName().toLowerCase(java.util.Locale.ROOT);
        return name.contains("mac") || name.contains("darwin");
    }

    public static boolean isUnix() {
        if (isWindows()) return false;
        String name = osName().toLowerCase(java.util.Locale.ROOT);
        return isMac() || name.contains("nix") || name.contains("nux")
                || name.contains("aix") || name.contains("bsd") || name.contains("sunos");
    }

    public static boolean isLinux() {
        return isUnix() && !isMac() && !osName().toLowerCase(java.util.Locale.ROOT).contains("bsd");
    }

    public static boolean isBsd() {
        return osName().toLowerCase(java.util.Locale.ROOT).contains("bsd");
    }

    public static boolean executorIsPrivileged() {
        if (isUnix()) return "root".equals(user());
        if (isWindows()) {
            try {
                return Run.Windows.waitForStatus("req query \"HKU\\S-1-5-19\"") == 0;
            } catch (RuntimeException e) {
                ExceptionHandling.report(e);
                return false;
            }
        }
        return false;
    }

    public static boolean isPrivileged() {
        return executorIsPrivileged();
    }

    public static String getHiddenInHomePath(String dir) {
        if (dir == null || dir.isBlank()) throw ExceptionHandling.invalid("Home directory name is required.");
        String name = hiddenName(dir);
        return Paths.get(userHome()).resolve(name).toString();
    }

    public static void createHiddenDirectoryInHome(String dir) {
        createHiddenDirectory(dir, userHome());
    }

    public static boolean checkIsHidden(String path) {
        if (path == null || path.isBlank()) return false;
        File file = new File(path);
        if(!file.exists()) return false;
        String filename = file.getName();
        if (filename.isEmpty()) return false;
        if(isUnix()) return filename.charAt(0) == '.';
        else {
            Path p = Paths.get(path);
            try {
                Object hidden = Files.getAttribute(p, "dos:hidden");
                return hidden instanceof Boolean && (Boolean) hidden;
            } catch (IOException | UnsupportedOperationException e) {
                ExceptionHandling.report(e);
                return false;
            }
        }
    }

    public static void createHiddenDirectory(String dir, String location) {
        if (dir == null || dir.isBlank()) throw ExceptionHandling.invalid("Directory name is required.");
        if (location == null || location.isBlank()) throw ExceptionHandling.invalid("Directory location is required.");
        try {
            Path path = Paths.get(location).resolve(hiddenName(dir));
            Files.createDirectories(path);
            if (isWindows()) Files.setAttribute(path, "dos:hidden", true);
        } catch (IOException e) {
            throw ExceptionHandling.failure("Unable to create hidden directory.", e);
        }
    }

    public static String osInfo() {
        return osName();
    }

    public static String osVersion() {
        return property("os.version", "unknown");
    }

    public static String osName() {
        return property("os.name", "unknown");
    }

    public static OpSys getOsType() {
        return OSUtil.get(false);
    }

    public static OpSys getOsType(boolean withLinuxDistro) {
        return OSUtil.get(withLinuxDistro);
    }

    public enum OpSys {
        WINDOWS,
        MAC_OSX,
        LINUX,
        ALPINE_LINUX,
        ARCH_LINUX,
        DEBIAN_LINUX,
        FREE_BSD_LINUX,
        REDHAT_LINUX,
        MINT_LINUX,
        UBUNTU_LINUX,
        ZORIN_LINUX,
        POP_OS_LINUX,
        ELEMENTARY_LINUX,
        FEDORA_LINUX,
        OPENSUS_LINUX,
        CENTOS_LINUX,
        ROCKY_LINUX,
        ALMA_LINUX,
        ORACLE_LINUX,
        AMAZON_LINUX,
        KALI_LINUX,
        PARROT_LINUX,
        MX_LINUX,
        RASPBIAN_LINUX,
        MANJARO_LINUX,
        ENDEAVOUROS_LINUX,
        GARUDA_LINUX,
        GENTOO_LINUX,
        NIXOS_LINUX,
        VOID_LINUX,
        SLACKWARE_LINUX,
        UNKNOWN
    }

    private static class OSUtil {

        public static OpSys parse(OpSys sys) {
            switch(sys) {
                case MAC_OSX:
                    return OpSys.MAC_OSX;
                case WINDOWS:
                    return OpSys.WINDOWS;
                case UNKNOWN:
                    return OpSys.UNKNOWN;
                default:
                    return OpSys.LINUX;
            }
        }

        public static OpSys get(boolean getLinuxDistros) {
            if(isWindows()) return OpSys.WINDOWS;
            if(isMac()) return OpSys.MAC_OSX;
            if(isUnix() && getLinuxDistros) return getLinux();
            if(isUnix() && !getLinuxDistros) return OpSys.LINUX;
            return OpSys.UNKNOWN;
        }

        public static OpSys getLinux() {
            if(!isUnix()) return OpSys.UNKNOWN;
            String os = (linuxReleaseInfo() + "\n" + osName()).toUpperCase();
            if(os.contains("ZORIN")) return OpSys.ZORIN_LINUX;
            if(os.contains("POP!_OS") || os.contains("POP_OS") || os.contains("POP OS")) return OpSys.POP_OS_LINUX;
            if(os.contains("ELEMENTARY")) return OpSys.ELEMENTARY_LINUX;
            if(os.contains("LINUX MINT") || os.contains("ID=MINT") || os.contains(" MINT")) return OpSys.MINT_LINUX;
            if(os.contains("UBUNTU")) return OpSys.UBUNTU_LINUX;
            if(os.contains("KALI")) return OpSys.KALI_LINUX;
            if(os.contains("PARROT")) return OpSys.PARROT_LINUX;
            if(os.contains("MX LINUX") || os.contains("ID=MX")) return OpSys.MX_LINUX;
            if(os.contains("RASPBIAN") || os.contains("RASPBERRY PI")) return OpSys.RASPBIAN_LINUX;
            if(os.contains("DEBIAN")) return OpSys.DEBIAN_LINUX;
            if(os.contains("MANJARO")) return OpSys.MANJARO_LINUX;
            if(os.contains("ENDEAVOUROS")) return OpSys.ENDEAVOUROS_LINUX;
            if(os.contains("GARUDA")) return OpSys.GARUDA_LINUX;
            if(os.contains("ARCH")) return OpSys.ARCH_LINUX;
            if(os.contains("ALPINE")) return OpSys.ALPINE_LINUX;
            if(os.contains("FEDORA")) return OpSys.FEDORA_LINUX;
            if(os.contains("ROCKY")) return OpSys.ROCKY_LINUX;
            if(os.contains("ALMA")) return OpSys.ALMA_LINUX;
            if(os.contains("ORACLE")) return OpSys.ORACLE_LINUX;
            if(os.contains("AMAZON")) return OpSys.AMAZON_LINUX;
            if(os.contains("CENTOS")) return OpSys.CENTOS_LINUX;
            if(os.contains("RED HAT") || os.contains("REDHAT") || os.contains("ID=RHEL") || os.contains("ID_LIKE=RHEL")) return OpSys.REDHAT_LINUX;
            if(os.contains("OPENSUSE") || os.contains("SLES") || os.contains("SUSE")) return OpSys.OPENSUS_LINUX;
            if(os.contains("FREEBSD")) return OpSys.FREE_BSD_LINUX;
            if(os.contains("GENTOO")) return OpSys.GENTOO_LINUX;
            if(os.contains("NIXOS")) return OpSys.NIXOS_LINUX;
            if(os.contains("VOID")) return OpSys.VOID_LINUX;
            if(os.contains("SLACKWARE")) return OpSys.SLACKWARE_LINUX;
            if(os.contains("LINUX")) return OpSys.LINUX;
            return OpSys.UNKNOWN;
        }

        private static String linuxReleaseInfo() {
            String cached = linuxRelease;
            if (cached != null) return cached;
            Path osRelease = Paths.get("/etc/os-release");
            Path lsbRelease = Paths.get("/etc/lsb-release");
            try {
                if(Files.exists(osRelease)) cached = Files.readString(osRelease);
                else if(Files.exists(lsbRelease)) cached = Files.readString(lsbRelease);
                else cached = "";
            } catch (IOException e) {
                ExceptionHandling.report(e);
                cached = "";
            }
            linuxRelease = cached;
            return cached;
        }

    }

    public static String property(String name, String fallback) {
        if (name == null || name.isBlank()) throw ExceptionHandling.invalid("System property name is required.");
        try {
            String value = System.getProperty(name);
            return value == null || value.isBlank() ? fallback : value;
        } catch (SecurityException e) {
            ExceptionHandling.report(e);
            return fallback;
        }
    }

    public static String environment(String name, String fallback) {
        if (name == null || name.isBlank()) throw ExceptionHandling.invalid("Environment variable name is required.");
        try {
            String value = System.getenv(name);
            return value == null || value.isBlank() ? fallback : value;
        } catch (SecurityException e) {
            ExceptionHandling.report(e);
            return fallback;
        }
    }

    public static Map<String, String> environment() {
        try {
            return Collections.unmodifiableMap(new HashMap<>(System.getenv()));
        } catch (SecurityException e) {
            ExceptionHandling.report(e);
            return Map.of();
        }
    }

    public static Path tempDirectory() {
        return Paths.get(property("java.io.tmpdir", ".")).toAbsolutePath().normalize();
    }

    public static long processId() {
        return ProcessHandle.current().pid();
    }

    public static int availableProcessors() {
        return Runtime.getRuntime().availableProcessors();
    }

    public static PlatformSnapshot snapshot() {
        return new PlatformSnapshot(
                getOsType(true), osName(), osVersion(), arch(), javaVersion(), javaHome(),
                user(), userHome(), userDirectory(), fileSeparator(), lineSeparator());
    }

    public record PlatformSnapshot(
            OpSys type,
            String name,
            String version,
            String architecture,
            String javaVersion,
            String javaHome,
            String user,
            String userHome,
            String userDirectory,
            String fileSeparator,
            String lineSeparator) { }

    private static String hiddenName(String dir) {
        String normalized = dir.replace('\\', '/');
        String name = normalized.substring(normalized.lastIndexOf('/') + 1);
        if (name.isBlank() || ".".equals(name) || "..".equals(name)) {
            throw ExceptionHandling.invalid("Invalid hidden directory name.");
        }
        return isUnix() && !name.startsWith(".") ? "." + name : name;
    }

}
