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

import com.fortrow.jflux.threading.types.DetachedThread;
import com.fortrow.jflux.todash.__;

public interface CommandImpl {

    void lineRead(String str, byte[] bytes);
    void noOut(String cmd) throws Exception;
    int runAndWait(String cmd) throws Exception;
    int execute(String cmd) throws Exception;

    int stream(String cmd, __.Console console) throws Exception;

    DetachedThread threaded(String cmd) throws Exception;

}
