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
package com.fortrow.jflux.files;

import com.fortrow.jflux.sys.PlatformUtils;

import java.util.HashMap;

public class PlatformPath {

    private HashMap<PlatformUtils.OpSys, PathObject> map = new HashMap<>();

    public PlatformPath() { }

    public void addPlatform(PlatformUtils.OpSys os, PathObject path) {
        map.put(os, path);
    }

    public PathObject get() {
        PlatformUtils.OpSys os = PlatformUtils.getOsType(containsDistros());
        return map.get(os);
    }

    private boolean containsDistros() {
        for(PlatformUtils.OpSys os : map.keySet()) {
            switch(os) {
                case ALPINE_LINUX:
                case ARCH_LINUX:
                case DEBIAN_LINUX:
                case FREE_BSD_LINUX:
                case REDHAT_LINUX:
                case MINT_LINUX:
                case UBUNTU_LINUX:
                case FEDORA_LINUX:
                case OPENSUS_LINUX:
                case CENTOS_LINUX:
                case SLACKWARE_LINUX:
                    return true;
            }
        }
        return false;
    }

}
