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

package com.fortrow.jflux.jni;

import com.fortrow.jflux.exceptions.ExceptionHandling;
import com.fortrow.jflux.sys.PlatformUtils;

import java.util.Locale;

/**
 * Normalized operating-system and CPU information used by JNI resource and
 * build helpers. The values are deliberately independent of the host JVM's
 * vendor-specific platform strings.
 */
public record JNIPlatform(OperatingSystem operatingSystem, Architecture architecture) {

    public JNIPlatform {
        operatingSystem = operatingSystem == null ? OperatingSystem.UNKNOWN : operatingSystem;
        architecture = architecture == null ? Architecture.UNKNOWN : architecture;
    }

    /** Returns the normalized platform reported by the current JVM. */
    public static JNIPlatform current() {
        return of(PlatformUtils.osName(), PlatformUtils.arch());
    }

    /** Normalizes an operating-system and architecture pair. */
    public static JNIPlatform of(String osName, String osArch) {
        return new JNIPlatform(OperatingSystem.from(osName), Architecture.from(osArch));
    }

    /** Whether the operating system has a known JNI naming and include convention. */
    public boolean supportedOperatingSystem() {
        return operatingSystem != OperatingSystem.UNKNOWN;
    }

    /** Whether both the operating system and architecture are recognized. */
    public boolean supported() {
        return supportedOperatingSystem() && architecture != Architecture.UNKNOWN;
    }

    /** Returns the native library filename for a logical name such as {@code telemetry}. */
    public String libraryFileName(String logicalName) {
        String name = validateLogicalName(logicalName);
        return switch (operatingSystem) {
            case WINDOWS -> name + ".dll";
            case MACOS -> "lib" + name + ".dylib";
            case LINUX, FREEBSD, UNKNOWN -> "lib" + name + ".so";
        };
    }

    /**
     * Returns the conventional classpath resource path for a bundled library.
     * For example: {@code native/linux/x86_64/libtelemetry.so}.
     */
    public String resourcePath(String logicalName) {
        return "native/" + operatingSystem.resourceId() + "/" + architecture.resourceId()
                + "/" + libraryFileName(logicalName);
    }

    /** Returns the directory name used by the JDK's {@code include} directory. */
    public String javaIncludeDirectory() {
        return operatingSystem.javaIncludeDirectory();
    }

    /** Returns the normalized platform identifier used in diagnostics and build output. */
    public String identifier() {
        return operatingSystem.resourceId() + "/" + architecture.resourceId();
    }

    private static String validateLogicalName(String logicalName) {
        if (logicalName == null || logicalName.isBlank()) {
            throw ExceptionHandling.invalid("JNI library name is required.");
        }
        if (!logicalName.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,95}")) {
            throw ExceptionHandling.invalid("JNI library name contains unsupported characters.");
        }
        return logicalName;
    }

    /** Supported native operating systems and their JNI resource identifiers. */
    public enum OperatingSystem {
        WINDOWS("windows", "win32"),
        MACOS("macos", "darwin"),
        LINUX("linux", "linux"),
        FREEBSD("freebsd", "freebsd"),
        UNKNOWN("unknown", "unknown");

        private final String resourceId;
        private final String javaIncludeDirectory;

        OperatingSystem(String resourceId, String javaIncludeDirectory) {
            this.resourceId = resourceId;
            this.javaIncludeDirectory = javaIncludeDirectory;
        }

        public String resourceId() {
            return resourceId;
        }

        public String javaIncludeDirectory() {
            return javaIncludeDirectory;
        }

        private static OperatingSystem from(String value) {
            String normalized = normalize(value);
            if (normalized.contains("mac") || normalized.contains("darwin") || normalized.contains("os x")) {
                return MACOS;
            }
            if (normalized.contains("win")) return WINDOWS;
            if (normalized.contains("linux")) return LINUX;
            if (normalized.contains("freebsd")) return FREEBSD;
            return UNKNOWN;
        }
    }

    /** Common architectures emitted by supported JVM distributions. */
    public enum Architecture {
        X86_64("x86_64"),
        X86("x86"),
        ARM64("aarch64"),
        ARM32("arm"),
        RISCV64("riscv64"),
        PPC64LE("ppc64le"),
        S390X("s390x"),
        UNKNOWN("unknown");

        private final String resourceId;

        Architecture(String resourceId) {
            this.resourceId = resourceId;
        }

        public String resourceId() {
            return resourceId;
        }

        private static Architecture from(String value) {
            String normalized = normalize(value).replace('-', '_');
            return switch (normalized) {
                case "amd64", "x86_64", "x8664" -> X86_64;
                case "x86", "i386", "i486", "i586", "i686", "x8632" -> X86;
                case "aarch64", "arm64" -> ARM64;
                case "arm", "arm32", "armv6", "armv7", "armv7l" -> ARM32;
                case "riscv64", "risc_v64" -> RISCV64;
                case "ppc64le", "powerpc64le" -> PPC64LE;
                case "s390x" -> S390X;
                default -> UNKNOWN;
            };
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
