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
package com.fortrow.jflux.shell.unix;

import com.fortrow.jflux.shell.CommandImpl;
import com.fortrow.jflux.exceptions.ExceptionHandling;
import com.fortrow.jflux.threading.types.DetachedThread;
import com.fortrow.jflux.todash.__;

import java.nio.charset.StandardCharsets;

public abstract class UnixCommand implements CommandImpl {

    @Override
    public void noOut(String cmd) throws Exception {
        com.fortrow.jflux.shell.Shell.startDetached(cmd);
    }

    @Override
    public int runAndWait(String cmd) throws Exception {
        return com.fortrow.jflux.shell.Shell.execute(cmd).exitCode();
    }

    @Override
    public int execute(String cmd) throws Exception {
        com.fortrow.jflux.shell.CommandResult result = com.fortrow.jflux.shell.Shell.execute(cmd);
        for (String line : result.stdout().split("\\R")) {
            if (line.isBlank()) continue;
            lineRead(line, line.getBytes(StandardCharsets.UTF_8));
        }
        return result.exitCode();
    }

    @Override
    public int stream(String cmd, __.Console console) throws Exception {
        com.fortrow.jflux.shell.CommandResult result = com.fortrow.jflux.shell.Shell.execute(cmd);
        for (String line : result.stdout().split("\\R")) {
            if (line.isBlank()) continue;
            console.println(line);
        }
        return result.exitCode();
    }

    @Override
    public DetachedThread threaded(String cmd) throws Exception {
        DetachedThread thread = new DetachedThread() {
            @Override
            protected boolean execute() {
                try {
                    return runAndWait(cmd) == 0;
                } catch (Exception e) { ExceptionHandling.report(e); }
                return false;
            }
        };
        thread.start();
        return thread;
    }
}
