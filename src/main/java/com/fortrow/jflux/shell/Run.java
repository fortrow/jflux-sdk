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
