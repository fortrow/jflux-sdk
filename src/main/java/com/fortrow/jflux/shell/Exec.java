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
package com.fortrow.jflux.shell;

import com.fortrow.jflux.exceptions.ExceptionHandling;

import java.io.File;
import java.io.IOException;

public class Exec {

    public static void noOut(String cmd) throws IOException {
        Shell.startDetached(cmd);
    }

    public static void runAndWait(String cmd) throws IOException, InterruptedException {
        Shell.execute(cmd);
    }

    public static String[] withOut(String cmd) throws IOException, InterruptedException {
        String output = Shell.execute(cmd).stdout();
        return output.isBlank() ? new String[]{""} : output.split("\\R");

    }

    public static String checkOsPath(String path) {
        if(getOs() == OpSys.OS.WINDOWS) {
            return path.replace("/", "\\");
        } else {
            return path.replace("\\", "/");
        }
    }

    public static int background(String cmd) throws IOException, InterruptedException {
        Shell.startDetached(cmd);
        return -1;

    }

    public static OpSys.OS getOs() {
        return OpSys.getOS();
    }

    public static String getJarPath() {

        try {

            String path = new File(Exec.class.getProtectionDomain().getCodeSource().getLocation()
                    .toURI()).getPath();

            path = path.replaceAll("((\\/||\\\\)[a-zA-Z0-9]+\\.jar)", "");

            path = ((getOs() == OpSys.OS.WINDOWS) ? "\\" : "/") + path;

            return path;

        } catch(Exception ex) {
            ExceptionHandling.report(ex);
            return "";
        }

    }

    public static int getUnixUID() {

        try {

            String userName = System.getProperty("user.name");

            String[] out = Shell.execute(java.util.List.of("id", "-u", userName)).stdout().split("\\R");

            return Integer.parseInt(out[0]);

        } catch (Exception ex) {
            ExceptionHandling.report(ex);
            return -1;
        }

    }

    public static class OpSys {
        public enum OS {
            WINDOWS, LINUX, MAC, SOLARIS
        };// Operating systems.

        private static OS os = null;

        public static OS getOS() {
            if (os == null) {
                String operSys = System.getProperty("os.name").toLowerCase();
                if (operSys.contains("win")) {
                    os = OS.WINDOWS;
                } else if (operSys.contains("nix") || operSys.contains("nux")
                        || operSys.contains("aix")) {
                    os = OS.LINUX;
                } else if (operSys.contains("mac")) {
                    os = OS.MAC;
                } else if (operSys.contains("sunos")) {
                    os = OS.SOLARIS;
                }
            }
            return os;
        }
    }

}
