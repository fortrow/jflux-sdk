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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-04 15:27:04 EDT
 * 
 * -------------------------------------------------------------------------

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
