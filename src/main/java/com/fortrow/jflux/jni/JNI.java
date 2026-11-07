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

/** Compact entry point for the common JNI platform, loading, and build operations. */
public final class JNI {

    private JNI() { }

    /** Returns the current normalized native platform. */
    public static JNIPlatform platform() {
        return JNIPlatform.current();
    }

    /** Loads a logical native library using the default resource strategy. */
    public static NativeLibraryLoadResult load(String logicalName) {
        return NativeLibraryLoader.load(logicalName);
    }

    /** Starts a portable JNI build specification for a logical library name. */
    public static JNIBuilder builder(String logicalName) {
        return JNIBuilder.forLibrary(logicalName);
    }

    /** Generates JNI C declarations for native methods declared by a class. */
    public static String header(Class<?> nativeClass) {
        return JNIHeaderGenerator.generate(nativeClass);
    }

    /** Generates a compilable C stub template for native methods declared by a class. */
    public static String sourceTemplate(Class<?> nativeClass) {
        return JNIHeaderGenerator.generateSourceTemplate(nativeClass);
    }
}
