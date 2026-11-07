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

import com.fortrow.jflux.sys.PlatformUtils;

import java.util.ArrayList;
import java.util.HashMap;

public class CommandGroup {

    private HashMap<PlatformUtils.OpSys, ArrayList<Group>> groups = new HashMap<>();

    public CommandGroup() { }

    public ArrayList<Group> get(PlatformUtils.OpSys os) {
        if(!groups.containsKey(os)) return new ArrayList<>();
        return groups.get(os);
    }

    public Group windowsGroup(Shell shell) {
        return new Group(shell);
    }

    public Group macOsGroup() {
        return new Group(Shell.UNIX_SHELL);
    }

    public Group linuxGroup() {
        return new Group(Shell.UNIX_SHELL);
    }

    public void appendGroup(Group group, PlatformUtils.OpSys os) {
        if(!groups.containsKey(os)) groups.put(os, new ArrayList<>());
        groups.get(os).add(group);
    }

    public static class Group {
        private Shell shell;
        private ArrayList<String> commands = new ArrayList<>();
        private String directory = ".";

        public Group(Shell shell) {
            this.shell = shell;
        }

        public Group(Shell shell, String directory) {
            this.shell = shell;
            setDirectory(directory);
        }

        public void setDirectory(String directory) {
            this.directory = directory;
        }

        public void appendCommand(String cmd) {
            commands.add(cmd);
        }

        public ArrayList<String> getCommands() {
            return commands;
        }

        public Shell getShell() {
            return shell;
        }

        public String getDirectory() {
            return directory;
        }

    }

    /** @deprecated use {@link Shell} argument-list execution instead. */
    @Deprecated(forRemoval = false)
    public static String[] getProcessBuilderArgs(Shell shell) {
        if(shell == Shell.COMMAND_PROMPT) {
            return new String[]{"cmd.exe", "/c"};
        } else if(shell == Shell.UNIX_SHELL) {
            return new String[]{"/bin/bash", "-c"};
        }
        return new String[]{"", ""};
    }

    public enum Shell {
        POWERSHELL,
        COMMAND_PROMPT,
        UNIX_SHELL
    }

}
