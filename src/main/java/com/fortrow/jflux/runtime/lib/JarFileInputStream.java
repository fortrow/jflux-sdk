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
package com.fortrow.jflux.runtime.lib;

import java.io.*;

public class JarFileInputStream extends InputStream {

    private ByteArrayInputStream bis;
    private byte[] data;

    public JarFileInputStream(String path) throws Exception {

        bis = new ByteArrayInputStream(new byte[]{});
        FileInputStream in = new FileInputStream(path);
        BufferedInputStream bin = new BufferedInputStream(in);
        ByteArrayOutputStream ba = new ByteArrayOutputStream();

        byte[] bite = new byte[1];
        while(bin.read(bite, 0, 1) > 0) {
            ba.write(bite[0]);
            bite = new byte[1];
        }
        bis = new ByteArrayInputStream(ba.toByteArray());
    }

    @Override
    public int read() throws IOException {
        return this.bis.read();
    }

    @Override
    public int read(byte[] toStore) throws IOException {
        return this.bis.read(toStore);
    }

    @Override
    public int read(byte[] toStore, int off, int len) throws IOException {
        return this.bis.read(toStore, off, len);
    }

}
