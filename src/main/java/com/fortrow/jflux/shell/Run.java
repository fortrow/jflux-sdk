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

public abstract class Run {

    public abstract void lineRead(String str, byte[] bytes);

//    public static JoinedThread group(CommandGroup group) {
//        JoinedThread thread = new JoinedThread() {
//            @Override
//            protected boolean execute() {
//                ArrayList<CommandGroup.Group> groups = group.get(PlatformUtils.getOsType());
//                for(CommandGroup.Group group : groups) {
//                    try {
//                        String dir = group.getDirectory();
//                        CommandGroup.Shell shell = group.getShell();
//                        ArrayList<String> commands = group.getCommands();
//                        for (String command : commands) {
//                            runFromGroup(shell, dir, command);
//                            System.out.println(command);
//                        }
//                    } catch (Exception e) {
//                        e.printStackTrace();
//                    }
//                }
//                return false;
//            }
//        };
//        thread.start();
//        return thread;
//    }
//
//    public static void noOut(String unixCmd, String windowsCmd, String powerShellCmd) {
//
//    }
//
//    private static int runFromGroup(CommandGroup.Shell shell, String directory, String command) {
//        try {
//            String[] processBuilderArgs = CommandGroup.getProcessBuilderArgs(shell);
//            String cd = (directory.equals(".") ? "" : "cd " + directory + " && ");
//            ProcessBuilder builder = new ProcessBuilder(
//                    processBuilderArgs[0], processBuilderArgs[1], cd + command);
//            builder.redirectErrorStream(true);
//            Process p = builder.start();
//            p.waitFor();
//            return p.exitValue();
//        } catch (Exception e) {
//            return -1;
//        }
//    }
//
//    public static int runAndWait(String unixCmd, String windowsCmd, WindowsShell shell) {
//        PlatformUtils.OpSys os = PlatformUtils.getOsType(false);
//        if(os == PlatformUtils.OpSys.WINDOWS) {
//            if(shell == WindowsShell.POWERSHELL) {
//                try {
//                    ProcessBuilder pb = new ProcessBuilder("powershell.exe", "/c", windowsCmd);
//                    Process process = pb.start();
//                    return process.waitFor();
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//            } else if(shell == WindowsShell.COMMAND_PROMPT) {
//                try {
//                    ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", windowsCmd);
//                    Process process = pb.start();
//                    return process.waitFor();
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//            }
//        } else if(os == PlatformUtils.OpSys.LINUX || os == PlatformUtils.OpSys.MAC_OSX) {
//            try {
//                ProcessBuilder pb = new ProcessBuilder("/bin/bash", "-c", unixCmd);
//                Process process = pb.start();
//                return process.waitFor();
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        }
//        return -1;
//    }
//
//    public static int stream(String unixCmd, String windowsCmd, String powerShellCmd, __.Console console) {
//        return -1;
//    }

    public static class CrossPlatform {
        public static String runAndWait(String cmd) {
            return Shell.execute(cmd).stdout();
        }

        public static int waitForStatus(String cmd) {
            return Shell.execute(cmd).exitCode();
        }
    }

    public static class Linux {

        public static String runAndWait(String cmd) {
            return Shell.execute(cmd).stdout();
        }

        public static int waitForStatus(String cmd) {
            return Shell.execute(cmd).exitCode();
        }

    }

    public static class Mac {

        public static String runAndWait(String cmd) {
            return Shell.execute(cmd).stdout();
        }

        public static int waitForStatus(String cmd) {
            return Shell.execute(cmd).exitCode();
        }

    }

    public static class Windows {

        public static String runAndWait(String cmd) {
            return Shell.execute(cmd).stdout();
        }

        public static String runAndWaitPS(String cmd) {
            return Shell.execute(cmd, WindowsShell.POWERSHELL, Shell.DEFAULT_TIMEOUT).stdout();
        }

        public static int waitForStatus(String cmd) {
            return Shell.execute(cmd).exitCode();
        }

        public static int waitForStatusPS(String cmd) {
            return Shell.execute(cmd, WindowsShell.POWERSHELL, Shell.DEFAULT_TIMEOUT).exitCode();
        }

    }

}
