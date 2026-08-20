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
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-05 10:54:51 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.files.volumes.supporting;

import com.fortrow.jflux.exceptions.types.CommonIOException;
import com.fortrow.jflux.sys.PlatformUtils;

public interface VolumeImpl {
    default VolumeStatus mount() throws CommonIOException {
        if (PlatformUtils.isWindows()) return mountWindows();
        if (PlatformUtils.isMac()) return mountMac();
        if (PlatformUtils.isUnix()) return mountLinux();
        return VolumeStatus.UNSUPPORTED;
    }
    default VolumeStatus umount() throws CommonIOException {
        if (PlatformUtils.isWindows()) return umountWindows();
        if (PlatformUtils.isMac()) return umountMac();
        if (PlatformUtils.isUnix()) return umountLinux();
        return VolumeStatus.UNSUPPORTED;
    }
    VolumeStatus mountWindows() throws CommonIOException;
    VolumeStatus mountMac() throws CommonIOException;
    VolumeStatus mountLinux() throws CommonIOException;
    VolumeStatus umountWindows() throws CommonIOException;
    VolumeStatus umountMac() throws CommonIOException;
    VolumeStatus umountLinux() throws CommonIOException;
}
